package com.xiaofu.daily.ui.today

import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.RecordType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 纯聚合逻辑：按本地日期汇总喝水 ml 与进食克数，空白天保留占位 */
class WeekStatsTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun aggregatesByLocalDayAndKeepsSevenDays() {
        val today = LocalDate.of(2026, 11, 23)
        val records = listOf(
            record(RecordType.WATER, at(today, 9), ml = 200),
            record(RecordType.WATER, at(today, 10), ml = 50),
            record(RecordType.FOOD, at(today.minusDays(2), 12), grams = 45),
            record(RecordType.WEIGHT, at(today, 8)),
        )

        val stats = buildWeekStats(today, records, zone)

        assertEquals(7, stats.days.size)
        assertEquals(today.minusDays(6), stats.days.first().date)
        assertEquals(today, stats.days.last().date)
        assertEquals(250, stats.days.last().waterMl)
        assertEquals(0, stats.days.last().foodGrams)
        assertEquals(45, stats.days.first { it.date == today.minusDays(2) }.foodGrams)
        assertEquals(0, stats.days.first { it.date == today.minusDays(5) }.waterMl)
    }

    @Test
    fun emptyWeekIsAllZero() {
        val today = LocalDate.of(2026, 11, 23)
        val stats = buildWeekStats(today, emptyList(), zone)
        assertEquals(7, stats.days.size)
        assertEquals(0, stats.days.sumOf { it.waterMl })
        assertEquals(0, stats.days.sumOf { it.foodGrams })
    }

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        type: RecordType,
        at: Long,
        ml: Int? = null,
        grams: Int? = null,
    ) = RecordEntity(
        type = type,
        occurredAt = at,
        createdAt = 0L,
        updatedAt = 0L,
        amountMl = ml,
        totalGrams = grams,
    )
}
