package com.canonreader.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.data.DripSchedule
import com.canonreader.app.domain.toArrivalString
import com.canonreader.app.domain.toTimeString
import com.canonreader.app.ui.components.CanonMasthead
import com.canonreader.app.ui.components.OverflowMenu
import com.canonreader.app.ui.components.rememberTopOrRefreshAction
import com.canonreader.app.ui.post.PostDeckHolder
import com.canonreader.app.ui.postlist.Paginator
import com.canonreader.app.ui.postlist.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: CanonRepository,
    private val drip: DripSchedule,
    private val deckHolder: PostDeckHolder,
) : ViewModel() {

    val paginator = Paginator(viewModelScope) { offset, limit -> repository.feedPage(offset, limit) }

    /** A one-line note on the publishing calendar, shown above the feed. */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var lastVisible = -1

    init {
        viewModelScope.launch { updateStatus() }
        viewModelScope.launch {
            // Settings changed the calendar: reload from the top.
            drip.changes.drop(1).collect {
                lastVisible = -1
                paginator.refresh()
                updateStatus()
            }
        }
    }

    /** Called when the feed comes back on screen: pick up anything published meanwhile. */
    fun onResume() {
        viewModelScope.launch {
            val visible = repository.visibleCount()
            if (lastVisible >= 0 && visible != lastVisible) paginator.refresh()
            lastVisible = visible
            updateStatus()
        }
    }

    fun refresh() {
        paginator.refresh()
        viewModelScope.launch { updateStatus() }
    }

    private suspend fun updateStatus() {
        val total = repository.total()
        if (drip.unlockAll.value) {
            _status.value = "The whole archive is open: $total posts."
            return
        }
        val today = drip.publishedToday(total)
        val next = drip.nextArrival(total)
        _status.value = when {
            next == null -> "Every post is out. The blog is complete."
            next.toLocalDate() == LocalDate.now(next.zone) ->
                "$today new today · next post at ${next.toTimeString()}"
            else -> "$today new today · next post ${next.toArrivalString()}"
        }
    }

    /** Record the current feed order so the detail screen can swipe through it. */
    fun rememberDeck() {
        deckHolder.ids = paginator.state.value.posts.map { it.id }
    }
}

@Composable
fun FeedScreen(
    onOpenPost: (Long) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.paginator.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val onHeaderTap = rememberTopOrRefreshAction(listState, viewModel::refresh)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val statusText = status
    val statusHeader: LazyListScope.() -> Unit = {
        item(key = "status") {
            Text(
                text = statusText.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        CanonMasthead(
            onTitleClick = onHeaderTap,
            actions = {
                OverflowMenu(
                    onOpenSaved = onOpenSaved,
                    onOpenSettings = onOpenSettings,
                    onOpenAbout = onOpenAbout,
                )
            },
        )
        PostList(
            state = state,
            listState = listState,
            onOpenPost = { id ->
                viewModel.rememberDeck()
                onOpenPost(id)
            },
            onLoadMore = viewModel.paginator::loadNext,
            onRefresh = viewModel::refresh,
            emptyMessage = "Nothing published yet. The first posts arrive this morning.",
            endMessage = "You've reached the blog's first post",
            header = if (statusText != null) statusHeader else null,
        )
    }
}
