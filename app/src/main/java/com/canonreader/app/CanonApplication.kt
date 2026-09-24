package com.canonreader.app

import android.app.Application
import androidx.work.Configuration
import com.canonreader.app.notifications.NewPostsScheduler
import com.canonreader.app.notifications.NewPostsWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CanonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: NewPostsWorkerFactory

    @Inject
    lateinit var newPostsScheduler: dagger.Lazy<NewPostsScheduler>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Keep the periodic new-posts check aligned with settings.
        newPostsScheduler.get().sync()
    }
}
