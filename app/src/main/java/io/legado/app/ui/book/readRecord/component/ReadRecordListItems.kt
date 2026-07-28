package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.ui.book.readRecord.ReadRecordFormatter
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.readRecord.TimelineItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.StringUtils.formatFriendlyDate

@Composable
fun LatestReadItem(
    record: ReadRecord,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(record.bookName, record.bookAuthor) {
        coverPath = viewModel.getBookCover(record.bookName, record.bookAuthor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = record.bookName,
            author = record.bookAuthor,
            path = coverPath,
            modifier = Modifier.size(48.dp, 64.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = record.bookName,
                style = LegadoTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            AppText(
                text = record.bookAuthor,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
            AppText(
                text = "最后阅读: ${formatFriendlyDate(record.lastRead.toString())}",
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        AppText(
            text = ReadRecordFormatter.formatDuration(record.readTime),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TimelineSessionItem(
    item: TimelineItem,
    viewModel: ReadRecordViewModel,
    onBookClick: (String, String) -> Unit
) {
    val session = item.session
    var coverPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.bookName, session.bookAuthor) {
        coverPath = viewModel.getBookCover(session.bookName, session.bookAuthor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBookClick(session.bookName, session.bookAuthor) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = session.bookName,
            author = session.bookAuthor,
            path = coverPath,
            modifier = Modifier.size(40.dp, 56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = session.bookName,
                style = LegadoTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppText(
                text = buildAnnotatedString {
                    append(formatFriendlyDate(session.startTime.toString()))
                    append(" · ")
                    append(ReadRecordFormatter.formatDuration(session.endTime - session.startTime))
                },
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReadRecordItem(
    detail: ReadRecordDetail,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detail.bookName, detail.bookAuthor) {
        coverPath = viewModel.getBookCover(detail.bookName, detail.bookAuthor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = detail.bookName,
            author = detail.bookAuthor,
            path = coverPath,
            modifier = Modifier.size(40.dp, 56.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = detail.bookName,
                style = LegadoTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppText(
                text = buildAnnotatedString {
                    append(detail.bookAuthor)
                    if (detail.readWords > 0) {
                        append(" · ")
                        append(ReadRecordFormatter.formatWords(detail.readWords))
                    }
                },
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
        AppText(
            text = ReadRecordFormatter.formatDuration(detail.readTime),
            style = LegadoTheme.typography.bodyMedium,
            color = LegadoTheme.colorScheme.primary
        )
    }
}

@Composable
fun DateHeader(
    date: String,
    totalTimeMillis: Long? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppText(
            text = date,
            style = LegadoTheme.typography.titleSmall,
            color = LegadoTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (totalTimeMillis != null) {
            AppText(
                text = ReadRecordFormatter.formatDuration(totalTimeMillis),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
