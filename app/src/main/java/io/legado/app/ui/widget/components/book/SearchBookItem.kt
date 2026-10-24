package io.legado.app.ui.widget.components.book

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.image.cover.cachedCoverAspectRatio
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.HtmlFormatter

private const val DEFAULT_SEARCH_BOOK_COVER_ASPECT_RATIO = 5f / 7f
private const val SEARCH_BOOK_COVER_WIDTH_DP = 72f
private const val MIN_SEARCH_BOOK_COVER_HEIGHT_DP = 80f
private const val MAX_SEARCH_BOOK_COVER_HEIGHT_DP = 140f

internal fun searchBookCoverHeightDp(aspectRatio: Float): Float {
    val safeAspectRatio =
        if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio
        else DEFAULT_SEARCH_BOOK_COVER_ASPECT_RATIO
    return (SEARCH_BOOK_COVER_WIDTH_DP / safeAspectRatio)
        .coerceIn(MIN_SEARCH_BOOK_COVER_HEIGHT_DP, MAX_SEARCH_BOOK_COVER_HEIGHT_DP)
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchBookListItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: (() -> Unit)?,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showPadding: Boolean = true,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    sourceCount: Int? = null,
    qualityMasked: Boolean = false,
    adaptCoverAspectRatio: Boolean = false,
) {
    // 调用点开启自适应且“限制封面比例”设置关闭时才按原图比例计算高度；
    // 限制态回到固定 5:7，与旧版行为完全一致
    val allowCoverRatioAdapt = adaptCoverAspectRatio &&
        !LocalAppUiConfiguration.current.cover.restrictCoverRatio
    // 初始值优先取该封面已加载过的比例缓存：列表项滚出视口销毁再回来时
    // 直接落在最终高度，不先回弹 5:7 再等内存缓存命中
    val coverAspectRatioState = remember(allowCoverRatioAdapt, book.coverUrl) {
        mutableFloatStateOf(
            if (allowCoverRatioAdapt) {
                cachedCoverAspectRatio(book.coverUrl) ?: DEFAULT_SEARCH_BOOK_COVER_ASPECT_RATIO
            } else {
                DEFAULT_SEARCH_BOOK_COVER_ASPECT_RATIO
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick?.let { cb -> { cb(book, sharedCoverKey) } }
                ) else Modifier
            )
            .then(if (showPadding) Modifier.adaptiveHorizontalPadding(vertical = 8.dp) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(SEARCH_BOOK_COVER_WIDTH_DP.dp)
                .then(
                    if (allowCoverRatioAdapt) {
                        Modifier.height(searchBookCoverHeightDp(coverAspectRatioState.floatValue).dp)
                    } else {
                        Modifier.aspectRatio(DEFAULT_SEARCH_BOOK_COVER_ASPECT_RATIO)
                    }
                )
        ) {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.coverUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (qualityMasked) Modifier.blur(8.dp) else Modifier),
                sourceOrigin = book.origin,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
                showLoadingPlaceholder = sharedCoverKey == null,
                onImageAspectRatio = if (allowCoverRatioAdapt) {
                    // 比例合法性已在 BookCoverImage 上报前校验，这里直接写入
                    { imageAspectRatio -> coverAspectRatioState.floatValue = imageAspectRatio }
                } else null
            )

            if (qualityMasked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LegadoTheme.colorScheme.scrim.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = stringResource(R.string.content_quality_excluded),
                        color = LegadoTheme.colorScheme.onSurface,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = book.name,
                    modifier = Modifier.weight(1f),
                    style = LegadoTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                sourceCount?.let { count ->
                    Spacer(modifier = Modifier.width(8.dp))
                    TextCard(
                        text = stringResource(R.string.search_book_source_count, count),
                        cornerRadius = 4.dp,
                        horizontalPadding = 4.dp,
                        verticalPadding = 2.dp
                    )
                }
            }

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

            val intro = remember(book.intro) { HtmlFormatter.formatSummaryText(book.intro) }
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

            val kinds = remember(book.wordCount, book.kind) { book.getKindList() }
            if (kinds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdge(scrollState, gradientWidth = 8.dp)
                        .horizontalScroll(scrollState)
                ) {
                    kinds.forEach { kind ->
                        SearchBookTagChip(text = kind)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchBookGridItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    qualityMasked: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { cb(book, sharedCoverKey) } }
            )
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
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (qualityMasked) Modifier.blur(8.dp) else Modifier),
                sourceOrigin = book.origin,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
                showLoadingPlaceholder = sharedCoverKey == null
            )

            if (qualityMasked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LegadoTheme.colorScheme.scrim.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = stringResource(R.string.content_quality_excluded),
                        color = LegadoTheme.colorScheme.onSurface,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        modifier = Modifier.padding(horizontal = 2.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

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
