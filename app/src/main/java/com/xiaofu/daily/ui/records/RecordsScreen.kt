package com.xiaofu.daily.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.DueCard
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.components.RecordRow
import com.xiaofu.daily.ui.components.SelectionTopBar
import com.xiaofu.daily.ui.components.SegmentedRow
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.recordStyle
import com.xiaofu.daily.ui.today.DetailSheet
import com.xiaofu.daily.ui.today.FoodSheet
import com.xiaofu.daily.ui.today.TodayViewModel
import com.xiaofu.daily.ui.today.WaterSheet
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val typeLabels = mapOf(
    RecordType.WATER to "喝水",
    RecordType.FOOD to "喂食",
    RecordType.WEIGHT to "体重",
    RecordType.DEWORM to "驱虫",
    RecordType.VACCINE to "疫苗",
    RecordType.ANOMALY to "异常",
    RecordType.VET to "就诊",
)

@Composable
fun RecordsScreen(
    vm: RecordsViewModel,
    actions: TodayViewModel,
    onOpenEditor: (EditorTarget) -> Unit,
    onDeleteRecord: (RecordEntity) -> Unit,
    openRecord: RecordEntity? = null,
    onOpenRecordHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val galleryHost = LocalGalleryHost.current
    val filter by vm.filter.collectAsState()
    val records by vm.records.collectAsState()
    val dueCards by vm.dueCards.collectAsState()
    val typeOrder by vm.typeOrder.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val latestOrder by rememberUpdatedState(typeOrder)
    val chipState = rememberLazyListState()
    val listState = rememberLazyListState()
    var draggingType by remember { mutableStateOf<RecordType?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<RecordEntity?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var waterEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var showFood by remember { mutableStateOf(false) }
    var foodEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var foodKind by remember { mutableStateOf(MealKind.MAIN) }
    var lastWater by remember { mutableStateOf(60) }
    var serving by remember { mutableStateOf(15) }
    var lastServings by remember { mutableIntStateOf(1) }
    var lastSnack by remember { mutableStateOf<String?>(null) }
    var weightView by remember { mutableIntStateOf(0) }

    // 从照片墙点「记录详情」跳转过来：直接打开对应记录
    LaunchedEffect(openRecord) {
        if (openRecord != null) {
            detail = openRecord
            onOpenRecordHandled()
        }
    }

    LaunchedEffect(Unit) {
        lastWater = actions.lastWaterMl()
        serving = actions.servingGrams()
        lastServings = actions.lastServings()
        lastSnack = actions.lastSnackType()
    }

    val groups = remember(records) { records.groupBy { Fmt.date(it.occurredAt) } }
    val visibleDueCards = remember(dueCards, filter) {
        when (filter) {
            null -> dueCards
            RecordType.DEWORM, RecordType.VACCINE -> dueCards.filter { it.type == filter }
            else -> emptyList()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore) {
        if (shouldLoadMore && hasMore) vm.loadMore()
    }
    LaunchedEffect(filter) { listState.scrollToItem(0) }

    if (selecting) {
        BackHandler {
            selecting = false
            selectedIds = emptySet()
        }
    }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionTopBar(
                count = selectedIds.size,
                onCancel = {
                    selecting = false
                    selectedIds = emptySet()
                },
                onDelete = { if (selectedIds.isNotEmpty()) confirmDeleteSelected = true },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        if (!selecting) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("记录", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                    Text("按类型查看历史", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                RoundIconButton(
                    R.drawable.ic_ms_image,
                    onClick = { galleryHost?.show() },
                    contentDescription = "异常照片墙",
                )
            }
        }
        }
        item(key = "filters") {
            LazyRow(state = chipState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    PillChip("全部", selected = filter == null, onClick = { vm.setFilter(null) })
                }
                items(items = typeOrder, key = { it.name }) { type ->
                    val order = latestOrder
                    val isDragging = draggingType == type
                    Box(
                        modifier = Modifier
                            // 被挤走的 chip 用 LazyRow 位置动画平滑让位；拖拽中的 chip 跟手，不参与动画
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                            .graphicsLayer { translationX = if (isDragging) dragOffset else 0f }
                            .zIndex(if (isDragging) 1f else 0f)
                            .pointerInput(type) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingType = type },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.x
                                        val current = latestOrder
                                        val visualIndex = current.indexOf(type) + 1
                                        val info = chipState.layoutInfo.visibleItemsInfo
                                        val currentItem = info.firstOrNull { it.index == visualIndex }
                                        val neighbor = if (dragOffset > 0) {
                                            info.firstOrNull { it.index == visualIndex + 1 }
                                        } else {
                                            info.firstOrNull { it.index == visualIndex - 1 }
                                        }
                                        if (neighbor != null) {
                                            val threshold = ((currentItem?.size ?: 0) + neighbor.size) / 2f
                                            if (kotlin.math.abs(dragOffset) > threshold) {
                                                vm.moveType(type, if (dragOffset > 0) 1 else -1)
                                                dragOffset += if (dragOffset > 0) {
                                                    -neighbor.size.toFloat()
                                                } else {
                                                    neighbor.size.toFloat()
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingType = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingType = null
                                        dragOffset = 0f
                                    },
                                )
                            },
                    ) {
                        PillChip(
                            text = typeLabels.getValue(type),
                            selected = filter == type,
                            onClick = { vm.setFilter(type) },
                        )
                    }
                }
            }
        }
        if (filter == RecordType.WEIGHT) {
            item(key = "weight_mode") {
                SegmentedRow(
                    options = listOf("列表", "趋势"),
                    selectedIndex = weightView,
                    onSelect = { weightView = it },
                )
            }
        }
        if (filter == RecordType.WEIGHT && weightView == 1) {
            item(key = "weight_trend") { WeightTrendCard(records) }
        } else {
            if (visibleDueCards.isNotEmpty()) {
                items(items = visibleDueCards, key = { "due_${it.type}_${it.dueDay}" }) { card ->
                    DueCardRow(card) { vm.setFilter(card.type) }
                }
            }
            if (records.isEmpty()) {
                item(key = "empty") {
                    CardSurface(contentPadding = 16.dp) {
                        Text(
                            if (filter == null) "还没有任何记录" else "没有${typeLabels[filter]}记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurface,
                        )
                        Text(
                            "回到「今日」记一笔，或在「日历」补记",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            groups.entries.forEach { (date, dayRecords) ->
                item(key = "head_$date") {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${date.monthValue}月${date.dayOfMonth}日 · ${Fmt.dayLabelOrWeekday(date)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${dayRecords.size} 条", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                item(key = "rows_$date") {
                    Column {
                        dayRecords.forEachIndexed { position, record ->
                            RecordRow(
                                record = record,
                                showDivider = position > 0,
                                onClick = {
                                    if (selecting) {
                                        selectedIds = if (record.id in selectedIds) {
                                            selectedIds - record.id
                                        } else {
                                            selectedIds + record.id
                                        }
                                    } else {
                                        detail = record
                                    }
                                },
                                onLongClick = {
                                    selecting = true
                                    selectedIds = selectedIds + record.id
                                },
                                selectionMode = selecting,
                                selected = record.id in selectedIds,
                            )
                        }
                    }
                }
            }
            if (hasMore) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "正在加载更早记录…",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }

    if (confirmDeleteSelected) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("删除 $count 条记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
            text = { Text("删除后不可恢复。", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelected = false
                    val ids = selectedIds
                    scope.launch {
                        vm.deleteMany(ids)
                        selectedIds = emptySet()
                        selecting = false
                    }
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("取消", color = cs.onSurfaceVariant) }
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
                    actions.saveWater(ml, editing)
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
            initialServings = editing?.servingCount?.roundToInt() ?: lastServings,
            initialSnack = editing?.snackType ?: lastSnack,
            editing = editing,
            onSavePreset = { preset, grams ->
                scope.launch {
                    actions.saveFood(
                        kind = foodKind,
                        servings = preset.servings,
                        grams = grams,
                        snackType = null,
                        existing = null,
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
            onSave = { kind, servings, grams, snackType ->
                scope.launch {
                    actions.saveFood(kind, servings, grams, snackType, editing)
                    serving = grams
                    lastServings = servings
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
private fun DueCardRow(card: DueCard, onClick: () -> Unit) {
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
                "${card.title} · 下次 ${Fmt.monthDay(card.dueDay)}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Text(
                card.note?.takeIf { it.isNotBlank() } ?: "${card.title}到期提醒",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(
            card.daysText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (card.daysLeft <= 3) cs.error else cs.onSurfaceVariant,
        )
        Icon(
            painterResource(R.drawable.ic_ms_chevron_right),
            null,
            Modifier.padding(start = 4.dp).size(17.dp),
            tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun WeightTrendCard(records: List<RecordEntity>) {
    val cs = MaterialTheme.colorScheme
    val points = remember(records) {
        records.mapNotNull { record -> record.weightGrams?.let { it to record.occurredAt } }
            .sortedBy { it.second }
            .takeLast(30)
    }
    CardSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("体重趋势", style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
            Text("近 ${points.size} 次", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        if (points.size < 2) {
            Text(
                "至少 2 次体重记录才能看到趋势",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@CardSurface
        }
        val latest = points.last().first
        val first = points.first().first
        val delta = latest - first
        val minValue = points.minOf { it.first }
        val maxValue = points.maxOf { it.first }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Metric("当前", "${Fmt.jin(latest)} 斤")
            Metric("变化", (if (delta > 0) "+" else "") + "${Fmt.jin(delta)} 斤")
            Metric("范围", "${Fmt.jin(minValue)}–${Fmt.jin(maxValue)} 斤")
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 14.dp)) {
            val padX = 4.dp.toPx()
            val padY = 6.dp.toPx()
            val width = size.width - padX * 2
            val height = size.height - padY * 2
            val range = (maxValue - minValue).coerceAtLeast(1)
            repeat(4) { index ->
                val y = padY + height * index / 3f
                drawLine(
                    color = cs.outline,
                    start = Offset(padX, y),
                    end = Offset(size.width - padX, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = padX + width * index / (points.size - 1)
                val y = padY + height * (1f - (point.first - minValue).toFloat() / range)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = cs.primary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            points.forEachIndexed { index, point ->
                val x = padX + width * index / (points.size - 1)
                val y = padY + height * (1f - (point.first - minValue).toFloat() / range)
                drawCircle(color = cs.surface, radius = 4.5.dp.toPx(), center = Offset(x, y))
                drawCircle(color = cs.primary, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
        val sameDay = Fmt.date(points.first().second) == Fmt.date(points.last().second)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                Fmt.monthDayOf(points.first().second) + if (sameDay) " ${Fmt.time(points.first().second)}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                Fmt.monthDayOf(points.last().second) + if (sameDay) " ${Fmt.time(points.last().second)}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = cs.onSurface)
    }
}
