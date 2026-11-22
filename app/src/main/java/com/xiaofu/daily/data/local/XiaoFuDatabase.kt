package com.xiaofu.daily.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
        }

        fun build(context: Context): XiaoFuDatabase =
            Room.databaseBuilder(context.applicationContext, XiaoFuDatabase::class.java, DB_NAME)
                .addCallback(seedCallback)
                .build()
    }
}
