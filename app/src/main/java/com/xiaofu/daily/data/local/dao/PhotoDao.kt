package com.xiaofu.daily.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.xiaofu.daily.data.local.entity.RecordPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Insert
    suspend fun insertAll(photos: List<RecordPhotoEntity>)

    @Query("DELETE FROM record_photos WHERE record_id = :recordId")
    suspend fun deleteByRecordId(recordId: Long)

    @Query("SELECT * FROM record_photos WHERE record_id = :recordId ORDER BY sort_order ASC")
    fun observeByRecord(recordId: Long): Flow<List<RecordPhotoEntity>>
}
