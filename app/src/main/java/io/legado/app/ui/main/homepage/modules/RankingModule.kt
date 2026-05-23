package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

private const val INITIAL_COUNT = 5
private const val MAX_COUNT = 20

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RankingModule(
    books: ImmutableList<SearchBook>,
    onClick: (SearchBook, String?) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKeySourceId: String? = null,
) {
    var visibleCount by rememberSaveable { mutableIntStateOf(INITIAL_COUNT) }
    val displayBooks = books.take(visibleCount)

    GlassCard(
        modifier = modifier
            .fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .animateContentSize()
        ) {
            // 显示书籍列表
            displayBooks.forEachIndexed { index, book ->
                RankingItem(
                    rank = index + 1,
                    book = book,
                    onClick = onClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedCoverKey = bookCoverSharedElementKey(
                        book.bookUrl,
                        sharedCoverKeySourceId?.let { "$it:$index" }
                    )
                )
            }

            if (books.size > INITIAL_COUNT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            visibleCount =
                                if (visibleCount == INITIAL_COUNT) MAX_COUNT else INITIAL_COUNT
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isExpanded = visibleCount > INITIAL_COUNT
                        AppIcon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = if (isExpanded) LegadoTheme.colorScheme.outline else LegadoTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        AppText(
                            text = if (isExpanded) stringResource(R.string.homepage_collapse) else stringResource(
                                R.string.homepage_show_all
                            ),
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            color = if (isExpanded) LegadoTheme.colorScheme.outline else LegadoTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RankingItem(
    rank: Int,
    book: SearchBook,
    onClick: (SearchBook, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(book, sharedCoverKey) }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = "$rank",
            style = LegadoTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
            color = if (rank <= 3) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(42.dp)
                .padding(start = 2.dp, end = 10.dp),
        )
        SearchBookListItem(
            book = book,
            shelfState = BookShelfState.NOT_IN_SHELF,
            onClick = null,
            showPadding = false,
            modifier = Modifier.weight(1f),
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey
        )
    }
}
