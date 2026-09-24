package com.canonreader.app.ui.author

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.domain.Author
import com.canonreader.app.domain.Work
import com.canonreader.app.domain.readingMinutes
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.TitleRibbon
import com.canonreader.app.ui.theme.CanonOnRibbon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CanonRepository,
) : ViewModel() {
    private val authorId: String = checkNotNull(savedStateHandle["authorId"])

    data class UiState(val author: Author? = null, val works: List<Work> = emptyList())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            _state.value = UiState(
                author = runCatching { repository.author(authorId) }.getOrNull(),
                works = runCatching { repository.works(authorId) }.getOrDefault(emptyList()),
            )
        }
    }
}

@Composable
fun AuthorScreen(
    onBack: () -> Unit,
    onOpenAuthorPosts: (authorId: String, name: String) -> Unit,
    onOpenWork: (workId: String, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: AuthorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    val author = state.author

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TitleRibbon(
                title = author?.name ?: "",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CanonOnRibbon)
                    }
                },
                actions = { OverflowMenu(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout) },
            )
        },
    ) { innerPadding ->
        LazyColumn(Modifier.padding(innerPadding).fillMaxSize()) {
            if (author != null) {
                item(key = "header") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(author.name, style = MaterialTheme.typography.headlineSmall)
                        Text(author.years, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(author.note, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${author.published} of ${author.postCount} posts published · " +
                                "about ${readingMinutes(author.words) / 60} hours of reading in all",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onOpenAuthorPosts(author.id, author.name) },
                            enabled = author.published > 0,
                        ) { Text("Latest from ${author.name}") }
                    }
                }
                item(key = "works-header") {
                    Text(
                        "Works, in the order they're posted",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }
            items(state.works, key = { it.id }) { work ->
                WorkRow(work = work, onClick = { onOpenWork(work.id, work.title) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun WorkRow(work: Work, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(work.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            work.year?.let {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        work.translator?.let {
            Text("Translated by $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fraction = if (work.postCount > 0) work.published.toFloat() / work.postCount else 0f
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            when {
                work.published >= work.postCount -> "All ${work.postCount} parts out"
                work.published == 0 -> "${work.postCount} parts · not started yet"
                else -> "${work.published} of ${work.postCount} parts out"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
