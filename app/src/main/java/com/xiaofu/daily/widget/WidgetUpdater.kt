package com.xiaofu.daily.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 渲染与刷新的统一入口；写入后刷新全部实例，保证三个组件数据一致 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val COALESCE_MS = 120L

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 合并短时间内的多次刷新请求（批量删除会连续触发） */
    private val requests = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var appContext: Context? = null

    init {
        scope.launch {
            for (ignored in requests) {
                val context = appContext ?: continue
                delay(COALESCE_MS)
                runCatching { refreshAll(context) }
                    .onFailure { Log.w(TAG, "refreshAll failed", it) }
            }
        }
    }

    private val providers = listOf(
        CombinedWidgetProvider::class.java to WidgetKind.COMBINED,
        WaterWidgetProvider::class.java to WidgetKind.WATER,
        FeedWidgetProvider::class.java to WidgetKind.FEED,
    )

    /** App 侧调用（写入记录、回到前台、恢复备份）：不阻塞调用方 */
    fun refreshAllAsync(context: Context) {
        appContext = context.applicationContext
        requests.trySend(Unit)
    }

    /** provider.onUpdate 调用：用 goAsync 撑住广播，避免渲染被系统提前回收 */
    fun refreshFromReceiver(
        context: Context,
        kind: WidgetKind,
        ids: IntArray,
        receiver: BroadcastReceiver,
    ) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val snapshot = WidgetSnapshot.load()
                ids.forEach { update(app, it, kind, snapshot) }
            } catch (t: Throwable) {
                Log.w(TAG, "refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    suspend fun refreshAll(context: Context) {
        val snapshot = WidgetSnapshot.load()
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { (provider, kind) ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                update(context, id, kind, snapshot, manager)
            }
        }
    }

    suspend fun refreshWidget(context: Context, widgetId: Int, kind: WidgetKind) {
        update(context, widgetId, kind, WidgetSnapshot.load(), AppWidgetManager.getInstance(context))
    }

    private fun update(
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        snapshot: WidgetSnapshot,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        val state = WidgetStateStore(context).read(widgetId)
        val views = WidgetRenderer.render(context, widgetId, kind, snapshot, state)
        manager.updateAppWidget(widgetId, views)
    }
}
