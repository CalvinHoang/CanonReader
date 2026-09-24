package com.canonreader.app.ui.theme

import androidx.lifecycle.ViewModel
import com.canonreader.app.data.preferences.ThemeMode
import com.canonreader.app.data.preferences.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the persisted [ThemeMode] and lets the UI change it. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    fun setThemeMode(mode: ThemeMode) = themePreferences.setThemeMode(mode)

    fun toggleDarkMode() {
        val next = if (themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        themePreferences.setThemeMode(next)
    }
}
