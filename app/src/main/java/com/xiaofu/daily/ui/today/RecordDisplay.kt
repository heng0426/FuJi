package com.xiaofu.daily.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.SnackStyle
import com.xiaofu.daily.ui.theme.recordStyle

/** 时间线/详情共用的展示模型 */
data class RecordDisplay(
    val iconRes: Int,
    val fg: Color,
    val bg: Color,
    val title: String,
    val value: String,
    val subtitle: String?,
)

@Composable
fun recordDisplay(record: RecordEntity): RecordDisplay {
    val isSnack = record.type == RecordType.FOOD && record.mealKind == MealKind.SNACK
    val style = if (isSnack) SnackStyle else recordStyle(record.type)

    return when (record.type) {
        RecordType.WATER -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = "喝水",
            value = "${record.amountMl ?: 0} ml",
            subtitle = record.note,
        )

        RecordType.FOOD -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = if (isSnack) "加餐" else "主粮",
            value = "${Fmt.servings(record.servingCount ?: 0.0)} 份 · ${record.totalGrams ?: 0} 克",
            subtitle = listOfNotNull(record.snackType, record.note).joinToString(" · ").ifBlank { null },
        )

        RecordType.WEIGHT -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = "体重",
            value = "${Fmt.jin(record.weightGrams ?: 0)} 斤",
            subtitle = record.note,
        )

        RecordType.DEWORM -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = if (record.dewormKind == DewormKind.INTERNAL) "内驱" else "外驱",
            value = record.medicine ?: "",
            subtitle = record.note ?: record.nextDueDate?.let { "下次到期 ${Fmt.monthDay(it)}" },
        )

        RecordType.VACCINE -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = "疫苗",
            value = record.vaccineName ?: "",
            subtitle = record.note ?: record.nextDueDate?.let { "下次接种 ${Fmt.monthDay(it)}" },
        )

        RecordType.ANOMALY -> RecordDisplay(
            iconRes = style.iconRes,
            fg = style.fg,
            bg = style.bg,
            title = "异常",
            value = listOfNotNull(record.anomalyCategory, record.severity?.label()).joinToString(" · "),
            subtitle = record.note,
        )
    }
}

fun Severity.label(): String = when (this) {
    Severity.LIGHT -> "轻度"
    Severity.MEDIUM -> "中度"
    Severity.HEAVY -> "重度"
}
