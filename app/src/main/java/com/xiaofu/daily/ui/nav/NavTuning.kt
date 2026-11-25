package com.xiaofu.daily.ui.nav

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 底栏的可调参数（验收期临时使用）。
 * 定稿后把数值写回 [com.xiaofu.daily.ui.XiaoFuApp] 的默认值，并删除本文件、面板与入口。
 */
data class NavTuning(
    // 外框
    val barHeight: Float = 72f,
    val barRadius: Float = 36f,
    val barBottom: Float = 12f,
    val barBorder: Float = 1f,
    val barShadow: Float = 20f,
    // 排布
    val rowPadding: Float = 8f,
    val cellWidth: Float = 56f,
    // 选中块
    val pillWidth: Float = 56f,
    val pillPadH: Float = 8.5f,
    val pillPadV: Float = 8.5f,
    val pillRadius: Float = 28f,
    val pillBorder: Float = 1f,
    val pillShadow: Float = 4f,
    // 图标与文字
    val iconSize: Float = 23f,
    val iconScale: Float = 1.06f,
    val labelSize: Float = 10.5f,
    val labelGap: Float = 3f,
    // 玻璃
    val blurRadius: Float = 26f,
    val glassTop: Float = 0.30f,
    val glassBottom: Float = 0.20f,
    val rimTop: Float = 0.90f,
    val rimBottom: Float = 0.12f,
    val sheen: Float = 0.24f,
) {
    companion object {
        private const val PREFS = "xiaofu_prefs"

        fun load(context: Context): NavTuning {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = NavTuning()
            return NavTuning(
                barHeight = p.getFloat("nav_bar_height", d.barHeight),
                barRadius = p.getFloat("nav_bar_radius", d.barRadius),
                barBottom = p.getFloat("nav_bar_bottom", d.barBottom),
                barBorder = p.getFloat("nav_bar_border", d.barBorder),
                barShadow = p.getFloat("nav_bar_shadow", d.barShadow),
                rowPadding = p.getFloat("nav_row_padding", d.rowPadding),
                cellWidth = p.getFloat("nav_cell_width", d.cellWidth),
                pillWidth = p.getFloat("nav_pill_width", d.pillWidth),
                pillPadH = p.getFloat("nav_pill_pad_h", d.pillPadH),
                pillPadV = p.getFloat("nav_pill_pad_v", d.pillPadV),
                pillRadius = p.getFloat("nav_pill_radius", d.pillRadius),
                pillBorder = p.getFloat("nav_pill_border", d.pillBorder),
                pillShadow = p.getFloat("nav_pill_shadow", d.pillShadow),
                iconSize = p.getFloat("nav_icon_size", d.iconSize),
                iconScale = p.getFloat("nav_icon_scale", d.iconScale),
                labelSize = p.getFloat("nav_label_size", d.labelSize),
                labelGap = p.getFloat("nav_label_gap", d.labelGap),
                blurRadius = p.getFloat("nav_blur_radius", d.blurRadius),
                glassTop = p.getFloat("nav_glass_top", d.glassTop),
                glassBottom = p.getFloat("nav_glass_bottom", d.glassBottom),
                rimTop = p.getFloat("nav_rim_top", d.rimTop),
                rimBottom = p.getFloat("nav_rim_bottom", d.rimBottom),
                sheen = p.getFloat("nav_sheen", d.sheen),
            )
        }

        fun save(context: Context, t: NavTuning) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("nav_bar_height", t.barHeight)
                .putFloat("nav_bar_radius", t.barRadius)
                .putFloat("nav_bar_bottom", t.barBottom)
                .putFloat("nav_bar_border", t.barBorder)
                .putFloat("nav_bar_shadow", t.barShadow)
                .putFloat("nav_row_padding", t.rowPadding)
                .putFloat("nav_cell_width", t.cellWidth)
                .putFloat("nav_pill_width", t.pillWidth)
                .putFloat("nav_pill_pad_h", t.pillPadH)
                .putFloat("nav_pill_pad_v", t.pillPadV)
                .putFloat("nav_pill_radius", t.pillRadius)
                .putFloat("nav_pill_border", t.pillBorder)
                .putFloat("nav_pill_shadow", t.pillShadow)
                .putFloat("nav_icon_size", t.iconSize)
                .putFloat("nav_icon_scale", t.iconScale)
                .putFloat("nav_label_size", t.labelSize)
                .putFloat("nav_label_gap", t.labelGap)
                .putFloat("nav_blur_radius", t.blurRadius)
                .putFloat("nav_glass_top", t.glassTop)
                .putFloat("nav_glass_bottom", t.glassBottom)
                .putFloat("nav_rim_top", t.rimTop)
                .putFloat("nav_rim_bottom", t.rimBottom)
                .putFloat("nav_sheen", t.sheen)
                .apply()
        }

        fun clear(context: Context) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit()
                .remove("nav_bar_height").remove("nav_bar_radius").remove("nav_bar_bottom")
                .remove("nav_bar_border").remove("nav_bar_shadow").remove("nav_row_padding")
                .remove("nav_cell_width").remove("nav_pill_width").remove("nav_pill_pad_h")
                .remove("nav_pill_pad_v").remove("nav_pill_radius").remove("nav_pill_border")
                .remove("nav_pill_shadow").remove("nav_icon_size").remove("nav_icon_scale")
                .remove("nav_label_size").remove("nav_label_gap").remove("nav_blur_radius")
                .remove("nav_glass_top").remove("nav_glass_bottom").remove("nav_rim_top")
                .remove("nav_rim_bottom").remove("nav_sheen")
                .apply()
        }
    }
}

