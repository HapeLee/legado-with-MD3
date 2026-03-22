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
import androidx.compose.material3.Text
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
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.widget.components.cover.BookCover
import io.legado.app.ui.widget.components.cover.BookCoverWithProgress
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
    if (isGrid) {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .then(
                            if (coverShadow) Modifier.shadow(
                                4.dp,
                                MaterialTheme.shapes.extraSmall
                            ) else Modifier
                        )
                        .clip(MaterialTheme.shapes.extraSmall) // 先阴影后裁剪
                ) {
                    cover(Modifier.fillMaxWidth())
                    if (gridStyle == 1) {
                        Text(
                            text = title,
                            style = (if (titleSmallFont) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium).copy(
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
                    Text(
                        text = title,
                        style = if (titleSmallFont) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = if (!isCompact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (!isCompact) {
                        desc?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
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
            if (!isCompact) HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun BookGroupCover(
    books: List<Book>,
    coverPath: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(5f / 7f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (coverPath != null) {
            BookCover(
                name = "",
                author = "",
                path = coverPath,
                modifier = Modifier.fillMaxSize(),
                ignoreUseDefaultCover = true
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
                            BookCover(
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
                            BookCover(
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
                            BookCover(
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
                            BookCover(
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
}

@Composable
fun BookGroupItemGrid(
    group: BookGroup,
    previewBooks: List<Book>,
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
        cover = { BookGroupCover(books = previewBooks, coverPath = group.cover, modifier = it) },
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
    previewBooks: List<Book>,
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
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        modifier = modifier,
        subTitle = "${previewBooks.size} 本书籍",
        desc = "点击打开文件夹",
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookItem(
    book: Book,
    layoutMode: Int,
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

    BookshelfItem(
        isGrid = layoutMode != 0,
        gridStyle = gridStyle,
        isCompact = isCompact,
        cover = { modifier ->
            BookCoverWithProgress(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                isUpdating = isUpdating,
                modifier = modifier,
                badgeText = if (BookshelfConfig.showUnread && unreadCount > 0) unreadCount.toString() else null,
                showBadgeDot = !BookshelfConfig.showUnread && BookshelfConfig.showUnreadNew && unreadCount > 0 && book.lastCheckCount > 0
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
                Text(
                    text = book.latestChapterTime.toTimeAgo(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (layoutMode != 0 || !isCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text = book.latestChapterTitle ?: "",
                style = MaterialTheme.typography.bodySmall,
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
