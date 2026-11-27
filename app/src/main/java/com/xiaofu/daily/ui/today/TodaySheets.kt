package com.xiaofu.daily.ui.today

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.R
import com.xiaofu.daily.data.SettingsKeys
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.MealKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.ui.components.BigNumberField
import com.xiaofu.daily.ui.components.CustomTagDialog
import com.xiaofu.daily.ui.components.DateTimePickerDialog
import com.xiaofu.daily.ui.components.FormLabel
import com.xiaofu.daily.ui.components.LocalPhotoViewerHost
import com.xiaofu.daily.ui.components.PickerRow
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.components.ViewerPhoto
import com.xiaofu.daily.ui.components.PrimaryButton
import com.xiaofu.daily.ui.components.SecondaryButton
import com.xiaofu.daily.ui.components.SegmentedRow
import com.xiaofu.daily.ui.components.SheetHandle
import com.xiaofu.daily.ui.components.StepperRow
import com.xiaofu.daily.ui.components.roundedRowClickable
import com.xiaofu.daily.ui.components.TextFieldBox
import com.xiaofu.daily.ui.components.TypeIcon
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.theme.SnackStyle
import com.xiaofu.daily.ui.theme.recordStyle
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 加餐类型标签库：与药品名标签同一套机制，默认值见 DefaultNameTags，可增删并落库 */
private suspend fun loadSnackTypeTags(): List<String> =
    runCatching { AppContainer.repository.nameTags(SettingsKeys.SNACK_TYPE_TAGS) }.getOrDefault(emptyList())

/** 喝水输入方式：喝水直接填喝掉的量；水瓶 / 水碗用上次剩余减本次剩余；记住上次选择 */
private enum class WaterEntryMode {
    DIRECT,
    BOTTLE,
    BOWL;

    companion object {
        private const val PREFS = "xiaofu_prefs"
        private const val KEY = "water_entry_mode"

        fun read(context: Context): WaterEntryMode {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == raw } ?: DIRECT
        }

        fun write(context: Context, mode: WaterEntryMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, mode.name)
                .apply()
        }
    }
}

/** 水瓶 / 水碗的剩余各存一份，只在本机，不进数据库与备份；保存后带入下次的「上次剩余」 */
private object WaterSourceMemory {
    private const val PREFS = "xiaofu_prefs"
    private const val BOTTLE_KEY = "water_bottle_last_left"
    private const val BOWL_KEY = "water_bowl_last_left"

    private fun key(mode: WaterEntryMode): String =
        if (mode == WaterEntryMode.BOWL) BOWL_KEY else BOTTLE_KEY

