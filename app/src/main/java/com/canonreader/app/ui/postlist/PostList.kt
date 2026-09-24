package com.canonreader.app.ui.postlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.canonreader.app.domain.Post
import com.canonreader.app.domain.toShortDisplayString
import com.canonreader.app.ui.components.EmptyState
import com.canonreader.app.ui.components.ErrorState
import com.canonreader.app.ui.components.LoadingState
import com.canonreader.app.ui.components.RefreshableBox

/**
 * Infinite-scrolling post list over a [Paginator.State]: renders rows, requests the
 * next page as the reader nears the bottom, and shows footer loading/error/end states.
 */
@Composable
fun PostList(
    state: Paginator.State,
    listState: LazyListState,
    onOpenPost: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No posts yet.",
    endMessage: String = "That's everything published so far",
    showWork: Boolean = true,
    header: (LazyListScope.() -> Unit)? = null,
) {
    when {
        state.isLoadingInitial -> LoadingState(modifier)
        state.initialError != null -> ErrorState(state.initialError, modifier, onRetry = onRefresh)
        state.posts.isEmpty() && header == null -> EmptyState(emptyMessage, modifier)
        else -> {
            LoadMoreEffect(listState, state, onLoadMore)
            RefreshableBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier,
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    header?.invoke(this)
                    items(state.posts, key = { it.id }) { post ->
                        PostRow(post = post, showWork = showWork, onClick = { onOpenPost(post.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item(key = "footer") {
                        ListFooter(state, onLoadMore, if (state.posts.isEmpty()) emptyMessage else endMessage)
                    }
                }
            }
        }
    }
}

@Composable
fun PostRow(post: Post, onClick: () -> Unit, showWork: Boolean = true) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = post.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(
                post.authorName.takeIf { it.isNotBlank() }?.let { "by $it" },
                post.workTitle.takeIf { showWork && it.isNotBlank() },
                post.publishedAt?.toShortDisplayString(),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    state: Paginator.State,
    onLoadMore: () -> Unit,
) {
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearBottom, state.posts.size, state.endReached, state.loadMoreError) {
        if (nearBottom && !state.endReached && state.loadMoreError == null &&
            !state.isLoadingMore && !state.isRefreshing
        ) {
            onLoadMore()
        }
    }
}

@Composable
private fun ListFooter(state: Paginator.State, onLoadMore: () -> Unit, endMessage: String) {
    when {
        state.isLoadingMore -> Box(
            Modifier.fillMaxWidth().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.padding(4.dp))
        }
        state.loadMoreError != null -> Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.loadMoreError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onLoadMore) { Text("Retry") }
        }
        state.endReached -> Text(
            endMessage,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            textAlign = TextAlign.Center,
        )
    }
}
