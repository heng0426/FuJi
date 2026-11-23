package com.xiaofu.daily.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.xiaofu.daily.R
import com.xiaofu.daily.ui.theme.ThemeMode
import com.xiaofu.daily.ui.theme.ThemePreference

/**
 * RemoteViews 渲染：待机汇总与卡片内确认态共用一套布局，靠可见性切换。
 * 颜色按 App「外观」设置显式下发（浅/深两套资源），不依赖系统夜间模式。
 */
object WidgetRenderer {

    fun layoutOf(kind: WidgetKind): Int = when (kind) {
        WidgetKind.COMBINED -> R.layout.widget_combined
        WidgetKind.WATER -> R.layout.widget_water
        WidgetKind.FEED -> R.layout.widget_feed
    }

    fun render(
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        snapshot: WidgetSnapshot,
        state: WidgetState,
    ): RemoteViews {
        val p = palette(context, resolveDark(context))
        val rv = RemoteViews(context.packageName, layoutOf(kind))
        rv.setInt(R.id.widget_root, "setBackgroundResource", p.card)
        bindHeader(rv, context, kind, snapshot, state, p)
        bindIdle(rv, context, widgetId, kind, snapshot, state, p)
        bindEdit(rv, context, widgetId, kind, state, p)
        return rv
    }

    // ---------- 头部 ----------

    private fun bindHeader(
        rv: RemoteViews,
        context: Context,
        kind: WidgetKind,
        snapshot: WidgetSnapshot,
        state: WidgetState,
        p: Palette,
    ) {
        rv.setTextViewText(R.id.pet_name, snapshot.petName)
        rv.setTextColor(R.id.pet_name, p.text)
        rv.setInt(R.id.pet_icon, "setColorFilter", p.accent)

        // 2×2 的头部在待机与确认态共用：名字 + 今日喝水/主粮
        when (kind) {
            WidgetKind.WATER -> rv.setTextColor(R.id.water_label, p.text2)
            WidgetKind.FEED -> rv.setTextColor(R.id.food_label, p.text2)
            WidgetKind.COMBINED -> Unit
        }
        if (kind != WidgetKind.COMBINED) return
        if (state.mode != WidgetMode.ERROR) {
            rv.setViewVisibility(R.id.fb_row, View.GONE)
            return
        }

        rv.setViewVisibility(R.id.fb_row, View.VISIBLE)
        rv.setImageViewResource(R.id.fb_icon, R.drawable.ic_ms_warning)
        rv.setInt(R.id.fb_icon, "setColorFilter", p.error)
        rv.setTextViewText(R.id.fb_text, state.feedback)
        rv.setTextColor(R.id.fb_text, p.error)
    }

    // ---------- 待机 ----------

    private fun bindIdle(
        rv: RemoteViews,
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        snapshot: WidgetSnapshot,
        state: WidgetState,
        p: Palette,
    ) {
        when (kind) {
            WidgetKind.COMBINED -> {
                rv.setTextColor(R.id.water_label, p.text2)
                rv.setTextColor(R.id.water_total, p.text)
                rv.setTextColor(R.id.water_unit, p.text2)
                rv.setTextViewText(R.id.water_total, snapshot.waterMl.toString())

                rv.setTextViewText(
                    R.id.food_line,
                    "主粮 ${snapshot.mainServings} 份 · ${snapshot.mainGrams} 克",
                )
                rv.setTextColor(R.id.food_line, p.text2)
                rv.setInt(R.id.divider, "setBackgroundColor", p.outline)

                // 与 2×2 组件同色：浅水蓝 / 浅橘底
                rv.setInt(R.id.btn_water, "setBackgroundResource", p.waterTint)
                rv.setInt(R.id.btn_water_icon, "setColorFilter", p.water)
                rv.setTextColor(R.id.btn_water_label, p.water)
                rv.setOnClickPendingIntent(
                    R.id.btn_water,
                    pending(context, widgetId, kind, WidgetActions.EDIT_WATER, CODE_EDIT_WATER),
                )
                rv.setContentDescription(
                    R.id.btn_water,
                    "确认喝水，默认 ${snapshot.lastWaterMl} 毫升",
                )

                rv.setInt(R.id.btn_food, "setBackgroundResource", p.foodTint)
                rv.setInt(R.id.btn_food_icon, "setColorFilter", p.food)
                rv.setTextColor(R.id.btn_food_label, p.food)
                rv.setOnClickPendingIntent(
                    R.id.btn_food,
                    pending(context, widgetId, kind, WidgetActions.EDIT_FOOD, CODE_EDIT_FOOD),
                )
                rv.setContentDescription(
                    R.id.btn_food,
                    "确认喂食，默认 ${snapshot.lastServings} 份 ${snapshot.lastFoodGrams} 克",
                )
            }

            WidgetKind.WATER -> {
                rv.setTextColor(R.id.water_label, p.text2)
                rv.setTextColor(R.id.water_total, p.text)
                rv.setTextColor(R.id.water_unit, p.text2)
                rv.setTextViewText(R.id.water_total, snapshot.waterMl.toString())

                bindSubLine(rv, "上次 ${snapshot.lastWaterMl} ml", state, p)

                rv.setInt(R.id.water_cta, "setBackgroundResource", p.waterTint)
                rv.setInt(R.id.water_cta_icon, "setColorFilter", p.water)
                rv.setTextColor(R.id.water_cta_label, p.water)
                rv.setOnClickPendingIntent(
                    R.id.water_cta,
                    pending(context, widgetId, kind, WidgetActions.EDIT_WATER, CODE_EDIT_WATER),
                )
                // 整卡点按同样进入确认态（与按钮一致）
                rv.setOnClickPendingIntent(
                    R.id.idle_root,
                    pending(context, widgetId, kind, WidgetActions.EDIT_WATER, CODE_CARD),
                )
                rv.setContentDescription(
                    R.id.water_cta,
                    "确认喝水，默认 ${snapshot.lastWaterMl} 毫升",
                )
            }

            WidgetKind.FEED -> {
                rv.setTextColor(R.id.food_label, p.text2)
                rv.setTextColor(R.id.feed_servings, p.text)
                rv.setTextColor(R.id.feed_grams, p.text2)
                rv.setTextViewText(R.id.feed_servings, "${snapshot.mainServings} 份")
                rv.setTextViewText(R.id.feed_grams, "${snapshot.mainGrams} 克")

                bindSubLine(rv, "上次 ${snapshot.lastServings} 份", state, p)

                rv.setInt(R.id.feed_cta, "setBackgroundResource", p.foodTint)
                rv.setInt(R.id.feed_cta_icon, "setColorFilter", p.food)
                rv.setTextColor(R.id.feed_cta_label, p.food)
                rv.setOnClickPendingIntent(
                    R.id.feed_cta,
                    pending(context, widgetId, kind, WidgetActions.EDIT_FOOD, CODE_EDIT_FOOD),
                )
                rv.setOnClickPendingIntent(
                    R.id.idle_root,
                    pending(context, widgetId, kind, WidgetActions.EDIT_FOOD, CODE_CARD),
                )
                rv.setContentDescription(
                    R.id.feed_cta,
                    "确认喂食，默认 ${snapshot.lastServings} 份",
                )
            }
        }
    }

