package com.xiaofu.daily.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.xiaofu.daily.ui.theme.LocalAppDarkTheme

/** 列表行的统一点击区域：12dp 圆角、按下可见、支持长按 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.roundedRowClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cs = MaterialTheme.colorScheme
    val tint = if (pressed && LocalAppDarkTheme.current) cs.primary.copy(alpha = 0.16f) else Color.Transparent
    val base = this.clip(shape).background(tint)
    return if (onLongClick == null) {
        base.clickable(
            interactionSource = interaction,
            indication = ripple(color = appRippleColor()),
            onClick = onClick,
        )
    } else {
        base.combinedClickable(
            interactionSource = interaction,
            indication = ripple(color = appRippleColor()),
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
}
