package com.xiaofu.daily.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.R
import com.xiaofu.daily.ui.components.PrimaryButton
import com.xiaofu.daily.ui.components.SecondaryButton
import com.xiaofu.daily.ui.components.SheetHandle
import com.xiaofu.daily.update.UpdateManifest
import com.xiaofu.daily.update.UpdateState

/**
 * 「关于」底部弹层：版本信息、手动检查、每日提醒开关。
 * 检查状态就地显示，不额外弹 toast，避免和升级弹窗抢注意力。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    versionName: String,
    currentVersionCode: Long,
    state: UpdateState,
    known: UpdateManifest?,
    notifyEnabled: Boolean,
    lastCheckAt: Long,
    onToggleNotify: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onInstall: (UpdateManifest) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val checking = state is UpdateState.Checking
    val pending = known?.takeIf { it.versionCode > currentVersionCode }
    val lastCheckLabel = if (lastCheckAt <= 0L) {
        "尚未检查"
    } else {
        "上次检查 ${checkTime(lastCheckAt)}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SheetHandle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ms_pets),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = cs.onPrimaryContainer,
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        "小福日常",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Text(
                        "版本 $versionName",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (checking) Modifier else Modifier.clickable(onClick = onCheck))
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ms_restore),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = cs.onSurfaceVariant,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        "检查更新",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface,
                    )
                    Text(
                        lastCheckLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    statusText(state, pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending != null) cs.primary else cs.onSurfaceVariant,
                )
            }

            Box(Modifier.fillMaxWidth().size(1.dp).background(cs.outline))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "每日检查并提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface,
                    )
                    Text(
                        "每天 10:00 检查一次，发现新版本时通知；可随时关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = notifyEnabled,
                    onCheckedChange = onToggleNotify,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Text(
                "记录与照片只保存在本机，不会上传；仅在检查更新时访问 GitHub 获取版本信息。",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            Column(Modifier.padding(top = 18.dp, bottom = 20.dp)) {
                if (pending != null) {
                    PrimaryButton(
                        text = "更新到 ${pending.versionName}",
                        onClick = { onInstall(pending) },
                    )
                } else {
                    SecondaryButton(text = "关闭", onClick = onDismiss)
                }
            }
        }
    }
}

private fun statusText(state: UpdateState, pending: UpdateManifest?): String = when (state) {
    is UpdateState.Checking -> "检查中…"
    is UpdateState.UpToDate -> "已是最新"
    is UpdateState.Available -> "发现 ${state.manifest.versionName}"
    is UpdateState.Working -> state.phase
    is UpdateState.NeedsPermission -> "待授权"
    is UpdateState.Failed -> "失败，可重试"
    else -> pending?.let { "可更新 ${it.versionName}" } ?: " "
}

private fun checkTime(at: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(at))
