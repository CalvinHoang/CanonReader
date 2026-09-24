package com.canonreader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canonreader.app.data.preferences.ThemeMode
import com.canonreader.app.notifications.PendingOpenHolder
import com.canonreader.app.ui.navigation.CanonApp
import com.canonreader.app.ui.theme.CanonTheme
import com.canonreader.app.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    @Inject
    lateinit var pendingOpenHolder: PendingOpenHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotificationTarget(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CanonTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CanonApp(pendingOpenHolder)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureNotificationTarget(intent)
    }

    private fun captureNotificationTarget(intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA_OPEN_POST_ID)) return
        val id = intent.getLongExtra(EXTRA_OPEN_POST_ID, -1L)
        if (id >= 0) pendingOpenHolder.set(id)
        intent.removeExtra(EXTRA_OPEN_POST_ID)
    }

    companion object {
        const val EXTRA_OPEN_POST_ID = "open_post_id"
    }
}
