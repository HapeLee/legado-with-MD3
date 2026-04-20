package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.cover.BookshelfCover
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
    subTitle: String? = null,
    desc: String? = null,
    extra: @Composable (RowScope.() -> Unit)? = null,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectedColor = if (isSelected) {
        LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    if (isGrid) {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(selectedColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(5f / 7f)
                        .then(
                            if (coverShadow) Modifier.shadow(
                                4.dp,
                                MaterialTheme.shapes.extraSmall
                            ) else Modifier
                        )
                        .clip(MaterialTheme.shapes.extraSmall) // 先阴影后裁剪
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
                                .padding(horizontal = 6.dp, vertical = 6.dp)
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
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    } else {
        // 列表布局
        Column {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(selectedColor)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cover(
                    Modifier
                        .width(if (!isCompact) 80.dp else 56.dp)
                        .padding(end = 12.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = if (!isCompact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subTitle?.let {
                        AppText(
                            text = it,
                            style = LegadoTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (!isCompact) {
                        desc?.let {
                            AppText(
                                text = it,
                                style = LegadoTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    extra?.let {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            content = it
                        )
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
}

@Composable
fun BookGroupCover(
    books: List<BookShelfItem>,
    coverPath: String? = null,
    leftBottomText: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(5f / 7f)
            .clip(RoundedCornerShape(4.dp))
            .background(LegadoTheme.colorScheme.surfaceContainer)
    ) {
        if (!coverPath.isNullOrBlank()) {
            CoilBookCover(
                name = null,
                author = null,
                path = coverPath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                    ) {
                        books.getOrNull(0)?.let {
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
                        books.getOrNull(1)?.let {
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
                        books.getOrNull(2)?.let {
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
                        books.getOrNull(3)?.let {
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

        if (!leftBottomText.isNullOrEmpty()) {
            TextCard(
                text = leftBottomText,
                backgroundColor = LegadoTheme.colorScheme.cardContainer,
                contentColor = LegadoTheme.colorScheme.onCardContainer,
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
    group: BookGroup,
    previewBooks: List<BookShelfItem>,
    countText: String? = null,
    gridStyle: Int = 0,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookGroupItemList(
    group: BookGroup,
    previewBooks: List<BookShelfItem>,
    countText: String? = null,
    isCompact: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    BookshelfItem(
        isGrid = false,
        gridStyle = 0,
        isCompact = isCompact,
        cover = { BookGroupCover(books = previewBooks, coverPath = group.cover, modifier = it) },
        title = group.groupName,
        subTitle = countText,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookItem(
    book: BookShelfItem,
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val unreadCount = book.getUnreadChapterNum()
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

    BookshelfItem(
        isGrid = layoutMode != 0,
        gridStyle = gridStyle,
        isCompact = isCompact,
        isSelected = isSelected,
        modifier = modifier,
        cover = { modifier ->
            BookshelfCover(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                isUpdating = isUpdating,
                modifier = modifier,
                badgeText = if (BookshelfConfig.showUnread && unreadCount > 0) unreadCount.toString() else null,
                showBadgeDot = BookshelfConfig.showUnread && BookshelfConfig.showUnreadNew && book.isNew,
                leftBottomText = bookTypeLabel
            )
        },
        title = book.name,
        subTitle = if (layoutMode == 0 && isCompact) {
            stringResource(R.string.author_read, book.author, unreadCount)
        } else {
            book.author
        },
        desc = stringResource(R.string.read_dur_progress, book.durChapterTitle ?: ""),
        extra = {
            if (BookshelfConfig.showLastUpdateTime && !book.isLocal) {
                AppText(
                    text = book.latestChapterTime.toTimeAgo(),
                    style = LegadoTheme.typography.bodySmall,
                    color = if (layoutMode != 0 || !isCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            AppText(
                text = book.latestChapterTitle ?: "",
                style = LegadoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        },
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
