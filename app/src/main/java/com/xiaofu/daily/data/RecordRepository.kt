package com.xiaofu.daily.data

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xiaofu.daily.data.local.XiaoFuDatabase
import com.xiaofu.daily.data.local.entity.AnomalyTagEntity
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.entity.RecordPhotoEntity
import com.xiaofu.daily.data.local.entity.SettingEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** 当日概览：喝水合计、主粮/加餐份数与克数、最近体重 */
data class DaySummary(
    val waterMl: Int = 0,
    val waterCount: Int = 0,
    val mainServings: Double = 0.0,
    val mainGrams: Int = 0,
    val snackServings: Double = 0.0,
    val snackGrams: Int = 0,
    val weightGrams: Int? = null,
    val weightAt: Long? = null,
) {
    val hasFood: Boolean get() = mainServings > 0 || snackServings > 0
}

/** 首页到期提醒（T-3 内出现，含已超期） */
data class DueInfo(
    val label: String,
    val daysLeft: Long,
    val type: RecordType,
) {
    val text: String
        get() = when {
            daysLeft == 0L -> "${label}今天到期"
            daysLeft > 0 -> "${label}还有 $daysLeft 天到期"
            else -> "${label}已超期 ${-daysLeft} 天"
        }
}

/** 记录页的到期卡：内驱 / 外驱 / 疫苗 */
data class DueCard(
    val title: String,
    val dueDay: Long,
    val daysLeft: Long,
    val type: RecordType,
    val note: String?,
) {
    val daysText: String
        get() = if (daysLeft >= 0) "还有 $daysLeft 天" else "已超期 ${-daysLeft} 天"
}

/** 删除快照：用于 Snackbar 撤销（照片文件保留，仅回写数据库行） */
data class RecordSnapshot(
    val record: RecordEntity,
    val photos: List<RecordPhotoEntity>,
)

object SettingsKeys {
    const val SERVING_GRAMS = "serving_grams_current"
    const val LAST_SERVINGS = "last_servings"
    const val LAST_WEIGHT_GRAMS = "last_weight_grams"
    const val FOOD_PRESETS = "food_presets"
    const val TYPE_ORDER = "record_type_order"
    const val DEWORM_INTERNAL_DAYS = "deworm_internal_cycle_days"
    const val DEWORM_EXTERNAL_DAYS = "deworm_external_cycle_days"
    const val NOTIFY_ENABLED = "notify_enabled"
    const val NOTIFY_DAYS_AHEAD = "notify_days_ahead"
    const val NOTIFY_HOUR = "notify_hour"
    const val LAST_WATER_ML = "last_water_ml"
    const val LAST_SNACK_TYPE = "last_snack_type"
    const val NOTIFY_LAST_DAY = "notify_last_day"
    const val DEWORM_MEDICINE_TAGS = "deworm_medicine_tags"
    const val VACCINE_NAME_TAGS = "vaccine_name_tags"
}

/** 药品名/疫苗名称常用标签默认值；存 settings 表后可增删（含默认项），随备份导出/导入同步 */
private val DefaultNameTags = mapOf(
    SettingsKeys.DEWORM_MEDICINE_TAGS to listOf("海乐妙", "大宠爱", "福来恩", "爱沃克", "博来恩"),
    SettingsKeys.VACCINE_NAME_TAGS to listOf("猫三联", "狂犬疫苗", "妙三多"),
)

