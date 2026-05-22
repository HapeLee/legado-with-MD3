package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BannerModule(
    books: ImmutableList<SearchBook>,
    onClick: (SearchBook, String?) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKeySourceId: String? = null,
) {
    if (books.isEmpty()) return

    val lazyListState = rememberLazyListState()
    LazyRow(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .fadingEdge(lazyListState, gradientWidth = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(books, key = { index, book -> "${book.bookUrl}:$index" }) { index, book ->
            val sharedCoverKey = bookCoverSharedElementKey(
                book.bookUrl,
                sharedCoverKeySourceId?.let { "$it:$index" }
            )
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                radius = 12.dp,
                sourceOrigin = book.origin,
                modifier = Modifier
                    .width(96.dp)
                    .clickable { onClick(book, sharedCoverKey) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey
            )
        }
    }
}
