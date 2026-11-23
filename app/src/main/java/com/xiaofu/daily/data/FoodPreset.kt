package com.xiaofu.daily.data

/** 喂食快捷预设：默认早餐 3 份、晚饭 4 份，可追加自定义 */
data class FoodPreset(
    val name: String,
    val servings: Int,
) {
    companion object {
        val defaults = listOf(
            FoodPreset("早餐", 3),
            FoodPreset("晚饭", 4),
        )
    }
}