class RecordRepository(
    db: XiaoFuDatabase,
    context: Context,
) {
    private val recordDao = db.recordDao()
    private val photoDao = db.photoDao()
    private val profileDao = db.profileDao()
    private val settingsDao = db.settingsDao()
    private val tagDao = db.anomalyTagDao()
    private val photoStore = PhotoStore(context.applicationContext)

    // ---------- 查询 ----------

    fun observeTimeline(limit: Int): Flow<List<RecordEntity>> =
        recordDao.observeTimelinePage(Long.MAX_VALUE, Long.MAX_VALUE, limit)

    fun observeRange(startMs: Long, endMs: Long): Flow<List<RecordEntity>> =
        recordDao.observeByRange(startMs, endMs)

    fun observeByType(type: RecordType, limit: Int = 300): Flow<List<RecordEntity>> =
        recordDao.observeByType(type, limit)

    /** 记录页顶部到期卡：内驱、外驱、疫苗各自最近一条的到期日 */
    fun observeDueCards(): Flow<List<DueCard>> {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        return combine(
            recordDao.observeLatestDeworm(RecordType.DEWORM, DewormKind.INTERNAL),
            recordDao.observeLatestDeworm(RecordType.DEWORM, DewormKind.EXTERNAL),
            recordDao.observeNearestVaccineDue(RecordType.VACCINE),
        ) { internal, external, vaccine ->
            buildList {
                internal?.nextDueDate?.let {
                    add(DueCard("内驱", it, it - today, RecordType.DEWORM, internal.medicine))
                }
                external?.nextDueDate?.let {
                    add(DueCard("外驱", it, it - today, RecordType.DEWORM, external.medicine))
                }
                vaccine?.nextDueDate?.let {
                    add(DueCard("疫苗", it, it - today, RecordType.VACCINE, vaccine.vaccineName))
                }
            }.sortedBy { it.dueDay }
        }
    }

    fun observeDaySummary(): Flow<DaySummary> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val water = recordDao.observeDayWaterMl(RecordType.WATER, start, end)
        val waterCount = recordDao.observeDayWaterCount(RecordType.WATER, start, end)
        val food = recordDao.observeDayFoodSummary(RecordType.FOOD, start, end)
        val weight = recordDao.observeLatestOfType(RecordType.WEIGHT)

        return combine(water, waterCount, food, weight) { waterMl, count, foodRows, latestWeight ->
            val main = foodRows.firstOrNull { it.mealKind == MealKind.MAIN }
            val snack = foodRows.firstOrNull { it.mealKind == MealKind.SNACK }
            DaySummary(
                waterMl = waterMl,
                waterCount = count,
                mainServings = main?.servings ?: 0.0,
                mainGrams = main?.grams ?: 0,
                snackServings = snack?.servings ?: 0.0,
                snackGrams = snack?.grams ?: 0,
                weightGrams = latestWeight?.weightGrams,
                weightAt = latestWeight?.occurredAt,
            )
        }
    }

    fun observeDueSoon(): Flow<DueInfo?> {
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val internal = recordDao.observeLatestDeworm(RecordType.DEWORM, DewormKind.INTERNAL)
        val external = recordDao.observeLatestDeworm(RecordType.DEWORM, DewormKind.EXTERNAL)
        val vaccine = recordDao.observeNearestVaccineDue(RecordType.VACCINE)

        return combine(internal, external, vaccine) { i, e, v ->
            val candidates = buildList {
                i?.nextDueDate?.let { add(Triple("内驱", it, RecordType.DEWORM)) }
                e?.nextDueDate?.let { add(Triple("外驱", it, RecordType.DEWORM)) }
                v?.nextDueDate?.let { add(Triple("疫苗", it, RecordType.VACCINE)) }
            }
            candidates.minByOrNull { it.second }?.let { (label, dueDay, type) ->
                val daysLeft = dueDay - today
                if (daysLeft <= NOTIFY_DAYS_AHEAD) DueInfo(label, daysLeft, type) else null
            }
        }
    }

    fun observeServingGrams(): Flow<Int> =
        settingsDao.observeAll().map { rows ->
            rows.firstOrNull { it.key == SettingsKeys.SERVING_GRAMS }?.value?.toIntOrNull() ?: 15
        }

    fun observeTags(): Flow<List<AnomalyTagEntity>> = tagDao.observeAll()

    fun observeProfile(): Flow<com.xiaofu.daily.data.local.entity.ProfileEntity?> = profileDao.observeProfile()

    suspend fun updateProfile(profile: com.xiaofu.daily.data.local.entity.ProfileEntity) =
        profileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))

    fun observeSettings(): Flow<List<SettingEntity>> = settingsDao.observeAll()

    suspend fun lastWaterMl(): Int =
        settingsDao.get(SettingsKeys.LAST_WATER_ML)?.toIntOrNull()
            ?: recordDao.getLastWaterAmount(RecordType.WATER)
            ?: 50

    suspend fun lastSnackType(): String? = settingsDao.get(SettingsKeys.LAST_SNACK_TYPE)

    suspend fun lastServings(): Double =
        settingsDao.get(SettingsKeys.LAST_SERVINGS)?.toDoubleOrNull()?.coerceAtLeast(1.0)
            ?: recordDao.latestOfType(RecordType.FOOD)?.servingCount?.coerceAtLeast(1.0)
            ?: 1.0

    suspend fun lastWeightGrams(): Int? =
        settingsDao.get(SettingsKeys.LAST_WEIGHT_GRAMS)?.toIntOrNull()
            ?: recordDao.latestOfType(RecordType.WEIGHT)?.weightGrams

    suspend fun foodPresets(): List<FoodPreset> {
        val raw = settingsDao.get(SettingsKeys.FOOD_PRESETS)
        val custom = runCatching {
            val array = org.json.JSONArray(raw ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val servings = item.optInt("servings", 1).coerceIn(1, 20)
                FoodPreset(name, servings)
            }
        }.getOrDefault(emptyList())
        return FoodPreset.defaults + custom.filter { candidate -> FoodPreset.defaults.none { it.name == candidate.name } }
    }

    suspend fun addFoodPreset(name: String, servings: Int) {
        val clean = name.trim().take(8)
        if (clean.isBlank()) return
        val custom = customPresets().filterNot { it.name == clean } +
            FoodPreset(clean, servings.coerceIn(1, 20))
        saveCustomPresets(custom)
    }

    suspend fun deleteFoodPreset(name: String) {
        saveCustomPresets(customPresets().filterNot { it.name == name })
    }

    private suspend fun customPresets(): List<FoodPreset> =
        foodPresets().filter { candidate -> FoodPreset.defaults.none { it.name == candidate.name } }

    private suspend fun saveCustomPresets(custom: List<FoodPreset>) {
        val array = org.json.JSONArray()
        custom.forEach {
            array.put(org.json.JSONObject().put("name", it.name).put("servings", it.servings))
        }
        settingsDao.put(SettingEntity(SettingsKeys.FOOD_PRESETS, array.toString()))
    }

    suspend fun setting(key: String): String? = settingsDao.get(key)

    suspend fun putSetting(key: String, value: String) = settingsDao.put(SettingEntity(key, value))

    suspend fun servingGrams(): Int =
        settingsDao.get(SettingsKeys.SERVING_GRAMS)?.toIntOrNull() ?: 15

    suspend fun dewormCycleDays(kind: DewormKind): Int {
        val key = if (kind == DewormKind.INTERNAL) SettingsKeys.DEWORM_INTERNAL_DAYS else SettingsKeys.DEWORM_EXTERNAL_DAYS
        return settingsDao.get(key)?.toIntOrNull() ?: if (kind == DewormKind.INTERNAL) 90 else 30
    }

    // ---------- 药品名 / 疫苗名称 常用标签 ----------

    /** 首次读取时写入默认值落库，使删除默认标签可持久化 */
    suspend fun nameTags(key: String): List<String> {
        val raw = settingsDao.get(key) ?: run {
            val initial = DefaultNameTags[key].orEmpty()
            saveNameTags(key, initial)
            return initial
        }
        return parseNameTags(raw) ?: DefaultNameTags[key].orEmpty()
    }

    suspend fun addNameTag(key: String, name: String) {
        val clean = name.trim().take(20)
        if (clean.isBlank()) return
        saveNameTags(key, nameTags(key).filterNot { it == clean } + clean)
    }

    suspend fun removeNameTag(key: String, name: String) {
        saveNameTags(key, nameTags(key).filterNot { it == name })
    }

    private fun parseNameTags(raw: String): List<String>? = runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
    }.getOrNull()

    private suspend fun saveNameTags(key: String, tags: List<String>) {
        val array = org.json.JSONArray()
        tags.forEach { array.put(it) }
        settingsDao.put(SettingEntity(key, array.toString()))
    }

    // ---------- 写入 ----------

    private fun now(): Long = System.currentTimeMillis()

    private fun base(type: RecordType, occurredAt: Long, note: String? = null) = RecordEntity(
        type = type,
        occurredAt = occurredAt,
        note = note,
        createdAt = now(),
        updatedAt = now(),
    )

    suspend fun saveWater(ml: Int, occurredAt: Long, existing: RecordEntity? = null): Long {
        settingsDao.put(SettingEntity(SettingsKeys.LAST_WATER_ML, ml.toString()))
        return if (existing == null) {
            recordDao.insert(base(RecordType.WATER, occurredAt).copy(amountMl = ml))
        } else {
            recordDao.update(existing.copy(amountMl = ml, occurredAt = occurredAt, updatedAt = now()))
            existing.id
        }
    }

    suspend fun saveFood(
        mealKind: MealKind,
        servings: Double,
        gramsPerServing: Int,
        snackType: String?,
        occurredAt: Long,
        existing: RecordEntity? = null,
        rememberDefaults: Boolean = true,
    ): Long {
        val total = (servings * gramsPerServing).roundToInt()
        if (rememberDefaults) {
            settingsDao.put(SettingEntity(SettingsKeys.SERVING_GRAMS, gramsPerServing.toString()))
            settingsDao.put(SettingEntity(SettingsKeys.LAST_SERVINGS, servings.toString()))
        }
        if (mealKind == MealKind.SNACK && snackType != null) {
            settingsDao.put(SettingEntity(SettingsKeys.LAST_SNACK_TYPE, snackType))
        }
        val record = (existing ?: base(RecordType.FOOD, occurredAt)).copy(
            occurredAt = occurredAt,
            mealKind = mealKind,
            servingCount = servings,
            gramsPerServing = gramsPerServing,
            totalGrams = total,
            snackType = if (mealKind == MealKind.SNACK) snackType else null,
            updatedAt = now(),
        )
        return if (existing == null) recordDao.insert(record) else {
            recordDao.update(record)
            record.id
        }
    }

    suspend fun saveWeight(grams: Int, occurredAt: Long, note: String?, existing: RecordEntity? = null): Long {
        settingsDao.put(SettingEntity(SettingsKeys.LAST_WEIGHT_GRAMS, grams.toString()))
        val record = (existing ?: base(RecordType.WEIGHT, occurredAt)).copy(
            occurredAt = occurredAt,
            weightGrams = grams,
            note = note,
            updatedAt = now(),
        )
        return if (existing == null) recordDao.insert(record) else {
            recordDao.update(record)
            record.id
        }
    }

    suspend fun saveDeworm(
        kind: DewormKind,
        medicine: String?,
        nextDueDay: Long?,
        occurredAt: Long,
        note: String?,
        existing: RecordEntity? = null,
    ): Long {
        val due = nextDueDay ?: (occurredAt.toLocalDate().toEpochDay() + dewormCycleDays(kind))
        val record = (existing ?: base(RecordType.DEWORM, occurredAt)).copy(
            occurredAt = occurredAt,
            dewormKind = kind,
            medicine = medicine,
            nextDueDate = due,
            note = note,
            updatedAt = now(),
        )
        return if (existing == null) recordDao.insert(record) else {
            recordDao.update(record)
            record.id
        }
    }

    suspend fun saveVaccine(
        name: String,
        nextDueDay: Long?,
        occurredAt: Long,
        note: String?,
        existing: RecordEntity? = null,
    ): Long {
        val record = (existing ?: base(RecordType.VACCINE, occurredAt)).copy(
            occurredAt = occurredAt,
            vaccineName = name,
            nextDueDate = nextDueDay,
            note = note,
            updatedAt = now(),
        )
        return if (existing == null) recordDao.insert(record) else {
            recordDao.update(record)
            record.id
        }
    }

    suspend fun saveAnomaly(
        category: String,
        severity: Severity?,
        note: String?,
        occurredAt: Long,
        newPhotoUris: List<Uri>,
        existing: RecordEntity? = null,
        keepPhotoNames: List<String> = emptyList(),
    ): Long {
        val record = (existing ?: base(RecordType.ANOMALY, occurredAt)).copy(
            occurredAt = occurredAt,
            anomalyCategory = category,
            severity = severity,
            note = note,
            updatedAt = now(),
        )
        val id = if (existing == null) recordDao.insert(record) else {
            recordDao.update(record)
            existing.id
        }
        val names = keepPhotoNames + newPhotoUris.mapNotNull { photoStore.import(it) }
        photoDao.deleteByRecordId(id)
        if (names.isNotEmpty()) {
            photoDao.insertAll(names.mapIndexed { index, name ->
                RecordPhotoEntity(recordId = id, fileName = name, sortOrder = index)
            })
        }
        return id
    }

    suspend fun deleteTag(id: Long) {
        tagDao.deleteCustom(id)
    }

    suspend fun typeOrder(): List<RecordType> {
        val saved = settingsDao.get(SettingsKeys.TYPE_ORDER)
            ?.split(",")
            ?.mapNotNull { name -> RecordType.entries.firstOrNull { it.name == name } }
            ?.distinct()
            .orEmpty()
        return saved + RecordType.entries.filterNot { it in saved }
    }

    suspend fun saveTypeOrder(order: List<RecordType>) {
        settingsDao.put(SettingEntity(SettingsKeys.TYPE_ORDER, order.joinToString(",") { it.name }))
    }

    suspend fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        tagDao.insertIgnore(
            AnomalyTagEntity(name = trimmed, isPreset = false, sortOrder = tagDao.maxSortOrder() + 1),
        )
    }

    suspend fun photoNames(recordId: Long): List<String> =
        photoDao.observeByRecord(recordId).let { flow -> firstValue(flow).map { it.fileName } }

    fun photoFile(name: String): File = photoStore.file(name)

    suspend fun importPhoto(uri: Uri): String? = photoStore.import(uri)

    // ---------- 删除与撤销 ----------

    suspend fun deleteWithSnapshot(id: Long): RecordSnapshot? {
        val record = recordDao.getById(id) ?: return null
        val photos = firstValue(photoDao.observeByRecord(id))
        recordDao.deleteById(id)
        return RecordSnapshot(record, photos)
    }

    suspend fun restore(snapshot: RecordSnapshot) {
        val restored = snapshot.record.copy(id = 0, updatedAt = now())
        val newId = recordDao.insert(restored)
        if (snapshot.photos.isNotEmpty()) {
            photoDao.insertAll(
                snapshot.photos.mapIndexed { index, photo ->
                    photo.copy(id = 0, recordId = newId, sortOrder = index)
                },
            )
        }
    }

    private suspend fun <T> firstValue(flow: Flow<T>): T = flow.first()

    private fun Long.toLocalDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        const val NOTIFY_DAYS_AHEAD = 3L
    }
}

/** 照片存储：长边 2560px、JPEG 90%（设计规范 §2 与数据模型 §5） */
class PhotoStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2560 * 2) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null

            val longSide = max(decoded.width, decoded.height)
            val scaled = if (longSide > 2560) {
                val ratio = 2560f / longSide
                Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).roundToInt(), (decoded.height * ratio).roundToInt(), true)
            } else {
                decoded
            }

            val name = "r_${System.currentTimeMillis()}_${(0..999).random()}.jpg"
            FileOutputStream(File(dir, name)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            name
        }.getOrNull()
    }
}
