package com.xiaofu.daily.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.model.MealKind
import kotlinx.coroutines.launch

/**
 * 小组件全部点击的落点：进入确认态、± 调量、取消、确认写入、撤销。
 * 不打开 Activity、不弹窗；确认后原地显示 4 秒「已记 · 撤销」，再回到汇总。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val widgetId = intent.getIntExtra(WidgetExtras.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val kind = runCatching {
            WidgetKind.valueOf(intent.getStringExtra(WidgetExtras.KIND).orEmpty())
        }.getOrNull() ?: return

        val pending = goAsync()
        val app = context.applicationContext
        WidgetUpdater.scope.launch {
            try {
                handle(app, widgetId, kind, action)
            } catch (t: Throwable) {
                Log.w(TAG, "action $action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, widgetId: Int, kind: WidgetKind, action: String) {
        val store = WidgetStateStore(context)
        val state = store.read(widgetId)
        when (action) {
            WidgetActions.EDIT_WATER, WidgetActions.EDIT_FOOD -> {
                val isWater = action == WidgetActions.EDIT_WATER
                // 预填沿用 App「上次值」，与弹窗行为一致
                val snapshot = WidgetSnapshot.load()
                store.write(
                    widgetId,
                    WidgetState(
                        mode = if (isWater) WidgetMode.EDIT_WATER else WidgetMode.EDIT_FOOD,
                        draft = if (isWater) {
                            snapshot.lastWaterMl.coerceIn(WATER_MIN, WATER_MAX)
                        } else {
                            snapshot.lastServings.coerceIn(FOOD_MIN, FOOD_MAX)
                        },
                        gramsPerServing = snapshot.servingGrams.coerceIn(1, 200),
                    ),
                )
                WidgetUpdater.refreshWidget(context, widgetId, kind)
            }

            WidgetActions.STEP_DOWN, WidgetActions.STEP_UP -> {
                val isWater = state.mode == WidgetMode.EDIT_WATER
                if (!isWater && state.mode != WidgetMode.EDIT_FOOD) return
                val step = if (isWater) WATER_STEP else 1
                val delta = if (action == WidgetActions.STEP_UP) step else -step
                val draft = (state.draft + delta).coerceIn(
                    if (isWater) WATER_MIN else FOOD_MIN,
                    if (isWater) WATER_MAX else FOOD_MAX,
                )
                store.write(widgetId, state.copy(draft = draft))
                WidgetUpdater.refreshWidget(context, widgetId, kind)
            }

            WidgetActions.STEP_GRAMS_DOWN, WidgetActions.STEP_GRAMS_UP -> {
                if (state.mode != WidgetMode.EDIT_FOOD) return
                val delta = if (action == WidgetActions.STEP_GRAMS_UP) 1 else -1
                val grams = (state.gramsPerServing + delta).coerceIn(1, 200)
                store.write(widgetId, state.copy(gramsPerServing = grams))
                WidgetUpdater.refreshWidget(context, widgetId, kind)
            }

            WidgetActions.CANCEL -> {
                if (state.mode == WidgetMode.EDIT_WATER || state.mode == WidgetMode.EDIT_FOOD) {
                    store.clear(widgetId)
                    WidgetUpdater.refreshWidget(context, widgetId, kind)
                }
            }

            WidgetActions.CONFIRM -> confirm(context, widgetId, kind, state, store)
        }
    }

    private suspend fun confirm(
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        state: WidgetState,
        store: WidgetStateStore,
    ) {
        val isWater = state.mode == WidgetMode.EDIT_WATER
        if (!isWater && state.mode != WidgetMode.EDIT_FOOD) return

        val draft = if (isWater) {
            state.draft.coerceIn(WATER_MIN, WATER_MAX)
        } else {
            state.draft.coerceIn(FOOD_MIN, FOOD_MAX)
        }
        val grams = state.gramsPerServing.coerceIn(1, 200)
        val now = System.currentTimeMillis()
        try {
            if (isWater) {
                AppContainer.repository.saveWater(draft, now)
            } else {
                AppContainer.repository.saveFood(MealKind.MAIN, draft, grams, null, now)
            }
            // 不显示「已记」：直接回汇总，数字与「上次 xx」都已更新
            store.clear(widgetId)
            WidgetUpdater.refreshAll(context)
        } catch (t: Throwable) {
            Log.w(TAG, "save failed", t)
            store.write(
                widgetId,
                WidgetState(
                    mode = WidgetMode.ERROR,
                    draft = draft,
                    gramsPerServing = grams,
                    feedback = context.getString(R.string.widget_failed),
                ),
            )
            WidgetUpdater.refreshWidget(context, widgetId, kind)
        }
    }

    private companion object {
        const val TAG = "WidgetAction"
        const val WATER_STEP = 10
        const val WATER_MIN = 5
        const val WATER_MAX = 500
        const val FOOD_MIN = 1
        const val FOOD_MAX = 20
    }
}
