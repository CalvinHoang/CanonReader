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

/** How the app resolves light vs. dark colors. [SYSTEM] follows the OS setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persists the reader's theme choice in SharedPreferences and exposes it as a
 * [StateFlow] so both the Activity (which applies it) and the settings menu
 * (which changes it) stay in sync through this single source of truth.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    private fun readThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
