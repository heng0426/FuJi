package com.xiaofu.daily.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.xiaofu.daily.R
import com.xiaofu.daily.ui.theme.LocalAppDarkTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全屏预览的一张图片：文件 + 该图对应的提示（分类/时间等） */
data class ViewerPhoto(val file: File, val hint: String? = null)

data class PhotoViewerRequest(
    val photos: List<ViewerPhoto>,
    val initialIndex: Int,
    val onOpenRecord: ((Int) -> Unit)? = null,
    val onClosed: (() -> Unit)? = null,
)

/**
 * 应用内全屏预览控制器：覆盖层挂在 XiaoFuApp 根节点，和 App 共用同一个 Window，
 * 内容能真正铺到状态栏下面，避免 Dialog 独立窗口带来的色带与图标不生效。
 */
class PhotoViewerHost {
    var request by mutableStateOf<PhotoViewerRequest?>(null)
        private set

    fun show(
        photos: List<ViewerPhoto>,
        initialIndex: Int,
        onOpenRecord: ((Int) -> Unit)? = null,
        onClosed: (() -> Unit)? = null,
    ) {
        if (photos.isEmpty()) return
        request = PhotoViewerRequest(
            photos = photos,
            initialIndex = initialIndex.coerceIn(0, photos.lastIndex),
            onOpenRecord = onOpenRecord,
            onClosed = onClosed,
        )
    }

    fun dismiss() {
        request = null
    }
}

val LocalPhotoViewerHost = staticCompositionLocalOf<PhotoViewerHost?> { null }

/**
 * 全屏图片预览覆盖层：
 * - 铺满整屏（含状态栏区域），状态栏保持显示、图标临时切浅色，关闭后恢复
 * - 左右滑动切换；1× 时不抢手势，放大后由图片拖动并禁用 Pager
 * - 双指 1×–5× 缩放、双击 2.5× / 还原；顶部序号与关闭，底部异常/就诊提示
 */
@Composable
fun PhotoViewerHostOverlay(modifier: Modifier = Modifier) {
    val host = LocalPhotoViewerHost.current ?: return
    val request = host.request ?: return
    val photos = request.photos

    // 关闭预览：先收起覆盖层，再执行调用方回调（例如重新打开来源详情）
    fun close() {
        val callback = request.onClosed
        host.dismiss()
        callback?.invoke()
    }

    BackHandler { close() }
    ViewerSystemBars()

    val pagerState = rememberPagerState(initialPage = request.initialIndex) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    val scrim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.35f else 0.62f
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
        ) { page ->
            ZoomablePhoto(
                file = photos[page].file,
                onZoomChanged = { value ->
                    if (page == pagerState.currentPage) zoomed = value
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { close() }) {
                Icon(
                    painterResource(R.drawable.ic_ms_close),
                    contentDescription = "关闭预览",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            if (request.onOpenRecord != null) {
                TextButton(
                    onClick = {
                        val index = pagerState.currentPage
                        host.dismiss()
                        request.onOpenRecord.invoke(index)
                    },
                ) {
                    Text("记录详情", color = Color.White)
                }
            }
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        val current = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]
        val hint = current.hint?.takeIf { it.isNotBlank() }
        if (hint != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
            ) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/** 预览期间把状态栏/导航栏图标切成浅色，退出时按当前主题恢复 */
@Composable
private fun ViewerSystemBars() {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() } ?: return
    val dark = LocalAppDarkTheme.current
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun ZoomablePhoto(file: File, onZoomChanged: (Boolean) -> Unit) {
    var failed by remember(file.path) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path) {
        val loaded = withContext(Dispatchers.IO) { decodeSampled(file, maxSide = 2048) }
        failed = loaded == null
        value = loaded
    }
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    // 按 Fit 后的实际显示区域限制平移，避免把图片拖出黑边
    fun clamp(o: Offset, s: Float): Offset {
        val w = bitmap?.width ?: 0
        val h = bitmap?.height ?: 0
        if (w <= 0 || h <= 0 || boxSize.width <= 0 || boxSize.height <= 0) return o
        val fit = minOf(boxSize.width.toFloat() / w, boxSize.height.toFloat() / h)
        val maxX = ((w * fit * s - boxSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((h * fit * s - boxSize.height) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .clipToBounds()
            .pointerInput(file.path) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val target = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            scale = target
                            offset = clamp((position - center) * (1f - target), target)
                        }
                    },
                )
            }
            .pointerInput(file.path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val multi = pressed > 1
                        val canManipulate = (multi && zoom != 1f) || scale > 1.01f
                        if (canManipulate && boxSize.width > 0 && boxSize.height > 0) {
                            val old = scale
                            val next = (old * zoom).coerceIn(1f, 5f)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val focus = if (centroid != Offset.Unspecified) centroid - center else Offset.Zero
                            val newOffset = (offset + pan) + focus * (1f - next / old)
                            scale = next
                            offset = clamp(newOffset, next)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        when {
            loaded != null -> Image(
                loaded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )

            failed -> Text(
                "图片无法加载",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )

            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 采样解码，长边不超过 maxSide，避免整张原图进内存 */
private fun decodeSampled(file: File, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
        sample *= 2
    }
    BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )?.asImageBitmap()
}.getOrNull()
