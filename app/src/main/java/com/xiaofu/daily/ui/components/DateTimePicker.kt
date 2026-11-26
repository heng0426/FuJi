package com.xiaofu.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaofu.daily.R
import com.xiaofu.daily.ui.format.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 统一的「点击行」：左侧标签、右侧当前值、右箭头。
 * 时间行传 [Fmt.detailTime]，纯日期行传 [Fmt.fullDate]。
 */
@Composable
fun PickerRow(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Spacer(Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Icon(
            painterResource(R.drawable.ic_ms_chevron_right),
            contentDescription = null,
            modifier = Modifier.padding(start = 3.dp).size(16.dp),
            tint = cs.onSurfaceVariant,
        )
    }
}

/**
 * 日期 + 时间一体选择：月历与「时:分」在同一个弹窗内完成，不分步。
 * 返回毫秒时间戳（本地时区）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(zone) }
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initial.toLocalDate())) }
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 340.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "选择日期与时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val today = LocalDate.now(zone)
                        selectedDate = today
                        visibleMonth = YearMonth.from(today)
                    }) { Text("今天", color = cs.primary) }
                }
                MiniMonthCalendar(
                    selected = selectedDate,
                    visibleMonth = visibleMonth,
                    onSelect = { selectedDate = it },
                    onMonthChange = { visibleMonth = it },
                )
                HorizontalDivider(color = cs.outline, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimeInput(state = timeState)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SecondaryButton("取消", onDismiss, Modifier.weight(1f))
                    PrimaryButton(
                        text = "确定",
                        onClick = {
                            onPick(
                                selectedDate
                                    .atTime(timeState.hour, timeState.minute)
                                    .atZone(zone)
                                    .toInstant()
                                    .toEpochMilli(),
                            )
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 纯日期选择：仅用于「下次到期 / 下次接种」这类只存 epochDay 的字段 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateOnlyPickerDialog(
    initialDay: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    onPick(day)
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun MiniMonthCalendar(
    selected: LocalDate,
    visibleMonth: YearMonth,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(R.drawable.ic_ms_chevron_left, "上个月") {
                onMonthChange(visibleMonth.minusMonths(1))
            }
            Text(
                "${visibleMonth.year}年${visibleMonth.monthValue}月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(R.drawable.ic_ms_chevron_right, "下个月") {
                onMonthChange(visibleMonth.plusMonths(1))
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Text(
                    label,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // 周日为首列，与日历页保持一致
        val leading = visibleMonth.atDay(1).dayOfWeek.value % 7
        val days = visibleMonth.lengthOfMonth()
        val rows = (leading + days + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayOfMonth = row * 7 + col - leading + 1
                    Box(
                        Modifier.weight(1f).height(34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayOfMonth in 1..days) {
                            val date = visibleMonth.atDay(dayOfMonth)
                            val isSelected = date == selected
                            val isToday = date == today
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .then(
                                        when {
                                            isSelected -> Modifier.background(cs.primary)
                                            isToday -> Modifier.border(1.dp, cs.primary, CircleShape)
                                            else -> Modifier
                                        },
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) cs.onPrimary else cs.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(iconRes: Int, description: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = cs.onSurfaceVariant,
        )
    }
}
