package com.xiaofu.daily.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xiaofu.daily.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}
