package com.xiaofu.daily.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaofu.daily.R

/** 多选顶栏：固定吸顶，展示已选数量与取消/删除 */
@Composable
fun SelectionTopBar(
    count: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已选 $count 条",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text("取消", color = cs.onSurfaceVariant) }
        RoundIconButton(
            R.drawable.ic_ms_delete,
            onClick = onDelete,
            tint = cs.error,
        )
    }
}
