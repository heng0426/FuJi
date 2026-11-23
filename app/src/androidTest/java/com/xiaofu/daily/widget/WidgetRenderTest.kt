package com.xiaofu.daily.widget

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xiaofu.daily.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 通过 RemoteViews.apply 真正走一遍布局填充：
 * 验证布局只使用 RemoteViews 支持的控件，并检查待机/确认/反馈三态的关键文案与可见性。
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val snapshot = WidgetSnapshot(
        petName = "小福",
        waterMl = 780,
        mainServings = 4,
        mainGrams = 48,
        lastWaterMl = 60,
        lastServings = 3,
        servingGrams = 10,
    )

    @Test
    fun combinedIdleShowsSummaryAndButtons() {
        val view = apply(WidgetKind.COMBINED, WidgetState())
        assertEquals("780", view.text(R.id.water_total))
        assertEquals("主粮 4 份 · 48 克", view.text(R.id.food_line))
        assertEquals("喝水", view.text(R.id.btn_water_label))
        assertEquals("喂食", view.text(R.id.btn_food_label))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.idle_root).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.edit_root).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.fb_row).visibility)
    }

    @Test
    fun waterIdlePutsLabelInHeaderAndTotalCentered() {
        val view = apply(WidgetKind.WATER, WidgetState())
        assertEquals("今日喝水", view.text(R.id.water_label))
        assertEquals("780", view.text(R.id.water_total))
        assertEquals("上次 60 ml", view.text(R.id.sub_line))
    }

    @Test
    fun feedIdlePutsLabelInHeaderAndTotalCentered() {
        val view = apply(WidgetKind.FEED, WidgetState())
        assertEquals("今日主粮", view.text(R.id.food_label))
        assertEquals("4 份", view.text(R.id.feed_servings))
        assertEquals("48 克", view.text(R.id.feed_grams))
        assertEquals("上次 3 份", view.text(R.id.sub_line))
    }

    @Test
    fun waterEditShowsDraftAndSteppers() {
        val state = WidgetState(mode = WidgetMode.EDIT_WATER, draft = 70, gramsPerServing = 10)
        val view = apply(WidgetKind.WATER, state)
        assertEquals(View.GONE, view.findViewById<View>(R.id.idle_root).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.edit_root).visibility)
        assertEquals("小福", view.text(R.id.pet_name))
        assertEquals("今日喝水", view.text(R.id.water_label))
        assertEquals("70", view.text(R.id.edit_value))
        assertEquals("ml", view.text(R.id.edit_unit))
    }

    @Test
    fun feedEditKeepsHeaderVisible() {
        val state = WidgetState(mode = WidgetMode.EDIT_FOOD, draft = 3, gramsPerServing = 10)
        val view = apply(WidgetKind.FEED, state)
        assertEquals("小福", view.text(R.id.pet_name))
        assertEquals("今日主粮", view.text(R.id.food_label))
    }

    @Test
    fun feedEditShowsGramsPerServingAndTotal() {
        val state = WidgetState(mode = WidgetMode.EDIT_FOOD, draft = 3, gramsPerServing = 10)
        val view = apply(WidgetKind.FEED, state)
        assertEquals("3", view.text(R.id.edit_value))
        assertEquals("份", view.text(R.id.edit_unit))
        assertEquals("10 克/份 · 共 30 克", view.text(R.id.edit_sub))
    }

    @Test
    fun combinedFeedEditShowsGramStepper() {
        val state = WidgetState(mode = WidgetMode.EDIT_FOOD, draft = 3, gramsPerServing = 15)
        val view = apply(WidgetKind.COMBINED, state)
        assertEquals("3", view.text(R.id.edit_value))
        assertEquals("份", view.text(R.id.edit_unit))
        assertEquals("15", view.text(R.id.gram_value))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.gram_row).visibility)
    }

    @Test
    fun combinedWaterEditHidesGramStepper() {
        val state = WidgetState(mode = WidgetMode.EDIT_WATER, draft = 60, gramsPerServing = 15)
        val view = apply(WidgetKind.COMBINED, state)
        assertEquals("60", view.text(R.id.edit_value))
        assertEquals(View.GONE, view.findViewById<View>(R.id.gram_row).visibility)
    }

    @Test
    fun combinedErrorShowsFeedbackInHeader() {
        val state = WidgetState(mode = WidgetMode.ERROR, feedback = "记录失败 · 重试")
        val view = apply(WidgetKind.COMBINED, state)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.fb_row).visibility)
        assertEquals("记录失败 · 重试", view.text(R.id.fb_text))
    }

    @Test
    fun waterErrorShowsFeedbackUnderNumber() {
        val state = WidgetState(mode = WidgetMode.ERROR, feedback = "记录失败 · 重试")
        val view = apply(WidgetKind.WATER, state)
        assertEquals("记录失败 · 重试", view.text(R.id.sub_line))
    }

    private fun apply(kind: WidgetKind, state: WidgetState): View =
        WidgetRenderer.render(context, 1, kind, snapshot, state).apply(context, null)

    private fun View.text(id: Int): String =
        findViewById<TextView>(id).text.toString()
}
