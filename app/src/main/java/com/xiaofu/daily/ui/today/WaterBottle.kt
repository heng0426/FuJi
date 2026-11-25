package com.xiaofu.daily.ui.today

/**
 * 水瓶换算：上次剩余 − 本次剩余 = 本次喝水量。
 * 任一为空、上次剩余超出 1..5000、本次剩余不在 0..上次剩余之间，或差值为 0 时返回 null。
 */
internal fun bottleDrinkMl(lastLeftText: String, nowLeftText: String): Int? {
    val last = lastLeftText.toIntOrNull() ?: return null
    val now = nowLeftText.toIntOrNull() ?: return null
    if (last !in 1..5000 || now !in 0 until last) return null
    return last - now
}

/** 只有用户看不出原因时才提示；空值与越界仅禁用保存 */
internal fun bottleHint(lastLeftText: String, nowLeftText: String): String? {
    val last = lastLeftText.toIntOrNull() ?: return null
    val now = nowLeftText.toIntOrNull() ?: return null
    if (last !in 1..5000) return null
    return when {
        now > last -> "本次剩余不能超过上次剩余"
        now == last -> "喝水量为 0，不用记"
        else -> null
    }
}
