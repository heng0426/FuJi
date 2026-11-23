package com.xiaofu.daily.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaofu.daily.data.local.dao.AnomalyTagDao
import com.xiaofu.daily.data.local.dao.PhotoDao
import com.xiaofu.daily.data.local.dao.ProfileDao
import com.xiaofu.daily.data.local.dao.RecordDao
import com.xiaofu.daily.data.local.dao.SettingsDao
import com.xiaofu.daily.data.local.entity.AnomalyTagEntity
import com.xiaofu.daily.data.local.entity.ProfileEntity
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.entity.RecordPhotoEntity
import com.xiaofu.daily.data.local.entity.SettingEntity

@Database(
    entities = [
        RecordEntity::class,
        RecordPhotoEntity::class,
        ProfileEntity::class,
        AnomalyTagEntity::class,
        SettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class XiaoFuDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao
    abstract fun photoDao(): PhotoDao
    abstract fun profileDao(): ProfileDao
    abstract fun settingsDao(): SettingsDao
    abstract fun anomalyTagDao(): AnomalyTagDao

    companion object {
        const val DB_NAME = "xiaofu.db"

        private val PRESET_TAGS = listOf(
            "呕吐", "腹泻", "精神不佳", "食欲不振", "咳嗽", "外伤", "体温异常", "其他",
        )

        private val DEFAULT_SETTINGS = listOf(
            "serving_grams_current" to "15",
            "deworm_internal_cycle_days" to "90",
            "deworm_external_cycle_days" to "30",
            "notify_enabled" to "true",
            "notify_days_ahead" to "3",
            "notify_hour" to "9",
        )

        /**
         * 首启种子数据（profile / 异常标签 / 默认设置）。
         * 公开可见以便 instrumentation 测试用同一套种子构建内存库。
         */
        val seedCallback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO profile (id, name, created_at, updated_at) VALUES (1, '小福', ?, ?)",
                    arrayOf<Any?>(now, now),
                )
                PRESET_TAGS.forEachIndexed { index, tag ->
                    db.execSQL(
                        "INSERT INTO anomaly_tags (name, is_preset, sort_order) VALUES (?, ?, ?)",
                        arrayOf<Any?>(tag, 1, index),
                    )
                }
                DEFAULT_SETTINGS.forEach { (key, value) ->
                    db.execSQL(
                        "INSERT INTO settings (`key`, value) VALUES (?, ?)",
                        arrayOf<Any?>(key, value),
                    )
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                // 份数只保留整份：历史 0.5 等小数值四舍五入（0.5 → 1），并夹取到 1–20。
                // 列类型仍为 REAL（schema v1），不因数据归一触发结构迁移。
                db.execSQL(
                    "UPDATE records SET serving_count = MAX(1, MIN(20, CAST(ROUND(serving_count) AS INTEGER))) " +
                        "WHERE type = 'FOOD' AND serving_count IS NOT NULL " +
                        "AND (serving_count < 1 OR serving_count > 20 " +
                        "OR serving_count != CAST(serving_count AS INTEGER))",
                )
            }
        }

        fun build(context: Context): XiaoFuDatabase =
            Room.databaseBuilder(context.applicationContext, XiaoFuDatabase::class.java, DB_NAME)
                .addCallback(seedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2：新增就医记录字段（全部可空，不影响既有记录） */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN vet_hospital TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN vet_doctor TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN vet_diagnosis TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN vet_cost_yuan INTEGER")
            }
        }
    }
}
