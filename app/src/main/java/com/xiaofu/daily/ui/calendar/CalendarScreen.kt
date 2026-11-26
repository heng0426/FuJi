package com.xiaofu.daily.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.DueCard
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.bottomBarAwarePadding
import com.xiaofu.daily.ui.components.RecordRow
import com.xiaofu.daily.ui.components.appRippleColor
import com.xiaofu.daily.ui.components.minTouchTarget
import com.xiaofu.daily.ui.components.roundedRowClickable
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.SheetHandle
import com.xiaofu.daily.ui.components.TextFieldBox
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.recordStyle
import com.xiaofu.daily.ui.today.DetailSheet
import com.xiaofu.daily.ui.today.FoodSheet
import com.xiaofu.daily.ui.today.TodayViewModel
import com.xiaofu.daily.ui.today.WaterSheet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class CreateOption(
    val label: String,
    val type: RecordType,
    val mealKind: MealKind? = null,
)

private val createOptions = listOf(
    CreateOption("喝水", RecordType.WATER),
    CreateOption("主粮", RecordType.FOOD, MealKind.MAIN),
    CreateOption("加餐", RecordType.FOOD, MealKind.SNACK),
    CreateOption("体重", RecordType.WEIGHT),
    CreateOption("驱虫", RecordType.DEWORM),
    CreateOption("疫苗", RecordType.VACCINE),
    CreateOption("就诊", RecordType.VET),
    CreateOption("异常", RecordType.ANOMALY),
)

