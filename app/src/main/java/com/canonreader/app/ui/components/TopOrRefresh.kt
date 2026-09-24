package com.canonreader.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberTopOrRefreshAction(
    listState: LazyListState,
    onRefresh: () -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(listState, onRefresh, scope) {
        {
            if (listState.isAtTop()) {
                onRefresh()
            } else {
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }
}

private fun LazyListState.isAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
