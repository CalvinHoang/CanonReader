package com.canonreader.app.ui.postlist

import com.canonreader.app.domain.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Page-by-page loader over any post query (feed, author, work, search). A short page
 * means the end has been reached.
 */
class Paginator(
    private val scope: CoroutineScope,
    private val pageSize: Int = 30,
    private val load: suspend (offset: Int, limit: Int) -> List<Post>,
) {
    data class State(
        val posts: List<Post> = emptyList(),
        val isLoadingInitial: Boolean = true,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        /** Failure before any posts arrived: render full-screen with retry. */
        val initialError: String? = null,
        /** Failure while appending: render as a footer with retry. */
        val loadMoreError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    init {
        loadNext()
    }

    fun refresh() {
        job?.cancel()
        _state.update {
            it.copy(
                isLoadingInitial = it.posts.isEmpty(),
                isRefreshing = it.posts.isNotEmpty(),
                isLoadingMore = false,
                initialError = null,
                loadMoreError = null,
            )
        }
        job = scope.launch {
            try {
                // Reload at least as many posts as are on screen so the list doesn't jump.
                val want = maxOf(pageSize, _state.value.posts.size)
                val posts = load(0, want)
                _state.update {
                    it.copy(
                        posts = posts,
                        isLoadingInitial = false,
                        isRefreshing = false,
                        endReached = posts.size < want,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingInitial = false,
                        isRefreshing = false,
                        initialError = if (it.posts.isEmpty()) e.message ?: "Couldn't load posts" else null,
                    )
                }
            }
        }
    }

    fun loadNext() {
        if (job?.isActive == true) return
        if (_state.value.endReached) return
        val offset = _state.value.posts.size
        job = scope.launch {
            _state.update {
                it.copy(
                    isLoadingInitial = offset == 0,
                    isLoadingMore = offset > 0,
                    initialError = null,
                    loadMoreError = null,
                )
            }
            try {
                val page = load(offset, pageSize)
                _state.update {
                    it.copy(
                        posts = (it.posts + page).distinctBy { post -> post.id },
                        isLoadingInitial = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        endReached = page.size < pageSize,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingInitial = false,
                        isLoadingMore = false,
                        initialError = if (offset == 0) e.message ?: "Couldn't load posts" else it.initialError,
                        loadMoreError = if (offset > 0) e.message ?: "Couldn't load more posts" else null,
                    )
                }
            }
        }
    }
}
