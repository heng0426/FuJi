package com.xiaofu.daily.ui.today

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.recordStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 图表里的一天：喝水合计、进食合计（含主粮/加餐）、当天最后一次体重 */
data class DayStat(
    val date: LocalDate,
    val waterMl: Int,
    val foodGrams: Int,
    val weightGrams: Int? = null,
)

/** 从最早记录（最多一年）到今天，按天升序；至少 7 天 */
data class ChartSeries(
    val today: LocalDate,
    val days: List<DayStat>,
) {
    companion object {
        fun empty(today: LocalDate): ChartSeries = ChartSeries(
            today = today,
            days = (6 downTo 0).map { offset ->
                DayStat(today.minusDays(offset.toLong()), waterMl = 0, foodGrams = 0)
            },
        )
    }
}

/** 把记录按本地日期聚合；起点取最早记录，不足 7 天补到 7 天，最多回看 maxDays 天 */
fun buildChartSeries(
    today: LocalDate,
    records: List<RecordEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
    maxDays: Int = 365,
): ChartSeries {
    val byDate = records.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }
    val earliest = byDate.keys.minOrNull()
    val earliestAllowed = today.minusDays((maxDays - 1).toLong())
    val start = if (earliest == null) {
        today.minusDays(6)
    } else {
        maxOf(earliest, earliestAllowed).coerceAtMost(today.minusDays(6))
    }
    val days = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { date ->
            val dayRecords = byDate[date].orEmpty()
            DayStat(
                date = date,
                waterMl = dayRecords.filter { it.type == RecordType.WATER }.sumOf { it.amountMl ?: 0 },
                foodGrams = dayRecords.filter { it.type == RecordType.FOOD }.sumOf { it.totalGrams ?: 0 },
                weightGrams = dayRecords.filter { it.type == RecordType.WEIGHT }
                    .maxByOrNull { it.occurredAt }
                    ?.weightGrams,
            )
        }
        .toList()
    return ChartSeries(today, days)
}

/**
 * 今日页「近 7 天」卡片：窗口 7 天，按天左右拖动回看——右滑看更早、左滑回到今天；
 * 拖动跟手，松手按惯性滚动并吸附到整日。喝水 / 主粮用柱子，体重用折线。
 */
