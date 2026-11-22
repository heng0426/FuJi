package com.xiaofu.daily.ui.records

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xiaofu.daily.data.DueCard
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.components.RecordRow
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.recordStyle
import com.xiaofu.daily.ui.today.DetailSheet
import com.xiaofu.daily.ui.today.FoodSheet
import com.xiaofu.daily.ui.today.TodayViewModel
import com.xiaofu.daily.ui.today.WaterSheet
import kotlinx.coroutines.launch

private val typeLabels = mapOf(
    RecordType.WATER to "喝水",
    RecordType.FOOD to "喂食",
    RecordType.WEIGHT to "体重",
    RecordType.DEWORM to "驱虫",
    RecordType.VACCINE to "疫苗",
    RecordType.ANOMALY to "异常",
)

@Composable
fun RecordsScreen(
    vm: RecordsViewModel,
    actions: TodayViewModel,
    onOpenEditor: (EditorTarget) -> Unit,
    onDeleteRecord: (RecordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val filter by vm.filter.collectAsState()
    val records by vm.records.collectAsState()
    val dueCards by vm.dueCards.collectAsState()

    val scope = rememberCoroutineScope()
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

    val groups = remember(records) { records.groupBy { Fmt.date(it.occurredAt) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("记录", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                    Text("按类型查看历史", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                RoundIconButton(R.drawable.ic_ms_search)
            }
        }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PillChip("全部", selected = filter == null, onClick = { vm.setFilter(null) })
                }
                items(items = RecordType.entries, key = { it.name }) { type ->
                    PillChip(
                        text = typeLabels.getValue(type),
                        selected = filter == type,
                        onClick = { vm.setFilter(type) },
                    )
                }
            }
        }
        if (dueCards.isNotEmpty() && filter == null) {
            items(items = dueCards, key = { "due_${it.type}_${it.dueDay}" }) { card ->
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
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${dayRecords.size} 条", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            items(items = dayRecords, key = { it.id }) { record ->
                RecordRow(
                    record = record,
                    showDivider = dayRecords.indexOf(record) > 0,
                    onClick = { detail = record },
                )
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
            initialSnack = editing?.snackType ?: lastSnack,
            editing = editing,
            onDismiss = {
                showFood = false
                foodEditing = null
            },
            onSave = { kind, servings, grams, snackType ->
                scope.launch {
                    actions.saveFood(kind, servings, grams, snackType, editing)
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
