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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(private val repo: RecordRepository) : ViewModel() {

    private data class Query(val type: RecordType?, val limit: Int)

    private val pageSize = 100
    private val _query = MutableStateFlow(Query(type = null, limit = pageSize))
    private val _typeOrder = MutableStateFlow(RecordType.entries.toList())
    val typeOrder: StateFlow<List<RecordType>> = _typeOrder.asStateFlow()

    init {
        viewModelScope.launch {
            _typeOrder.value = repo.typeOrder()
        }
    }

    val filter: StateFlow<RecordType?> = _query
        .map { it.type }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val records: StateFlow<List<RecordEntity>> = _query
        .flatMapLatest { query ->
            if (query.type == null) {
                repo.observeTimeline(query.limit)
            } else {
                repo.observeByType(query.type, query.limit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCards: StateFlow<List<DueCard>> = repo.observeDueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(records, _query) { list, query ->
        list.size >= query.limit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setFilter(type: RecordType?) {
        if (_query.value.type == type) return
        // 切换筛选时重置分页，避免沿用上一类型已展开的深度
        _query.value = Query(type = type, limit = pageSize)
    }

    fun loadMore() {
        val current = _query.value
        if (hasMore.value) _query.value = current.copy(limit = current.limit + pageSize)
    }

    suspend fun deleteMany(ids: Set<Long>) {
        ids.forEach { repo.deleteWithSnapshot(it) }
    }

    fun moveType(type: RecordType, offset: Int) {
        val current = _typeOrder.value.toMutableList()
        val index = current.indexOf(type)
        val target = index + offset
        if (index < 0 || target !in current.indices) return
        current.removeAt(index)
        current.add(target, type)
        _typeOrder.value = current
        viewModelScope.launch { repo.saveTypeOrder(current) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RecordsViewModel(AppContainer.repository) as T
        }
    }
}
