package com.xiaofu.daily.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaofu.daily.AppContainer
import com.xiaofu.daily.R
import com.xiaofu.daily.data.backup.BackupFormatException
import com.xiaofu.daily.data.backup.BackupManager
import com.xiaofu.daily.ui.components.CardSurface
import com.xiaofu.daily.widget.WidgetUpdater
import kotlinx.coroutines.launch
import org.json.JSONException

/** 「我的」页备份卡：导出备份（SAF 保存 ZIP）/ 从备份恢复（SAF 选择 ZIP，替换式恢复） */
@Composable
fun BackupSection(onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busyAction by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busyAction = "export"
        scope.launch {
            val result = runCatching { AppContainer.backup.exportTo(uri) }
            busyAction = null
            result.fold(
                onSuccess = { stats ->
                    val missing = if (stats.missingPhotos > 0) "（${stats.missingPhotos} 张照片文件缺失）" else ""
                    onMessage("已导出 ${stats.records} 条记录、${stats.photos} 张照片$missing")
                },
                onFailure = { e ->
                    // 导出中途失败时清掉残留的半截文件
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    onMessage("导出失败：${e.userMessage()}")
                },
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busyAction = "import"
        scope.launch {
            val result = runCatching { AppContainer.backup.importFrom(uri) }
            busyAction = null
            result.fold(
                onSuccess = { stats ->
                    val missing = if (stats.missingPhotos > 0) "（${stats.missingPhotos} 张照片文件缺失）" else ""
                    onMessage("已恢复 ${stats.records} 条记录、${stats.photos} 张照片$missing")
                    WidgetUpdater.refreshAllAsync(context)
                },
                onFailure = { e ->
                    onMessage("恢复失败：${e.userMessage()}")
                },
            )
        }
    }

    CardSurface(contentPadding = 0.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
            BackupRow(
                iconRes = R.drawable.ic_ms_backup,
                title = "导出备份",
                value = "记录、档案、设置与照片打包保存",
                busy = busyAction == "export",
                enabled = busyAction == null,
                onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
            BackupRow(
                iconRes = R.drawable.ic_ms_restore,
                title = "从备份恢复",
                value = "选择备份文件，替换当前全部数据",
                busy = busyAction == "import",
                enabled = busyAction == null,
                onClick = { confirmRestore = true },
            )
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(22.dp),
            title = { Text("从备份恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface) },
            text = {
                Text(
                    "恢复将替换当前全部数据（记录、档案、设置与照片），且无法撤销。建议先「导出备份」保存当前数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("继续", color = cs.primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消", color = cs.onSurfaceVariant) }
            },
        )
    }
}

@Composable
private fun BackupRow(
    iconRes: Int,
    title: String,
    value: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(17.dp), tint = cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = cs.primary,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_ms_chevron_right),
                null,
                Modifier.padding(start = 3.dp).size(17.dp),
                tint = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun Throwable.userMessage(): String = when (this) {
    is BackupFormatException -> message ?: "备份文件无效"
    is JSONException -> "备份文件格式不正确"
    else -> message?.takeIf { it.isNotBlank() } ?: "未知错误"
}
