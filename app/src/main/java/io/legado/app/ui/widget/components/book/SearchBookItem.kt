package io.legado.app.ui.widget.components.book

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showPadding: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (showPadding) Modifier.adaptiveHorizontalPadding(vertical = 8.dp) else Modifier)
    ) {
        Box(modifier = Modifier
            .width(72.dp)
            .aspectRatio(5f / 7f)) {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                modifier = Modifier.fillMaxSize(),
                sourceOrigin = book.origin,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
                showLoadingPlaceholder = sharedCoverKey == null
            )

            val shelfIcon = when (shelfState) {
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

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            AppText(
                text = book.name,
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row {
                AppText(
                    text = book.author,
                    style = LegadoTheme.typography.bodySmall,
                    maxLines = 1,
                )

                val latestChapter = book.latestChapterTitle
                if (!latestChapter.isNullOrEmpty()) {
                    AppText(
                        text = " • ",
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    AppText(
                        text = "最新: $latestChapter",
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val intro = book.intro?.replace("\\s+".toRegex(), "") ?: ""
            if (intro.isNotEmpty()) {
                AppText(
                    text = intro,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val kinds = book.getKindList()
            if (kinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val lazyListState = rememberLazyListState()
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdge(lazyListState, gradientWidth = 8.dp)
                ) {
                    items(kinds) { kind ->
                        SearchBookTagChip(text = kind)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchBookGridItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f)
        ) {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                modifier = Modifier.fillMaxSize(),
                sourceOrigin = book.origin,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
                showLoadingPlaceholder = sharedCoverKey == null
            )

            val shelfIcon = when (shelfState) {
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            AppText(
                text = book.name,
                style = LegadoTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SearchBookTagChip(
    text: String,
    color: Color = LegadoTheme.colorScheme.surfaceContainerHigh
) {
    GlassCard(
        containerColor = color,
        cornerRadius = 4.dp
    ) {
        AppText(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = LegadoTheme.typography.labelSmallEmphasized,
            color = LegadoTheme.colorScheme.onCardContainer,
        )
    }
}
