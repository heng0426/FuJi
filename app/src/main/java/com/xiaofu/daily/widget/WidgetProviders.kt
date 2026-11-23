package com.xiaofu.daily.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 4×2 组合：喝水 + 喂食 */
class CombinedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetUpdater.refreshFromReceiver(context, WidgetKind.COMBINED, appWidgetIds, this)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStateStore(context)
        appWidgetIds.forEach { store.clear(it) }
    }
}

/** 2×2 喝水 */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetUpdater.refreshFromReceiver(context, WidgetKind.WATER, appWidgetIds, this)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStateStore(context)
        appWidgetIds.forEach { store.clear(it) }
    }
}

/** 2×2 喂食 */
class FeedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        WidgetUpdater.refreshFromReceiver(context, WidgetKind.FEED, appWidgetIds, this)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStateStore(context)
        appWidgetIds.forEach { store.clear(it) }
    }
}
