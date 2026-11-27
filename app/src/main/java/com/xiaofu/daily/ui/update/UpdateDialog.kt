package com.xiaofu.daily.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaofu.daily.update.UpdateManifest
import com.xiaofu.daily.update.UpdateState

/**
 * 升级弹窗：按状态切换内容。
 * 不做强制更新，最低限度保留「稍后」与「跳过此版本」。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: (UpdateManifest) -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermission: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onLater,
            containerColor = cs.surface,
            shape = shape,
            title = {
                Text(
                    "发现新版本 ${state.manifest.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "安装包 ${formatSize(state.manifest.sizeBytes)} · 覆盖安装，数据保留",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Changelog(state.manifest.changelog)
                }
            },
            confirmButton = {
                TextButton(onClick = { onInstall(state.manifest) }) {
                    Text("立即更新", color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onSkip) {
                        Text("跳过此版本", color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = onLater) {
                        Text("稍后", color = cs.onSurfaceVariant)
                    }
                }
            },
        )

        is UpdateState.Working -> AlertDialog(
            onDismissRequest = { },
            containerColor = cs.surface,
            shape = shape,
            title = {
                Text(
                    "正在更新到 ${state.manifest.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant,
                    )
                    Text(
                        when (state.phase) {
                            "正在下载" -> "${state.phase} ${state.progress}%"
                            else -> state.phase
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    if (state.phase == "正在下载") {
                        Text(
                            "约 20 MB，请保持网络通畅",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { },
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = onClose,
            containerColor = cs.surface,
            shape = shape,
            title = {
                Text(
                    "还需要一步授权",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "安装包已下载并校验通过。系统要求先允许「小福日常」安装应用：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        "设置 → 应用 → 小福日常 → 安装未知应用 → 允许",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                    Text(
                        "部分机型会先打开应用列表，请在其中找到「小福日常」；必要时关闭「纯净模式」，或在应用信息右上角放开「受限制的设置」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenPermission) {
                    Text("去授权", color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onClose) {
                    Text("取消", color = cs.onSurfaceVariant)
                }
            },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onClose,
            containerColor = cs.surface,
            shape = shape,
            title = {
                Text(
                    "更新未完成",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
            },
            text = {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = onRetry) {
                    Text(
                        if (state.manifest != null) "重试" else "重新检查",
                        color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onClose) {
                    Text("关闭", color = cs.onSurfaceVariant)
                }
            },
        )

        else -> Unit
    }
}

@Composable
private fun Changelog(items: List<String>) {
    val cs = MaterialTheme.colorScheme
    if (items.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                )
            }
        }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    val mb = bytes / 1024.0 / 1024.0
    return String.format(java.util.Locale.CHINA, "%.1f MB", mb)
}
