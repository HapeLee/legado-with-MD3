package io.legado.app.ui.book.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizonalPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchSourceSection(
    sourceName: String,
    items: List<SearchResultItemUi>,
    onClickBook: (SearchBook, String?) -> Unit,
    onLongClickBook: ((SearchBook, String?) -> Unit)? = null,
    onViewAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sourceSectionIndex: Int = 0,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = sourceName,
                style = LegadoTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextCard(
                text = "${items.size}",
                modifier = Modifier
                    .padding(start = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (onViewAll != null) {
                SmallTonalButton(
                    onClick = onViewAll,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.show_all),
                )
            }
        }

        val lazyListState = rememberLazyListState()
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .fadingEdge(lazyListState, gradientWidth = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = adaptiveHorizonalPadding(),
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${item.book.origin}:${item.book.bookUrl}:$index" }
            ) { index, item ->
                val sharedCoverKey = bookCoverSharedElementKey(
                    item.book.bookUrl,
                    "search_source:$sourceSectionIndex:$index"
                )
                SearchBookGridItem(
                    book = item.book,
                    shelfState = item.shelfState,
                    onClick = { onClickBook(item.book, sharedCoverKey) },
                    onLongClick = onLongClickBook,
                    modifier = Modifier.width(100.dp),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedCoverKey = sharedCoverKey,
                )
            }
        }
    }
}
