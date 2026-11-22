package com.xiaofu.daily.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.DaySummary
import com.xiaofu.daily.data.DueInfo
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.CircleTypeIcon
import com.xiaofu.daily.ui.components.DayGroupHeader
import com.xiaofu.daily.ui.components.RecordRow
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.SnackStyle
import com.xiaofu.daily.ui.theme.LocalAppDarkTheme
import com.xiaofu.daily.ui.theme.recordStyle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onOpenEditor: (EditorTarget) -> Unit,
    onDeleteRecord: (RecordEntity) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary by vm.summary.collectAsState()
    val due by vm.due.collectAsState()
    val records by vm.records.collectAsState()
    val hasMore by vm.hasMore.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var lastWater by remember { mutableStateOf(60) }
    var serving by remember { mutableStateOf(15) }
    var lastSnack by remember { mutableStateOf<String?>(null) }
    var showWater by remember { mutableStateOf(false) }
    var waterEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var showFood by remember { mutableStateOf(false) }
    var foodEditing by remember { mutableStateOf<RecordEntity?>(null) }
    var foodKind by remember { mutableStateOf(MealKind.MAIN) }
    var showMore by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<RecordEntity?>(null) }

    LaunchedEffect(Unit) {
        lastWater = vm.lastWaterMl()
        serving = vm.servingGrams()
        lastSnack = vm.lastSnackType()
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

    val zone = remember { ZoneId.systemDefault() }
    val groups = remember(records) {
        records.groupBy { Fmt.date(it.occurredAt) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "topbar") { TodayTopBar() }
        item(key = "overview") {
            OverviewCard(summary = summary, onWeightClick = { onOpenEditor(EditorTarget.Weight()) })
        }
        due?.let { info -> item(key = "due") { DueBar(info) } }
        item(key = "quick") {
            QuickActions(
                onWater = {
                    scope.launch { lastWater = vm.lastWaterMl() }
                    waterEditing = null
                    showWater = true
                },
                onFood = {
                    foodEditing = null
                    foodKind = MealKind.MAIN
                    showFood = true
                },
                onSnack = {
                    foodEditing = null
                    foodKind = MealKind.SNACK
                    showFood = true
                },
                onMore = { showMore = true },
            )
        }

        if (groups.isEmpty()) {
            item(key = "empty") { EmptyTimeline() }
        }

        groups.entries.forEachIndexed { index, (date, dayRecords) ->
            item(key = "head_$date") { DayGroupHeader(date, dayRecords.size) }
            item(key = "rows_$date") {
                Column {
                    dayRecords.forEachIndexed { position, record ->
                        RecordRow(
                            record = record,
                            showDivider = position > 0,
                            onClick = { detail = record },
                        )
                    }
                }
            }
            if (index == 0 && groups.size > 1) {
                item(key = "scroll_hint") { ScrollHint() }
            }
        }

        if (hasMore) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "正在加载更早记录…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
                    vm.saveWater(ml, editing)
                    lastWater = ml
                    showWater = false
                    waterEditing = null
                    onMessage(if (editing == null) "已记喝水 $ml ml" else "已更新喝水记录")
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
                    vm.saveFood(kind, servings, grams, snackType, editing)
                    serving = grams
                    if (snackType != null) lastSnack = snackType
                    showFood = false
                    foodEditing = null
                    onMessage("已记录 ${Fmt.servings(servings)} 份 · ${(servings * grams).roundToInt()} 克")
                }
            },
        )
    }

    if (showMore) {
        MoreSheet(
            onDismiss = { showMore = false },
            onPick = { target ->
                showMore = false
                onOpenEditor(target)
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
private fun TodayTopBar() {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("今天", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
            Text(
                Fmt.todayHeader(),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box {
            RoundIconButton(R.drawable.ic_ms_notifications)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD9542B))
                    .border(2.dp, cs.surface, CircleShape),
            )
        }
    }
}

@Composable
private fun OverviewCard(summary: DaySummary, onWeightClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val waterStyle = recordStyle(RecordType.WATER)
    val foodStyle = recordStyle(RecordType.FOOD)
    val weightStyle = recordStyle(RecordType.WEIGHT)

    CardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(108.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CircleTypeIcon(waterStyle.iconRes, waterStyle.fg, waterStyle.bg)
                    Text("今日喝水", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${summary.waterMl}",
                        style = MaterialTheme.typography.displaySmall,
                        color = cs.onBackground,
                    )
                    Text(
                        "ml",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                    )
                }
                Text(
                    if (summary.waterCount > 0) "${summary.waterCount} 次记录" else "今天还没喝水",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Box(Modifier.width(1.dp).height(96.dp).background(cs.outline))
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MiniRow(
                    iconRes = foodStyle.iconRes,
                    fg = foodStyle.fg,
                    bg = foodStyle.bg,
                    label = "主粮",
                    value = "${Fmt.servings(summary.mainServings)} 份 · ${summary.mainGrams} 克",
                )
                AnimatedVisibility(
                    visible = summary.snackGrams > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    MiniRow(
                        iconRes = R.drawable.ic_ms_cookie,
                        fg = SnackStyle.fg,
                        bg = SnackStyle.bg,
                        label = "加餐",
                        value = "${Fmt.servings(summary.snackServings)} 份 · ${summary.snackGrams} 克",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onWeightClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleTypeIcon(weightStyle.iconRes, weightStyle.fg, weightStyle.bg)
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text("最近体重", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                summary.weightGrams?.let { "${Fmt.jin(it)} 斤" } ?: "未记录",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurface,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                            summary.weightAt?.let {
                                Text(
                                    " ${Fmt.dayLabel(Fmt.date(it))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Icon(
                        painterResource(R.drawable.ic_ms_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = cs.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRow(iconRes: Int, fg: Color, bg: Color, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleTypeIcon(iconRes, fg, bg)
        Column(Modifier.padding(start = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DueBar(info: DueInfo) {
    val dark = LocalAppDarkTheme.current
    val bg = if (dark) Color(0xFF33220F) else Color(0xFFFFF1DF)
    val borderColor = if (dark) Color(0xFF553516) else Color(0xFFF6DDBC)
    val style = recordStyle(info.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleTypeIcon(style.iconRes, style.fg, style.bg)
        Text(
            info.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Icon(
            painterResource(R.drawable.ic_ms_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActions(
    onWater: () -> Unit,
    onFood: () -> Unit,
    onSnack: () -> Unit,
    onMore: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = if (LocalAppDarkTheme.current) cs.primary else Color(0xFFB5501A)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        QuickTile(
            iconRes = R.drawable.ic_ms_water_drop,
            label = "喝水",
            modifier = Modifier.weight(1f),
            background = cs.primary,
            contentColor = cs.onPrimary,
            iconColor = cs.onPrimary,
            bordered = false,
            onClick = onWater,
        )
        QuickTile(
            iconRes = R.drawable.ic_ms_restaurant,
            label = "喂食",
            modifier = Modifier.weight(1f),
            background = cs.surface,
            contentColor = cs.onSurface,
            iconColor = accent,
            bordered = true,
            onClick = onFood,
        )
        QuickTile(
            iconRes = R.drawable.ic_ms_cookie,
            label = "加餐",
            modifier = Modifier.weight(1f),
            background = cs.surface,
            contentColor = cs.onSurface,
            iconColor = accent,
            bordered = true,
            onClick = onSnack,
        )
        QuickTile(
            iconRes = R.drawable.ic_ms_add,
            label = "更多",
            modifier = Modifier.weight(1f),
            background = cs.surface,
            contentColor = cs.onSurface,
            iconColor = accent,
            bordered = true,
            onClick = onMore,
        )
    }
}

@Composable
private fun QuickTile(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    background: Color,
    contentColor: Color,
    iconColor: Color,
    bordered: Boolean,
    onClick: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(if (bordered) Modifier.border(1.dp, outline, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp), tint = iconColor)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (!bordered) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}




@Composable
private fun EmptyTimeline() {
    CardSurface(contentPadding = 16.dp) {
        Text("今天还没有记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "点上面的「喝水」或「喂食」，3 秒记一笔",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ScrollHint() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_ms_expand_more),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        Text(
            "上滑查看更早记录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}
