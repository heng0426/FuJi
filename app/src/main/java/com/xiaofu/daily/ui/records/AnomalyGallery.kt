package com.xiaofu.daily.ui.records

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaofu.daily.R
import com.xiaofu.daily.data.AnomalyPhotoItem
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.ui.components.PhotoViewerDialog
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.ViewerPhoto
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.today.label
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 异常照片墙：三列网格按记录时间倒序；点按关闭并打开该条记录详情。
 * photos 为 null 表示加载中。
 */
@Composable
fun AnomalyGalleryDialog(
    photos: List<AnomalyPhotoItem>?,
    onDismiss: () -> Unit,
    onOpenRecord: (RecordEntity) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(
                        R.drawable.ic_ms_arrow_back,
                        onClick = onDismiss,
                        contentDescription = "返回",
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            "异常照片墙",
                            style = MaterialTheme.typography.titleLarge,
                            color = cs.onSurface,
                        )
                        Text(
                            if (photos == null) "加载中…" else "${photos.size} 张照片 · 点按预览",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                when {
                    photos == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = cs.primary)
                    }

                    photos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(R.drawable.ic_ms_image),
                                contentDescription = null,
                                modifier = Modifier.padding(bottom = 8.dp),
                                tint = cs.onSurfaceVariant,
                            )
                            Text("还没有异常照片", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                            Text(
                                "在「异常」记录里添加照片后会出现在这里",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(items = photos, key = { _, item -> item.file.path }) { index, item ->
                            GalleryTile(item) { viewerIndex = index }
                        }
                    }
                }

                if (photos != null && viewerIndex != null) {
                    val list = photos
                    PhotoViewerDialog(
                        photos = list.map { ViewerPhoto(it.file, anomalyHint(it.record)) },
                        initialIndex = viewerIndex!!,
                        onDismiss = { viewerIndex = null },
                        onOpenRecord = { index ->
                            viewerIndex = null
                            onOpenRecord(list[index].record)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(item: AnomalyPhotoItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(
                    item.file.path,
                    BitmapFactory.Options().apply { inSampleSize = 8 },
                )?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                it,
                contentDescription = "${Fmt.monthDayOf(item.record.occurredAt)} 异常照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            Fmt.monthDayOf(item.record.occurredAt),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(cs.surface.copy(alpha = 0.86f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

private fun anomalyHint(record: RecordEntity): String = listOfNotNull(
    record.anomalyCategory,
    record.severity?.label(),
    Fmt.detailTime(record.occurredAt),
).joinToString(" · ")
