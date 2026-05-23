package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

/**
 * 卡片模块：横向滚动的推荐卡片
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CardModule(
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
            .fadingEdge(lazyListState, gradientWidth = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(books, key = { index, book -> "${book.bookUrl}:$index" }) { index, book ->
            val sharedCoverKey = bookCoverSharedElementKey(
                book.bookUrl,
                sharedCoverKeySourceId?.let { "$it:$index" }
            )
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LegadoTheme.colorScheme.surfaceContainerLow)
                    .clickable { onClick(book, sharedCoverKey) }
            ) {
                CoilBookCover(
                    name = book.name,
                    author = book.author,
                    path = book.coverUrl,
                    radius = 16.dp,
                    sourceOrigin = book.origin,
                    modifier = Modifier
                        .wrapContentWidth(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedCoverKey = sharedCoverKey
                )

                AppText(
                    text = book.name,
                    style = LegadoTheme.typography.labelLargeEmphasized,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                )

                val intro = book.intro?.takeIf { it.isNotBlank() }
                    ?.replace("\\s+".toRegex(), " ")
                if (intro != null) {
                    AppText(
                        text = intro,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
