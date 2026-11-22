package com.xiaofu.daily.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xiaofu.daily.data.local.model.Gender

/** 宠物档案（单行，id 固定 1；预留多宠时为每宠一行）。 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = 1,
    @ColumnInfo(name = "name")
    val name: String,
    /** 生日 epochDay，用于年龄展示 */
    @ColumnInfo(name = "birthday_day")
    val birthdayDay: Long? = null,
    @ColumnInfo(name = "breed")
    val breed: String? = null,
    @ColumnInfo(name = "gender")
    val gender: Gender? = null,
    /** 头像文件名（相对 files/photos/） */
    @ColumnInfo(name = "photo_file")
    val photoFile: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