/** 跳转日期解析：按非数字分段提取，兼容 2026-11-23 / 2026/11/23 / 2026年11月23日 */
private fun parseJumpDate(raw: String): LocalDate? {
    val parts = raw.trim().split(Regex("\\D+")).filter { it.isNotBlank() }
    if (parts.size != 3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val (year, month, day) = numbers
    // 页面范围 2000-01 ~ 2199-12（锚点 2000-01，共 200 年）
    if (year !in 2000..2199) return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: CalendarViewModel,
    actions: TodayViewModel,
    onOpenEditor: (EditorTarget) -> Unit,
    onDeleteRecord: (RecordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val month by vm.month.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val records by vm.monthRecords.collectAsState()
    val dueCards by vm.dueCards.collectAsState()

    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<RecordEntity?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var waterEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var showFood by remember { mutableStateOf(false) }
    var foodEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var foodKind by remember { mutableStateOf(MealKind.MAIN) }
    var lastWater by remember { mutableStateOf(60) }
    var serving by remember { mutableStateOf(15) }
    var lastServings by remember { mutableIntStateOf(1) }
    var snackServing by remember { mutableStateOf(15) }
    var snackServings by remember { mutableIntStateOf(1) }
    var lastSnack by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lastWater = actions.lastWaterMl()
        serving = actions.servingGrams(MealKind.MAIN)
        lastServings = actions.lastServings(MealKind.MAIN)
        snackServing = actions.servingGrams(MealKind.SNACK)
        snackServings = actions.lastServings(MealKind.SNACK)
        lastSnack = actions.lastSnackType()
    }

    val byDay = remember(records) { records.groupBy { Fmt.date(it.occurredAt) } }
    val dueDays = remember(dueCards) { dueCards.map { it.dueDay }.toSet() }
    val dueByDay = remember(dueCards) {
        dueCards.groupBy { it.dueDay }.mapValues { (_, cards) -> cards.map { it.type }.toSet() }
    }
    val selectedRecords = byDay[selected].orEmpty().sortedByDescending { it.occurredAt }
    val selectedDue = remember(dueCards, selected) {
        dueCards.filter { it.dueDay == selected.toEpochDay() }
    }
    val backfillAt = remember(selected) { selected.atTime(LocalTime.now(ZoneId.systemDefault())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomBarAwarePadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("日历", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                    Text("按日期回看与补记", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                RoundIconButton(R.drawable.ic_ms_add, onClick = { showCreate = true }, contentDescription = "新增记录")
            }
        }
        item(key = "calendar") {
            MonthCard(
                month = month,
                selected = selected,
                byDay = byDay,
                dueDays = dueDays,
                dueByDay = dueByDay,
                onMonthChange = vm::showMonth,
                onSelect = vm::select,
                onJump = {
                    jumpText = selected.toString()
                    showJump = true
                },
                onToday = vm::goToday,
            )
        }
        item(key = "day_head") {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日 · ${Fmt.dayLabelOrWeekday(selected)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("${selectedRecords.size} 条", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        items(items = selectedDue, key = { "sel_due_${it.recordId}" }) { card ->
            CalendarDueRow(card) {
                scope.launch { vm.recordById(card.recordId)?.let { detail = it } }
            }
        }
        if (selectedRecords.isEmpty() && selectedDue.isEmpty()) {
            item(key = "empty") {
                CardSurface(contentPadding = 16.dp) {
                    Text("这一天还没有记录", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    Text(
                        "点右上角 + 补记这一天的记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            item(key = "selected_rows") {
                Column {
                    selectedRecords.forEachIndexed { position, record ->
                        RecordRow(
                            record = record,
                            showDivider = position > 0,
                            onClick = { detail = record },
                        )
                    }
                }
            }
        }
    }

    if (showJump) {
        val parsed = parseJumpDate(jumpText)
        var showJumpPicker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showJump = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("跳转到日期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
            text = {
                Column {
                    Text(
                        "支持 2026-11-23、2026/11/23、2026年11月23日",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    TextFieldBox(
                        value = jumpText,
                        onValueChange = { jumpText = it.take(14) },
                        placeholder = "2026-11-23",
                    )
                    if (parsed == null && jumpText.isNotBlank()) {
                        Text(
                            "无法识别的日期，可切换到下方日历选择",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "从日历选择日期",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = cs.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showJumpPicker = true }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let { vm.select(it) }
                        showJump = false
                    },
                    enabled = parsed != null,
                ) { Text("跳转", color = if (parsed != null) cs.primary else cs.onSurfaceVariant) }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("取消", color = cs.onSurfaceVariant) } },
        )

        if (showJumpPicker) {
            val initialDate = parsed ?: selected
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showJumpPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            vm.select(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showJumpPicker = false
                        showJump = false
                    }) { Text("确定", color = cs.primary) }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpPicker = false }) { Text("取消", color = cs.onSurfaceVariant) }
                },
            ) {
                DatePicker(state = state)
            }
        }
    }

    if (showCreate) {
        CreateSheet(
            onDismiss = { showCreate = false },
            onPick = { option ->
                showCreate = false
                when {
                    option.type == RecordType.WATER -> {
                        waterEditing = null
                        showWater = true
                    }
                    option.type == RecordType.FOOD -> {
                        foodEditing = null
                        foodKind = option.mealKind ?: MealKind.MAIN
                        showFood = true
                    }
                    option.type == RecordType.WEIGHT -> onOpenEditor(EditorTarget.Weight(occurredAtMs = backfillAt))
                    option.type == RecordType.DEWORM -> onOpenEditor(EditorTarget.Deworm(occurredAtMs = backfillAt))
                    option.type == RecordType.VACCINE -> onOpenEditor(EditorTarget.Vaccine(occurredAtMs = backfillAt))
                    option.type == RecordType.VET -> onOpenEditor(EditorTarget.Vet(occurredAtMs = backfillAt))
                    else -> onOpenEditor(EditorTarget.Anomaly(occurredAtMs = backfillAt))
                }
            },
        )
    }

    if (showWater) {
        val editing = waterEditing
        WaterSheet(
            lastMl = lastWater,
            editing = editing,
            initialOccurredAt = if (editing == null) backfillAt else null,
            onDismiss = {
                showWater = false
                waterEditing = null
            },
            onSave = { ml, at ->
                scope.launch {
                    actions.saveWater(ml, editing, at)
                    lastWater = ml
                    showWater = false
                    waterEditing = null
                }
            },
        )
    }

    if (showFood) {
        val editing = foodEditing
        FoodSheet(
            initialKind = foodKind,
            initialGrams = editing?.gramsPerServing
                ?: if (foodKind == MealKind.SNACK) snackServing else serving,
            initialServings = editing?.servingCount?.roundToInt()
                ?: if (foodKind == MealKind.SNACK) snackServings else lastServings,
            initialSnack = editing?.snackType ?: lastSnack,
            editing = editing,
            initialOccurredAt = if (editing == null) backfillAt else null,
            onSavePreset = { preset, grams, at ->
                scope.launch {
                    actions.saveFood(
                        kind = foodKind,
                        servings = preset.servings,
                        grams = grams,
                        snackType = null,
                        existing = null,
                        at = at,
                        rememberDefaults = false,
                    )
                    showFood = false
                    foodEditing = null
                }
            },
            onDismiss = {
                showFood = false
                foodEditing = null
            },
            onSave = { kind, servings, grams, snackType, at ->
                scope.launch {
                    actions.saveFood(kind, servings, grams, snackType, editing, at)
                    if (kind == MealKind.SNACK) {
                        snackServing = grams
                        snackServings = servings
                    } else {
                        serving = grams
                        lastServings = servings
                    }
                    if (snackType != null) lastSnack = snackType
                    showFood = false
                    foodEditing = null
                }
            },
        )
    }

    detail?.let { record ->
        DetailSheet(
            record = record,
            onDismiss = { detail = null },
            onReopen = { detail = it },
            onEdit = {
                detail = null
                when (record.type) {
                    RecordType.WATER -> {
                        waterEditing = record
                        showWater = true
                    }
                    RecordType.FOOD -> {
                        foodEditing = record
                        foodKind = record.mealKind ?: MealKind.MAIN
                        showFood = true
                    }
                    RecordType.WEIGHT -> onOpenEditor(EditorTarget.Weight(record))
                    RecordType.DEWORM -> onOpenEditor(EditorTarget.Deworm(record))
                    RecordType.VACCINE -> onOpenEditor(EditorTarget.Vaccine(record))
                    RecordType.VET -> onOpenEditor(EditorTarget.Vet(record))
                    RecordType.ANOMALY -> onOpenEditor(EditorTarget.Anomaly(record))
                }
            },
            onDelete = {
                detail = null
                onDeleteRecord(record)
            },
        )
    }
}

@Composable
private fun MonthCard(
    month: YearMonth,
    selected: LocalDate,
    byDay: Map<LocalDate, List<RecordEntity>>,
    dueDays: Set<Long>,
    dueByDay: Map<Long, Set<RecordType>>,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onJump: () -> Unit,
    onToday: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val anchor = remember { YearMonth.of(2000, 1) }
    val pagerState = rememberPagerState(
        initialPage = (month.year - anchor.year) * 12 + (month.monthValue - anchor.monthValue),
        pageCount = { 12 * 200 },
    )
    // 标题月份随拖动连续变化：越过页面中线（±50%）即切换，与网格跟手动作同步；
    // derivedStateOf 让重组只发生在月份值真正变化时
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

    LaunchedEffect(month) {
        val targetPage = (month.year - anchor.year) * 12 + (month.monthValue - anchor.monthValue)
        if (pagerState.currentPage != targetPage) pagerState.scrollToPage(targetPage)
    }
    // 监听里必须读最新的 month：effect 只随 pagerState 启动一次，
    // 直接捕获参数会一直拿着首帧的月份，翻回来时不再触发选日
    val currentMonth by rememberUpdatedState(month)
    LaunchedEffect(pagerState) {
        // 以当前页（而非落位页）同步月份：滑过中线即加载目标月记录，圆点无需再点日期
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val target = anchor.plusMonths(page.toLong())
            if (target != currentMonth) onMonthChange(target)
        }
    }

    CardSurface(contentPadding = 14.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        }
                        .minTouchTarget(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_ms_chevron_left), "上个月", Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onJump)
                        .minTouchTarget(44.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Fmt.monthTitle(visibleMonth),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Icon(
                        painterResource(R.drawable.ic_ms_calendar_month),
                        contentDescription = "跳转日期",
                        modifier = Modifier.padding(start = 4.dp).size(15.dp),
                        tint = cs.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1))
                            }
                        }
                        .minTouchTarget(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_ms_chevron_right), "下个月", Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 10.5.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height((6 * 42).dp),
                beyondViewportPageCount = 1,
            ) { page ->
                val pageMonth = anchor.plusMonths(page.toLong())
                MonthGrid(
                    month = pageMonth,
                    selected = selected,
                    byDay = byDay,
                    dueDays = dueDays,
                    dueByDay = dueByDay,
                    onSelect = onSelect,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(Color(0xFFA08B7C).copy(alpha = 0.55f), "有记录")
                    LegendDot(cs.error, "异常")
                    LegendDot(recordStyle(RecordType.DEWORM).fg, "驱虫")
                    LegendDot(recordStyle(RecordType.VACCINE).fg, "疫苗")
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onToday)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_today),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = cs.primary,
                    )
                    Text(
                        "今天",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    byDay: Map<LocalDate, List<RecordEntity>>,
    dueDays: Set<Long>,
    dueByDay: Map<Long, Set<RecordType>>,
    onSelect: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val first = month.atDay(1)
        val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val date = gridStart.plusDays((week * 7 + index).toLong())
                    val dayRecords = byDay[date].orEmpty()
                    DayCell(
                        date = date,
                        selected = date == selected,
                        isToday = date == LocalDate.now(),
                        inMonth = YearMonth.from(date) == month,
                        hasRecords = dayRecords.isNotEmpty(),
                        hasAnomaly = dayRecords.any { it.type == RecordType.ANOMALY },
                        due = dueDays.contains(date.toEpochDay()),
                        dueTypes = dueByDay[date.toEpochDay()].orEmpty(),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    hasRecords: Boolean,
    hasAnomaly: Boolean,
    due: Boolean,
    dueTypes: Set<RecordType>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dueColor = when {
        RecordType.DEWORM in dueTypes && RecordType.VACCINE in dueTypes -> Color(0xFFC98A3E)
        RecordType.DEWORM in dueTypes -> recordStyle(RecordType.DEWORM).fg
        RecordType.VACCINE in dueTypes -> recordStyle(RecordType.VACCINE).fg
        else -> Color(0xFFC98A3E)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .semantics {
                    // TalkBack 读完整日期与状态，而不是裸数字
                    contentDescription = buildString {
                        append(date.monthValue).append("月").append(date.dayOfMonth).append("日 · ")
                        append(Fmt.weekday(date))
                        if (selected) append("，已选中") else if (isToday) append("，今天")
                        if (due) append("，到期日")
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = appRippleColor()),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else Color.Transparent)
                    .then(
                        if (!selected && (isToday || due)) {
                            Modifier.border(1.5.dp, if (isToday) cs.primary else dueColor, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        selected -> cs.onPrimary
                        !inMonth -> cs.onSurfaceVariant.copy(alpha = 0.35f)
                        else -> cs.onSurface
                    },
                    // 日期语义已由外层 contentDescription 提供，避免数字被重复朗读
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (hasRecords) Dot(Color(0xFF8A7566).copy(alpha = 0.8f))
            if (hasAnomaly) Dot(cs.error)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.5.dp).clip(CircleShape).background(color))
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Text(
            text,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(onDismiss: () -> Unit, onPick: (CreateOption) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHandle()
            Text("补记一条", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            createOptions.forEachIndexed { index, option ->
                val style = recordStyle(option.type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .roundedRowClickable(onClick = { onPick(option) })
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeIcon(style.iconRes, style.fg, style.bg, size = 34.dp, iconSize = 17.dp, radius = 11.dp)
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(17.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.7f))
                }
                if (index < createOptions.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
                }
            }
        }
    }
}

@Composable
private fun CalendarDueRow(card: DueCard, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val style = recordStyle(card.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeIcon(style.iconRes, style.fg, style.bg, size = 36.dp, iconSize = 18.dp, radius = 12.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                if (card.type == RecordType.DEWORM) "驱虫到期" else "疫苗到期",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(
                card.note?.takeIf { it.isNotBlank() } ?: "今天到期",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            "到期日",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = style.fg,
        )
    }
}
