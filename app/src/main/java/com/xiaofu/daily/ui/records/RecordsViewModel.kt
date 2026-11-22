package com.xiaofu.daily.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.data.DueCard
import com.xiaofu.daily.data.RecordRepository
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.RecordType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(private val repo: RecordRepository) : ViewModel() {

    private val _filter = MutableStateFlow<RecordType?>(null)
    val filter: StateFlow<RecordType?> = _filter

    val records: StateFlow<List<RecordEntity>> = _filter
        .flatMapLatest { type ->
            if (type == null) repo.observeTimeline(300) else repo.observeByType(type, 300)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCards: StateFlow<List<DueCard>> = repo.observeDueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(type: RecordType?) {
        _filter.value = type
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RecordsViewModel(AppContainer.repository) as T
        }
    }
}
