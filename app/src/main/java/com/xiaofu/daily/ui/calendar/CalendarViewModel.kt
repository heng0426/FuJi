package com.xiaofu.daily.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.data.DueCard
import com.xiaofu.daily.data.RecordRepository
import com.xiaofu.daily.data.local.entity.RecordEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: RecordRepository) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val monthRecords: StateFlow<List<RecordEntity>> = _month
        .flatMapLatest { month ->
            val zone = ZoneId.systemDefault()
            // 多取前后一个月：分页跟手滑动时，相邻月份的小点也能立即显示
            val start = month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(2).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            repo.observeRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCards: StateFlow<List<DueCard>> = repo.observeDueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun showMonth(month: YearMonth) {
        _month.value = month
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _month.value) {
            _month.value = YearMonth.from(date)
        }
    }

    fun goToday() {
        _month.value = YearMonth.now()
        _selectedDate.value = LocalDate.now()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalendarViewModel(AppContainer.repository) as T
        }
    }
}
