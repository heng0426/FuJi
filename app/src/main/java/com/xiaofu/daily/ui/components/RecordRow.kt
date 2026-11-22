package com.xiaofu.daily.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xiaofu.daily.data.local.entity.RecordEntity
import com.xiaofu.daily.ui.format.Fmt
import com.xiaofu.daily.ui.today.recordDisplay
import java.time.LocalDate

/** 记录行（今日 / 日历 / 记录三页共用） */
@Composable
fun RecordRow(
    record: RecordEntity,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val display = recordDisplay(record)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (showDivider) DashedDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeIcon(display.iconRes, display.fg, display.bg)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append(display.title)
                        if (display.value.isNotBlank()) {
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(display.value) }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                display.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                Fmt.time(record.occurredAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun DayGroupHeader(date: LocalDate, count: Int) {
    Text(
        "${Fmt.dayLabel(date)} · $count 条",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun DashedDivider() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}
