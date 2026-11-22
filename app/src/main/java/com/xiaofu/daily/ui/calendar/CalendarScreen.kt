package com.xiaofu.daily.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.RecordRow
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.SheetHandle
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.recordStyle
import com.xiaofu.daily.ui.today.DetailSheet
import com.xiaofu.daily.ui.today.FoodSheet
import com.xiaofu.daily.ui.today.TodayViewModel
import com.xiaofu.daily.ui.today.WaterSheet
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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
    CreateOption("异常", RecordType.ANOMALY),
)

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
    var detail by remember { mutableStateOf<RecordEntity?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var waterEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var showFood by remember { mutableStateOf(false) }
    var foodEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var foodKind by remember { mutableStateOf(MealKind.MAIN) }
    var lastWater by remember { mutableStateOf(60) }
    var serving by remember { mutableStateOf(15) }
    var lastSnack by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lastWater = actions.lastWaterMl()
        serving = actions.servingGrams()
        lastSnack = actions.lastSnackType()
    }

    val byDay = remember(records) { records.groupBy { Fmt.date(it.occurredAt) } }
    val dueDays = remember(dueCards) { dueCards.map { it.dueDay }.toSet() }
    val selectedRecords = byDay[selected].orEmpty().sortedByDescending { it.occurredAt }
    val backfillAt = remember(selected) { selected.atTime(LocalTime.now(ZoneId.systemDefault())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("日历", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                    Text("按日期回看与补记", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                RoundIconButton(R.drawable.ic_ms_today, onClick = vm::goToday)
            }
        }
        item(key = "calendar") {
            MonthCard(
                month = month,
                selected = selected,
                byDay = byDay,
                dueDays = dueDays,
                onPrev = vm::previousMonth,
                onNext = vm::nextMonth,
                onSelect = vm::select,
            )
        }
        item(key = "day_head") {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.monthValue}月${selected.dayOfMonth}日 · ${Fmt.dayLabelOrWeekday(selected)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("${selectedRecords.size} 条", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        if (selectedRecords.isEmpty()) {
            item(key = "empty") {
                CardSurface(contentPadding = 16.dp) {
                    Text("这一天还没有记录", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    Text(
                        "点下面的「补记这一天的记录」添加",
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
        item(key = "add") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
                    .clickable { showCreate = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(17.dp), tint = cs.primary)
                Text(
                    "补记这一天的记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp),
                )
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
            onDismiss = {
                showWater = false
                waterEditing = null
            },
            onSave = { ml ->
                scope.launch {
                    actions.saveWater(ml, editing, if (editing == null) backfillAt else null)
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
            initialGrams = editing?.gramsPerServing ?: serving,
            initialSnack = editing?.snackType ?: lastSnack,
            editing = editing,
            onDismiss = {
                showFood = false
                foodEditing = null
            },
            onSave = { kind, servings, grams, snackType ->
                scope.launch {
                    actions.saveFood(kind, servings, grams, snackType, editing, if (editing == null) backfillAt else null)
                    serving = grams
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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    CardSurface(contentPadding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_ms_chevron_left), "上个月", Modifier.size(20.dp), tint = cs.onSurfaceVariant)
            }
            Text(
                Fmt.monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
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
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendDot(Color(0xFFA08B7C).copy(alpha = 0.55f), "有记录")
            LegendDot(cs.error, "异常")
            LegendDot(Color(0xFFC98A3E), "驱虫/疫苗到期")
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
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
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
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else Color.Transparent)
                    .then(
                        if (!selected && (isToday || due)) {
                            Modifier.border(1.5.dp, if (isToday) cs.primary else Color(0xFFC98A3E), CircleShape)
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
                        .clickable { onPick(option) }
                        .padding(vertical = 9.dp),
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
