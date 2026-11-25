package com.xiaofu.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R

/** 记录类型图标底（时间线 36dp / 概览 22dp / 快捷 圆底） */
@Composable
fun TypeIcon(
    iconRes: Int,
    fg: Color,
    bg: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    radius: Dp = 12.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize), tint = fg)
    }
}

@Composable
fun CircleTypeIcon(iconRes: Int, fg: Color, bg: Color, size: Dp = 22.dp, iconSize: Dp = 14.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize), tint = fg)
    }
}

/** 白卡：20dp 圆角 + 1dp 描边 + 18dp 内边距 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 圆形/胶囊图标按钮（导航、铃铛、返回）；有 onClick 时触控区扩展到 ≥44dp */
@Composable
fun RoundIconButton(
    iconRes: Int,
    onClick: (() -> Unit)? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surface,
    border: Color = MaterialTheme.colorScheme.outline,
    contentDescription: String? = null,
) {
    val shape = CircleShape
    val interactive = onClick != null
    Box(
        modifier = Modifier
            .size(if (interactive) maxOf(size, 44.dp) else size)
            .clip(shape)
            .then(if (interactive) Modifier.clickable(onClick = onClick!!) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(shape).background(background).border(1.dp, border, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription, Modifier.size(iconSize), tint = tint)
        }
    }
}

/** 标签 chip：30dp 高、999dp 圆角、左右 11dp（按真机反馈收窄；30dp ≥ WCAG AA 24dp 最低触控目标） */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIconRes: Int? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surface)
            .border(1.dp, if (selected) Color.Transparent else cs.outline, shape)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                },
            )
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (leadingIconRes != null) {
            Icon(
                painterResource(leadingIconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) cs.onPrimaryContainer else cs.primary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 主按钮：50dp 高、16dp 圆角、橘底深棕字 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) cs.primary else cs.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) cs.onPrimary else cs.onSurfaceVariant,
        )
    }
}

/** 次按钮：白底 1dp 描边 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** 分段控件（主粮/加餐、严重程度） */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) cs.surface else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** 数字步进器：− 值 +（按钮 34dp，可点区 44dp） */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showDivider) {
                    Modifier.padding(vertical = 12.dp)
                } else {
                    Modifier.padding(top = 12.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(R.drawable.ic_ms_remove, "减少", onMinus, minusEnabled)
            Text(
                text = valueText,
                modifier = Modifier.widthIn(min = 64.dp),
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            StepButton(R.drawable.ic_ms_add, "增加", onPlus, plusEnabled)
        }
    }
    if (showDivider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
    }
}

/** 步进按钮：视觉 34dp，触控区扩展到 44dp，带无障碍描述（减少/增加） */
@Composable
private fun StepButton(iconRes: Int, description: String, onClick: () -> Unit, enabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(cs.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(17.dp),
                tint = if (enabled) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/** 大数字输入（喝水弹窗）：44sp + 单位，surface-2 底；compact 为并排输入的小号版本 */
@Composable
fun BigNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    allowDecimal: Boolean = false,
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val style = (if (compact) StyleNumberCompact else StyleNumber).copy(color = cs.onSurface)
    val measured = measurer.measure(AnnotatedString(value.ifEmpty { placeholder.ifEmpty { "0" } }), style)
    val density = LocalDensity.current
    val widthDp = with(density) { measured.size.width.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .padding(vertical = if (compact) 12.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    val filtered = if (allowDecimal) input.filter { it.isDigit() || it == '.' } else input.filter { it.isDigit() }
                    onValueChange(filtered.take(6))
                },
                singleLine = true,
                textStyle = style,
                keyboardOptions = KeyboardOptions(keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.width((widthDp + 10.dp).coerceAtLeast(44.dp)),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                style = style.copy(color = cs.onSurfaceVariant.copy(alpha = 0.35f)),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        inner()
                    }
                },
            )
            Text(unit, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = if (compact) 5.dp else 8.dp))
        }
    }
}

private val StyleNumber = TextStyle(
    fontSize = 44.sp,
    lineHeight = 44.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-1).sp,
    textAlign = TextAlign.Center,
    fontFeatureSettings = "tnum",
)

private val StyleNumberCompact = TextStyle(
    fontSize = 30.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.5).sp,
    textAlign = TextAlign.Center,
    fontFeatureSettings = "tnum",
)

/** 单行文本输入（底部下划线式容器） */
@Composable
fun TextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(cs.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 多行备注框 */
@Composable
fun NoteFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            // 大字号（2.0×）下内容超出 78dp 时可内部滚动，避免溢出绘制
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        )
    }
}

/** 表单区块标题 */
@Composable
fun FormLabel(text: String, badge: String? = null, badgeColor: Color = MaterialTheme.colorScheme.error) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (badge != null) {
            Text(
                badge,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = badgeColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.10f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** 弹窗顶部把手 */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** 自定义标签输入弹窗（分类/药品名/疫苗名称等通用） */
@Composable
fun CustomTagDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "自定义分类",
    placeholder: String = "例如：打喷嚏",
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
        },
        text = {
            TextFieldBox(
                value = value,
                onValueChange = { onValueChange(it.take(20)) },
                placeholder = placeholder,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) {
                Text("添加", color = if (value.isNotBlank()) cs.primary else cs.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = cs.onSurfaceVariant)
            }
        },
    )
}

/**
 * 无障碍触控目标：保持内部视觉尺寸与点击反馈不变，把布局边界（含命中区域）扩展到 ≥ size。
 * 用在可点击修饰链的最外层。
 */
fun Modifier.minTouchTarget(size: Dp): Modifier = composed {
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = maxOf(placeable.width, size.roundToPx())
        val height = maxOf(placeable.height, size.roundToPx())
        layout(width, height) {
            placeable.placeRelative((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}
