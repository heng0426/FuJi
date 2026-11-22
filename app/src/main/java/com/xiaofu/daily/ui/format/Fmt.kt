package com.xiaofu.daily.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val weekFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.CHINA)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun time(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(zone).format(timeFormatter)

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** 今天 / 昨天 / yyyy年M月d日 */
    fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> date.format(fullDateFormatter)
        }
    }

    /** 首页顶部日期：2026年11月22日 · 周六 */
    fun todayHeader(): String = LocalDate.now(zone).format(weekFormatter)

    /** 详情时间：2026年11月22日 08:12 */
    fun detailTime(ms: Long): String {
        val zoned = Instant.ofEpochMilli(ms).atZone(zone)
        return "${zoned.format(fullDateFormatter)} ${zoned.format(timeFormatter)}"
    }

    /** 克 → 斤（1 位小数） */
    fun jin(grams: Int): String = String.format(Locale.CHINA, "%.1f", grams / 500.0)

    /** 份数：整数不带小数点 */
    fun servings(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.CHINA, "%.1f", rounded)
    }

    fun epochDayToDate(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun monthDay(day: Long): String = epochDayToDate(day).format(fullDateFormatter)

    fun monthDayOf(ms: Long): String = date(ms).format(fullDateFormatter)

    fun fullDate(day: Long): String = epochDayToDate(day).format(weekFormatter)

    fun monthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    fun weekday(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA))

    /** 今天 / 昨天 / 周三 */
    fun dayLabelOrWeekday(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> weekday(date)
        }
    }
}
