package com.canonreader.app.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One blog post: a passage cut from a work at the author's own boundaries.
 * [id] is the post's place in the feed (0 = the blog's first post ever).
 */
data class Post(
    val id: Long,
    val title: String,
    val authorId: String,
    val authorName: String,
    val workId: String,
    val workTitle: String,
    val workSeq: Int,
    /** Where the passage sits in the work, e.g. "Book I · Part 3 of 9". */
    val section: String,
    val excerpt: String,
    /** Full body HTML. Empty for list rows, which only need the excerpt. */
    val html: String,
    val words: Int,
    val translator: String?,
    val publishedAt: ZonedDateTime?,
)

data class Author(
    val id: String,
    val name: String,
    val years: String,
    val note: String,
    val postCount: Int,
    val words: Int,
    /** How many of this author's posts are out so far. */
    val published: Int,
    /** The work the author is currently posting from, if any. */
    val currentWork: String?,
)

data class Work(
    val id: String,
    val authorId: String,
    val title: String,
    val year: Int?,
    val translator: String?,
    val postCount: Int,
    val words: Int,
    val published: Int,
)

/** The previous or next part of the same work. */
data class Neighbour(
    val id: Long,
    val title: String,
    val isPublished: Boolean,
    val publishedAt: ZonedDateTime?,
)

private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
private val shortDisplayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val arrivalFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM 'at' h:mm a")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun ZonedDateTime.toDisplayString(): String = format(displayFormatter)
fun ZonedDateTime.toShortDisplayString(): String = format(shortDisplayFormatter)
fun ZonedDateTime.toArrivalString(): String = format(arrivalFormatter)
fun ZonedDateTime.toTimeString(): String = format(timeFormatter)

/** Minutes to read at an unhurried 230 words a minute. */
fun readingMinutes(words: Int): Int = max(1, (words / 230.0).roundToInt())
