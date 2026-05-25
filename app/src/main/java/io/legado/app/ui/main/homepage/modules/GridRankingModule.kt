package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GridRankingModule(
    books: ImmutableList<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    rows: Int = 4,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKeySourceId: String? = null,
) {
    if (books.isEmpty()) return
    // 限制最多显示 20 项
    val limitedBooks = books.take(20)
    val pages = limitedBooks.chunked(rows)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    HorizontalPager(
        state = pagerState,
        // 由于父容器已经有 16.dp padding，这里 start 设为 0
        contentPadding = PaddingValues(start = 0.dp, end = 100.dp),
        pageSpacing = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .fadingEdge(pagerState, gradientWidth = 16.dp),
    ) { pageIndex ->
        val page = pages[pageIndex]
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            // 使用 MD3 标准容器色，增加微妙的深度感
            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            cornerRadius = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                for ((rowIndex, item) in page.withIndex()) {
                    val itemIndex = pageIndex * rows + rowIndex
                    val sharedCoverKey = bookCoverSharedElementKey(
                        item.book.bookUrl,
                        sharedCoverKeySourceId?.let { "$it:$itemIndex" }
                    )
                    GridRankingItem(
                        rank = pages.flatten().indexOf(item) + 1,
                        item = item,
                        onClick = { onClick(item.book, sharedCoverKey) },
                        onLongClick = onLongClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = sharedCoverKey,
                    )
                }
                // 占位逻辑
                repeat(rows - page.size) {
                    Spacer(modifier = Modifier.height(76.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GridRankingItem(
    rank: Int,
    item: HomepageBookItemUi,
    onClick: () -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val book = item.book
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { cb(book, sharedCoverKey) } }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. 封面
        Box {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                sourceOrigin = book.origin,
                modifier = Modifier.width(48.dp),
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
                        .padding(2.dp),
                    cornerRadius = 4.dp,
                    horizontalPadding = 2.dp,
                    verticalPadding = 2.dp
                )
            }
        }

        // 2. 排名
        AppText(
            text = "$rank",
            style = LegadoTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
            color = if (rank <= 3) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.outline,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        // 3. 文字信息
        Column(
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f)
        ) {
            AppText(
                text = book.name,
                style = LegadoTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subTitle = buildString {
                append(book.kind?.split(",")?.firstOrNull() ?: "")
                if (book.author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.author)
                }
            }
            AppText(
                text = subTitle,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
