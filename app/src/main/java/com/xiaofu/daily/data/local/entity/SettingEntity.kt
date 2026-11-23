package com.xiaofu.daily.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 键值配置表。键清单见数据模型文档 §1.5。 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)
