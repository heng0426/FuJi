package com.xiaofu.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaofu.daily.R
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.format.parseDateInput
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.launch

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
 * 日期 + 时间一体选择：跟手翻月（与日历页一致）、点击月份手输跳转、
 * 「时:分」双列滚轮。全部在同一弹窗内完成，返回毫秒时间戳（本地时区）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(zone) }
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var showJump by remember { mutableStateOf(false) }

    // 与日历页一致：锚点 2000-01，共 200 年可翻
    val anchor = remember { YearMonth.of(2000, 1) }
    val pagerState = rememberPagerState(
        initialPage = (initial.year - anchor.year) * 12 + (initial.monthValue - anchor.monthValue),
        pageCount = { 12 * 200 },
    )
    // 标题月份随拖动连续变化：越过页面中线（±50%）即切换，与网格跟手同步
    val visibleMonth = remember(anchor) {
        derivedStateOf {
            val fraction = pagerState.currentPageOffsetFraction
            val shift = when {
                fraction <= -0.5f -> 1L
                fraction >= 0.5f -> -1L
                else -> 0L
            }
            anchor.plusMonths(pagerState.currentPage + shift)
        }
    }.value

    // 手动跳转或点「今天」后，把分页动画对齐到目标月份
    LaunchedEffect(selectedDate) {
        val target = (selectedDate.year - anchor.year) * 12 + (selectedDate.monthValue - anchor.monthValue)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    // 翻月时选中日跟随到该月同一天（月末自动收敛），与日历页行为一致
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val month = anchor.plusMonths(page.toLong())
            if (YearMonth.from(selectedDate) != month) {
                val day = selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
                selectedDate = month.atDay(day)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 340.dp),
            shape = RoundedCornerShape(24.dp),
            color = cs.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "选择日期与时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selectedDate = LocalDate.now(zone) }) {
                        Text("今天", color = cs.primary)
                    }
                }
                MonthHeader(
                    month = visibleMonth,
                    onPrev = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    },
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1),
                            )
                        }
                    },
                    onJump = { showJump = true },
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height((6 * 30).dp),
                    beyondViewportPageCount = 1,
                ) { page ->
                    MonthGridCompact(
                        month = anchor.plusMonths(page.toLong()),
                        selected = selectedDate,
                        onSelect = { selectedDate = it },
                    )
                }
                HorizontalDivider(color = cs.outline, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                TimeWheels(
                    hour = hour,
                    minute = minute,
                    onHour = { hour = it },
                    onMinute = { minute = it },
                )
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
                                    .atTime(hour, minute)
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

    if (showJump) {
        JumpDateDialog(
            initial = selectedDate,
            onDismiss = { showJump = false },
            onPick = {
                selectedDate = it
                showJump = false
            },
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow(R.drawable.ic_ms_chevron_left, "上个月", onPrev)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onJump)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Fmt.monthTitle(month),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Icon(
                painterResource(R.drawable.ic_ms_calendar_month),
                contentDescription = "跳转日期",
                modifier = Modifier.padding(start = 4.dp).size(14.dp),
                tint = cs.onSurfaceVariant,
            )
        }
        MonthArrow(R.drawable.ic_ms_chevron_right, "下个月", onNext)
    }
}

@Composable
private fun MonthGridCompact(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    Column(Modifier.fillMaxWidth()) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val date = gridStart.plusDays((week * 7 + index).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    val isToday = date == today
                    Box(
                        Modifier.weight(1f).height(30.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
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
                                date.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected -> cs.onPrimary
                                    inMonth -> cs.onSurface
                                    else -> cs.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWheels(
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(values = 0..23, selected = hour, onSelect = onHour, modifier = Modifier.width(62.dp))
        Text(
            ":",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        WheelColumn(values = 0..59, selected = minute, onSelect = onMinute, modifier = Modifier.width(62.dp))
    }
}

/** 单列滚轮：吸附居中、中心项随滚动连续放大加粗、上下渐隐；停止后回报选中值 */
@Composable
private fun WheelColumn(
    values: IntRange,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val itemHeight = 34.dp
    val list = remember(values) { values.toList() }
    val initialIndex = (selected - values.first).coerceIn(0, list.lastIndex)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }

    // 连续滚动进度：视口中心对应的索引（contentPadding 恰好等于一个 item 高）
    val centerIndex by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                list.getOrNull(state.firstVisibleItemIndex)?.let {
                    if (it != selected) onSelect(it)
                }
            }
        }
    }

    Box(modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // 中心选中带
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(cs.surfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(list) { index, value ->
                val fraction = 1f - abs(index - centerIndex).coerceAtMost(1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        value.toString().padStart(2, '0'),
                        fontSize = (13f + 3f * fraction).sp,
                        fontWeight = if (fraction > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        color = cs.onSurface.copy(alpha = 0.3f + 0.7f * fraction),
                    )
                }
            }
        }
        // 上下渐隐，强化滚轮纵深
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(cs.surface.copy(alpha = 0.95f), cs.surface.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(cs.surface.copy(alpha = 0f), cs.surface.copy(alpha = 0.95f)),
                    ),
                ),
        )
    }
}

@Composable
private fun JumpDateDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = parseDateInput(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                "跳转到日期",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    "支持 2026-11-23、2026/11/23、2026年11月23日",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextFieldBox(
                    value = text,
                    onValueChange = { text = it.take(14) },
                    placeholder = "2026-11-23",
                )
                if (parsed == null && text.isNotBlank()) {
                    Text(
                        "无法识别的日期",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onPick) }, enabled = parsed != null) {
                Text("跳转", color = if (parsed != null) cs.primary else cs.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = cs.onSurfaceVariant) }
        },
    )
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
