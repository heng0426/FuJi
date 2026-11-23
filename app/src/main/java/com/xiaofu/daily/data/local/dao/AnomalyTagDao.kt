package com.xiaofu.daily.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xiaofu.daily.data.local.entity.AnomalyTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnomalyTagDao {

    @Query("SELECT * FROM anomaly_tags ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AnomalyTagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tag: AnomalyTagEntity)

    /** 仅允许删除自定义标签；预设标签的 is_preset = 1，不受影响 */
    @Query("DELETE FROM anomaly_tags WHERE id = :id AND is_preset = 0")
    suspend fun deleteCustom(id: Long)

    @Query("SELECT COUNT(*) FROM anomaly_tags")
    suspend fun count(): Int

    @Query("SELECT IFNULL(MAX(sort_order), -1) FROM anomaly_tags")
    suspend fun maxSortOrder(): Int
}
