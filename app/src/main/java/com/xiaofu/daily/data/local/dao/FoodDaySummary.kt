package com.xiaofu.daily.data.local.dao

import androidx.room.ColumnInfo
import com.xiaofu.daily.data.local.model.MealKind

/** 当日进食汇总（按餐类分组）：主粮 / 加餐各自的份数与克数合计。 */
data class FoodDaySummary(
    @ColumnInfo(name = "meal_kind")
    val mealKind: MealKind?,
    @ColumnInfo(name = "servings")
    val servings: Double,
    @ColumnInfo(name = "grams")
    val grams: Int,
)
