package com.xiaofu.daily.ui.today

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.Flow

/** 近 7 天里的一天：喝水合计、进食合计（含主粮/加餐）、当天最后一次体重 */
data class WeekDayStat(
    val date: LocalDate,
    val waterMl: Int,
    val foodGrams: Int,
    val weightGrams: Int? = null,
)

data class WeekStats(
    val today: LocalDate,
    val days: List<WeekDayStat>,
) {
    companion object {
        fun empty(today: LocalDate): WeekStats = WeekStats(
            today = today,
            days = (6 downTo 0).map { offset ->
                WeekDayStat(today.minusDays(offset.toLong()), waterMl = 0, foodGrams = 0)
            },
        )
    }
}

/** 把区间记录按本地日期聚合；end 为窗口最后一天，默认今天 */
fun buildWeekStats(
    today: LocalDate,
    records: List<RecordEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
    end: LocalDate = today,
): WeekStats {
    val byDate = records.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }
    return WeekStats(
        today = today,
        days = (6 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong())
            val dayRecords = byDate[date].orEmpty()
            WeekDayStat(
                date = date,
                waterMl = dayRecords.filter { it.type == RecordType.WATER }
                    .sumOf { it.amountMl ?: 0 },
                foodGrams = dayRecords.filter { it.type == RecordType.FOOD }
                    .sumOf { it.totalGrams ?: 0 },
                weightGrams = dayRecords.filter { it.type == RecordType.WEIGHT }
                    .maxByOrNull { it.occurredAt }
                    ?.weightGrams,
            )
        },
    )
}

/**
 * 今日页「近 7 天」卡片：左右翻页回看更早的 7 天，跟手滑动、松手吸附；
 * 喝水 / 主粮用柱子，体重用折线；今天用实色高亮。
 */
@Composable
fun WeekChartCard(
    current: WeekStats,
    pageCount: Int,
    statsForPage: (Int) -> Flow<WeekStats>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    val loaded = remember { mutableStateMapOf<Int, WeekStats>() }
    val today = current.today

    val page = pagerState.currentPage
    val title = if (page == 0) "近 7 天" else pageRangeTitle(today, page)
    val footer = (loaded[page] ?: if (page == 0) current else null)
        ?.let { footerText(mode, it) }
        ?: " "

    CardSurface(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = title,
                modifier = Modifier.weight(1f),
                animationSpec = tween(140),
                label = "weekTitle",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                )
            }
            PillChip("喝水", selected = mode == 0, onClick = { mode = 0 })
            Spacer(Modifier.padding(horizontal = 3.dp))
            PillChip("主粮", selected = mode == 1, onClick = { mode = 1 })
            Spacer(Modifier.padding(horizontal = 3.dp))
            PillChip("体重", selected = mode == 2, onClick = { mode = 2 })
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(144.dp),
            beyondViewportPageCount = 1,
        ) { index ->
            val stats by remember(index) { statsForPage(index) }
                .collectAsState(initial = if (index == 0) current else emptyPage(today, index))
            LaunchedEffect(stats) { loaded[index] = stats }
            WeekChartBody(stats, mode)
        }

        Crossfade(
            targetState = footer,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            animationSpec = tween(140),
            label = "weekFooter",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekChartBody(stats: WeekStats, mode: Int) {
    val color = recordStyle(
        when (mode) {
            0 -> RecordType.WATER
            1 -> RecordType.FOOD
            else -> RecordType.WEIGHT
        }
    ).fg
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(132.dp),
    ) {
        if (mode == 2) {
            WeightTrend(stats, color, Modifier.fillMaxSize())
        } else {
            BarTrend(stats, mode, color, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun BarTrend(stats: WeekStats, mode: Int, color: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val values = stats.days.map { if (mode == 0) it.waterMl else it.foodGrams }
    val max = values.maxOrNull() ?: 0
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        stats.days.forEachIndexed { index, day ->
            val value = values[index]
            val isToday = day.date == stats.today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (value > 0) value.toString() else " ",
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = if (isToday) color else cs.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.56f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(7.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.5f)),
                    )
                    if (value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.56f)
                                .height((92f * value / max.coerceAtLeast(1)).dp.coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isToday) color else color.copy(alpha = 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isToday) "今天" else "${day.date.monthValue}/${day.date.dayOfMonth}",
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    color = if (isToday) color else cs.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 体重折线：只连有记录的日期，纵轴按窗口内最高/最低加一点留白 */
@Composable
private fun WeightTrend(stats: WeekStats, color: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val points = stats.days.mapIndexedNotNull { index, day ->
        day.weightGrams?.let { index to it }
    }
    Box(modifier) {
        if (points.isNotEmpty()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp)
                    .height(92.dp),
            ) {
                val gap = 6.dp.toPx()
                val cell = (size.width - gap * 6f) / 7f
                fun centerX(index: Int) = index * (cell + gap) + cell / 2f

                val weights = points.map { it.second }
                val minValue = weights.min()
                val maxValue = weights.max()
                val pad = maxOf(100, (maxValue - minValue) / 4)
                val low = (minValue - pad).coerceAtLeast(0)
                val high = maxValue + pad
                val span = (high - low).coerceAtLeast(1)
                val topPad = 6.dp.toPx()
                val bottomPad = 6.dp.toPx()
                fun centerY(value: Int) =
                    topPad + (1f - (value - low).toFloat() / span) * (size.height - topPad - bottomPad)

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
                    val isToday = stats.days[index].date == stats.today
                    drawCircle(
                        color = color,
                        radius = (if (isToday) 4.5.dp else 3.dp).toPx(),
                        center = Offset(centerX(index), centerY(value)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stats.days.forEach { day ->
                val isToday = day.date == stats.today
                Text(
                    if (isToday) "今天" else "${day.date.monthValue}/${day.date.dayOfMonth}",
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    color = if (isToday) color else cs.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun emptyPage(today: LocalDate, page: Int): WeekStats {
    val end = today.minusDays(page * 7L)
    return WeekStats(
        today = today,
        days = (6 downTo 0).map { offset ->
            WeekDayStat(end.minusDays(offset.toLong()), waterMl = 0, foodGrams = 0)
        },
    )
}

private fun pageRangeTitle(today: LocalDate, page: Int): String {
    val end = today.minusDays(page * 7L)
    val start = end.minusDays(6)
    return "${start.monthValue}.${start.dayOfMonth}–${end.monthValue}.${end.dayOfMonth}"
}

private fun footerText(mode: Int, stats: WeekStats): String {
    val isCurrent = stats.days.any { it.date == stats.today }
    if (mode == 2) return weightSummary(stats, isCurrent)
    val values = stats.days.map { if (mode == 0) it.waterMl else it.foodGrams }
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

private fun weightSummary(stats: WeekStats, isCurrent: Boolean): String {
    val weights = stats.days.mapNotNull { it.weightGrams }
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
