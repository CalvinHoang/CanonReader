package com.canonreader.app.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.canonreader.app.domain.Author
import com.canonreader.app.domain.Neighbour
import com.canonreader.app.domain.Post
import com.canonreader.app.domain.Work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the screens read: the feed, authors, works, search and single posts,
 * always filtered to what the publishing calendar has released (or everything, when
 * the archive is unlocked).
 */
@Singleton
class CanonRepository @Inject constructor(
    private val database: CanonDatabase,
    private val drip: DripSchedule,
) {
    private data class AuthorMeta(val id: String, val name: String, val years: String, val note: String,
                                  val postCount: Int, val words: Int)

    private data class WorkMeta(val id: String, val authorId: String, val title: String, val year: Int?,
                                val translator: String?, val postCount: Int, val words: Int)

    @Volatile private var authorsMeta: List<AuthorMeta>? = null
    @Volatile private var worksMeta: List<WorkMeta>? = null
    @Volatile private var totalPosts: Int = -1

    private suspend fun <T> read(block: (SQLiteDatabase) -> T): T {
        val db = database.get()
        return withContext(Dispatchers.IO) { block(db) }
    }

    /** Loads the small author and work tables once. */
    private suspend fun ensureMeta() {
        if (authorsMeta != null && worksMeta != null && totalPosts >= 0) return
        read { db ->
            val authors = mutableListOf<AuthorMeta>()
            db.rawQuery("SELECT id, name, years, note, post_count, words FROM authors ORDER BY sort", null).use { c ->
                while (c.moveToNext()) {
                    authors += AuthorMeta(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5))
                }
            }
            val works = mutableListOf<WorkMeta>()
            db.rawQuery("SELECT id, author_id, title, year, translator, post_count, words FROM works ORDER BY sort", null).use { c ->
                while (c.moveToNext()) {
                    works += WorkMeta(
                        id = c.getString(0),
                        authorId = c.getString(1),
                        title = c.getString(2),
                        year = if (c.isNull(3)) null else c.getInt(3),
                        translator = if (c.isNull(4)) null else c.getString(4),
                        postCount = c.getInt(5),
                        words = c.getInt(6),
                    )
                }
            }
            val total = db.rawQuery("SELECT COUNT(*) FROM posts", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            authorsMeta = authors
            worksMeta = works
            totalPosts = total
        }
    }

    suspend fun total(): Int {
        ensureMeta()
        return totalPosts
    }

    /** Number of posts visible now; posts with id below this are readable. */
    suspend fun visibleCount(): Int = drip.visible(total())

    // --- Lists -----------------------------------------------------------------------

    /** The blog: newest first. */
    suspend fun feedPage(offset: Int, limit: Int): List<Post> {
        val visible = visibleCount()
        return listQuery("WHERE id < $visible ORDER BY id DESC LIMIT $limit OFFSET $offset", emptyArray())
    }

    /** One author's posts, newest first. */
    suspend fun authorPage(authorId: String, offset: Int, limit: Int): List<Post> {
        val visible = visibleCount()
        return listQuery(
            "WHERE author_id = ? AND id < $visible ORDER BY id DESC LIMIT $limit OFFSET $offset",
            arrayOf(authorId),
        )
    }

    /** One work's posts in reading order. */
    suspend fun workPage(workId: String, offset: Int, limit: Int): List<Post> {
        val visible = visibleCount()
        return listQuery(
            "WHERE work_id = ? AND id < $visible ORDER BY work_seq ASC LIMIT $limit OFFSET $offset",
            arrayOf(workId),
        )
    }

    /** Full-text search over titles and text, newest first; falls back to a plain scan. */
    suspend fun searchPage(query: String, offset: Int, limit: Int): List<Post> {
        val visible = visibleCount()
        val fts = ftsQuery(query) ?: return emptyList()
        return try {
            listQuery(
                "WHERE id IN (SELECT docid FROM posts_fts WHERE posts_fts MATCH ?) AND id < $visible " +
                    "ORDER BY id DESC LIMIT $limit OFFSET $offset",
                arrayOf(fts),
            )
        } catch (e: SQLiteException) {
            val like = "%" + query.trim().replace("%", "").replace("_", "") + "%"
            listQuery(
                "WHERE (title LIKE ? OR html LIKE ?) AND id < $visible ORDER BY id DESC LIMIT $limit OFFSET $offset",
                arrayOf(like, like),
            )
        }
    }

    /** Turns what the reader typed into an FTS query: quoted phrases kept, other words prefix-matched. */
    private fun ftsQuery(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = mutableListOf<String>()
        Regex("\"([^\"]+)\"|([\\p{L}\\p{N}]+)").findAll(trimmed).forEach { m ->
            val phrase = m.groups[1]?.value
            val word = m.groups[2]?.value
            if (phrase != null) {
                val words = Regex("[\\p{L}\\p{N}]+").findAll(phrase).map { it.value }.toList()
                if (words.isNotEmpty()) parts += "\"" + words.joinToString(" ") + "\""
            } else if (word != null) {
                parts += "$word*"
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    suspend fun postsByIds(ids: List<Long>): List<Post> {
        if (ids.isEmpty()) return emptyList()
        val found = listQuery("WHERE id IN (${ids.joinToString(",")})", emptyArray()).associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    // --- Single posts ------------------------------------------------------------------

    suspend fun post(id: Long): Post? {
        ensureMeta()
        val total = totalPosts
        return read { db ->
            db.rawQuery("SELECT $COLUMNS, html FROM posts WHERE id = $id", null).use { c ->
                if (c.moveToFirst()) toPost(c, html = c.getString(COLUMN_COUNT), total = total) else null
            }
        }
    }

    suspend fun isVisible(id: Long): Boolean = id < visibleCount()

    /** The part before ([delta] = -1) or after (+1) [post] in its work. */
    suspend fun neighbour(post: Post, delta: Int): Neighbour? {
        val visible = visibleCount()
        val total = total()
        return read { db ->
            db.rawQuery(
                "SELECT id, title FROM posts WHERE work_id = ? AND work_seq = ${post.workSeq + delta}",
                arrayOf(post.workId),
            ).use { c ->
                if (!c.moveToFirst()) return@use null
                val id = c.getLong(0)
                Neighbour(
                    id = id,
                    title = c.getString(1),
                    isPublished = id < visible,
                    publishedAt = drip.displayTime(id, total),
                )
            }
        }
    }

    suspend fun randomVisibleId(): Long? {
        val visible = visibleCount()
        if (visible <= 0) return null
        return (0 until visible).random().toLong()
    }

    // --- Authors and works -------------------------------------------------------------

    suspend fun authors(): List<Author> {
        ensureMeta()
        val visible = visibleCount()
        val published = countBy("author_id", visible)
        val current = currentWorks(visible)
        return authorsMeta.orEmpty().map { a ->
            Author(
                id = a.id,
                name = a.name,
                years = a.years,
                note = a.note,
                postCount = a.postCount,
                words = a.words,
                published = published[a.id] ?: 0,
                currentWork = current[a.id],
            )
        }
    }

    suspend fun author(id: String): Author? = authors().firstOrNull { it.id == id }

    suspend fun works(authorId: String): List<Work> {
        ensureMeta()
        val visible = visibleCount()
        val published = countBy("work_id", visible)
        return worksMeta.orEmpty().filter { it.authorId == authorId }.map { w ->
            Work(
                id = w.id,
                authorId = w.authorId,
                title = w.title,
                year = w.year,
                translator = w.translator,
                postCount = w.postCount,
                words = w.words,
                published = published[w.id] ?: 0,
            )
        }
    }

    suspend fun workTitle(workId: String): String? {
        ensureMeta()
        return worksMeta.orEmpty().firstOrNull { it.id == workId }?.title
    }

    private suspend fun countBy(column: String, visible: Int): Map<String, Int> = read { db ->
        val out = mutableMapOf<String, Int>()
        db.rawQuery("SELECT $column, COUNT(*) FROM posts WHERE id < $visible GROUP BY $column", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
        }
        out
    }

    /** For each author, the title of the work their latest visible post comes from. */
    private suspend fun currentWorks(visible: Int): Map<String, String> {
        ensureMeta()
        val titles = worksMeta.orEmpty().associate { it.id to it.title }
        return read { db ->
            val out = mutableMapOf<String, String>()
            db.rawQuery(
                "SELECT author_id, work_id FROM posts WHERE id IN " +
                    "(SELECT MAX(id) FROM posts WHERE id < $visible GROUP BY author_id)",
                null,
            ).use { c ->
                while (c.moveToNext()) titles[c.getString(1)]?.let { out[c.getString(0)] = it }
            }
            out
        }
    }

    // --- Row mapping -------------------------------------------------------------------

    private suspend fun listQuery(where: String, args: Array<String>): List<Post> {
        ensureMeta()
        val total = totalPosts
        return read { db ->
            db.rawQuery("SELECT $COLUMNS FROM posts $where", args).use { c ->
                val out = ArrayList<Post>(c.count)
                while (c.moveToNext()) out += toPost(c, html = "", total = total)
                out
            }
        }
    }

    private fun toPost(c: Cursor, html: String, total: Int): Post {
        val id = c.getLong(0)
        val authorId = c.getString(1)
        val workId = c.getString(2)
        val work = worksMeta.orEmpty().firstOrNull { it.id == workId }
        return Post(
            id = id,
            title = c.getString(4),
            authorId = authorId,
            authorName = authorsMeta.orEmpty().firstOrNull { it.id == authorId }?.name.orEmpty(),
            workId = workId,
            workTitle = work?.title.orEmpty(),
            workSeq = c.getInt(3),
            section = c.getString(5),
            excerpt = c.getString(6),
            html = html,
            words = c.getInt(7),
            translator = work?.translator,
            publishedAt = drip.displayTime(id, total),
        )
    }

    private companion object {
        const val COLUMNS = "id, author_id, work_id, work_seq, title, section, excerpt, words"
        const val COLUMN_COUNT = 8
    }
}
