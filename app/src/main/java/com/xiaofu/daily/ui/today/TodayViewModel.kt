package com.xiaofu.daily.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.data.DaySummary
import com.xiaofu.daily.data.DueInfo
import com.xiaofu.daily.data.RecordRepository
import com.xiaofu.daily.data.RecordSnapshot
import com.xiaofu.daily.data.local.entity.AnomalyTagEntity
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.Severity
import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: RecordRepository) : ViewModel() {

    private val pageSize = 100
    private val limit = MutableStateFlow(pageSize)

    val records: StateFlow<List<RecordEntity>> = limit
        .flatMapLatest { repo.observeTimeline(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(records, limit) { list, lim -> list.size >= lim }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val summary: StateFlow<DaySummary> = repo.observeDaySummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaySummary())

    val due: StateFlow<DueInfo?> = repo.observeDueSoon()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 近 7 天喝水/进食柱状图 */
    val week: StateFlow<WeekStats> = repo.observeRecentDays(7)
        .map { (today, records) -> buildWeekStats(today, records) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WeekStats.empty(java.time.LocalDate.now()),
        )

    val tags: StateFlow<List<AnomalyTagEntity>> = repo.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadMore() {
        if (hasMore.value) limit.value += pageSize
    }

    suspend fun lastWaterMl(): Int = repo.lastWaterMl()

    suspend fun servingGrams(): Int = repo.servingGrams()

    suspend fun lastServings(): Int = repo.lastServings()

    suspend fun lastWeightGrams(): Int? = repo.lastWeightGrams()

    suspend fun foodPresets(): List<com.xiaofu.daily.data.FoodPreset> = repo.foodPresets()

    suspend fun addFoodPreset(name: String, servings: Int) = repo.addFoodPreset(name, servings)

    suspend fun deleteTag(id: Long) = repo.deleteTag(id)

    suspend fun lastSnackType(): String? = repo.lastSnackType()

    suspend fun dewormCycleDays(kind: DewormKind): Int = repo.dewormCycleDays(kind)

    suspend fun medicineTags(): List<String> = repo.nameTags(com.xiaofu.daily.data.SettingsKeys.DEWORM_MEDICINE_TAGS)

    suspend fun vaccineTags(): List<String> = repo.nameTags(com.xiaofu.daily.data.SettingsKeys.VACCINE_NAME_TAGS)

    suspend fun addMedicineTag(name: String) =
        repo.addNameTag(com.xiaofu.daily.data.SettingsKeys.DEWORM_MEDICINE_TAGS, name)

    suspend fun addVaccineTag(name: String) =
        repo.addNameTag(com.xiaofu.daily.data.SettingsKeys.VACCINE_NAME_TAGS, name)

    suspend fun removeMedicineTag(name: String) =
        repo.removeNameTag(com.xiaofu.daily.data.SettingsKeys.DEWORM_MEDICINE_TAGS, name)

    suspend fun removeVaccineTag(name: String) =
        repo.removeNameTag(com.xiaofu.daily.data.SettingsKeys.VACCINE_NAME_TAGS, name)

    suspend fun delete(record: RecordEntity): RecordSnapshot? = repo.deleteWithSnapshot(record.id)

    suspend fun deleteMany(ids: Set<Long>) {
        ids.forEach { repo.deleteWithSnapshot(it) }
    }

    suspend fun restore(snapshot: RecordSnapshot) = repo.restore(snapshot)

    suspend fun saveWater(ml: Int, existing: RecordEntity?, at: Long? = null) =
        repo.saveWater(ml, at ?: existing?.occurredAt ?: System.currentTimeMillis(), existing)

    suspend fun saveFood(
        kind: MealKind,
        servings: Int,
        grams: Int,
        snackType: String?,
        existing: RecordEntity?,
        at: Long? = null,
        rememberDefaults: Boolean = true,
    ) = repo.saveFood(
        kind,
        servings,
        grams,
        snackType,
        at ?: existing?.occurredAt ?: System.currentTimeMillis(),
        existing,
        rememberDefaults,
    )

    suspend fun addTag(name: String) = repo.addTag(name)

    suspend fun saveWeight(
        grams: Int,
        note: String?,
        existing: RecordEntity?,
        at: Long? = null,
    ) = repo.saveWeight(grams, at ?: existing?.occurredAt ?: System.currentTimeMillis(), note, existing)

    suspend fun saveDeworm(
        kind: DewormKind,
        medicine: String?,
        nextDueDay: Long?,
        note: String?,
        existing: RecordEntity?,
        at: Long? = null,
    ) = repo.saveDeworm(
        kind,
        medicine,
        nextDueDay,
        at ?: existing?.occurredAt ?: System.currentTimeMillis(),
        note,
        existing,
    )

    suspend fun saveVaccine(
        name: String,
        nextDueDay: Long?,
        note: String?,
        existing: RecordEntity?,
        at: Long? = null,
    ) = repo.saveVaccine(
        name,
        nextDueDay,
        at ?: existing?.occurredAt ?: System.currentTimeMillis(),
        note,
        existing,
    )

    suspend fun saveAnomaly(
        category: String,
        severity: Severity?,
        note: String?,
        photos: List<Uri>,
        existing: RecordEntity?,
        keepPhotoNames: List<String>,
        at: Long? = null,
    ) = repo.saveAnomaly(
        category,
        severity,
        note,
        at ?: existing?.occurredAt ?: System.currentTimeMillis(),
        photos,
        existing,
        keepPhotoNames,
    )

    suspend fun photoNames(recordId: Long): List<String> = repo.photoNames(recordId)

    suspend fun saveVet(
        hospital: String?,
        doctor: String?,
        diagnosis: String?,
        costYuan: Int?,
        note: String?,
        photos: List<Uri>,
        existing: RecordEntity?,
        keepPhotoNames: List<String>,
        at: Long? = null,
    ) = repo.saveVet(
        hospital = hospital,
        doctor = doctor,
        diagnosis = diagnosis,
        costYuan = costYuan,
        note = note,
        occurredAt = at ?: existing?.occurredAt ?: System.currentTimeMillis(),
        newPhotoUris = photos,
        existing = existing,
        keepPhotoNames = keepPhotoNames,
    )

    fun photoFile(name: String) = repo.photoFile(name)

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TodayViewModel(AppContainer.repository) as T
        }
    }
}
