package com.canonreader.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.canonreader.app.MainActivity
import com.canonreader.app.R
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.data.DripSchedule
import com.canonreader.app.data.preferences.NotificationPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the periodic "new posts" check scheduled while notifications are on. */
@Singleton
class NewPostsScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationPreferences: NotificationPreferences,
) {
    fun sync() {
        val workManager = WorkManager.getInstance(context)
        if (!notificationPreferences.notifyNewPosts.value) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // Posts come out on a fixed calendar, so no network is needed: the worker only
        // compares the calendar with what it last announced.
        val request = PeriodicWorkRequestBuilder<NewPostsWorker>(2, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "new-posts-periodic"
    }
}

/**
 * Builds [NewPostsWorker] with its injected dependencies. Written by hand instead of
 * androidx.hilt's @HiltWorker, whose annotation processor can't read Kotlin 2.4 metadata.
 */
@Singleton
class NewPostsWorkerFactory @Inject constructor(
    private val newPostsWorker: NewPostsWorker.Factory,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        NewPostsWorker::class.java.name -> newPostsWorker.create(appContext, workerParameters)
        else -> null
    }
}

class NewPostsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CanonRepository,
    private val drip: DripSchedule,
    private val notificationPreferences: NotificationPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!notificationPreferences.notifyNewPosts.value || drip.unlockAll.value || !canNotify()) {
            return Result.success()
        }
        val total = repository.total()
        val published = drip.published(total)
        val last = notificationPreferences.lastNotifiedCount
        notificationPreferences.lastNotifiedCount = published
        if (last < 0 || published <= last) return Result.success()

        val fresh = repository.postsByIds(((last until published).map { it.toLong() }).takeLast(MAX_LINES))
            .sortedByDescending { it.id }
        if (fresh.isEmpty()) return Result.success()
        val count = published - last
        val title = if (count == 1) fresh.first().title else "$count new posts"
        val lines = fresh.map { "${it.authorName}: ${it.title}" }
        notify(
            title = title,
            text = if (count == 1) "${fresh.first().authorName} · ${fresh.first().workTitle}" else lines.first(),
            lines = if (count == 1) emptyList() else lines,
            openPostId = if (count == 1) fresh.first().id else null,
        )
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(title: String, text: String, lines: List<String>, openPostId: Long?) {
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New posts", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openPostId != null) putExtra(MainActivity.EXTRA_OPEN_POST_ID, openPostId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = if (lines.isEmpty()) {
            NotificationCompat.BigTextStyle().bigText(text)
        } else {
            NotificationCompat.InboxStyle().also { inbox -> lines.forEach { inbox.addLine(it) } }
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        if (canNotify()) {
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): NewPostsWorker
    }

    private companion object {
        const val CHANNEL_ID = "new_posts"
        const val NOTIFICATION_ID = 1001
        const val MAX_LINES = 6
    }
}

/**
 * Hands a notification tap's target from MainActivity's intent to the nav graph.
 * The graph consumes the value so process restarts don't re-navigate.
 */
@Singleton
class PendingOpenHolder @Inject constructor() {
    private val _pendingPostId = MutableStateFlow<Long?>(null)
    val pendingPostId: StateFlow<Long?> = _pendingPostId.asStateFlow()

    fun set(postId: Long) {
        _pendingPostId.value = postId
    }

    fun consume() {
        _pendingPostId.value = null
    }
}
