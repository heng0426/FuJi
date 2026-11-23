package com.xiaofu.daily.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.theme.recordStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 近 7 天里的一天：喝水合计与进食合计（含主粮/加餐） */
data class WeekDayStat(
    val date: LocalDate,
    val waterMl: Int,
    val foodGrams: Int,
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

/** 把区间记录按本地日期聚合；时间换算在本地时区做，避免 SQL 时区问题 */
fun buildWeekStats(
    today: LocalDate,
    records: List<RecordEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): WeekStats {
    val byDate = records.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }
    return WeekStats(
        today = today,
        days = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dayRecords = byDate[date].orEmpty()
            WeekDayStat(
                date = date,
                waterMl = dayRecords.filter { it.type == RecordType.WATER }
                    .sumOf { it.amountMl ?: 0 },
                foodGrams = dayRecords.filter { it.type == RecordType.FOOD }
                    .sumOf { it.totalGrams ?: 0 },
            )
        },
    )
}

/**
 * 今日页「近 7 天」卡片：喝水 / 主粮切换，柱高按 7 天最大值归一，今天用实色高亮。
 */
@Composable
fun WeekChartCard(stats: WeekStats, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val water = mode == 0
    val style = recordStyle(if (water) RecordType.WATER else RecordType.FOOD)
    val values = stats.days.map { if (water) it.waterMl else it.foodGrams }
    val max = values.maxOrNull() ?: 0
    val total = values.sum()
    val unit = if (water) "ml" else "克"

    CardSurface(modifier = modifier, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "近 7 天",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            PillChip("喝水", selected = water, onClick = { mode = 0 })
            Spacer(Modifier.padding(horizontal = 3.dp))
            PillChip("主粮", selected = !water, onClick = { mode = 1 })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(132.dp),
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
                        color = if (isToday) style.fg else cs.onSurfaceVariant,
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
                                    .background(if (isToday) style.fg else style.fg.copy(alpha = 0.45f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isToday) "今天" else "${day.date.monthValue}/${day.date.dayOfMonth}",
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        color = if (isToday) style.fg else cs.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            if (total == 0) {
                "近 7 天还没有记录，记一笔就会出现在这里"
            } else {
                "今天 ${values.last()} $unit · 日均 ${total / 7} $unit"
            },
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
