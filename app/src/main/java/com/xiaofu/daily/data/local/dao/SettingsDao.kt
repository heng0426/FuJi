package com.xiaofu.daily.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xiaofu.daily.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Upsert
    suspend fun putAll(settings: List<SettingEntity>)

    // ---- 备份/恢复 ----

    @Query("SELECT * FROM settings ORDER BY `key` ASC")
    suspend fun getAll(): List<SettingEntity>

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
