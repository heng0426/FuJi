package com.xiaofu.daily.widget

import android.content.Context

/** 按 widgetId 保存界面状态；广播接收器与渲染器不再持有内存态 */
class WidgetStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(widgetId: Int): WidgetState = WidgetState(
        mode = runCatching { WidgetMode.valueOf(prefs.getString(key(widgetId, KEY_MODE), null).orEmpty()) }
            .getOrDefault(WidgetMode.IDLE),
        draft = prefs.getInt(key(widgetId, KEY_DRAFT), 0),
        gramsPerServing = prefs.getInt(key(widgetId, KEY_GRAMS), 15),
        feedback = prefs.getString(key(widgetId, KEY_FEEDBACK), "").orEmpty(),
    )

    fun write(widgetId: Int, state: WidgetState) {
        prefs.edit()
            .putString(key(widgetId, KEY_MODE), state.mode.name)
            .putInt(key(widgetId, KEY_DRAFT), state.draft)
            .putInt(key(widgetId, KEY_GRAMS), state.gramsPerServing)
            .putString(key(widgetId, KEY_FEEDBACK), state.feedback)
            .apply()
    }

    /** 回到待机：保留克/份记忆没有意义，统一清空 */
    fun clear(widgetId: Int) {
        write(widgetId, WidgetState())
    }

    private fun key(widgetId: Int, field: String) = "w$widgetId.$field"

    private companion object {
        const val PREFS = "xiaofu_widget_state"
        const val KEY_MODE = "mode"
        const val KEY_DRAFT = "draft"
        const val KEY_GRAMS = "grams"
        const val KEY_FEEDBACK = "feedback"
    }
}
