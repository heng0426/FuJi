package com.xiaofu.daily.ui.records

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.R
import com.xiaofu.daily.data.AnomalyPhotoItem
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.ui.components.LocalPhotoViewerHost
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.ViewerPhoto
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.today.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 照片墙控制器：页面挂在 XiaoFuApp 根节点，避免 Dialog 独立窗口造成状态栏色差。
 */
class GalleryHost {
    var visible by mutableStateOf(false)
        private set

    fun show() {
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

val LocalGalleryHost = staticCompositionLocalOf<GalleryHost?> { null }

/**
 * 异常照片墙（应用内全屏覆盖层）：
 * 背景铺满状态栏区域与页面同色；点按照片进入全屏预览。
 */
@Composable
fun AnomalyGalleryOverlay(
    onDismiss: () -> Unit,
    onOpenRecord: (RecordEntity) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var photos by remember { mutableStateOf<List<AnomalyPhotoItem>?>(null) }
    val viewerHost = LocalPhotoViewerHost.current

    LaunchedEffect(Unit) {
        photos = runCatching { AppContainer.repository.anomalyPhotos() }.getOrDefault(emptyList())
    }
    BackHandler(onBack = onDismiss)
    val loaded = photos

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
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
                        if (loaded == null) "加载中…" else "${loaded.size} 张照片 · 点按预览",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = cs.primary)
                }

                loaded.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(items = loaded, key = { _, item -> item.file.path }) { index, item ->
                            GalleryTile(item) {
                                viewerHost?.show(
                                    photos = loaded.map {
                                        ViewerPhoto(it.file, anomalyHint(it.record))
                                    },
                                    initialIndex = index,
                                    onOpenRecord = { i ->
                                        onDismiss()
                                        onOpenRecord(loaded[i].record)
                                    },
                                )
                            }
                        }
                    }
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
