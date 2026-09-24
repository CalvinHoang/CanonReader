package com.canonreader.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether to notify about newly published posts, and how far notifications have got. */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _notifyNewPosts = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY, false))
    val notifyNewPosts: StateFlow<Boolean> = _notifyNewPosts.asStateFlow()

    fun setNotifyNewPosts(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFY, enabled) }
        _notifyNewPosts.value = enabled
    }

    /** Published-post count at the last notification (or when notifications were switched on). */
    var lastNotifiedCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, -1)
        set(value) = prefs.edit { putInt(KEY_LAST_COUNT, value) }

    private companion object {
        const val KEY_NOTIFY = "notify_new_posts"
        const val KEY_LAST_COUNT = "notify_last_count"
    }
}
