package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.hutool.core.date.DateUtil
import io.legado.app.R
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.formatReadDuration
import java.util.Date

@Composable
fun BookReadRecordSheet(
    show: Boolean,
    totalReadTime: Long,
    timelineDays: List<ReadRecordTimelineDay>,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_record),
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary
                )
                Column {
                    AppText(
                        text = stringResource(R.string.all_read_time),
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.primary
                    )
                    AppText(
                        text = formatReadDuration(totalReadTime),
                        style = LegadoTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (timelineDays.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyMessage(message = stringResource(R.string.empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                timelineDays.forEach { day ->
                    item(key = "header_${day.date}") {
                        AppText(
                            text = day.date,
                            style = LegadoTheme.typography.titleSmall,
                            color = LegadoTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(day.sessions, key = { it.id }) { session ->
                        TimelineSessionRow(session = session)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TimelineSessionRow(session: ReadRecordSession) {
    val lineColor = LegadoTheme.colorScheme.surfaceContainerHigh
    val nodeColor = LegadoTheme.colorScheme.primary
    val duration = (session.endTime - session.startTime).coerceAtLeast(0L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 12.dp.toPx()
                val centerY = size.height / 2f
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = nodeColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, centerY)
                )
            }
            .padding(start = 28.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = DateUtil.format(Date(session.endTime), "HH:mm"),
                style = LegadoTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            AppText(
                text = formatReadDuration(duration),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
        if (session.words > 0) {
            TextCard(
                text = "第${session.words}章",
                textStyle = LegadoTheme.typography.labelSmall,
                backgroundColor = LegadoTheme.colorScheme.secondaryContainer,
                contentColor = LegadoTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