    fun readLeft(context: Context, mode: WaterEntryMode): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(mode), "").orEmpty()

    fun writeLeft(context: Context, mode: WaterEntryMode, left: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(mode), left)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterSheet(
    lastMl: Int,
    editing: RecordEntity?,
    initialOccurredAt: Long? = null,
    onDismiss: () -> Unit,
    onSave: (Int, Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 编辑沿用原时刻；从日历补记时用选中日；其余取当前时间
    var occurredAt by remember {
        mutableStateOf(editing?.occurredAt ?: initialOccurredAt ?: System.currentTimeMillis())
    }
    var showTimePicker by remember { mutableStateOf(false) }
    // 编辑只针对已记下的数值，固定走喝水
    var mode by remember {
        mutableStateOf(if (editing == null) WaterEntryMode.read(context) else WaterEntryMode.DIRECT)
    }
    var text by remember { mutableStateOf((editing?.amountMl ?: lastMl).toString()) }
    // 水瓶与水碗各自记住上次剩余，互不覆盖
    var lastLeftByMode by remember {
        mutableStateOf(
            WaterEntryMode.entries.associateWith { WaterSourceMemory.readLeft(context, it) },
        )
    }
    var nowLeftText by remember { mutableStateOf("") }
    val lastLeftText = lastLeftByMode[mode].orEmpty()

    val value = text.toIntOrNull()
    val sourceMl = sourceDrinkMl(lastLeftText, nowLeftText)
    // 只填上次剩余：按钮只更新剩余，不产生喝水记录
    val sourceOnly = mode != WaterEntryMode.DIRECT && sourceMl == null
    val valid = if (mode == WaterEntryMode.DIRECT) {
        value != null && value in 1..5000
    } else {
        sourceCanSave(lastLeftText, nowLeftText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHandle()
            Text(
                if (editing == null) "记喝水" else "编辑喝水",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            if (editing == null) {
                Spacer(Modifier.height(12.dp))
                SegmentedRow(
                    options = listOf("喝水", "水瓶", "水碗"),
                    selectedIndex = mode.ordinal,
                    onSelect = { index ->
                        val next = WaterEntryMode.entries[index]
                        if (next != mode) {
                            mode = next
                            WaterEntryMode.write(context, next)
                            // 本次剩余属于各自的来源，切换后留空
                            nowLeftText = ""
                        }
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                // 水瓶与水碗的输入项相同，切换时只换数值，不做淡入淡出，避免输入框闪一下
                targetState = mode == WaterEntryMode.DIRECT,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "waterEntry",
            ) { isDirect ->
                if (isDirect) {
                    Column {
                        BigNumberField(value = text, onValueChange = { text = it }, unit = "ml")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(50, 100, 150, 200).forEach { preset ->
                                PillChip(
                                    text = preset.toString(),
                                    selected = value == preset,
                                    onClick = { text = preset.toString() },
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                FormLabel("上次剩余")
                                Spacer(Modifier.height(6.dp))
                                BigNumberField(
                                    value = lastLeftText,
                                    onValueChange = { lastLeftByMode = lastLeftByMode + (mode to it) },
                                    unit = "ml",
                                    compact = true,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                FormLabel("本次剩余")
                                Spacer(Modifier.height(6.dp))
                                BigNumberField(
                                    value = nowLeftText,
                                    onValueChange = { nowLeftText = it },
                                    unit = "ml",
                                    compact = true,
                                )
                            }
                        }
                        sourceHint(lastLeftText, nowLeftText)?.let { hint ->
                            Text(
                                hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PickerRow("时间", Fmt.detailTime(occurredAt)) { showTimePicker = true }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("取消", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    text = if (editing == null && !sourceOnly) "记下来" else "保存",
                    onClick = {
                        when {
                            mode == WaterEntryMode.DIRECT -> value?.let { onSave(it, occurredAt) }
                            sourceMl != null -> {
                                WaterSourceMemory.writeLeft(
                                    context,
                                    mode,
                                    nowLeftText.toIntOrNull()?.toString() ?: "0",
                                )
                                onSave(sourceMl, occurredAt)
                            }
                            else -> {
                                // 只填上次剩余：只记住这个值，不记喝水
                                WaterSourceMemory.writeLeft(context, mode, lastLeftText)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = valid,
                )
            }
        }
    }

    if (showTimePicker) {
        DateTimePickerDialog(
            initialMillis = occurredAt,
            onDismiss = { showTimePicker = false },
            onPick = { occurredAt = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodSheet(
    initialKind: MealKind,
    initialGrams: Int,
    initialServings: Int,
    initialSnack: String?,
    editing: RecordEntity?,
    initialOccurredAt: Long? = null,
    onSavePreset: (com.xiaofu.daily.data.FoodPreset, Int, Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (MealKind, Int, Int, String?, Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 编辑沿用原时刻；从日历补记时用选中日；其余取当前时间
    var occurredAt by remember {
        mutableStateOf(editing?.occurredAt ?: initialOccurredAt ?: System.currentTimeMillis())
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var servings by remember {
        mutableIntStateOf((editing?.servingCount?.roundToInt() ?: initialServings).coerceIn(1, 20))
    }
    var grams by remember { mutableIntStateOf((editing?.gramsPerServing ?: initialGrams).coerceIn(1, 200)) }
    var snackType by remember { mutableStateOf(editing?.snackType ?: initialSnack.orEmpty()) }
    var presets by remember { mutableStateOf<List<com.xiaofu.daily.data.FoodPreset>>(emptyList()) }
    var showAddPreset by remember { mutableStateOf(false) }
    var pendingDeletePreset by remember { mutableStateOf<com.xiaofu.daily.data.FoodPreset?>(null) }

    // 加餐类型：默认「冻干 / 罐头 / 猫条 / 其他」，与药品名标签同一套增删机制（落库、可长按删除）
    var snackTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAddSnackType by remember { mutableStateOf(false) }
    var newSnackType by remember { mutableStateOf("") }
    var pendingDeleteSnackType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        presets = runCatching { AppContainer.repository.foodPresets() }.getOrDefault(emptyList())
        snackTypes = loadSnackTypeTags()
        if (snackType.isBlank()) snackType = snackTypes.firstOrNull().orEmpty()
    }

    val kind = editing?.mealKind ?: initialKind
    val total = servings * grams

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHandle()
            Text(
                when {
                    editing != null && kind == MealKind.SNACK -> "编辑加餐"
                    editing != null -> "编辑喂食"
                    kind == MealKind.SNACK -> "记加餐"
                    else -> "记喂食"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            if (kind == MealKind.MAIN && editing == null) {
                Spacer(Modifier.height(12.dp))
                Text("常用", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        val isCustom = com.xiaofu.daily.data.FoodPreset.defaults.none { it.name == preset.name }
                        PillChip(
                            text = "${preset.name} ${preset.servings}份",
                            selected = false,
                            onClick = { onSavePreset(preset, grams, occurredAt) },
                            onLongClick = if (isCustom) ({ pendingDeletePreset = preset }) else null,
                        )
                    }
                    PillChip(
                        text = "+ 自定义",
                        selected = false,
                        onClick = { showAddPreset = true },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StepperRow(
                label = "份数",
                valueText = Fmt.servings(servings),
                unit = "份",
                onMinus = { if (servings > 1) servings -= 1 },
                onPlus = { if (servings < 20) servings += 1 },
                minusEnabled = servings > 1,
                plusEnabled = servings < 20,
            )
            StepperRow(
                label = "每份克重",
                valueText = grams.toString(),
                unit = "克",
                onMinus = { if (grams > 1) grams -= 1 },
                onPlus = { if (grams < 200) grams += 1 },
                minusEnabled = grams > 1,
                plusEnabled = grams < 200,
                showDivider = false,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (kind == MealKind.SNACK) "加餐" else "主粮"} · ${Fmt.servings(servings)} 份 × $grams 克",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    Text(
                        "本次共 $total 克",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    painterResource(if (kind == MealKind.SNACK) R.drawable.ic_ms_cookie else R.drawable.ic_ms_restaurant),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = cs.onPrimaryContainer,
                )
            }
            if (kind == MealKind.SNACK) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 编辑旧记录时，若原类型已从标签库删除，临时并入显示，避免看不到当前值
                    val shownTypes =
                        if (snackType.isBlank() || snackType in snackTypes) snackTypes
                        else snackTypes + snackType
                    shownTypes.forEach { type ->
                        PillChip(
                            text = type,
                            selected = snackType == type,
                            onClick = { snackType = type },
                            onLongClick = { pendingDeleteSnackType = type },
                        )
                    }
                    PillChip(
                        text = "自定义",
                        selected = false,
                        onClick = { showAddSnackType = true },
                        leadingIconRes = R.drawable.ic_ms_add,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PickerRow("时间", Fmt.detailTime(occurredAt)) { showTimePicker = true }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("取消", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    text = if (editing == null) "保存" else "保存修改",
                    onClick = { onSave(kind, servings, grams, if (kind == MealKind.SNACK) snackType else null, occurredAt) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    pendingDeletePreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("删除预设", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
            text = { Text("删除「${preset.name}」这个预设？", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val name = preset.name
                    pendingDeletePreset = null
                    scope.launch {
                        runCatching { AppContainer.repository.deleteFoodPreset(name) }
                        presets = runCatching { AppContainer.repository.foodPresets() }.getOrDefault(presets)
                    }
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) { Text("取消", color = cs.onSurfaceVariant) }
            },
        )
    }

    if (showAddPreset) {
        AddPresetDialog(
            onDismiss = { showAddPreset = false },
            onConfirm = { name, count ->
                showAddPreset = false
                scope.launch {
                    runCatching { AppContainer.repository.addFoodPreset(name, count) }
                    presets = runCatching { AppContainer.repository.foodPresets() }.getOrDefault(presets)
                }
            },
        )
    }

    if (showAddSnackType) {
        CustomTagDialog(
            value = newSnackType,
            onValueChange = { newSnackType = it },
            onDismiss = {
                showAddSnackType = false
                newSnackType = ""
            },
            onConfirm = {
                val name = newSnackType.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        runCatching {
                            AppContainer.repository.addNameTag(SettingsKeys.SNACK_TYPE_TAGS, name)
                        }
                        snackTypes = loadSnackTypeTags()
                        snackType = name
                        showAddSnackType = false
                        newSnackType = ""
                    }
                }
            },
            title = "自定义加餐类型",
            placeholder = "例如：猫条",
        )
    }

    pendingDeleteSnackType?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSnackType = null },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    "删除加餐类型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
            },
            text = {
                Text(
                    "删除「$tag」？已记录的历史不受影响。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteSnackType = null
                    scope.launch {
                        runCatching {
                            AppContainer.repository.removeNameTag(SettingsKeys.SNACK_TYPE_TAGS, tag)
                        }
                        snackTypes = loadSnackTypeTags()
                        if (snackType == tag) snackType = snackTypes.firstOrNull().orEmpty()
                    }
                }) { Text("删除", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSnackType = null }) {
                    Text("取消", color = cs.onSurfaceVariant)
                }
            },
        )
    }

    if (showTimePicker) {
        DateTimePickerDialog(
            initialMillis = occurredAt,
            onDismiss = { showTimePicker = false },
            onPick = { occurredAt = it },
        )
    }
}

@Composable
private fun AddPresetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text("自定义预设", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
        text = {
            Column {
                Text("名称", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                TextFieldBox(
                    value = name,
                    onValueChange = { name = it.take(8) },
                    placeholder = "例如：夜宵",
                )
                StepperRow(
                    label = "份数",
                    valueText = count.toString(),
                    unit = "份",
                    onMinus = { if (count > 1) count-- },
                    onPlus = { if (count < 20) count++ },
                    minusEnabled = count > 1,
                    plusEnabled = count < 20,
                    showDivider = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, count) }, enabled = name.isNotBlank()) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = cs.onSurfaceVariant) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(onDismiss: () -> Unit, onPick: (EditorTarget) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHandle()
            Text("记点什么", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            MoreRow(R.drawable.ic_ms_monitor_weight, "体重", "按需记录，查看趋势", RecordType.WEIGHT) {
                onPick(EditorTarget.Weight())
            }
            MoreRow(R.drawable.ic_ms_pest_control, "驱虫", "内驱 / 外驱，自动推算下次到期", RecordType.DEWORM) {
                onPick(EditorTarget.Deworm())
            }
            MoreRow(R.drawable.ic_ms_vaccines, "疫苗", "接种记录与下次接种提醒", RecordType.VACCINE) {
                onPick(EditorTarget.Vaccine())
            }
            MoreRow(R.drawable.ic_ms_local_hospital, "就诊", "医院、诊断与费用", RecordType.VET) {
                onPick(EditorTarget.Vet())
            }
            MoreRow(R.drawable.ic_ms_warning, "异常", "呕吐、腹泻等异常情况", RecordType.ANOMALY, last = true) {
                onPick(EditorTarget.Anomaly())
            }
        }
    }
}

@Composable
private fun MoreRow(iconRes: Int, title: String, subtitle: String, type: RecordType, last: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val style = recordStyle(type)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .roundedRowClickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeIcon(style.iconRes, style.fg, style.bg, size = 36.dp, iconSize = 18.dp, radius = 12.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = cs.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            }
            Icon(painterResource(R.drawable.ic_ms_chevron_right), null, Modifier.size(17.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.75f))
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    record: RecordEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReopen: (RecordEntity) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val display = recordDisplay(record)
    val viewerHost = LocalPhotoViewerHost.current
    val photos by produceState(initialValue = emptyList<File>(), record.id) {
        value = runCatching {
            AppContainer.repository.photoNames(record.id)
                .map { AppContainer.repository.photoFile(it) }
        }.getOrDefault(emptyList())
    }
    // 预览挂在 App 根窗口上，先关掉底部弹窗，关闭预览后再恢复详情
    val openViewer: (Int) -> Unit = { index ->
        viewerHost?.let { host ->
            host.show(
                photos = photos.map { ViewerPhoto(it, photoHint(record)) },
                initialIndex = index,
                onClosed = { onReopen(record) },
            )
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetHandle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeIcon(display.iconRes, display.fg, display.bg, size = 40.dp, iconSize = 20.dp, radius = 13.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(display.title, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(
                        display.value.ifBlank { "—" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            DetailRow("时间", Fmt.detailTime(record.occurredAt))
            when (record.type) {
                RecordType.ANOMALY -> {
                    DetailRow("分类", record.anomalyCategory ?: "—")
                    record.severity?.let { DetailRow("程度", it.label()) }
                    DetailRow("备注", record.note?.takeIf { it.isNotBlank() } ?: "无", multiline = true)
                    RecordPhotoStrip(photos, openViewer)
                }

                RecordType.VET -> {
                    record.vetHospital?.let { DetailRow("医院", it) }
                    record.vetDoctor?.let { DetailRow("医生", it) }
                    record.vetDiagnosis?.let { DetailRow("诊断", it, multiline = true) }
                    record.vetCostYuan?.let { DetailRow("费用", "¥$it") }
                    DetailRow("备注", record.note?.takeIf { it.isNotBlank() } ?: "无", multiline = true)
                    RecordPhotoStrip(photos, openViewer)
                }

                else -> {
                    record.note?.takeIf { it.isNotBlank() }?.let { DetailRow("备注", it, multiline = true) }
                    if (record.type == RecordType.DEWORM || record.type == RecordType.VACCINE) {
                        record.nextDueDate?.let {
                            DetailRow(if (record.type == RecordType.VACCINE) "下次接种" else "下次到期", Fmt.fullDate(it))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton("删除", onDelete, Modifier.weight(1f), textColor = cs.error)
                PrimaryButton("编辑", onEdit, Modifier.weight(1f))
            }
        }
    }
}

/** 预览底部的提示：异常=分类·程度·时间，就医=医院/诊断·医生·时间 */
private fun photoHint(record: RecordEntity): String = when (record.type) {
    RecordType.ANOMALY -> listOfNotNull(
        record.anomalyCategory,
        record.severity?.label(),
        Fmt.detailTime(record.occurredAt),
    ).joinToString(" · ")

    RecordType.VET -> listOfNotNull(
        record.vetHospital ?: record.vetDiagnosis,
        record.vetDoctor?.let { "医生 $it" },
        Fmt.detailTime(record.occurredAt),
    ).joinToString(" · ")

    else -> Fmt.detailTime(record.occurredAt)
}

@Composable
private fun DetailRow(label: String, value: String, multiline: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecordPhotoStrip(photos: List<File>, onPhotoClick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(8.dp))
    Text("照片", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    if (photos.isEmpty()) {
        Text(
            "无照片",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photos.take(3).forEachIndexed { index, file ->
                RecordThumb(file) { onPhotoClick(index) }
            }
        }
    }
}

@Composable
private fun RecordThumb(file: File, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(bitmap!!, contentDescription = "预览照片", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