/** 当前底栏参数，供页面底部留白等计算读取 */
val LocalNavTuning = staticCompositionLocalOf { NavTuning() }

/** 底栏参数面板：不遮住底部导航，改动即时可见 */
@Composable
fun NavTuningPanel(
    tuning: NavTuning,
    onChange: (NavTuning) -> Unit,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val reserved = tuning.barHeight.dp + tuning.barBottom.dp + navInset + 12.dp

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = reserved)
            .background(cs.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("底栏参数", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                Text(
                    "改动即时生效，底部可见预览",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TextButton(onClick = {
                NavTuning.clear(context)
                onChange(NavTuning())
            }) { Text("重置", color = cs.onSurfaceVariant) }
            TextButton(onClick = onDone) {
                Text("完成", color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { SectionTitle("外框") }
            item { TuneRow("栏高", tuning.barHeight, 1f, 40f, 120f) { onChange(tuning.copy(barHeight = it)) } }
            item { TuneRow("栏圆角", tuning.barRadius, 1f, 0f, 60f) { onChange(tuning.copy(barRadius = it)) } }
            item { TuneRow("栏底距", tuning.barBottom, 1f, 0f, 48f) { onChange(tuning.copy(barBottom = it)) } }
            item { TuneRow("栏描边", tuning.barBorder, 0.5f, 0f, 4f) { onChange(tuning.copy(barBorder = it)) } }
            item { TuneRow("栏投影", tuning.barShadow, 1f, 0f, 40f) { onChange(tuning.copy(barShadow = it)) } }

            item { SectionTitle("排布") }
            item { TuneRow("栏边留白", tuning.rowPadding, 1f, 0f, 40f) { onChange(tuning.copy(rowPadding = it)) } }
            item { TuneRow("单元宽", tuning.cellWidth, 1f, 40f, 100f) { onChange(tuning.copy(cellWidth = it)) } }

            item { SectionTitle("选中块") }
            item { TuneRow("宽度", tuning.pillWidth, 1f, 24f, 100f) { onChange(tuning.copy(pillWidth = it)) } }
            item { TuneRow("左右内边距", tuning.pillPadH, 0.5f, 0f, 30f) { onChange(tuning.copy(pillPadH = it)) } }
            item { TuneRow("上下内边距", tuning.pillPadV, 0.5f, 0f, 30f) { onChange(tuning.copy(pillPadV = it)) } }
            item { TuneRow("圆角", tuning.pillRadius, 1f, 0f, 60f) { onChange(tuning.copy(pillRadius = it)) } }
            item { TuneRow("描边", tuning.pillBorder, 0.5f, 0f, 4f) { onChange(tuning.copy(pillBorder = it)) } }
            item { TuneRow("投影", tuning.pillShadow, 1f, 0f, 20f) { onChange(tuning.copy(pillShadow = it)) } }

            item { SectionTitle("图标与文字") }
            item { TuneRow("图标尺寸", tuning.iconSize, 0.5f, 14f, 36f) { onChange(tuning.copy(iconSize = it)) } }
            item { TuneRow("选中放大", tuning.iconScale, 0.01f, 1f, 1.4f) { onChange(tuning.copy(iconScale = it)) } }
            item { TuneRow("字号", tuning.labelSize, 0.5f, 8f, 16f) { onChange(tuning.copy(labelSize = it)) } }
            item { TuneRow("文字上距", tuning.labelGap, 0.5f, 0f, 12f) { onChange(tuning.copy(labelGap = it)) } }

            item { SectionTitle("玻璃与模糊") }
            item { TuneRow("模糊半径", tuning.blurRadius, 1f, 0f, 60f) { onChange(tuning.copy(blurRadius = it)) } }
            item { TuneRow("上层透明度", tuning.glassTop, 0.02f, 0f, 1f) { onChange(tuning.copy(glassTop = it)) } }
            item { TuneRow("下层透明度", tuning.glassBottom, 0.02f, 0f, 1f) { onChange(tuning.copy(glassBottom = it)) } }
            item { TuneRow("描边上透明度", tuning.rimTop, 0.02f, 0f, 1f) { onChange(tuning.copy(rimTop = it)) } }
            item { TuneRow("描边下透明度", tuning.rimBottom, 0.02f, 0f, 1f) { onChange(tuning.copy(rimBottom = it)) } }
            item { TuneRow("顶部高光", tuning.sheen, 0.02f, 0f, 1f) { onChange(tuning.copy(sheen = it)) } }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun TuneRow(
    label: String,
    value: Float,
    step: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = format(value)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepButton("−") { onChange((value - step).coerceIn(min, max)) }
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                if (raw.length <= 6 && raw.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                    text = raw
                    raw.toFloatOrNull()?.let { onChange(it.coerceIn(min, max)) }
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(cs.primary),
            modifier = Modifier
                .width(58.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(cs.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        StepButton("+") { onChange((value + step).coerceIn(min, max)) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
    }
}

private fun format(value: Float): String =
    if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
