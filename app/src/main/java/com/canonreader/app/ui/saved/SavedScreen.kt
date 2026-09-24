package com.canonreader.app.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.data.SavedStore
import com.canonreader.app.domain.Post
import com.canonreader.app.ui.components.EmptyState
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.TitleRibbon
import com.canonreader.app.ui.post.PostDeckHolder
import com.canonreader.app.ui.postlist.PostRow
import com.canonreader.app.ui.theme.CanonInk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val repository: CanonRepository,
    private val savedStore: SavedStore,
    private val deckHolder: PostDeckHolder,
) : ViewModel() {

    /** Saved posts, most recently saved first; null while loading. */
    val posts: StateFlow<List<Post>?> = savedStore.entries
        .mapLatest { entries -> repository.postsByIds(entries.map { it.postId }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(id: Long) = savedStore.remove(id)

    fun rememberDeck() {
        deckHolder.ids = posts.value.orEmpty().map { it.id }
    }
}

@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    showBack: Boolean = true,
    viewModel: SavedViewModel = hiltViewModel(),
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TitleRibbon(
                title = "Saved",
                navigation = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CanonInk)
                        }
                    }
                },
                actions = { OverflowMenu(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout) },
            )
        },
    ) { innerPadding ->
        val list = posts
        when {
            list == null -> Box(Modifier.padding(innerPadding).fillMaxSize())
            list.isEmpty() -> EmptyState(
                "Nothing saved yet. Tap the bookmark on any post to keep it here.",
                Modifier.padding(innerPadding),
            )
            else -> LazyColumn(Modifier.padding(innerPadding).fillMaxSize()) {
                items(list, key = { it.id }) { post ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            PostRow(post = post, onClick = {
                                viewModel.rememberDeck()
                                onOpenPost(post.id)
                            })
                        }
                        IconButton(onClick = { viewModel.remove(post.id) }) {
                            Icon(
                                Icons.Filled.BookmarkRemove,
                                contentDescription = "Remove from saved",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
