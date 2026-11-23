package com.xiaofu.daily.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 记录照片：随记录级联删除；只存文件名（相对 files/photos/），不存绝对路径。 */
@Entity(
    tableName = "record_photos",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["record_id"])],
)
data class RecordPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