@Composable
fun WeekChartCard(series: ChartSeries, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }

    val lastIndex = series.days.lastIndex.coerceAtLeast(6)
    val minEnd = 6f
    val maxEnd = lastIndex.toFloat()
    var scroll by remember(series.days.size) { mutableFloatStateOf(maxEnd) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }
    val anim = remember(series.days.size) { Animatable(maxEnd) }
    val decay = rememberSplineBasedDecay<Float>()
    val scope = rememberCoroutineScope()

    // 拖动时只有绘制跟随；标题/底注只在跨天时重组
    val endIndex by remember(series.days.size) {
        derivedStateOf { scroll.roundToInt().coerceIn(6, lastIndex) }
    }
    val startIndex = endIndex - 6
    val isCurrent = endIndex == lastIndex
    // 柱子高度按当前窗口最大值归一；跨天时平滑过渡，避免拖动中柱高突跳
    val windowMax = remember(series, endIndex, mode) {
        if (mode == 2) {
            0f
        } else {
            (startIndex..endIndex).maxOfOrNull {
                if (mode == 0) series.days[it].waterMl else series.days[it].foodGrams
            }?.toFloat() ?: 0f
        }
    }
    val scaleMax by animateFloatAsState(
        targetValue = windowMax,
        animationSpec = tween(220),
        label = "chartMax",
    )
    val title = if (isCurrent) {
        "近 7 天"
    } else {
        "${series.days[startIndex].date.monthValue}.${series.days[startIndex].date.dayOfMonth}" +
            "–${series.days[endIndex].date.monthValue}.${series.days[endIndex].date.dayOfMonth}"
    }

    val dragState = rememberDraggableState { delta ->
        val step = widthPx / 7f
        if (step > 0f) {
            scroll = (scroll - delta / step).coerceIn(minEnd, maxEnd)
        }
    }

    fun settle(velocity: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val step = widthPx / 7f
            if (step <= 0f) return@launch
            // 惯性：按松手速度估算还会滑多远，再吸附到最近的一天
            val projected = scroll + decay.calculateTargetValue(0f, -velocity) / step
            val target = projected.coerceIn(minEnd, maxEnd).roundToInt()
                .coerceIn(minEnd.toInt(), maxEnd.toInt()).toFloat()
            anim.snapTo(scroll)
            // 带着松手速度收进最近的一天，先快后慢
            anim.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = -velocity / step,
            ) {
                scroll = value.coerceIn(minEnd, maxEnd)
            }
        }
    }

    CardSurface(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            PillChip("喝水", selected = mode == 0, onClick = { mode = 0 })
            Spacer(Modifier.padding(horizontal = 3.dp))
            PillChip("主粮", selected = mode == 1, onClick = { mode = 1 })
            Spacer(Modifier.padding(horizontal = 3.dp))
            PillChip("体重", selected = mode == 2, onClick = { mode = 2 })
        }

        DayStrip(
            series = series,
            end = { scroll },
            mode = mode,
            scaleMax = scaleMax,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(132.dp)
                .onSizeChanged { widthPx = it.width }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { settleJob?.cancel() },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        )

        Text(
            footerText(mode, series, startIndex, endIndex, isCurrent),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DayStrip(
    series: ChartSeries,
    end: () -> Float,
    mode: Int,
    scaleMax: Float,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val color = recordStyle(
        when (mode) {
            0 -> RecordType.WATER
            1 -> RecordType.FOOD
            else -> RecordType.WEIGHT
        }
    ).fg
    val valueStyle = TextStyle(fontSize = 9.sp, lineHeight = 11.sp)
    val labelStyle = TextStyle(fontSize = 9.5.sp, lineHeight = 12.sp)
    val trackColor = cs.surfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier.clipToBounds()) {
        val step = size.width / 7f
        if (step <= 0f) return@Canvas
        val endValue = end()
        // 窗口末日在天数坐标上的位置；列 i 的中心 = (i - start + 0.5) * step
        val start = endValue - 6f
        val first = (floor(start).toInt() - 1).coerceAtLeast(0)
        val last = (ceil(start + 6f).toInt() + 1).coerceAtMost(series.days.lastIndex)
        val winEnd = endValue.roundToInt().coerceIn(0, series.days.lastIndex)
        val winStart = (winEnd - 6).coerceAtLeast(0)

        val valueTop = 15.dp.toPx()
        val labelHeight = 16.dp.toPx()
        val barBottom = size.height - labelHeight
        val barArea = (barBottom - valueTop).coerceAtLeast(1f)

        fun centerX(index: Int) = (index - start + 0.5f) * step
        fun valueOf(day: DayStat) = if (mode == 0) day.waterMl else day.foodGrams

        if (mode != 2) {
            for (i in first..last) {
                val day = series.days[i]
                val isToday = day.date == series.today
                val x = centerX(i)
                val barWidth = step * 0.56f
                val value = valueOf(day)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x - barWidth / 2f, valueTop),
                    size = Size(barWidth, barArea),
                    cornerRadius = CornerRadius(7.dp.toPx()),
                )
                if (value > 0 && scaleMax > 0) {
                    val height = (barArea * value / scaleMax).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (isToday) color else color.copy(alpha = 0.45f),
                        topLeft = Offset(x - barWidth / 2f, barBottom - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(minOf(7.dp.toPx(), height / 2f)),
                    )
                    val layout = measurer.measure(
                        value.toString(),
                        valueStyle.copy(
                            color = if (isToday) color else cs.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, 0f))
                }
            }
        } else {
            val points = (winStart..winEnd).mapNotNull { i ->
                series.days[i].weightGrams?.let { i to it }
            }
            if (points.isNotEmpty()) {
                val weights = points.map { it.second }
                val minValue = weights.min()
                val maxValue = weights.max()
                val pad = maxOf(100, (maxValue - minValue) / 4)
                val low = (minValue - pad).coerceAtLeast(0)
                val high = maxValue + pad
                val span = (high - low).coerceAtLeast(1)
                val padY = 6.dp.toPx()
                fun centerY(value: Int) =
                    valueTop + padY + (1f - (value - low).toFloat() / span) * (barArea - padY * 2)

                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(centerX(points.first().first), centerY(points.first().second))
                        points.drop(1).forEach { (index, value) ->
                            lineTo(centerX(index), centerY(value))
                        }
                    }
                    drawPath(
                        path,
                        color = color.copy(alpha = 0.55f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                points.forEach { (index, value) ->
                    val isToday = series.days[index].date == series.today
                    drawCircle(
                        color = color,
                        radius = (if (isToday) 4.5.dp else 3.dp).toPx(),
                        center = Offset(centerX(index), centerY(value)),
                    )
                }
            }
        }

        for (i in first..last) {
            val day = series.days[i]
            val isToday = day.date == series.today
            val label = if (isToday) "今天" else "${day.date.monthValue}/${day.date.dayOfMonth}"
            val layout = measurer.measure(
                label,
                labelStyle.copy(
                    color = if (isToday) color else cs.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(
                    centerX(i) - layout.size.width / 2f,
                    size.height - layout.size.height - 1.dp.toPx(),
                ),
            )
        }
    }
}

private fun footerText(
    mode: Int,
    series: ChartSeries,
    startIndex: Int,
    endIndex: Int,
    isCurrent: Boolean,
): String {
    val days = series.days.subList(startIndex, endIndex + 1)
    if (mode == 2) return weightSummary(days, isCurrent)
    val values = days.map { if (mode == 0) it.waterMl else it.foodGrams }
    val total = values.sum()
    if (total == 0) {
        return if (isCurrent) "近 7 天还没有记录，记一笔就会出现在这里" else "这 7 天没有记录"
    }
    val unit = if (mode == 0) "ml" else "克"
    return if (isCurrent) {
        "今天 ${values.last()} $unit · 日均 ${total / 7} $unit"
    } else {
        "日均 ${total / 7} $unit"
    }
}

private fun weightSummary(days: List<DayStat>, isCurrent: Boolean): String {
    val weights = days.mapNotNull { it.weightGrams }
    if (weights.isEmpty()) {
        return if (isCurrent) "近 7 天还没有体重记录，记一笔就会出现在这里" else "这 7 天没有称重"
    }
    val latest = weights.last()
    if (weights.size == 1) return "最近 ${Fmt.jin(latest)} 斤"
    val diff = latest - weights.first()
    if (diff == 0) return "最近 ${Fmt.jin(latest)} 斤"
    val sign = if (diff > 0) "+" else "-"
    return "最近 ${Fmt.jin(latest)} 斤 · 变化 $sign${Fmt.jin(abs(diff))} 斤"
}
