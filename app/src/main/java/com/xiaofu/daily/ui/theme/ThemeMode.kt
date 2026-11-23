package com.xiaofu.daily.ui.theme

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 外观偏好存 SharedPreferences：需在 setContent 前同步读取，避免启动闪烁 */
object ThemePreference {

    private const val PREFS = "xiaofu_prefs"
    private const val KEY = "theme_mode"

    fun read(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }
}
