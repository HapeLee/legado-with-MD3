package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
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
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GridRankingModule(
    books: ImmutableList<SearchBook>,
    onClick: (SearchBook, String?) -> Unit,
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
                for ((rowIndex, book) in page.withIndex()) {
                    val itemIndex = pageIndex * rows + rowIndex
                    val sharedCoverKey = bookCoverSharedElementKey(
                        book.bookUrl,
                        sharedCoverKeySourceId?.let { "$it:$itemIndex" }
                    )
                    GridRankingItem(
                        rank = pages.flatten().indexOf(book) + 1,
                        book = book,
                        onClick = { onClick(book, sharedCoverKey) },
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GridRankingItem(
    rank: Int,
    book: SearchBook,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. 封面
        CoilBookCover(
            name = book.name,
            author = book.author,
            path = book.coverUrl,
            sourceOrigin = book.origin,
            modifier = Modifier.width(48.dp),
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey
        )

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
