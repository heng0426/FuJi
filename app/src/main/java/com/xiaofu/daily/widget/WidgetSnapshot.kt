package com.xiaofu.daily.widget

import com.xiaofu.daily.AppContainer
import kotlinx.coroutines.flow.first

/** 渲染小组件所需的一次性快照；一次查询供所有实例共用 */
data class WidgetSnapshot(
    val petName: String,
    val waterMl: Int,
    val mainServings: Int,
    val mainGrams: Int,
    val lastWaterMl: Int,
    val lastServings: Int,
    val servingGrams: Int,
) {
    val lastFoodGrams: Int get() = lastServings * servingGrams

    companion object {
        suspend fun load(): WidgetSnapshot {
            val repo = AppContainer.repository
            val profile = repo.observeProfile().first()
            val summary = repo.observeDaySummary().first()
            return WidgetSnapshot(
                petName = profile?.name?.takeIf { it.isNotBlank() } ?: "小福",
                waterMl = summary.waterMl,
                mainServings = summary.mainServings,
                mainGrams = summary.mainGrams,
                lastWaterMl = repo.lastWaterMl(),
                lastServings = repo.lastServings(),
                servingGrams = repo.servingGrams(),
            )
        }
    }
}
