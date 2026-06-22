package com.sdai.news.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdai.news.data.db.SDAIDatabase
import com.sdai.news.data.db.ScanHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SDAIDatabase.get(app).scanHistoryDao()

    val history: StateFlow<List<ScanHistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entity: ScanHistoryEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }
}
