package com.canonreader.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The publishing calendar. Posts come out [perDay] times a day, spread between
 * 6:30 am and 9:30 pm, starting from the day the app was installed (with a few days
 * of backlog so the blog doesn't open empty). "Unlock everything" shows the whole
 * archive as if the blog had already finished, dated so its last post lands today.
 *
 * Everything is derived from three stored numbers, so the schedule survives restarts
 * and needs no background work to advance:
 *   released(now) = base + perDay × (days since startDay) + today's slots already passed
 */
@Singleton
class DripSchedule @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("drip", Context.MODE_PRIVATE)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val _perDay = MutableStateFlow(prefs.getInt(KEY_PER_DAY, DEFAULT_PER_DAY))
    val perDay: StateFlow<Int> = _perDay.asStateFlow()

    private val _unlockAll = MutableStateFlow(prefs.getBoolean(KEY_UNLOCK_ALL, false))
    val unlockAll: StateFlow<Boolean> = _unlockAll.asStateFlow()

    /** Bumped whenever the schedule changes, so screens know to reload. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        if (!prefs.contains(KEY_START_DAY)) {
            prefs.edit {
                putLong(KEY_START_DAY, LocalDate.now(zone).toEpochDay())
                putInt(KEY_BASE, INITIAL_BACKLOG_DAYS * _perDay.value)
            }
        }
    }

    private val startDay: Long get() = prefs.getLong(KEY_START_DAY, LocalDate.now(zone).toEpochDay())
    private val base: Int get() = prefs.getInt(KEY_BASE, 0)

    /** Time of day for the [slot]th post of a day with [perDay] posts. */
    private fun slotTime(slot: Int, perDay: Int): LocalTime {
        val minutes = FIRST_POST_MINUTE + (slot * POSTING_WINDOW_MINUTES) / perDay
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun slotsPassed(perDay: Int, now: LocalTime): Int =
        (0 until perDay).count { !slotTime(it, perDay).isAfter(now) }

    /** How many posts the calendar has published by [now] (ignores unlock-all). */
    fun published(total: Int, now: LocalDateTime = LocalDateTime.now(zone)): Int {
        val pd = _perDay.value
        val days = (now.toLocalDate().toEpochDay() - startDay).coerceAtLeast(0)
        val n = base.toLong() + pd.toLong() * days + slotsPassed(pd, now.toLocalTime())
        return n.coerceIn(0L, total.toLong()).toInt()
    }

    /** How many posts the reader can see right now. */
    fun visible(total: Int): Int = if (_unlockAll.value) total else published(total)

    /** When post [id] comes out on the calendar. */
    fun releaseTime(id: Long): LocalDateTime {
        val pd = _perDay.value
        val idx = id - base
        val day: Long
        val slot: Int
        if (idx >= 0) {
            day = startDay + idx / pd
            slot = (idx % pd).toInt()
        } else {
            val back = -idx - 1
            day = startDay - 1 - back / pd
            slot = (pd - 1 - back % pd).toInt()
        }
        return LocalDateTime.of(LocalDate.ofEpochDay(day), slotTime(slot, pd))
    }

    /** The date shown on a post. Unlocked, the calendar shifts so the last post is today. */
    fun displayTime(id: Long, total: Int): ZonedDateTime {
        val t = releaseTime(id)
        if (!_unlockAll.value || total <= 0) return t.atZone(zone)
        val last = releaseTime((total - 1).toLong()).toLocalDate().toEpochDay()
        val shift = (last - LocalDate.now(zone).toEpochDay()).coerceAtLeast(0)
        return t.minusDays(shift).atZone(zone)
    }

    /** When the next unpublished post arrives, or null when everything is out. */
    fun nextArrival(total: Int): ZonedDateTime? {
        val next = published(total)
        if (next >= total) return null
        return releaseTime(next.toLong()).atZone(zone)
    }

    /** Posts that came out today on the calendar. */
    fun publishedToday(total: Int): Int {
        val now = LocalDateTime.now(zone)
        val startOfDay = now.toLocalDate().atStartOfDay().minusNanos(1)
        return published(total, now) - published(total, startOfDay)
    }

    /** Change the rate from today on. Everything already out stays out. */
    fun setPerDay(newPerDay: Int, total: Int) {
        if (newPerDay == _perDay.value || newPerDay <= 0) return
        val current = published(total)
        val todaySlots = slotsPassed(newPerDay, LocalTime.now(zone))
        prefs.edit {
            putInt(KEY_PER_DAY, newPerDay)
            putInt(KEY_BASE, (current - todaySlots).coerceAtLeast(0))
            putLong(KEY_START_DAY, LocalDate.now(zone).toEpochDay())
        }
        _perDay.value = newPerDay
        _changes.value += 1
    }

    fun setUnlockAll(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_UNLOCK_ALL, enabled) }
        _unlockAll.value = enabled
        _changes.value += 1
    }

    companion object {
        const val DEFAULT_PER_DAY = 6
        val PER_DAY_OPTIONS = listOf(3, 6, 10, 20)
        private const val INITIAL_BACKLOG_DAYS = 3
        private const val FIRST_POST_MINUTE = 6 * 60 + 30
        private const val POSTING_WINDOW_MINUTES = 15 * 60

        private const val KEY_PER_DAY = "per_day"
        private const val KEY_UNLOCK_ALL = "unlock_all"
        private const val KEY_START_DAY = "start_epoch_day"
        private const val KEY_BASE = "base_count"
    }
}
