package com.xiaofu.daily.ui.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.data.local.model.DewormKind
import com.xiaofu.daily.data.local.model.RecordType
import com.xiaofu.daily.data.local.model.Severity
import com.xiaofu.daily.ui.components.BigNumberField
import com.xiaofu.daily.ui.components.CustomTagDialog
import com.xiaofu.daily.ui.components.FormLabel
import com.xiaofu.daily.ui.components.NoteFieldBox
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.components.PrimaryButton
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.SegmentedRow
import com.xiaofu.daily.ui.components.TextFieldBox
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.today.TodayViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    primaryText: String = "保存记录",
    primaryEnabled: Boolean = true,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(R.drawable.ic_ms_arrow_back, onBack, contentDescription = "返回")
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
        Box(
            Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 20.dp),
        ) {
            PrimaryButton(primaryText, onSave, enabled = primaryEnabled)
        }
    }
}

@Composable
private fun TimeRow(label: String, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
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
        Text(text, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Icon(
            painterResource(R.drawable.ic_ms_chevron_right),
            contentDescription = null,
            modifier = Modifier.padding(start = 3.dp).size(16.dp),
            tint = cs.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialDay: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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

private fun nowMillis(): Long = System.currentTimeMillis()

/** 改日期时保留记录原有时间点，避免时刻被重置为当前 */
private fun combineDayWithTime(day: Long, sourceMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(sourceMillis).atZone(zone).toLocalTime()
    return LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()
}

// ===================== 体重 =====================

@Composable
fun WeightEditor(vm: TodayViewModel, record: RecordEntity?, initialOccurredAt: Long? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var jinText by remember {
        mutableStateOf(record?.weightGrams?.let { String.format(Locale.CHINA, "%.1f", it / 500.0) } ?: "")
    }
    LaunchedEffect(record) {
        if (record == null && jinText.isBlank()) {
            vm.lastWeightGrams()?.let {
                jinText = String.format(Locale.CHINA, "%.1f", it / 500.0)
            }
        }
    }
    var note by remember { mutableStateOf(record?.note ?: "") }
    var occurredAt by remember { mutableStateOf(record?.occurredAt ?: initialOccurredAt ?: nowMillis()) }
    var showPicker by remember { mutableStateOf(false) }
    val jin = jinText.toDoubleOrNull()
    val valid = jin != null && jin in 0.1..50.0

    EditorScaffold(
        title = if (record == null) "记录体重" else "编辑体重",
        onBack = onBack,
        primaryEnabled = valid,
        onSave = {
            val value = jin
            if (value != null) {
                scope.launch {
                    vm.saveWeight((value * 500).roundToInt(), note.ifBlank { null }, record, occurredAt)
                    onBack()
                }
            }
        },
    ) {
        FormLabel("体重", badge = "必填")
        BigNumberField(
            value = jinText,
            onValueChange = { jinText = it },
            unit = "斤",
            placeholder = "0.0",
            allowDecimal = true,
        )
        Text(
            "0.1 斤精度，例如 8.6",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        TimeRow("时间", Fmt.detailTime(occurredAt)) { showPicker = true }
        FormLabel("备注", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        NoteFieldBox(value = note, onValueChange = { note = it }, placeholder = "例如：晚间称重")
    }

    if (showPicker) {
        DatePickerSheet(
            initialDay = Fmt.date(occurredAt).toEpochDay(),
            onDismiss = { showPicker = false },
            onPick = { day -> occurredAt = combineDayWithTime(day, occurredAt) },
        )
    }
}

// ===================== 驱虫 =====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DewormEditor(vm: TodayViewModel, record: RecordEntity?, initialOccurredAt: Long? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kindIndex by remember { mutableIntStateOf(if (record?.dewormKind == DewormKind.EXTERNAL) 1 else 0) }
    var medicine by remember { mutableStateOf(record?.medicine ?: "") }
    var note by remember { mutableStateOf(record?.note ?: "") }
    var occurredAt by remember { mutableStateOf(record?.occurredAt ?: initialOccurredAt ?: nowMillis()) }
    var nextDueDay by remember { mutableStateOf(record?.nextDueDate) }
    var cycleDays by remember { mutableIntStateOf(if (kindIndex == 1) 30 else 90) }
    var showOccurredPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var medicineTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCustomTag by remember { mutableStateOf(false) }
    var customTagName by remember { mutableStateOf("") }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { medicineTags = vm.medicineTags() }

    val kind = if (kindIndex == 1) DewormKind.EXTERNAL else DewormKind.INTERNAL
    LaunchedEffect(kindIndex) {
        cycleDays = vm.dewormCycleDays(kind)
        if (record?.nextDueDate == null) {
            nextDueDay = Fmt.date(occurredAt).plusDays(cycleDays.toLong()).toEpochDay()
        }
    }

    EditorScaffold(
        title = if (record == null) "记录驱虫" else "编辑驱虫",
        onBack = onBack,
        onSave = {
            scope.launch {
                vm.saveDeworm(kind, medicine.ifBlank { null }, nextDueDay, note.ifBlank { null }, record, occurredAt)
                onBack()
            }
        },
    ) {
        FormLabel("类型", badge = "必填")
        SegmentedRow(
            options = listOf("内驱", "外驱"),
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
        )
        FormLabel("药品名", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        TextFieldBox(value = medicine, onValueChange = { medicine = it }, placeholder = "例如：海乐妙")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            medicineTags.forEach { tag ->
                PillChip(
                    text = tag,
                    selected = medicine == tag,
                    onClick = { medicine = tag },
                    onLongClick = { pendingDeleteTag = tag },
                )
            }
            PillChip(
                text = "自定义",
                selected = false,
                onClick = { showCustomTag = true },
                leadingIconRes = R.drawable.ic_ms_add,
            )
        }
        TimeRow("驱虫时间", Fmt.detailTime(occurredAt)) { showOccurredPicker = true }
        TimeRow(
            "下次到期",
            nextDueDay?.let { "${Fmt.fullDate(it)}（$cycleDays 天周期）" } ?: "未设置",
        ) { showDuePicker = true }
        FormLabel("备注", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        NoteFieldBox(value = note, onValueChange = { note = it }, placeholder = "例如：滴剂，颈后给药")
    }

    if (showOccurredPicker) {
        DatePickerSheet(
            initialDay = Fmt.date(occurredAt).toEpochDay(),
            onDismiss = { showOccurredPicker = false },
            onPick = { day ->
                val oldDay = Fmt.date(occurredAt).toEpochDay()
                occurredAt = combineDayWithTime(day, occurredAt)
                // 未手动改写的到期日（= 旧日期 + 周期）按新日期随周期平移；手动改写过的保持不变
                if (nextDueDay == oldDay + cycleDays) {
                    nextDueDay = Fmt.date(occurredAt).plusDays(cycleDays.toLong()).toEpochDay()
                }
            },
        )
    }
    if (showDuePicker) {
        DatePickerSheet(
            initialDay = nextDueDay ?: LocalDate.now().toEpochDay(),
            onDismiss = { showDuePicker = false },
            onPick = { day -> nextDueDay = day },
        )
    }

    if (showCustomTag) {
        CustomTagDialog(
            value = customTagName,
            onValueChange = { customTagName = it },
            onDismiss = { showCustomTag = false; customTagName = "" },
            onConfirm = {
                val name = customTagName.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        vm.addMedicineTag(name)
                        medicineTags = vm.medicineTags()
                        medicine = name
                        showCustomTag = false
                        customTagName = ""
                    }
                }
            },
            title = "自定义药品",
            placeholder = "例如：海乐妙",
        )
    }

    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("删除药品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("删除常用药品「$tag」？", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteTag = null
                    scope.launch {
                        vm.removeMedicineTag(tag)
                        medicineTags = medicineTags - tag
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") }
            },
        )
    }
}

// ===================== 疫苗 =====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaccineEditor(vm: TodayViewModel, record: RecordEntity?, initialOccurredAt: Long? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(record?.vaccineName ?: "") }
    var note by remember { mutableStateOf(record?.note ?: "") }
    var occurredAt by remember { mutableStateOf(record?.occurredAt ?: initialOccurredAt ?: nowMillis()) }
    var nextDueDay by remember { mutableStateOf(record?.nextDueDate) }
    var showPicker by remember { mutableStateOf(false) }
    var vaccineTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCustomTag by remember { mutableStateOf(false) }
    var customTagName by remember { mutableStateOf("") }
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vaccineTags = vm.vaccineTags() }

    EditorScaffold(
        title = if (record == null) "记录疫苗" else "编辑疫苗",
        onBack = onBack,
        primaryEnabled = name.isNotBlank(),
        onSave = {
            scope.launch {
                vm.saveVaccine(name.trim(), nextDueDay, note.ifBlank { null }, record, occurredAt)
                onBack()
            }
        },
    ) {
        FormLabel("疫苗名称", badge = "必填")
        TextFieldBox(value = name, onValueChange = { name = it }, placeholder = "例如：猫三联 / 狂犬疫苗")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vaccineTags.forEach { tag ->
                PillChip(
                    text = tag,
                    selected = name.trim() == tag,
                    onClick = { name = tag },
                    onLongClick = { pendingDeleteTag = tag },
                )
            }
            PillChip(
                text = "自定义",
                selected = false,
                onClick = { showCustomTag = true },
                leadingIconRes = R.drawable.ic_ms_add,
            )
        }
        TimeRow("接种日期", Fmt.detailTime(occurredAt)) { showPicker = true }
        FormLabel("下次接种", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("1 年后", selected = false, onClick = {
                nextDueDay = Fmt.date(occurredAt).plusYears(1).toEpochDay()
            })
            PillChip("3 年后", selected = false, onClick = {
                nextDueDay = Fmt.date(occurredAt).plusYears(3).toEpochDay()
            })
            if (nextDueDay != null) {
                PillChip("清除", selected = false, onClick = { nextDueDay = null })
            }
        }
        TimeRow("下次接种日期", nextDueDay?.let { Fmt.fullDate(it) } ?: "未设置") { showPicker = true }
        FormLabel("备注", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        NoteFieldBox(value = note, onValueChange = { note = it }, placeholder = "例如：年度加强")
    }

    if (showPicker) {
        DatePickerSheet(
            initialDay = nextDueDay ?: Fmt.date(occurredAt).toEpochDay(),
            onDismiss = { showPicker = false },
            onPick = { day ->
                val oldDate = Fmt.date(occurredAt)
                occurredAt = combineDayWithTime(day, occurredAt)
                val newDate = Fmt.date(occurredAt)
                // 快捷选项（1年/3年）产生的下次接种日期随接种日期平移；手动自定义的保持不变
                nextDueDay = when (nextDueDay) {
                    oldDate.plusYears(1).toEpochDay() -> newDate.plusYears(1).toEpochDay()
                    oldDate.plusYears(3).toEpochDay() -> newDate.plusYears(3).toEpochDay()
                    else -> nextDueDay
                }
            },
        )
    }

    if (showCustomTag) {
        CustomTagDialog(
            value = customTagName,
            onValueChange = { customTagName = it },
            onDismiss = { showCustomTag = false; customTagName = "" },
            onConfirm = {
                val tag = customTagName.trim()
                if (tag.isNotEmpty()) {
                    scope.launch {
                        vm.addVaccineTag(tag)
                        vaccineTags = vm.vaccineTags()
                        name = tag
                        showCustomTag = false
                        customTagName = ""
                    }
                }
            },
            title = "自定义疫苗",
            placeholder = "例如：猫三联",
        )
    }

    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("删除疫苗", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("删除常用疫苗「$tag」？", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteTag = null
                    scope.launch {
                        vm.removeVaccineTag(tag)
                        vaccineTags = vaccineTags - tag
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") }
            },
        )
    }
}

