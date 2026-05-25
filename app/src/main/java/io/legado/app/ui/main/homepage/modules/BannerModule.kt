package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun BannerModule(
    books: ImmutableList<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
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
        itemsIndexed(books, key = { index, item -> "${item.book.bookUrl}:$index" }) { index, item ->
            val book = item.book
            val sharedCoverKey = bookCoverSharedElementKey(
                book.bookUrl,
                sharedCoverKeySourceId?.let { "$it:$index" }
            )
            Box {
                CoilBookCover(
                    name = book.name,
                    author = book.author,
                    path = book.coverUrl,
                    radius = 12.dp,
                    sourceOrigin = book.origin,
                    modifier = Modifier
                        .width(96.dp)
                        .combinedClickable(
                            onClick = { onClick(book, sharedCoverKey) },
                            onLongClick = onLongClick?.let { cb -> { cb(book, sharedCoverKey) } }
                        )
                        .then(
                            with(sharedTransitionScope) {
                                if (this != null) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState("preview:${book.bookUrl}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                            ?: return@with Modifier,
                                    )
                                } else Modifier
                            }
                        ),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedCoverKey = sharedCoverKey,
                )

                val shelfIcon = when (item.shelfState) {
                    BookShelfState.IN_SHELF -> Icons.Default.Check
                    BookShelfState.SAME_NAME_AUTHOR -> Icons.Default.Shuffle
                    else -> null
                }
                if (shelfIcon != null) {
                    TextCard(
                        icon = shelfIcon,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        cornerRadius = 4.dp,
                        horizontalPadding = 2.dp,
                        verticalPadding = 2.dp
                    )
                }
            }
        }
    }
}
