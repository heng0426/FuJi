package com.xiaofu.daily.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 异常分类标签库：预设 8 个 + 用户自定义；预设不可删除。 */
@Entity(
    tableName = "anomaly_tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class AnomalyTagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "is_preset")
    val isPreset: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
