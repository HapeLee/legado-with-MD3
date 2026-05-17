package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.cover.BookshelfCover
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toTimeAgo

/**
 * 通用的书架条目布局组件
 * 支持 列表/网格 模式及 标准/紧凑/仅封面 样式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfItem(
    isGrid: Boolean,
    gridStyle: Int, // 0: Standard, 1: Compact, 2: Cover Only
    isCompact: Boolean, // For List Mode
    cover: @Composable (Modifier) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    titleEnd: @Composable (() -> Unit)? = null,
    subTitle: String? = null,
    desc: String? = null,
    descMaxLines: Int = 1,
    extra: @Composable (RowScope.() -> Unit)? = null,
    columnContent: @Composable (ColumnScope.() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    titleColor: Color? = null,
    descAnnotated: AnnotatedString? = null,
    coverWidth: Int = 84,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val isDark = LegadoTheme.isDark
    val bookshelfCardColor =
        if (isDark) BookshelfConfig.bookshelfCardColorDark else BookshelfConfig.bookshelfCardColor
    val containerColor = if (!isGrid && bookshelfCardColor != 0) {
        Color(bookshelfCardColor)
    } else {
        LegadoTheme.colorScheme.cardContainer
    }

    if (isGrid) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (isSelected) {
                        Modifier.background(LegadoTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth()
                        .aspectRatio(5f / 7f)
                        .then(
                            if (coverShadow) Modifier.shadow(
                                4.dp,
                                RoundedCornerShape(4.dp)
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    cover(Modifier.fillMaxSize())
                    if (gridStyle == 1) {
                        AppText(
                            text = title,
                            style = (if (titleSmallFont) LegadoTheme.typography.labelSmall else LegadoTheme.typography.labelMedium).copy(
                                color = Color.White,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    blurRadius = 4f
                                )
                            ),
                            textAlign = if (titleCenter) TextAlign.Center else TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                                .padding(all = 4.dp)
                        )
                    }
                }

                if (gridStyle == 0) {
                    AppText(
                        text = title,
                        style = if (titleSmallFont) LegadoTheme.typography.labelSmall else LegadoTheme.typography.labelMedium,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (titleCenter) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    } else {
        Column {
            NormalCard(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 8.dp,
                containerColor = if (isSelected) {
                    LegadoTheme.colorScheme.secondaryContainer
                } else {
                    containerColor
                },
                onClick = onClick,
                onLongClick = onLongClick
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .width(coverWidth.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                                .aspectRatio(5f / 7f)
                                .then(
                                    if (coverShadow) Modifier.shadow(
                                        4.dp,
                                        RoundedCornerShape(4.dp)
                                    ) else Modifier
                                )
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            cover(Modifier.fillMaxSize())
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp, bottom = 4.dp, end = 8.dp, start = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        AppText(
                            text = title,
                            style = if (titleColor != null) {
                                LegadoTheme.typography.titleMediumEmphasized.copy(color = titleColor)
                            } else {
                                LegadoTheme.typography.titleMediumEmphasized
                            },
                            maxLines = BookshelfConfig.bookshelfTitleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        titleEnd?.let {
                            Box(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                                it.invoke()
                            }
                        }
                    }
                        subTitle?.let {
                            AppText(
                                text = it,
                                style = LegadoTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isCompact) {
                            if (descAnnotated != null) {
                                AppText(
                                    text = descAnnotated,
                                    style = LegadoTheme.typography.labelSmallEmphasized,
                                    maxLines = descMaxLines,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                desc?.let {
                                    AppText(
                                        text = it,
                                        style = LegadoTheme.typography.labelSmallEmphasized,
                                        maxLines = descMaxLines,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        extra?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                content = it
                            )
                        }
                        columnContent?.invoke(this)
                    }
                }
                bottomContent?.invoke()
            }
            if (BookshelfConfig.bookshelfShowDivider)
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
        }
    }
}

@Composable
fun BookGroupCover(
    books: List<BookUiItem>,
    coverPath: String? = null,
    leftBottomText: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(5f / 7f)
            .clip(RoundedCornerShape(4.dp))
    ) {
        if (!coverPath.isNullOrBlank()) {
            CoilBookCover(
                name = null,
                author = null,
                path = coverPath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.run {
                    if (BookshelfConfig.bookshelfCoverShadow) {
                        background(LegadoTheme.colorScheme.surface)
                    } else {
                        this
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 1.dp),
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            books.getOrNull(0)?.book?.let {
                                CoilBookCover(
                                    name = it.name,
                                    author = it.author,
                                    path = it.getDisplayCover(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            books.getOrNull(1)?.book?.let {
                                CoilBookCover(
                                    name = it.name,
                                    author = it.author,
                                    path = it.getDisplayCover(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            books.getOrNull(2)?.book?.let {
                                CoilBookCover(
                                    name = it.name,
                                    author = it.author,
                                    path = it.getDisplayCover(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                        ) {
                            books.getOrNull(3)?.book?.let {
                                CoilBookCover(
                                    name = it.name,
                                    author = it.author,
                                    path = it.getDisplayCover(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!leftBottomText.isNullOrEmpty()) {
            TextCard(
                text = leftBottomText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp),
                cornerRadius = 4.dp,
                horizontalPadding = 4.dp,
                verticalPadding = 0.dp
            )
        }
    }
}

@Composable
fun BookGroupItemGrid(
    group: BookGroupUi,
    previewBooks: List<BookUiItem>,
    countText: String? = null,
    gridStyle: Int = 0,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    BookshelfItem(
        isGrid = true,
        gridStyle = gridStyle,
        isCompact = false,
        cover = {
            BookGroupCover(
                books = previewBooks,
                coverPath = group.cover,
                leftBottomText = countText,
                modifier = it
            )
        },
        title = group.groupName,
        modifier = modifier,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        coverWidth = 84,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookGroupItemList(
    group: BookGroupUi,
    previewBooks: List<BookUiItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countText: String? = null,
    isCompact: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onBookClick: ((BookShelfItem) -> Unit)? = null
) {
    if (BookshelfConfig.bookshelfGroupListStyle == 2) {
        BookGroupItemHorizontalCovers(
            group = group,
            previewBooks = previewBooks,
            onClick = onClick,
            modifier = modifier,
            countText = countText,
            onLongClick = onLongClick,
            onBookClick = onBookClick
        )
        return
    }
    val firstBookName = previewBooks.firstOrNull()?.book?.name
    val descAnnotated = if (firstBookName != null) {
        buildAnnotatedString {
            append("最近阅读：")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append(firstBookName)
            }
        }
    } else {
        null
    }
    BookshelfItem(
        isGrid = false,
        gridStyle = 0,
        isCompact = BookshelfConfig.bookshelfGroupListStyle == 1 || isCompact,
        cover = { BookGroupCover(books = previewBooks, coverPath = group.cover, modifier = it) },
        title = group.groupName,
        subTitle = countText,
        descAnnotated = descAnnotated,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        coverWidth = BookshelfConfig.bookshelfListCoverWidth,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookGroupItemHorizontalCovers(
    group: BookGroupUi,
    previewBooks: List<BookUiItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countText: String? = null,
    onLongClick: (() -> Unit)? = null,
    onBookClick: ((BookShelfItem) -> Unit)? = null
) {
    Column {
        val isDark = LegadoTheme.isDark
        val bookshelfCardColor =
            if (isDark) BookshelfConfig.bookshelfCardColorDark else BookshelfConfig.bookshelfCardColor
        NormalCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(all = 4.dp),
            cornerRadius = 12.dp,
            containerColor = if (bookshelfCardColor != 0) {
                Color(bookshelfCardColor)
            } else {
                LegadoTheme.colorScheme.cardContainer
            },
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = group.groupName,
                        style = LegadoTheme.typography.titleMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (countText != null) {
                        AppText(
                            text = countText,
                            style = LegadoTheme.typography.labelSmallEmphasized,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppIcon(
                        modifier = Modifier.padding(end = 4.dp),
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "",
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val coverCount = BookshelfConfig.bookshelfGroupCoverCount
                    previewBooks.take(coverCount).forEach { bookUi ->
                        val book = bookUi.book
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(5f / 7f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onBookClick?.invoke(book) }
                        ) {
                            CoilBookCover(
                                name = book.name,
                                author = book.author,
                                path = book.getDisplayCover(),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    repeat(maxOf(0, coverCount - previewBooks.size)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (BookshelfConfig.bookshelfShowDivider)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookItem(
    bookUi: BookUiItem,
    layoutMode: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    gridStyle: Int = 0,
    isCompact: Boolean = false,
    isUpdating: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    isSearchMode: Boolean = false,
    searchKey: String = "",
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val book = bookUi.book
    val unreadCount = book.getUnreadChapterNum()
    val unreadText = if (BookshelfConfig.showUnread && unreadCount > 0) unreadCount.toString() else null
    val bookTypeLabel = if (BookshelfConfig.showTip) {
        when {
            book.isAudio -> stringResource(R.string.audio)
            book.isImage -> stringResource(R.string.manga)
            (book.type and BookType.webFile) > 0 -> stringResource(R.string.web_file)
            book.isLocal -> stringResource(R.string.local)
            else -> stringResource(R.string.noval)
        }
    } else {
        null
    }
    val matchedSourceLabel = if (
        isSearchMode &&
        searchKey.isNotBlank() &&
        book.originName.contains(searchKey, ignoreCase = true)
    ) {
        book.originName
    } else {
        null
    }

    BookshelfItem(
        isGrid = layoutMode != 0,
        gridStyle = gridStyle,
        isCompact = isCompact,
        isSelected = isSelected,
        modifier = modifier,
        titleEnd = if (layoutMode == 0 && unreadText != null) {
            {
                TextCard(
                    text = unreadText,
                    cornerRadius = 4.dp,
                    horizontalPadding = 4.dp,
                    verticalPadding = 0.dp
                )
            }
        } else null,
        cover = { modifier ->
            BookshelfCover(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                isUpdating = isUpdating,
                modifier = modifier,
                coverModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                sourceOrigin = book.origin,
                badgeText = if (layoutMode != 0) unreadText else null,
                showBadgeDot = BookshelfConfig.showUnread && BookshelfConfig.showUnreadNew && book.isNew,
                leftBottomText = matchedSourceLabel ?: bookTypeLabel,
                showLoadingPlaceholder = true,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
            )
        },
        title = book.name,
        subTitle = if (layoutMode == 0 && isCompact) {
            stringResource(R.string.author_read, book.author, unreadCount)
        } else {
            book.author
        },
        desc = book.durChapterTitle ?: "",
        columnContent = if (layoutMode == 0 && !isCompact && BookshelfConfig.showBookIntro) {
            {
                val kindList = bookUi.displayTags
                val intro = book.intro?.takeIf { it.isNotBlank() }
                val customTagColors = if (ThemeConfig.enableCustomTagColors) ThemeConfig.getCustomTagColors() else emptyList()
                if (BookshelfConfig.bookshelfShowTag && kindList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        kindList.forEachIndexed { index, label ->
                            val colorPair = if (customTagColors.isNotEmpty()) {
                                customTagColors[index % customTagColors.size]
                            } else {
                                null
                            }
                            TextCard(
                                text = label,
                                backgroundColor = if (colorPair != null && colorPair.bgColor != 0) Color(colorPair.bgColor) else LegadoTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (colorPair != null && colorPair.textColor != 0) Color(colorPair.textColor) else LegadoTheme.colorScheme.primary.copy(alpha = 0.8f),
                                cornerRadius = 4.dp,
                                horizontalPadding = 4.dp,
                                verticalPadding = 2.dp,
                                textStyle = LegadoTheme.typography.labelSmallEmphasized
                            )
                        }
                    }
                }
                if (BookshelfConfig.bookshelfShowIntro && intro != null) {
                    val maxLines = if (BookshelfConfig.bookshelfIntroMaxLines == 0) Int.MAX_VALUE else BookshelfConfig.bookshelfIntroMaxLines
                    AppText(
                        text = intro,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        } else null,
        bottomContent = null,
        extra = if (layoutMode == 0 && !isCompact && BookshelfConfig.showBookIntro && BookshelfConfig.bookshelfShowLatestChapter) {
            {
                if (BookshelfConfig.showLastUpdateTime && !book.isLocal) {
                    AppText(
                        text = book.latestChapterTime.toTimeAgo(),
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                AppText(
                    text = book.latestChapterTitle ?: "",
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        } else null,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        coverWidth = if (layoutMode == 0) BookshelfConfig.bookshelfListCoverWidth else 84,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
