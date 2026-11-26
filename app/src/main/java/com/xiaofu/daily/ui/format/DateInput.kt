package com.xiaofu.daily.ui.format

import java.time.LocalDate

/**
 * 日期输入解析：按非数字分段提取，兼容 2026-11-23 / 2026/11/23 / 2026年11月23日。
 * 年份限制在 2000–2199，与日历页 200 年的翻页范围一致。
 */
fun parseDateInput(raw: String): LocalDate? {
    val parts = raw.trim().split(Regex("\\D+")).filter { it.isNotBlank() }
    if (parts.size != 3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val (year, month, day) = numbers
    if (year !in 2000..2199) return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}
