package com.xiaofu.daily.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 00:05 刷新点的计算：当天未到就排今天，过了就排明天，跨时区按本地时间 */
class WidgetScheduleTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun beforeFiveAfterMidnightSchedulesToday() {
        val now = ZonedDateTime.of(2026, 11, 24, 0, 3, 0, 0, zone)
        val next = WidgetRefreshScheduler.nextMidnightRefresh(now)
        assertEquals(ZonedDateTime.of(2026, 11, 24, 0, 5, 0, 0, zone), next)
    }

    @Test
    fun afterFiveSchedulesTomorrow() {
        val now = ZonedDateTime.of(2026, 11, 24, 0, 6, 0, 0, zone)
        val next = WidgetRefreshScheduler.nextMidnightRefresh(now)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 0, 5, 0, 0, zone), next)
    }

    @Test
    fun daytimeSchedulesNextDay() {
        val now = ZonedDateTime.of(2026, 11, 24, 20, 30, 0, 0, zone)
        val next = WidgetRefreshScheduler.nextMidnightRefresh(now)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 0, 5, 0, 0, zone), next)
    }
}
