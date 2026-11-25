package com.xiaofu.daily.ui.today

/**
 * 水瓶换算：加水后总量 − 当前剩余 = 本次喝水量。
 * 任一为空、加水后超出 1..5000、剩余不在 0..加水后之间，或差值为 0 时返回 null。
 */
internal fun bottleDrinkMl(addedText: String, leftText: String): Int? {
    val added = addedText.toIntOrNull() ?: return null
    val left = leftText.toIntOrNull() ?: return null
    if (added !in 1..5000 || left !in 0 until added) return null
    return added - left
}

/** 只有用户看不出原因时才提示；空值与越界仅禁用保存 */
internal fun bottleHint(addedText: String, leftText: String): String? {
    val added = addedText.toIntOrNull() ?: return null
    val left = leftText.toIntOrNull() ?: return null
    if (added !in 1..5000) return null
    return when {
        left > added -> "剩余不能超过加水后水量"
        left == added -> "喝水量为 0，不用记"
        else -> null
    }
}
