package com.canonreader.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.data.DripSchedule
import com.canonreader.app.data.preferences.NotificationPreferences
import com.canonreader.app.data.preferences.ThemeMode
import com.canonreader.app.data.preferences.ThemePreferences
import com.canonreader.app.notifications.NewPostsScheduler
import com.canonreader.app.ui.components.TitleRibbon
import com.canonreader.app.ui.theme.CanonInk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val notificationPreferences: NotificationPreferences,
    private val scheduler: NewPostsScheduler,
    private val drip: DripSchedule,
    private val repository: CanonRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
    val perDay: StateFlow<Int> = drip.perDay
    val unlockAll: StateFlow<Boolean> = drip.unlockAll
    val notifyNewPosts: StateFlow<Boolean> = notificationPreferences.notifyNewPosts

    fun setThemeMode(mode: ThemeMode) = themePreferences.setThemeMode(mode)

    fun setPerDay(n: Int) {
        viewModelScope.launch { drip.setPerDay(n, repository.total()) }
    }

    fun setUnlockAll(enabled: Boolean) = drip.setUnlockAll(enabled)

    fun setNotifyNewPosts(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Start from what's already out, so switching on doesn't announce a backlog.
                notificationPreferences.lastNotifiedCount = drip.published(repository.total())
            }
            notificationPreferences.setNotifyNewPosts(enabled)
            scheduler.sync()
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val perDay by viewModel.perDay.collectAsStateWithLifecycle()
    val unlockAll by viewModel.unlockAll.collectAsStateWithLifecycle()
    val notify by viewModel.notifyNewPosts.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.setNotifyNewPosts(true) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TitleRibbon(
                title = "Settings",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CanonInk)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Publishing")
            Note("How many posts come out each day, spread between 6:30 am and 9:30 pm. Changing it keeps everything already published.")
            DripSchedule.PER_DAY_OPTIONS.forEach { n ->
                OptionRow("$n a day", selected = perDay == n) { viewModel.setPerDay(n) }
            }
            SwitchRow(
                title = "Unlock everything",
                subtitle = "Show the whole archive now, as if the blog had already finished. Turn off to go back to the daily schedule.",
                checked = unlockAll,
                onCheckedChange = viewModel::setUnlockAll,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Notifications")
            SwitchRow(
                title = "New posts",
                subtitle = "A notification when new posts are published.",
                checked = notify,
                onCheckedChange = { checked ->
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotifyNewPosts(checked)
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Appearance")
            OptionRow("Follow system", themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            OptionRow("Light", themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            OptionRow("Dark", themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
