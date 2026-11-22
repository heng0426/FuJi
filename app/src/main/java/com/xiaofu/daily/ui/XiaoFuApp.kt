package com.xiaofu.daily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaofu.daily.R
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.ui.editor.AnomalyEditor
import com.xiaofu.daily.ui.editor.DewormEditor
import com.xiaofu.daily.ui.editor.EditorTarget
import com.xiaofu.daily.ui.editor.VaccineEditor
import com.xiaofu.daily.ui.editor.WeightEditor
import com.xiaofu.daily.ui.today.TodayScreen
import com.xiaofu.daily.ui.today.TodayViewModel
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
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val accentText = if (isSystemInDarkTheme()) cs.primary else Color(0xFFB5501A)

    val onDelete: (RecordEntity) -> Unit = { record ->
        scope.launch {
            val snapshot = vm.delete(record)
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

    val onMessage: (String) -> Unit = { text ->
        scope.launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        val current = editor
        if (current == null) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    BottomNav(selected = selectedTab, accentText = accentText) { selectedTab = it }
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

                    Tab.CALENDAR -> PlaceholderScreen("日历", "按日期回看与补记\n将在阶段 3 开放", Modifier.padding(innerPadding))
                    Tab.RECORDS -> PlaceholderScreen("记录", "按类型查看历史\n将在阶段 3 开放", Modifier.padding(innerPadding))
                    Tab.PROFILE -> PlaceholderScreen("我的", "档案与设置\n将在阶段 4 开放", Modifier.padding(innerPadding))
                }
            }
        } else {
            when (current) {
                is EditorTarget.Weight -> WeightEditor(vm, current.record) { editor = null }
                is EditorTarget.Deworm -> DewormEditor(vm, current.record) { editor = null }
                is EditorTarget.Vaccine -> VaccineEditor(vm, current.record) { editor = null }
                is EditorTarget.Anomaly -> AnomalyEditor(vm, current.record) { editor = null }
            }
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
