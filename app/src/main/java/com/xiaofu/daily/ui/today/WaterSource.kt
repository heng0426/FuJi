package com.xiaofu.daily.ui.today

/**
 * 水瓶 / 水碗的可保存条件：上次剩余在 1..5000；
 * 本次剩余留空表示只更新剩余、不记喝水；填写时必须在 0..上次剩余之间。
 */
internal fun sourceCanSave(lastLeftText: String, nowLeftText: String): Boolean {
    val last = lastLeftText.toIntOrNull() ?: return false
    if (last !in 1..5000) return false
    if (nowLeftText.isBlank()) return true
    val now = nowLeftText.toIntOrNull() ?: return false
    return now in 0 until last
}

/** 本次喝水量：两项都填且差值大于 0 时才有值；只填上次剩余不产生记录 */
internal fun sourceDrinkMl(lastLeftText: String, nowLeftText: String): Int? {
    val last = lastLeftText.toIntOrNull() ?: return null
    if (last !in 1..5000) return null
    val now = nowLeftText.toIntOrNull() ?: return null
    if (now !in 0 until last) return null
    return last - now
}

/** 只有用户看不出原因时才提示；空值与越界仅禁用保存 */
internal fun sourceHint(lastLeftText: String, nowLeftText: String): String? {
    val last = lastLeftText.toIntOrNull() ?: return null
    if (last !in 1..5000) return null
    if (nowLeftText.isBlank()) return null
    val now = nowLeftText.toIntOrNull() ?: return null
    return when {
        now > last -> "本次剩余不能超过上次剩余"
        now == last -> "喝水量为 0，不用记"
        else -> null
    }
}
