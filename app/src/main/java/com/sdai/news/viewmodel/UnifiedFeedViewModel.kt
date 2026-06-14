package com.sdai.news.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdai.news.SDAINewsApp
import com.sdai.news.data.GeneralArticleRepository
import com.sdai.news.data.Article
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UnifiedFeedViewModel(app: Application) : AndroidViewModel(app) {

    val repo = GeneralArticleRepository(app.applicationContext)

    val allArticles = repo.observeBySection(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val breakingArticles = repo.observeBySection("breaking")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val worldArticles = repo.observeBySection("world")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nationalArticles = repo.observeBySection("national")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val regionalArticles = repo.observeBySection("regional")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val refreshCount = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredAll: StateFlow<List<Article>> = _categoryFilter
        .flatMapLatest { category ->
            if (category == null) allArticles
            else repo.observeByCategory(category)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refreshAll() }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun refreshAll() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            runCatching { repo.refreshGeneral() }
            _isRefreshing.value = false
            refreshCount.value++
        }
        viewModelScope.launch {
            val city = SDAINewsApp.get().prefs.locationCity.first()
            if (city.isNotBlank()) {
                runCatching { repo.refreshRegional(city) }
            }
        }
    }

    fun articlesForSection(section: String?): StateFlow<List<Article>> = when (section) {
        "breaking" -> breakingArticles
        "world" -> worldArticles
        "national" -> nationalArticles
        "regional" -> regionalArticles
        else -> allArticles
    }
}
