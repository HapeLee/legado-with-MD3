package io.legado.app.ui.book.readRecord.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.readRecord.ReadRecordFormatter
import io.legado.app.ui.book.readRecord.ReadRecordUiState
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import java.time.format.DateTimeFormatter

@Composable
fun SummarySection(
    state: ReadRecordUiState,
    viewModel: ReadRecordViewModel,
    onSummaryClick: () -> Unit
) {
    val selectedDate = state.selectedDate

    if (selectedDate != null) {
        val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dailyDetails = state.groupedRecords[dateKey] ?: emptyList()

        if (dailyDetails.isNotEmpty()) {
            val distinctBooks = dailyDetails.map { it.bookName to it.bookAuthor }.distinct()
            val dailyTime = dailyDetails.sumOf { it.readTime }

            ReadingSummaryCard(
                title = selectedDate.format(DateTimeFormatter.ofPattern("M月d日阅读概览")),
                bookCount = distinctBooks.size,
                totalTimeMillis = dailyTime,
                bookNamesForCover = distinctBooks.take(3),
                viewModel = viewModel,
                onClick = onSummaryClick
            )
        }
    } else {
        val allBooksCount = state.latestRecords.size
        val totalTime = state.totalReadTime

        if (allBooksCount > 0) {
            ReadingSummaryCard(
                title = "累计阅读成就",
                bookCount = allBooksCount,
                totalTimeMillis = totalTime,
                bookNamesForCover = state.latestRecords.take(5).map { it.bookName to it.bookAuthor },
                viewModel = viewModel,
                onClick = onSummaryClick
            )
        }
    }
}

@Composable
fun ReadingSummaryCard(
    title: String,
    bookCount: Int,
    totalTimeMillis: Long,
    bookNamesForCover: List<Pair<String, String>>,
    viewModel: ReadRecordViewModel,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .adaptiveHorizontalPadding(vertical = 8.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(title, style = LegadoTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = "共阅读 $bookCount 本书，时长 ${ReadRecordFormatter.formatDuration(totalTimeMillis)}",
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }
            
            BookStackView(
                bookNamesForCover = bookNamesForCover,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun BookStackView(
    bookNamesForCover: List<Pair<String, String>>,
    viewModel: ReadRecordViewModel
) {
    Box(contentAlignment = Alignment.CenterEnd) {
        bookNamesForCover.reversed().forEachIndexed { index, (name, author) ->
            var coverPath by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(name, author) {
                coverPath = viewModel.getBookCover(name, author)
            }
            
            CoilBookCover(
                name = name,
                author = author,
                path = coverPath,
                modifier = Modifier
                    .padding(end = (index * 12).dp)
                    .width(32.dp)
            )
        }
    }
}
