package com.xiaofu.daily.ui.today

import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.RecordType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 纯聚合逻辑：按本地日期汇总喝水 ml 与进食克数，至少保留两个 7 天窗口 */
class WeekStatsTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun aggregatesByLocalDayAndKeepsTwoWindows() {
        val today = LocalDate.of(2026, 11, 23)
        val records = listOf(
            record(RecordType.WATER, at(today, 9), ml = 200),
            record(RecordType.WATER, at(today, 10), ml = 50),
            record(RecordType.FOOD, at(today.minusDays(2), 12), grams = 45),
            record(RecordType.WEIGHT, at(today, 8), weight = 4200),
            record(RecordType.WEIGHT, at(today, 11), weight = 4300),
        )

        val series = buildChartSeries(today, records, zone)

        assertEquals(14, series.days.size)
        assertEquals(today.minusDays(13), series.days.first().date)
        assertEquals(today, series.days.last().date)
        assertEquals(250, series.days.last().waterMl)
        assertEquals(0, series.days.last().foodGrams)
        assertEquals(45, series.days.first { it.date == today.minusDays(2) }.foodGrams)
        assertEquals(0, series.days.first { it.date == today.minusDays(5) }.waterMl)
        // 同一天两次称重取较晚的一条；没称重的日子留空
        assertEquals(4300, series.days.last().weightGrams)
        assertNull(series.days.first { it.date == today.minusDays(2) }.weightGrams)
    }

    @Test
    fun emptySeriesStillCoversTwoWindows() {
        val today = LocalDate.of(2026, 11, 23)
        val series = buildChartSeries(today, emptyList(), zone)
        assertEquals(14, series.days.size)
        assertEquals(today.minusDays(13), series.days.first().date)
        assertEquals(0, series.days.sumOf { it.waterMl })
        assertEquals(0, series.days.sumOf { it.foodGrams })
    }

    @Test
    fun startsFromEarliestRecordWhenOlderThanTwoWindows() {
        val today = LocalDate.of(2026, 11, 23)
        val old = today.minusDays(39)
        val series = buildChartSeries(
            today,
            listOf(record(RecordType.WATER, at(old, 9), ml = 90)),
            zone,
        )
        assertEquals(40, series.days.size)
        assertEquals(old, series.days.first().date)
        assertEquals(today, series.days.last().date)
        assertEquals(90, series.days.first().waterMl)
    }

    @Test
    fun capsLookbackAtMaxDays() {
        val today = LocalDate.of(2026, 11, 23)
        val tooOld = today.minusDays(40)
        val series = buildChartSeries(
            today,
            listOf(record(RecordType.WATER, at(tooOld, 9), ml = 10)),
            zone,
            maxDays = 30,
        )
        assertEquals(30, series.days.size)
        assertEquals(today.minusDays(29), series.days.first().date)
        // 超出回看上限的记录不进窗口
        assertEquals(0, series.days.sumOf { it.waterMl })
    }

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        type: RecordType,
        at: Long,
        ml: Int? = null,
        grams: Int? = null,
        weight: Int? = null,
    ) = RecordEntity(
        type = type,
        occurredAt = at,
        createdAt = 0L,
        updatedAt = 0L,
        amountMl = ml,
        totalGrams = grams,
        weightGrams = weight,
    )
}
