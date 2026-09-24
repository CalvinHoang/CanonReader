package com.canonreader.app.ui.post

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canonreader.app.data.CanonRepository
import com.canonreader.app.data.SavedStore
import com.canonreader.app.domain.Neighbour
import com.canonreader.app.domain.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the ordered list of post ids the reader was browsing when they opened a post,
 * so the detail screen can swipe through the same deck: feed, author, work, search or
 * saved. Set by a list screen just before it navigates.
 */
@Singleton
class PostDeckHolder @Inject constructor() {
    @Volatile
    var ids: List<Long> = emptyList()
}

/** Resolves the deck the detail pager swipes through. */
@HiltViewModel
class PostPagerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deckHolder: PostDeckHolder,
) : ViewModel() {
    private val startId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val ids: List<Long> = deckHolder.ids.takeIf { startId in it } ?: listOf(startId)

    val startIndex: Int = ids.indexOf(startId).coerceAtLeast(0)
}

/** One instance backs one post in the pager (keyed by id); the pager [bind]s it. */
@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val repository: CanonRepository,
    private val savedStore: SavedStore,
) : ViewModel() {

    data class UiState(
        val post: Post? = null,
        val isLoading: Boolean = true,
        val previous: Neighbour? = null,
        val next: Neighbour? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _id = MutableStateFlow<Long?>(null)

    val isSaved: StateFlow<Boolean> = combine(_id, savedStore.entries) { id, entries ->
        id != null && entries.any { it.postId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun bind(id: Long) {
        if (_id.value == id) return
        _id.value = id
        _state.value = UiState()
        viewModelScope.launch {
            val post = runCatching { repository.post(id) }.getOrNull()
            _state.value = UiState(post = post, isLoading = false)
            if (post != null) {
                val previous = runCatching { repository.neighbour(post, -1) }.getOrNull()
                val next = runCatching { repository.neighbour(post, +1) }.getOrNull()
                _state.value = _state.value.copy(previous = previous, next = next)
            }
        }
    }

    fun toggleSave() {
        _id.value?.let { savedStore.toggle(it) }
    }
}
