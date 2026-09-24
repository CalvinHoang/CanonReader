package com.canonreader.app.ui.postlist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.TitleRibbon
import com.canonreader.app.ui.post.PostDeckHolder
import com.canonreader.app.ui.theme.CanonOnRibbon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What a post list shows: one author, one work in reading order, or search results. */
object ListKind {
    const val AUTHOR = "author"
    const val WORK = "work"
    const val SEARCH = "search"
}

@HiltViewModel
class PostListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CanonRepository,
    private val deckHolder: PostDeckHolder,
) : ViewModel() {

    val kind: String = checkNotNull(savedStateHandle["kind"])
    private val arg: String = checkNotNull(savedStateHandle["arg"])
    val title: String = checkNotNull(savedStateHandle["title"])

    val paginator = Paginator(viewModelScope) { offset, limit ->
        when (kind) {
            ListKind.AUTHOR -> repository.authorPage(arg, offset, limit)
            ListKind.WORK -> repository.workPage(arg, offset, limit)
            else -> repository.searchPage(arg, offset, limit)
        }
    }

    /** Record the current list order so the detail screen can swipe through it. */
    fun rememberDeck() {
        deckHolder.ids = paginator.state.value.posts.map { it.id }
    }
}

@Composable
fun PostListScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: PostListViewModel = hiltViewModel(),
) {
    val state by viewModel.paginator.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TitleRibbon(
                title = viewModel.title,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CanonOnRibbon)
                    }
                },
                actions = {
                    OverflowMenu(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout)
                },
            )
        },
    ) { innerPadding ->
        PostList(
            state = state,
            listState = listState,
            onOpenPost = { id ->
                viewModel.rememberDeck()
                onOpenPost(id)
            },
            onLoadMore = viewModel.paginator::loadNext,
            onRefresh = viewModel.paginator::refresh,
            modifier = Modifier.padding(innerPadding),
            showWork = viewModel.kind != ListKind.WORK,
            emptyMessage = when (viewModel.kind) {
                ListKind.SEARCH -> "No published posts match that search."
                ListKind.WORK -> "None of this work is out yet. Turn on \"Unlock everything\" in Settings to read ahead."
                else -> "Nothing from this author yet."
            },
            endMessage = when (viewModel.kind) {
                ListKind.WORK -> "That's everything published so far"
                ListKind.SEARCH -> "End of results"
                else -> "That's everything published so far"
            },
        )
    }
}
