package com.xiaofu.daily.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.xiaofu.daily.data.local.XiaoFuDatabase
import com.xiaofu.daily.data.local.entity.AnomalyTagEntity
import com.xiaofu.daily.data.local.entity.ProfileEntity
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.entity.RecordPhotoEntity
import com.xiaofu.daily.data.local.entity.SettingEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.Gender
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 导出结果：records/photos 为成功写入的数量，missingPhotos 为 DB 引用但磁盘缺失的照片数 */
data class ExportStats(val records: Int, val photos: Int, val missingPhotos: Int)

/** 导入结果：missingPhotos 为备份包里缺失照片文件的引用数 */
data class ImportStats(val records: Int, val photos: Int, val missingPhotos: Int)

/** 备份包不是有效的小福日常备份（缺条目、版本不符、字段非法等） */
class BackupFormatException(message: String) : Exception(message)

/**
 * JSON 导出/恢复（v1.1 P1）。
 *
 * 备份包为 ZIP：`backup.json` + `photos/<file_name>`。
 * JSON 字段名与数据库列名一致（snake_case），schema_version 当前为 1。
 *
 * 导入为替换式恢复：records/record_photos 全部清空后按原 id 写回；
 * anomaly_tags / settings / profile 仅在备份包含对应段时替换，缺失则保留现状。
 */
