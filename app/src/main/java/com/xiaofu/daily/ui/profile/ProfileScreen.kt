package com.xiaofu.daily.ui.profile

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.data.SettingsKeys
import com.xiaofu.daily.data.local.entity.ProfileEntity
import com.xiaofu.daily.data.local.model.Gender
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.ui.components.PillChip
import com.xiaofu.daily.ui.components.PrimaryButton
import com.xiaofu.daily.ui.components.RoundIconButton
import com.xiaofu.daily.ui.components.SecondaryButton
import com.xiaofu.daily.ui.components.SegmentedRow
import com.xiaofu.daily.ui.components.SheetHandle
import com.xiaofu.daily.ui.components.StepperRow
import com.xiaofu.daily.ui.components.TextFieldBox
import com.xiaofu.daily.ui.format.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val profile by vm.profile.collectAsState()
    val settings by vm.settings.collectAsState()

    var editProfile by remember { mutableStateOf(false) }
    var editServing by remember { mutableStateOf(false) }
    var editCycle by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    val serving = settings[SettingsKeys.SERVING_GRAMS]?.toIntOrNull() ?: 15
    val internalDays = settings[SettingsKeys.DEWORM_INTERNAL_DAYS]?.toIntOrNull() ?: 90
    val externalDays = settings[SettingsKeys.DEWORM_EXTERNAL_DAYS]?.toIntOrNull() ?: 30
    val notifyEnabled = settings[SettingsKeys.NOTIFY_ENABLED] != "false"
    val notifyDays = settings[SettingsKeys.NOTIFY_DAYS_AHEAD]?.toIntOrNull() ?: 3
    val notifyHour = settings[SettingsKeys.NOTIFY_HOUR]?.toIntOrNull() ?: 9

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                    Text("小福的档案与偏好", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                RoundIconButton(R.drawable.ic_ms_settings)
            }
        }
        item(key = "profile") {
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photoFile = profile?.photoFile)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(profile?.name ?: "小福", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        Text(
                            metaText(profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        "编辑",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, cs.outline, RoundedCornerShape(999.dp))
                            .clickable { editProfile = true }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        item(key = "settings") {
            CardSurface(contentPadding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                    SettingRow(
                        iconRes = R.drawable.ic_ms_restaurant,
                        title = "喂养 · 每份克重",
                        value = "$serving 克",
                        onClick = { editServing = true },
                    )
                    SettingRow(
                        iconRes = R.drawable.ic_ms_pest_control,
                        title = "驱虫周期",
                        value = "内 $internalDays 天 / 外 $externalDays 天",
                        onClick = { editCycle = true },
                    )
                    SettingRow(
                        iconRes = R.drawable.ic_ms_notifications,
                        title = "提醒",
                        value = if (notifyEnabled) {
                            "提前 $notifyDays 天 · ${notifyHour.toString().padStart(2, '0')}:00"
                        } else {
                            "已关闭"
                        },
                        onClick = { showReminder = true },
                    )
                    SettingRow(
                        iconRes = R.drawable.ic_ms_dark_mode,
                        title = "外观",
                        value = "跟随系统",
                        onClick = null,
                        showChevron = false,
                    )
                    SettingRow(
                        iconRes = R.drawable.ic_ms_info,
                        title = "关于",
                        value = "版本 0.1.0",
                        onClick = { showAbout = true },
                        last = true,
                    )
                }
            }
        }
        item(key = "note") {
            CardSurface(contentPadding = 16.dp) {
                Text("数据都在本机", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(
                    "当前版本仅本地存储，不上传云端；导出备份将在后续版本提供。",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (editProfile) {
        EditProfileSheet(
            profile = profile,
            onDismiss = { editProfile = false },
            onSave = { name, breed, gender, birthday, photoUri ->
                vm.saveProfile(profile, name, breed, gender, birthday, photoUri)
                editProfile = false
            },
        )
    }

    if (showReminder) {
        ReminderDialog(
            enabled = notifyEnabled,
            days = notifyDays,
            hour = notifyHour,
            onDismiss = { showReminder = false },
            onSave = { e, d, h ->
                vm.setSetting(SettingsKeys.NOTIFY_ENABLED, e.toString())
                vm.setSetting(SettingsKeys.NOTIFY_DAYS_AHEAD, d.toString())
                vm.setSetting(SettingsKeys.NOTIFY_HOUR, h.toString())
                showReminder = false
            },
        )
    }

    if (editServing) {
        NumberSettingDialog(
            title = "每份克重",
            unit = "克",
            initial = serving,
            range = 1..200,
            onDismiss = { editServing = false },
            onSave = {
                vm.setSetting(SettingsKeys.SERVING_GRAMS, it.toString())
                editServing = false
            },
        )
    }

    if (editCycle) {
        CycleDialog(
            internal = internalDays,
            external = externalDays,
            onDismiss = { editCycle = false },
            onSave = { i, e ->
                vm.setSetting(SettingsKeys.DEWORM_INTERNAL_DAYS, i.toString())
                vm.setSetting(SettingsKeys.DEWORM_EXTERNAL_DAYS, e.toString())
                editCycle = false
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("小福日常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
            text = {
                Text(
                    "版本 0.1.0\n数据仅保存在本机，不上传云端。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("知道了", color = cs.primary) }
            },
        )
    }
}

private fun metaText(profile: ProfileEntity?): String {
    if (profile == null) return "橘猫"
    val age = ageText(profile.birthdayDay)
    val gender = when (profile.gender) {
        Gender.MALE -> "男孩"
        Gender.FEMALE -> "女孩"
        null -> null
    }
    return listOfNotNull(profile.breed ?: "橘猫", age, gender).joinToString(" · ")
}

private fun ageText(birthdayDay: Long?): String? {
    if (birthdayDay == null) return null
    val birthday = LocalDate.ofEpochDay(birthdayDay)
    if (birthday.isAfter(LocalDate.now())) return null
    val period = Period.between(birthday, LocalDate.now())
    return when {
        period.years > 0 -> "${period.years} 岁 ${period.months} 个月"
        period.months > 0 -> "${period.months} 个月"
        else -> "${period.days} 天"
    }
}

@Composable
private fun SettingRow(
    iconRes: Int,
    title: String,
    value: String,
    onClick: (() -> Unit)?,
    last: Boolean = false,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(iconRes), null, Modifier.size(17.dp), tint = cs.onSurfaceVariant)
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(end = if (trailing != null) 8.dp else 0.dp),
            )
            if (trailing != null) {
                trailing()
            } else if (showChevron) {
                Icon(
                    painterResource(R.drawable.ic_ms_chevron_right),
                    null,
                    Modifier.padding(start = 3.dp).size(17.dp),
                    tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    profile: ProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Gender?, Long?, Uri?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(profile?.name ?: "小福") }
    var breed by remember { mutableStateOf(profile?.breed ?: "") }
    var genderIndex by remember {
        mutableIntStateOf(
            when (profile?.gender) {
                Gender.MALE -> 0
                Gender.FEMALE -> 1
                null -> -1
            },
        )
    }
    var birthdayDay by remember { mutableStateOf(profile?.birthdayDay) }
    var showPicker by remember { mutableStateOf(false) }
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pickedPhoto = uri }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHandle()
            Text("编辑档案", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(photoFile = profile?.photoFile, pickedUri = pickedPhoto, size = 64.dp, radius = 22.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("更换头像", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = cs.onSurface)
                    Text(
                        if (pickedPhoto != null) "已选择新照片，保存后生效" else "从相册选择",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text("名字", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            TextFieldBox(value = name, onValueChange = { name = it.take(12) }, placeholder = "小福")
            Text("品种", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            TextFieldBox(value = breed, onValueChange = { breed = it.take(12) }, placeholder = "例如：橘猫")
            Text("性别", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            SegmentedRow(
                options = listOf("男孩", "女孩"),
                selectedIndex = genderIndex,
                onSelect = { genderIndex = if (genderIndex == it) -1 else it },
            )
            Text("生日", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surface)
                        .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    Text(
                        birthdayDay?.let { Fmt.fullDate(it) } ?: "未设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                }
                if (birthdayDay != null) {
                    Text(
                        "清除",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { birthdayDay = null }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("取消", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    "保存",
                    onClick = {
                        val gender = when (genderIndex) {
                            0 -> Gender.MALE
                            1 -> Gender.FEMALE
                            else -> null
                        }
                        onSave(name, breed.ifBlank { null }, gender, birthdayDay, pickedPhoto)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                )
            }
        }
    }

    if (showPicker) {
        val initial = birthdayDay ?: LocalDate.now().minusYears(1).toEpochDay()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        birthdayDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun NumberSettingDialog(
    title: String,
    unit: String,
    initial: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var value by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
        text = {
            Column {
                StepperRow(
                    label = title,
                    valueText = value.toString(),
                    unit = unit,
                    onMinus = { if (value > range.first) value-- },
                    onPlus = { if (value < range.last) value++ },
                    minusEnabled = value > range.first,
                    plusEnabled = value < range.last,
                    showDivider = false,
                )
                Text(
                    "范围 ${range.first}–${range.last} $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = cs.onSurfaceVariant) }
        },
    )
}

@Composable
private fun CycleDialog(
    internal: Int,
    external: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var internalDays by remember { mutableIntStateOf(internal) }
    var externalDays by remember { mutableIntStateOf(external) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text("驱虫周期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
        text = {
            Column {
                StepperRow(
                    label = "内驱",
                    valueText = internalDays.toString(),
                    unit = "天",
                    onMinus = { if (internalDays > 30) internalDays -= 5 },
                    onPlus = { if (internalDays < 180) internalDays += 5 },
                    minusEnabled = internalDays > 30,
                    plusEnabled = internalDays < 180,
                )
                StepperRow(
                    label = "外驱",
                    valueText = externalDays.toString(),
                    unit = "天",
                    onMinus = { if (externalDays > 14) externalDays-- },
                    onPlus = { if (externalDays < 90) externalDays++ },
                    minusEnabled = externalDays > 14,
                    plusEnabled = externalDays < 90,
                    showDivider = false,
                )
                Text(
                    "内驱 30–180 天，外驱 14–90 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(internalDays, externalDays) }) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = cs.onSurfaceVariant) }
        },
    )
}

@Composable
private fun ReminderDialog(
    enabled: Boolean,
    days: Int,
    hour: Int,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var on by remember { mutableStateOf(enabled) }
    var daysAhead by remember { mutableIntStateOf(days) }
    var hourOfDay by remember { mutableIntStateOf(hour) }
    val notificationsAllowed = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text("提醒设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("到期提醒", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                    Switch(
                        checked = on,
                        onCheckedChange = { on = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = cs.primary, checkedThumbColor = Color.White),
                    )
                }
                if (on) {
                    StepperRow(
                        label = "提前天数",
                        valueText = daysAhead.toString(),
                        unit = "天",
                        onMinus = { if (daysAhead > 0) daysAhead-- },
                        onPlus = { if (daysAhead < 14) daysAhead++ },
                        minusEnabled = daysAhead > 0,
                        plusEnabled = daysAhead < 14,
                    )
                    StepperRow(
                        label = "提醒时间",
                        valueText = hourOfDay.toString().padStart(2, '0'),
                        unit = "点",
                        onMinus = { if (hourOfDay > 0) hourOfDay-- },
                        onPlus = { if (hourOfDay < 23) hourOfDay++ },
                        minusEnabled = hourOfDay > 0,
                        plusEnabled = hourOfDay < 23,
                        showDivider = false,
                    )
                }
                if (on && !notificationsAllowed) {
                    Text(
                        "系统通知未开启，提醒无法展示。点此去开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.error,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(on, daysAhead, hourOfDay) }) { Text("保存", color = cs.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = cs.onSurfaceVariant) }
        },
    )
}

@Composable
private fun ProfileAvatar(
    photoFile: String?,
    pickedUri: Uri? = null,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    radius: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, photoFile, pickedUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = when {
                    pickedUri != null -> context.contentResolver.openInputStream(pickedUri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    photoFile != null -> BitmapFactory.decodeFile(
                        File(context.filesDir, "photos/$photoFile").path,
                        opts,
                    )
                    else -> null
                }
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!, contentDescription = "小福头像", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.ic_ms_pets),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = cs.onPrimaryContainer,
            )
        }
    }
}
