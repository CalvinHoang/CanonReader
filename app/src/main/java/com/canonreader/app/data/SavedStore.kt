package com.canonreader.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookmarked posts. The text itself is bundled with the app, so only post ids (and
 * when they were saved) need storing: "id:savedAtMillis" entries, newest first.
 */
@Singleton
class SavedStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class Entry(val postId: Long, val savedAtEpochMs: Long)

    private val prefs: SharedPreferences = context.getSharedPreferences("saved", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun isSaved(postId: Long): Boolean = _entries.value.any { it.postId == postId }

    fun toggle(postId: Long) {
        val current = _entries.value
        val next = if (current.any { it.postId == postId }) {
            current.filterNot { it.postId == postId }
        } else {
            listOf(Entry(postId, System.currentTimeMillis())) + current
        }
        write(next)
    }

    fun remove(postId: Long) = write(_entries.value.filterNot { it.postId == postId })

    private fun write(entries: List<Entry>) {
        prefs.edit { putString(KEY, entries.joinToString(",") { "${it.postId}:${it.savedAtEpochMs}" }) }
        _entries.value = entries
    }

    private fun read(): List<Entry> =
        prefs.getString(KEY, "").orEmpty()
            .split(',')
            .mapNotNull { token ->
                val parts = token.split(':')
                val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                Entry(id, parts.getOrNull(1)?.toLongOrNull() ?: 0L)
            }

    private companion object {
        const val KEY = "saved_posts"
    }
}
