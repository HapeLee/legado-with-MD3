package io.legado.app.ui.widget.components.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchBookPreviewSheet(
    data: SearchBook?,
    shelfState: BookShelfState? = null,
    sharedCoverKey: String? = null,
    onDismissRequest: () -> Unit,
    onOpenDetail: (SearchBook, String?) -> Unit,
    onAddToShelf: (SearchBook) -> Unit,
) {
    AppModalBottomSheet(
        data = data,
        onDismissRequest = onDismissRequest,
    ) { book ->
        val isInShelf = shelfState == BookShelfState.IN_SHELF
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(5f / 7f)
                ) {
                    CoilBookCover(
                        name = book.name,
                        author = book.author,
                        path = book.coverUrl,
                        sourceOrigin = book.origin,
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(5f / 7f),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppText(
                        text = book.name,
                        style = LegadoTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (book.author.isNotBlank()) {
                        AppText(
                            text = book.author,
                            style = LegadoTheme.typography.bodyLarge,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (book.originName.isNotBlank()) {
                        AppText(
                            text = book.originName,
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val latestChapter = book.latestChapterTitle
                    if (!latestChapter.isNullOrBlank()) {
                        AppText(
                            text = latestChapter,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (book.time > 0) {
                        val dateFormat = remember {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        }
                        AppText(
                            text = dateFormat.format(Date(book.time)),
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }

            val kinds = book.getKindList()
            if (kinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val lazyListState = rememberLazyListState()
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdge(lazyListState, gradientWidth = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(kinds) { kind ->
                        SearchBookTagChip(text = kind)
                    }
                }
            }

            val intro = book.intro?.replace("\\s+".toRegex(), " ")?.trim()
            if (!intro.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppText(
                        text = intro,
                        style = LegadoTheme.typography.labelMediumEmphasized,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ConfirmDismissButtonsRow(
                onDismiss = { onOpenDetail(book, sharedCoverKey) },
                onConfirm = { onAddToShelf(book) },
                dismissText = stringResource(R.string.book_info),
                confirmText = if (isInShelf) stringResource(R.string.already_in_bookshelf)
                else stringResource(R.string.add_to_bookshelf),
                confirmEnabled = !isInShelf,
            )
        }
    }
}