    /** 2×2 数字下方一行：平时显示上次值，写入失败时就地显示错误 */
    private fun bindSubLine(
        rv: RemoteViews,
        idleText: String,
        state: WidgetState,
        p: Palette,
    ) {
        val error = state.mode == WidgetMode.ERROR
        rv.setTextViewText(R.id.sub_line, if (error) state.feedback else idleText)
        rv.setTextColor(R.id.sub_line, if (error) p.error else p.text2)
    }

    // ---------- 卡片内确认态 ----------

    private fun bindEdit(
        rv: RemoteViews,
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        state: WidgetState,
        p: Palette,
    ) {
        val editing = state.mode == WidgetMode.EDIT_WATER || state.mode == WidgetMode.EDIT_FOOD
        rv.setViewVisibility(R.id.idle_root, if (editing) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.edit_root, if (editing) View.VISIBLE else View.GONE)
        if (!editing) return

        val isWater = state.mode == WidgetMode.EDIT_WATER
        val combined = kind == WidgetKind.COMBINED
        if (combined) {
            rv.setTextViewText(
                R.id.edit_title,
                if (isWater) "喝水 · 这次多少" else "喂食 · 份数与克数",
            )
            rv.setTextColor(R.id.edit_title, p.text)
        }

        rv.setTextViewText(R.id.edit_value, state.draft.toString())
        rv.setTextColor(R.id.edit_value, p.text)
        rv.setTextViewText(R.id.edit_unit, if (isWater) "ml" else "份")
        rv.setTextColor(R.id.edit_unit, p.text2)

        val grams = state.gramsPerServing.coerceIn(1, 200)
        if (kind == WidgetKind.COMBINED) {
            // 4×2 喂食：份数与克数各一行步进；喝水时隐藏克数行
            rv.setViewVisibility(R.id.gram_row, if (isWater) View.GONE else View.VISIBLE)
            if (!isWater) {
                rv.setTextViewText(R.id.gram_value, grams.toString())
                rv.setTextColor(R.id.gram_value, p.text)
                rv.setTextColor(R.id.gram_unit, p.text2)
                for (id in intArrayOf(R.id.gram_down, R.id.gram_up)) {
                    rv.setInt(id, "setBackgroundResource", p.outlineDrawable)
                    rv.setTextColor(id, p.text)
                }
                rv.setOnClickPendingIntent(
                    R.id.gram_down,
                    pending(context, widgetId, kind, WidgetActions.STEP_GRAMS_DOWN, CODE_GRAM_DOWN),
                )
                rv.setOnClickPendingIntent(
                    R.id.gram_up,
                    pending(context, widgetId, kind, WidgetActions.STEP_GRAMS_UP, CODE_GRAM_UP),
                )
                rv.setContentDescription(R.id.gram_value, "每份 $grams 克")
            }
        } else {
            rv.setTextViewText(
                R.id.edit_sub,
                if (isWater) {
                    context.getString(R.string.widget_water_step_tip)
                } else {
                    "$grams 克/份 · 共 ${state.draft * grams} 克"
                },
            )
            rv.setTextColor(R.id.edit_sub, p.text2)
        }

        for (id in intArrayOf(R.id.step_down, R.id.step_plus)) {
            rv.setInt(id, "setBackgroundResource", p.outlineDrawable)
            rv.setTextColor(id, p.text)
        }
        rv.setOnClickPendingIntent(
            R.id.step_down,
            pending(context, widgetId, kind, WidgetActions.STEP_DOWN, CODE_STEP_DOWN),
        )
        rv.setOnClickPendingIntent(
            R.id.step_plus,
            pending(context, widgetId, kind, WidgetActions.STEP_UP, CODE_STEP_UP),
        )

        rv.setInt(R.id.edit_cancel, "setBackgroundResource", p.outlineDrawable)
        rv.setTextColor(R.id.edit_cancel, p.text)
        rv.setOnClickPendingIntent(
            R.id.edit_cancel,
            pending(context, widgetId, kind, WidgetActions.CANCEL, CODE_CANCEL),
        )

        rv.setInt(R.id.edit_ok, "setBackgroundResource", p.primaryDrawable)
        rv.setTextColor(R.id.edit_ok, p.onPrimary)
        rv.setTextViewText(
            R.id.edit_ok,
            context.getString(
                if (isWater) R.string.widget_confirm_water else R.string.widget_confirm_food,
            ),
        )
        rv.setOnClickPendingIntent(
            R.id.edit_ok,
            pending(context, widgetId, kind, WidgetActions.CONFIRM, CODE_CONFIRM),
        )

        rv.setContentDescription(
            R.id.edit_value,
            if (isWater) "当前 ${state.draft} 毫升" else "当前 ${state.draft} 份，共 ${state.draft * grams} 克",
        )
        rv.setContentDescription(
            R.id.step_down,
            if (isWater) "减少 10 毫升" else "减少 1 份",
        )
        rv.setContentDescription(
            R.id.step_plus,
            if (isWater) "增加 10 毫升" else "增加 1 份",
        )
    }