class BackupManager(
    private val db: XiaoFuDatabase,
    private val context: Context,
) {

    // ---------- 导出 ----------

    suspend fun exportTo(uri: Uri): ExportStats = withContext(Dispatchers.IO) {
        val records = db.recordDao().getAll()
        val photos = db.photoDao().getAll()
        val profile = db.profileDao().getProfile()
        val tags = db.anomalyTagDao().getAll()
        val settings = db.settingsDao().getAll()
        val json = buildJson(records, photos, profile, tags, settings)

        // 只打包 DB 引用的照片（记录照片 + 档案头像），忽略孤儿文件
        val referenced = buildSet {
            photos.forEach { add(it.fileName) }
            profile?.photoFile?.let { add(it) }
        }
        val dir = photoDir()
        val present = referenced.filterTo(mutableListOf()) { File(dir, it).isFile }
        val missing = referenced.size - present.size

        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw BackupFormatException("无法写入所选位置")
        out.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_JSON))
                zip.write(json.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                present.forEach { name ->
                    zip.putNextEntry(ZipEntry("$DIR_PHOTOS/$name"))
                    File(dir, name).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        ExportStats(records.size, present.size, missing)
    }

    // ---------- 导入 ----------

    suspend fun importFrom(uri: Uri): ImportStats = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, STAGING_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            // 1. 解包：backup.json 读内存，photos/* 先落暂存目录，全部校验通过前不动现有数据
            var jsonText: String? = null
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw BackupFormatException("无法读取所选文件")
            stream.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        when {
                            name == ENTRY_JSON -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("$DIR_PHOTOS/") && !entry.isDirectory -> {
                                val fileName = name.removePrefix("$DIR_PHOTOS/")
                                if (PHOTO_NAME.matches(fileName)) {
                                    File(staging, fileName).outputStream().use { zip.copyTo(it) }
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val parsed = parseJson(
                jsonText ?: throw BackupFormatException("备份包缺少 backup.json，不是有效的小福日常备份文件"),
            )

            val dir = photoDir()
            val names = buildSet {
                parsed.photos.forEach { add(it.fileName) }
                parsed.profile?.photoFile?.let { add(it) }
            }
            val missing = names.count { !File(staging, it).isFile }

            // 2. 先把照片落盘，再提交数据库：复制失败时数据库保持原状，
            //    避免"记录已恢复但照片缺失"的半完成状态。
            val existedBefore = names.filterTo(mutableSetOf()) { File(dir, it).exists() }
            try {
                names.forEach { name ->
                    File(staging, name).takeIf { it.isFile }?.copyTo(File(dir, name), overwrite = true)
                }
            } catch (e: Exception) {
                // 回滚本次新建的文件；被覆盖的同名文件无法还原（正常备份文件名唯一，碰撞概率极低）
                names.filterNot { it in existedBefore }.forEach { name ->
                    runCatching { File(dir, name).delete() }
                }
                throw e
            }

            // 3. 事务内替换式恢复
            val currentProfile = db.profileDao().getProfile()
            db.withTransaction {
                db.photoDao().deleteAll()
                db.recordDao().deleteAll()
                if (parsed.tags != null) {
                    db.anomalyTagDao().deleteAll()
                    db.anomalyTagDao().insertAll(parsed.tags)
                }
                if (parsed.settings != null) {
                    db.settingsDao().deleteAll()
                    db.settingsDao().putAll(parsed.settings)
                }
                db.recordDao().insertAll(parsed.records)
                db.photoDao().insertAll(parsed.photos)
                parsed.profile?.let { db.profileDao().upsert(it) }
            }

            // 4. 成功后清理不再被引用的旧照片（旧记录照片与历史孤儿文件）
            val keep = buildSet {
                parsed.photos.forEach { add(it.fileName) }
                (parsed.profile?.photoFile ?: currentProfile?.photoFile)?.let { add(it) }
            }
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in keep) runCatching { file.delete() }
            }

            ImportStats(
                records = parsed.records.size,
                photos = parsed.photos.count { File(dir, it.fileName).isFile },
                missingPhotos = missing,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---------- 解析与校验 ----------

    private data class ParsedBackup(
        val records: List<RecordEntity>,
        val photos: List<RecordPhotoEntity>,
        /** null = 备份包未包含该段，保留现状 */
        val tags: List<AnomalyTagEntity>?,
        val settings: List<SettingEntity>?,
        val profile: ProfileEntity?,
    )

    private fun parseJson(text: String): ParsedBackup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("backup.json 不是有效的 JSON")
        }
        val version = root.optInt("schema_version", -1)
        if (version != SCHEMA_VERSION) {
            throw BackupFormatException("备份格式版本不支持（$version），当前支持版本 $SCHEMA_VERSION")
        }

        val records = try {
            val array = root.getJSONArray("records")
            (0 until array.length()).map { recordFromJson(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            throw BackupFormatException("records 数据缺失或格式不正确")
        }
        val recordIds = records.mapTo(mutableSetOf()) { it.id }

        val photos = optSection(root, "record_photos") { photoFromJson(it) }.orEmpty()
        photos.forEach { photo ->
            if (photo.recordId !in recordIds) {
                throw BackupFormatException("照片引用了不存在的记录（record_id=${photo.recordId}）")
            }
        }

        val tags = optSection(root, "anomaly_tags") { tagFromJson(it) }
        val settings = optSection(root, "settings") { o ->
            SettingEntity(
                key = o.optStringOrNull("key") ?: throw BackupFormatException("settings 存在缺少键名的条目"),
                value = o.optString("value", ""),
            )
        }
        val profile = if (root.has("profile") && !root.isNull("profile")) {
            try {
                profileFromJson(root.getJSONObject("profile"))
            } catch (e: JSONException) {
                throw BackupFormatException("profile 数据格式不正确")
            }
        } else {
            null
        }
        return ParsedBackup(records, photos, tags, settings, profile)
    }

    private fun <T> optSection(root: JSONObject, key: String, map: (JSONObject) -> T): List<T>? {
        if (!root.has(key) || root.isNull(key)) return null
        val array = try {
            root.getJSONArray(key)
        } catch (e: JSONException) {
            throw BackupFormatException("$key 数据格式不正确")
        }
        return try {
            (0 until array.length()).map { map(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            throw BackupFormatException("$key 数据格式不正确")
        }
    }

    private fun recordFromJson(o: JSONObject): RecordEntity = try {
        RecordEntity(
            id = o.getLong("id"),
            petId = o.optLong("pet_id", 1L),
            type = recordTypeOf(o),
            occurredAt = o.getLong("occurred_at"),
            note = o.optStringOrNull("note"),
            createdAt = o.getLong("created_at"),
            updatedAt = o.getLong("updated_at"),
            amountMl = o.optIntOrNull("amount_ml"),
            mealKind = o.optEnum<MealKind>("meal_kind"),
            // 份数只保留整份：旧备份中的 0.5 等小数按四舍五入归一（0.5 → 1）
            servingCount = o.optDoubleOrNull("serving_count")?.roundToInt()?.coerceIn(1, 20)?.toDouble(),
            gramsPerServing = o.optIntOrNull("grams_per_serving"),
            totalGrams = o.optIntOrNull("total_grams"),
            snackType = o.optStringOrNull("snack_type"),
            weightGrams = o.optIntOrNull("weight_grams"),
            dewormKind = o.optEnum<DewormKind>("deworm_kind"),
            medicine = o.optStringOrNull("medicine"),
            nextDueDate = o.optLongOrNull("next_due_date"),
            vaccineName = o.optStringOrNull("vaccine_name"),
            anomalyCategory = o.optStringOrNull("anomaly_category"),
            severity = o.optEnum<Severity>("severity"),
        )
    } catch (e: JSONException) {
        throw BackupFormatException("记录数据缺少必要字段（id=${o.optLong("id")}）")
    }

    private fun photoFromJson(o: JSONObject): RecordPhotoEntity = try {
        val name = o.getString("file_name")
        if (!PHOTO_NAME.matches(name)) throw BackupFormatException("照片文件名不合法：$name")
        RecordPhotoEntity(
            id = o.getLong("id"),
            recordId = o.getLong("record_id"),
            fileName = name,
            sortOrder = o.optInt("sort_order", 0),
        )
    } catch (e: JSONException) {
        throw BackupFormatException("照片数据缺少必要字段")
    }

    private fun tagFromJson(o: JSONObject): AnomalyTagEntity = try {
        AnomalyTagEntity(
            id = o.getLong("id"),
            name = o.getString("name"),
            isPreset = o.optInt("is_preset", 0) == 1,
            sortOrder = o.optInt("sort_order", 0),
        )
    } catch (e: JSONException) {
        throw BackupFormatException("异常分类数据格式不正确")
    }

    private fun profileFromJson(o: JSONObject): ProfileEntity = ProfileEntity(
        id = o.optLong("id", 1L),
        name = o.optString("name", "").trim().ifBlank { "小福" },
        birthdayDay = o.optLongOrNull("birthday_day"),
        breed = o.optStringOrNull("breed"),
        gender = o.optEnum<Gender>("gender"),
        photoFile = o.optStringOrNull("photo_file")?.also { name ->
            if (!PHOTO_NAME.matches(name)) throw BackupFormatException("头像文件名不合法：$name")
        },
        createdAt = o.optLong("created_at", System.currentTimeMillis()),
        updatedAt = o.optLong("updated_at", System.currentTimeMillis()),
    )

    // ---------- JSON 编码 ----------

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private fun buildJson(
        records: List<RecordEntity>,
        photos: List<RecordPhotoEntity>,
        profile: ProfileEntity?,
        tags: List<AnomalyTagEntity>,
        settings: List<SettingEntity>,
    ): JSONObject = JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("app_version", appVersion())
        put("exported_at", System.currentTimeMillis())
        put("profile", profile?.toJson() ?: JSONObject.NULL)
        put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })
        put("record_photos", JSONArray().apply { photos.forEach { put(it.toJson()) } })
        put("anomaly_tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
        put("settings", JSONArray().apply { settings.forEach { put(it.toJson()) } })
    }

    private fun RecordEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("pet_id", petId)
        put("type", type.name)
        put("occurred_at", occurredAt)
        putOpt("note", note)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        putOpt("amount_ml", amountMl)
        putOpt("meal_kind", mealKind?.name)
        putOpt("serving_count", servingCount?.roundToInt())
        putOpt("grams_per_serving", gramsPerServing)
        putOpt("total_grams", totalGrams)
        putOpt("snack_type", snackType)
        putOpt("weight_grams", weightGrams)
        putOpt("deworm_kind", dewormKind?.name)
        putOpt("medicine", medicine)
        putOpt("next_due_date", nextDueDate)
        putOpt("vaccine_name", vaccineName)
        putOpt("anomaly_category", anomalyCategory)
        putOpt("severity", severity?.name)
    }

    private fun RecordPhotoEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("record_id", recordId)
        put("file_name", fileName)
        put("sort_order", sortOrder)
    }

    private fun ProfileEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putOpt("birthday_day", birthdayDay)
        putOpt("breed", breed)
        putOpt("gender", gender?.name)
        putOpt("photo_file", photoFile)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun AnomalyTagEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("is_preset", if (isPreset) 1 else 0)
        put("sort_order", sortOrder)
    }

    private fun SettingEntity.toJson() = JSONObject().apply {
        put("key", key)
        put("value", value)
    }

    // ---------- 工具 ----------

    private fun photoDir(): File = File(context.filesDir, "photos").apply { mkdirs() }

    private inline fun <reified E : Enum<E>> JSONObject.optEnum(key: String): E? =
        when (val raw = optStringOrNull(key)) {
            null -> null
            else -> runCatching { enumValueOf<E>(raw) }
                .getOrElse { throw BackupFormatException("$key 值无效：$raw") }
        }

    /** type 是必填枚举，单独校验给出明确错误 */
    private fun recordTypeOf(o: JSONObject): RecordType =
        o.optStringOrNull("type")?.let { raw ->
            runCatching { RecordType.valueOf(raw) }
                .getOrElse { throw BackupFormatException("记录类型无效：$raw（id=${o.optLong("id")}）") }
        } ?: throw BackupFormatException("记录缺少 type 字段（id=${o.optLong("id")}）")

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    companion object {
        const val SCHEMA_VERSION = 1
        const val ENTRY_JSON = "backup.json"
        const val DIR_PHOTOS = "photos"
        private const val STAGING_DIR = "import_staging"

        /**
         * 照片文件名白名单：与 PhotoStore 生成规则一致，防 zip 路径穿越。
         * 首字符必须为字母/数字，排除 "."、".."、隐藏文件与前导点。
         */
        private val PHOTO_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")

        fun suggestedFileName(): String =
            "小福日常备份-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    }
}
