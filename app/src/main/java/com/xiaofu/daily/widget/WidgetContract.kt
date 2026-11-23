package com.xiaofu.daily.widget

/** 小组件类型：4×2 组合、2×2 喝水、2×2 喂食 */
enum class WidgetKind { COMBINED, WATER, FEED }

/** 点击动作：全部走显式广播，不打开 Activity、不弹窗 */
object WidgetActions {
    const val EDIT_WATER = "com.xiaofu.daily.widget.action.EDIT_WATER"
    const val EDIT_FOOD = "com.xiaofu.daily.widget.action.EDIT_FOOD"
    const val STEP_UP = "com.xiaofu.daily.widget.action.STEP_UP"
    const val STEP_DOWN = "com.xiaofu.daily.widget.action.STEP_DOWN"
    const val STEP_GRAMS_UP = "com.xiaofu.daily.widget.action.STEP_GRAMS_UP"
    const val STEP_GRAMS_DOWN = "com.xiaofu.daily.widget.action.STEP_GRAMS_DOWN"
    const val CONFIRM = "com.xiaofu.daily.widget.action.CONFIRM"
    const val CANCEL = "com.xiaofu.daily.widget.action.CANCEL"
}

object WidgetExtras {
    const val WIDGET_ID = "com.xiaofu.daily.widget.extra.WIDGET_ID"
    const val KIND = "com.xiaofu.daily.widget.extra.KIND"
}

/** 单个实例的界面模式；持久化在 SharedPreferences，进程重启后可恢复 */
enum class WidgetMode { IDLE, EDIT_WATER, EDIT_FOOD, ERROR }

/**
 * 单个 widgetId 的界面状态。
 * draft：确认态草稿（喝水 ml 或喂食份数）；feedback：写入失败提示。
 */
data class WidgetState(
    val mode: WidgetMode = WidgetMode.IDLE,
    val draft: Int = 0,
    val gramsPerServing: Int = 15,
    val feedback: String = "",
)