    // ---------- 工具 ----------

    private fun pending(
        context: Context,
        widgetId: Int,
        kind: WidgetKind,
        action: String,
        code: Int,
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(action)
            .putExtra(WidgetExtras.WIDGET_ID, widgetId)
            .putExtra(WidgetExtras.KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            widgetId * 32 + code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun resolveDark(context: Context): Boolean = when (ThemePreference.read(context)) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun palette(context: Context, dark: Boolean): Palette = if (dark) {
        Palette(
            text = context.getColor(R.color.w_text_dark),
            text2 = context.getColor(R.color.w_text2_dark),
            accent = context.getColor(R.color.w_accent_dark),
            primary = context.getColor(R.color.w_primary_dark),
            onPrimary = context.getColor(R.color.w_on_primary_dark),
            water = context.getColor(R.color.w_water_dark),
            food = context.getColor(R.color.w_food_dark),
            outline = context.getColor(R.color.w_outline_dark),
            error = context.getColor(R.color.w_error_dark),
            card = R.drawable.widget_card_dark,
            primaryDrawable = R.drawable.widget_primary_dark,
            outlineDrawable = R.drawable.widget_outline_dark,
            waterTint = R.drawable.widget_water_dark,
            foodTint = R.drawable.widget_food_dark,
        )
    } else {
        Palette(
            text = context.getColor(R.color.w_text),
            text2 = context.getColor(R.color.w_text2),
            accent = context.getColor(R.color.w_accent),
            primary = context.getColor(R.color.w_primary),
            onPrimary = context.getColor(R.color.w_on_primary),
            water = context.getColor(R.color.w_water),
            food = context.getColor(R.color.w_food),
            outline = context.getColor(R.color.w_outline),
            error = context.getColor(R.color.w_error),
            card = R.drawable.widget_card_light,
            primaryDrawable = R.drawable.widget_primary_light,
            outlineDrawable = R.drawable.widget_outline_light,
            waterTint = R.drawable.widget_water_light,
            foodTint = R.drawable.widget_food_light,
        )
    }

    private class Palette(
        val text: Int,
        val text2: Int,
        val accent: Int,
        val primary: Int,
        val onPrimary: Int,
        val water: Int,
        val food: Int,
        val outline: Int,
        val error: Int,
        val card: Int,
        val primaryDrawable: Int,
        val outlineDrawable: Int,
        val waterTint: Int,
        val foodTint: Int,
    )

    private const val CODE_EDIT_WATER = 1
    private const val CODE_EDIT_FOOD = 2
    private const val CODE_STEP_DOWN = 3
    private const val CODE_STEP_UP = 4
    private const val CODE_CANCEL = 5
    private const val CODE_CONFIRM = 6
    private const val CODE_CARD = 8
    private const val CODE_GRAM_DOWN = 9
    private const val CODE_GRAM_UP = 10
}
