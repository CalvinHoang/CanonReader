package com.canonreader.app.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.domain.Author
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.TopRibbon
import com.canonreader.app.ui.post.PostDeckHolder
import com.canonreader.app.ui.theme.CanonOnRibbon
import com.canonreader.app.ui.theme.Cinzel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: CanonRepository,
    private val deckHolder: PostDeckHolder,
) : ViewModel() {

    private val _authors = MutableStateFlow<List<Author>>(emptyList())
    val authors: StateFlow<List<Author>> = _authors.asStateFlow()

    fun reload() {
        viewModelScope.launch { _authors.value = runCatching { repository.authors() }.getOrDefault(emptyList()) }
    }

    fun openRandom(onOpenPost: (Long) -> Unit) {
        viewModelScope.launch {
            repository.randomVisibleId()?.let { id ->
                deckHolder.ids = listOf(id)
                onOpenPost(id)
            }
        }
    }
}

@Composable
fun ExploreScreen(
    onSearch: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val authors by viewModel.authors.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopRibbon(
                actions = {
                    OverflowMenu(
                        onOpenSaved = onOpenSaved,
                        onOpenSettings = onOpenSettings,
                        onOpenAbout = onOpenAbout,
                    )
                },
            ) {
                Text(
                    "Explore",
                    fontFamily = Cinzel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    color = CanonOnRibbon,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            item(key = "search") { SearchField(onSearch = onSearch) }
            item(key = "random") {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    AssistChip(
                        onClick = { viewModel.openRandom(onOpenPost) },
                        label = { Text("Surprise me") },
                        leadingIcon = { Icon(Icons.Filled.Casino, contentDescription = null) },
                    )
                }
            }
            item(key = "authors-header") {
                Text(
                    "The authors",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 4.dp),
                )
            }
            items(authors, key = { it.id }) { author ->
                AuthorRow(author = author, onClick = { onOpenAuthor(author.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SearchField(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        placeholder = { Text("Search everything published so far") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (query.isNotBlank()) {
                focusManager.clearFocus()
                onSearch(query.trim())
            }
        }),
    )
}

@Composable
private fun AuthorRow(author: Author, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(author.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(author.years, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            author.currentWork?.let { "Now posting from $it" } ?: author.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        val fraction = if (author.postCount > 0) author.published.toFloat() / author.postCount else 0f
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            "${author.published} of ${author.postCount} posts published",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
