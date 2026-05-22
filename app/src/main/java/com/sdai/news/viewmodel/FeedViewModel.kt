package com.sdai.news.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdai.news.data.Article
import com.sdai.news.data.ArticleRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level UI state for the feed. */
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Ready(val articles: List<Article>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

/**
 * Owns the article feed state. Constructed by the default
 * [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory] —
 * screens can call `viewModel<FeedViewModel>()` without a custom factory.
 *
 * UI state is derived (`combine`) from two upstreams so a Room flow
 * re-emission can't silently overwrite a refresh error:
 *  - the cached article list from Room
 *  - the most recent refresh error (or null if last refresh succeeded /
 *    is in flight)
 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {

    val repo = ArticleRepository(app.applicationContext)

    private val refreshError = MutableStateFlow<String?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // One-shot UI events. Conflated so a burst of refresh taps only
    // emits a single scroll; capacity=1 so we never block the producer.
    private val scrollToTopEvents = Channel<Unit>(capacity = Channel.CONFLATED)
    val scrollToTopRequests: Flow<Unit> = scrollToTopEvents.receiveAsFlow()

    val status: StateFlow<FeedUiState> = combine(
        repo.observeArticles(),
        refreshError,
    ) { articles, err ->
        when {
            articles.isNotEmpty() -> FeedUiState.Ready(articles)
            err != null -> FeedUiState.Error(err)
            else -> FeedUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)

    val bookmarkIds: StateFlow<Set<String>> = repo.observeBookmarkIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init { refresh() }

    /**
     * @param scrollToTop fire a one-shot scroll event the FeedScreen
     *        observes — used for user-initiated refreshes so the new
     *        articles appear in view. Background load-more passes false.
     */
    fun refresh(scrollToTop: Boolean = false) {
        if (_isRefreshing.value) return
        refreshError.value = null
        _isRefreshing.value = true
        if (scrollToTop) scrollToTopEvents.trySend(Unit)
        viewModelScope.launch {
            try {
                runCatching { repo.refresh() }.onFailure { err ->
                    refreshError.value = err.message?.takeIf { it.isNotBlank() }
                        ?: "Could not reach the news service."
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Pull another batch using a rotated query set in the repository.
     * No-op if a refresh is already in flight — protects against burst
     * triggers from the pager's near-end detector.
     */
    fun loadMore() = refresh(scrollToTop = false)

    fun toggleBookmark(article: Article) {
        viewModelScope.launch {
            if (bookmarkIds.value.contains(article.id)) repo.removeBookmark(article.id)
            else repo.bookmark(article)
        }
    }
}