// ===================== 异常 =====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnomalyEditor(vm: TodayViewModel, record: RecordEntity?, initialOccurredAt: Long? = null, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tags by vm.tags.collectAsState()
    var category by remember { mutableStateOf(record?.anomalyCategory ?: "") }
    var severityIndex by remember {
        mutableIntStateOf(
            when (record?.severity) {
                Severity.LIGHT -> 0
                Severity.MEDIUM -> 1
                Severity.HEAVY -> 2
                null -> -1
            },
        )
    }
    var note by remember { mutableStateOf(record?.note ?: "") }
    var occurredAt by remember { mutableStateOf(record?.occurredAt ?: initialOccurredAt ?: nowMillis()) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var pendingDeleteTag by remember {
        mutableStateOf<com.xiaofu.daily.data.local.entity.AnomalyTagEntity?>(null)
    }
    var showPicker by remember { mutableStateOf(false) }

    var existingPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var newUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(record?.id) {
        existingPhotos = record?.let { vm.photoNames(it.id) } ?: emptyList()
    }

    val remaining = (3 - existingPhotos.size - newUris.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3),
    ) { picked ->
        newUris = (newUris + picked).take(3 - existingPhotos.size)
    }

    EditorScaffold(
        title = if (record == null) "记录异常" else "编辑异常",
        onBack = onBack,
        primaryEnabled = category.isNotBlank(),
        onSave = {
            scope.launch {
                vm.saveAnomaly(
                    category = category,
                    severity = when (severityIndex) {
                        0 -> Severity.LIGHT
                        1 -> Severity.MEDIUM
                        2 -> Severity.HEAVY
                        else -> null
                    },
                    note = note.ifBlank { null },
                    photos = newUris,
                    existing = record,
                    keepPhotoNames = existingPhotos,
                    at = occurredAt,
                )
                onBack()
            }
        },
    ) {
        FormLabel("分类", badge = "必填")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 历史记录的分类可能已从标签库删除：仍显示为选中 chip，避免编辑时“无分类”
            if (category.isNotBlank() && tags.none { it.name == category }) {
                PillChip(text = category, selected = true, onClick = {})
            }
            tags.forEach { tag ->
                PillChip(
                    text = tag.name,
                    selected = category == tag.name,
                    onClick = { category = tag.name },
                    onLongClick = if (tag.isPreset) null else ({ pendingDeleteTag = tag }),
                )
            }
            PillChip(
                text = "自定义",
                selected = false,
                onClick = { showCustomDialog = true },
                leadingIconRes = R.drawable.ic_ms_add,
            )
        }
        FormLabel("严重程度", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedRow(
            options = listOf("轻", "中", "重"),
            selectedIndex = severityIndex,
            onSelect = { index -> severityIndex = if (severityIndex == index) -1 else index },
        )
        TimeRow("时间", Fmt.detailTime(occurredAt)) { showPicker = true }
        FormLabel("照片", badge = "最多 3 张", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            existingPhotos.forEach { fileName ->
                PhotoTile(file = vm.photoFile(fileName), onRemove = {
                    existingPhotos = existingPhotos - fileName
                })
            }
            newUris.forEach { uri ->
                PhotoTile(uri = uri, onRemove = { newUris = newUris - uri })
            }
            repeat((3 - existingPhotos.size - newUris.size).coerceAtLeast(0)) {
                AddPhotoTile {
                    if (remaining > 0) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
        }
        FormLabel("备注", badge = "选填", badgeColor = MaterialTheme.colorScheme.onSurfaceVariant)
        NoteFieldBox(value = note, onValueChange = { note = it }, placeholder = "例如：之后精神正常，已观察")
    }

    if (showPicker) {
        DatePickerSheet(
            initialDay = Fmt.date(occurredAt).toEpochDay(),
            onDismiss = { showPicker = false },
            onPick = { day -> occurredAt = combineDayWithTime(day, occurredAt) },
        )
    }

    pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("删除分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            text = { Text("删除自定义分类「${tag.name}」？历史记录不受影响。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    val removed = tag.name
                    pendingDeleteTag = null
                    scope.launch {
                        vm.deleteTag(tag.id)
                        if (category == removed) {
                            category = tags.firstOrNull { it.isPreset }?.name ?: ""
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) { Text("取消") }
            },
        )
    }

    if (showCustomDialog) {
        com.xiaofu.daily.ui.components.CustomTagDialog(
            value = customName,
            onValueChange = { customName = it },
            onDismiss = { showCustomDialog = false; customName = "" },
            onConfirm = {
                val name = customName.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        vm.addTag(name)
                        category = name
                        showCustomDialog = false
                        customName = ""
                    }
                }
            },
        )
    }
}

@Composable
private fun PhotoTile(file: File? = null, uri: Uri? = null, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file?.path, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = when {
                    file != null -> BitmapFactory.decodeFile(file.path, opts)
                    uri != null -> context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    else -> null
                }
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp)),
    ) {
        if (bitmap != null) {
            Image(bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.ic_ms_image),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(22.dp),
                tint = cs.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(ColorScrim)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ms_close),
                contentDescription = "移除照片",
                modifier = Modifier.size(11.dp),
                tint = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

private val ColorScrim = androidx.compose.ui.graphics.Color(0x8C000000)

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_ms_add),
            contentDescription = "添加照片",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
