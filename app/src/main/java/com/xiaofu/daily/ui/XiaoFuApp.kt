package com.xiaofu.daily.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.ui.editor.AnomalyEditor
import com.xiaofu.daily.ui.editor.DewormEditor
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.editor.VaccineEditor
import com.xiaofu.daily.ui.editor.VetEditor
import com.xiaofu.daily.ui.editor.WeightEditor
import com.xiaofu.daily.ui.calendar.CalendarScreen
import com.xiaofu.daily.ui.calendar.CalendarViewModel
import com.xiaofu.daily.ui.components.LocalPhotoViewerHost
import com.xiaofu.daily.ui.components.PhotoViewerHost
import com.xiaofu.daily.ui.components.PhotoViewerHostOverlay
import com.xiaofu.daily.ui.records.AnomalyGalleryOverlay
import com.xiaofu.daily.ui.records.GalleryHost
import com.xiaofu.daily.ui.records.LocalGalleryHost
import com.xiaofu.daily.ui.records.RecordsScreen
import com.xiaofu.daily.ui.profile.ProfileScreen
import com.xiaofu.daily.ui.profile.ProfileViewModel
import com.xiaofu.daily.ui.records.RecordsViewModel
import com.xiaofu.daily.ui.today.TodayScreen
import com.xiaofu.daily.ui.theme.LocalAppDarkTheme
import com.xiaofu.daily.ui.today.TodayViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val iconRes: Int) {
    TODAY("今日", R.drawable.ic_ms_home),
    CALENDAR("日历", R.drawable.ic_ms_calendar_month),
    RECORDS("记录", R.drawable.ic_ms_list),
    PROFILE("我的", R.drawable.ic_ms_person),
}

@Composable
fun XiaoFuApp() {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    val viewerHost = remember { PhotoViewerHost() }
    val galleryHost = remember { GalleryHost() }
    var pendingRecord by remember { mutableStateOf<RecordEntity?>(null) }
    val viewerVisible = viewerHost.request != null
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val calendarVm: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)
    val recordsVm: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory)
    val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accentText = if (LocalAppDarkTheme.current) cs.primary else Color(0xFFB5501A)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("xiaofu_prefs", Context.MODE_PRIVATE) }
    var showGuide by remember { mutableStateOf(!prefs.getBoolean("onboarding_done", false)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            val prefs = context.getSharedPreferences("xiaofu_prefs", Context.MODE_PRIVATE)
            if (!granted && !prefs.getBoolean("notif_asked", false)) {
                prefs.edit().putBoolean("notif_asked", true).apply()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val onDelete: (RecordEntity) -> Unit = { record ->
        scope.launch {
            val snapshot = vm.delete(record)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "已删除该记录",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && snapshot != null) {
                vm.restore(snapshot)
            }
        }
    }

    // 只保留最新一条提示：新消息到达即取消上一条，避免快速操作造成积压与延迟
    val messages = remember {
        MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(Unit) {
        messages.collectLatest { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }
    val onMessage: (String) -> Unit = { messages.tryEmit(it) }

    if (showGuide && editor == null) {
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putBoolean("onboarding_done", true).apply()
                showGuide = false
            },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("欢迎使用小福日常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideRow(R.drawable.ic_ms_water_drop, "点一下「喝水」或「喂食」，3 秒记一笔")
                    GuideRow(R.drawable.ic_ms_calendar_month, "日历可回看，也能补记过去的记录")
                    GuideRow(R.drawable.ic_ms_notifications, "驱虫、疫苗到期前会提醒你")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("onboarding_done", true).apply()
                    showGuide = false
                }) { Text("开始使用", color = cs.primary, fontWeight = FontWeight.SemiBold) }
            },
        )
    }

    CompositionLocalProvider(
        LocalPhotoViewerHost provides viewerHost,
        LocalGalleryHost provides galleryHost,
    ) {
    Box(Modifier.fillMaxSize().background(cs.background)) {
        val current = editor
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (viewerVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(24.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
        if (current == null) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    BottomNav(selected = selectedTab, accentText = accentText) { selectedTab = it }
                },
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) { data ->
                        XiaoFuSnackbar(data, accentText)
                    }
                },
            ) { innerPadding ->
                when (selectedTab) {
                    Tab.TODAY -> TodayScreen(
                        vm = vm,
                        onOpenEditor = { editor = it },
                        onDeleteRecord = onDelete,
                        onMessage = onMessage,
                        modifier = Modifier.padding(innerPadding),
                    )

                    Tab.CALENDAR -> CalendarScreen(
                        vm = calendarVm,
                        actions = vm,
                        onOpenEditor = { editor = it },
                        onDeleteRecord = onDelete,
                        modifier = Modifier.padding(innerPadding),
                    )

                    Tab.RECORDS -> RecordsScreen(
                        vm = recordsVm,
                        actions = vm,
                        onOpenEditor = { editor = it },
                        onDeleteRecord = onDelete,
                        openRecord = pendingRecord,
                        onOpenRecordHandled = { pendingRecord = null },
                        modifier = Modifier.padding(innerPadding),
                    )
                    Tab.PROFILE -> ProfileScreen(
                        vm = profileVm,
                        modifier = Modifier.padding(innerPadding),
                        onMessage = onMessage,
                    )
                }
            }
        } else {
            when (current) {
                is EditorTarget.Weight -> WeightEditor(vm, current.record, current.occurredAtMs) { editor = null }
                is EditorTarget.Deworm -> DewormEditor(vm, current.record, current.occurredAtMs) { editor = null }
                is EditorTarget.Vaccine -> VaccineEditor(vm, current.record, current.occurredAtMs) { editor = null }
                is EditorTarget.Anomaly -> AnomalyEditor(vm, current.record, current.occurredAtMs) { editor = null }
                is EditorTarget.Vet -> VetEditor(vm, current.record, current.occurredAtMs) { editor = null }
            }
        }

            if (galleryHost.visible) {
                AnomalyGalleryOverlay(
                    onDismiss = { galleryHost.dismiss() },
                    onOpenRecord = { record ->
                        galleryHost.dismiss()
                        pendingRecord = record
                        selectedTab = Tab.RECORDS
                    },
                )
            }
        }
        PhotoViewerHostOverlay()
    }
    }
}

@Composable
private fun BottomNav(selected: Tab, accentText: Color, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surface)) {
        Box(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(tab) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .fillMaxWidth(0.66f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) cs.primaryContainer else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(tab.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (active) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            )
                        }
                        Text(
                            tab.label,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) accentText else cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, hint: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

/** 自定义提示条：奶白卡片 + 描边 + 橘色操作，替代 Material 默认深色 Snackbar */
@Composable
private fun XiaoFuSnackbar(data: SnackbarData, accent: Color) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.outline, shape)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        data.visuals.actionLabel?.let { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { data.performAction() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (data.visuals.withDismissAction) {
            Icon(
                painterResource(R.drawable.ic_ms_close),
                contentDescription = "关闭",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { data.dismiss() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideRow(iconRes: Int, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
