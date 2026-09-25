package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.image.cover.BookshelfCover
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.privacy.PrivateLockedCover
import io.legado.app.ui.widget.components.privacy.PrivateLockedCoverBlurRadius
import io.legado.app.ui.widget.components.privacy.PrivateLockedCoverOverlay
import io.legado.app.ui.widget.components.privacy.RuntimeBlurSupported
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.toTimeAgo
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfGridItem(
    cover: @Composable (Modifier) -> Unit,
    title: String,
    gridStyle: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    accessibilityLabel: String? = null,
    coverWidth: Int = 84,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(if (isSelected) Modifier.background(LegadoTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .bookshelfItemSemantics(accessibilityLabel ?: title, isSelected)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .width(coverWidth.dp)
        ) {
            BookshelfItemCover(coverShadow = coverShadow, cover = cover) {
                if (gridStyle == 1) {
                    AppText(
                        text = title,
                        style = (if (titleSmallFont) LegadoTheme.typography.labelSmall else LegadoTheme.typography.labelMedium).copy(
                            color = Color.White,
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
                        ),
                        textAlign = if (titleCenter) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
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
}

@Composable
fun BookshelfListItem(
    settings: BookshelfSettings,
    isCompact: Boolean,
    cover: @Composable (Modifier) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    titleEnd: @Composable (() -> Unit)? = null,
    subTitle: String? = null,
    desc: String? = null,
    descAnnotated: AnnotatedString? = null,
    descMaxLines: Int = 1,
    extra: @Composable (RowScope.() -> Unit)? = null,
    columnContent: @Composable (ColumnScope.() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    titleColor: Color? = null,
    accessibilityLabel: String? = null,
    coverWidth: Int = 84,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val cardColor =
        if (LegadoTheme.isDark) settings.bookshelfCardColorDark else settings.bookshelfCardColor
    Column {
        NormalCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .bookshelfItemSemantics(accessibilityLabel ?: title, isSelected),
            cornerRadius = 8.dp,
            containerColor = if (isSelected) LegadoTheme.colorScheme.secondaryContainer else if (cardColor != 0) Color(
                cardColor
            ) else LegadoTheme.colorScheme.cardContainer,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .align(
                            if (settings.bookshelfListCoverCenter) {
                                Alignment.CenterVertically
                            } else {
                                Alignment.Top
                            }
                        )
                        .width(coverWidth.dp)
                ) {
                    BookshelfItemCover(coverShadow = coverShadow, cover = cover)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp, bottom = 4.dp, end = 8.dp, start = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        AppText(
                            text = title,
                            style = if (titleColor != null) LegadoTheme.typography.titleMediumEmphasized.copy(
                                color = titleColor
                            ) else LegadoTheme.typography.titleMediumEmphasized,
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        titleEnd?.let { Box(Modifier.padding(top = 4.dp, start = 4.dp)) { it() } }
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
                        descAnnotated?.let {
                            AppText(
                                text = it,
                                style = LegadoTheme.typography.labelSmallEmphasized,
                                maxLines = descMaxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } ?: desc?.let {
                            AppText(
                                text = it,
                                style = LegadoTheme.typography.labelSmallEmphasized,
                                maxLines = descMaxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    extra?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
                    columnContent?.invoke(this)
                }
            }
            bottomContent?.invoke()
        }
        if (settings.bookshelfShowDivider) {
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BookshelfItemCover(
    coverShadow: Boolean,
    cover: @Composable (Modifier) -> Unit,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(5f / 7f)
            .then(if (coverShadow) Modifier.shadow(4.dp, RoundedCornerShape(4.dp)) else Modifier)
            .clip(RoundedCornerShape(4.dp))
    ) {
        cover(Modifier.fillMaxSize())
        overlay()
    }
}

private fun Modifier.bookshelfItemSemantics(label: String, isSelected: Boolean): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = label
        role = Role.Button
        if (isSelected) selected = true
    }

/**
 * 简介排版缓存：跨 LazyList 回收存活。
 *
 * LazyGrid/LazyList 滚出可视范围的条目会离开组合，其 remember 被丢弃，滚回来又重建，
 * 于是每本含长简介的书会被反复 Jsoup 解析——400+ 本书上下滚动时足够把主线程打爆。
 * 这里用“行数设置 + 原始简介”做 key，全局只解析一次；上限限制避免无限增长。
 * 网格模式不展示简介、不参与解析，只有列表模式确实要显示时才解析。
 */
private object formattedIntroCache {
    private const val MAX_ENTRIES = 500

    // accessOrder=true 的 LinkedHashMap 天然 LRU；synchronized 内查写保证同 key 只解析一次。
    private val cache = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String?>): Boolean =
            size > MAX_ENTRIES
    }

    fun getOrFormat(intro: String?, maxLines: Int): String? {
        // 空简介直接返回 null，不进缓存，也不触发解析。
        if (intro.isNullOrBlank()) return null
        val key = "$maxLines|$intro"
        return synchronized(cache) {
            if (cache.containsKey(key)) {
                cache[key]
            } else {
                val formatted = if (maxLines == 0) {
                    HtmlFormatter.formatIntroText(intro)
                } else {
                    HtmlFormatter.formatSummaryText(intro)
                }.takeIf { it.isNotBlank() }
                cache[key] = formatted
                formatted
            }
        }
    }
}

@Composable
fun BookGroupCover(
    settings: BookshelfSettings,
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
                    if (settings.bookshelfCoverShadow) {
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
                                    bookUrl = it.bookUrl,
                                    preferCache = true,
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
                                    bookUrl = it.bookUrl,
                                    preferCache = true,
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
                                    bookUrl = it.bookUrl,
                                    preferCache = true,
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
                                    bookUrl = it.bookUrl,
                                    preferCache = true,
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
    settings: BookshelfSettings,
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
    BookshelfGridItem(
        gridStyle = gridStyle,
        cover = {
            BookGroupCover(
                settings = settings,
                books = previewBooks,
                coverPath = group.cover,
                leftBottomText = countText,
                modifier = it
            )
        },
        title = group.groupName,
        accessibilityLabel = groupAccessibilityLabel(group.groupName, countText),
        modifier = modifier,
        titleSmallFont = titleSmallFont,
        titleCenter = titleCenter,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        coverWidth = settings.bookshelfGridCoverWidth,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookGroupItemList(
    settings: BookshelfSettings,
    group: BookGroupUi,
    previewBooks: List<BookUiItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countText: String? = null,
    isCompact: Boolean = false,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onBookClick: ((BookShelfItem) -> Unit)? = null
) {
    if (settings.bookshelfGroupListStyle == 2) {
        BookGroupItemHorizontalCovers(
            settings = settings,
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
            append(stringResource(R.string.recently_read))
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append(firstBookName)
            }
        }
    } else {
        null
    }
    BookshelfListItem(
        settings = settings,
        isCompact = settings.bookshelfGroupListStyle == 1 || isCompact,
        cover = {
            BookGroupCover(
                settings = settings,
                books = previewBooks,
                coverPath = group.cover,
                modifier = it,
            )
        },
        title = group.groupName,
        subTitle = countText,
        descAnnotated = descAnnotated,
        accessibilityLabel = groupAccessibilityLabel(
            group.groupName,
            countText,
            firstBookName?.let { "${stringResource(R.string.recently_read)}$it" },
        ),
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        coverWidth = settings.bookshelfListCoverWidth,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
fun BookGroupItemHorizontalCovers(
    settings: BookshelfSettings,
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
            if (isDark) settings.bookshelfCardColorDark else settings.bookshelfCardColor
        NormalCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(all = 4.dp)
                .semantics {
                    contentDescription = groupAccessibilityLabel(group.groupName, countText)
                    role = Role.Button
                },
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
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val coverCount = settings.bookshelfGroupCoverCount
                    previewBooks.take(coverCount).forEach { bookUi ->
                        val book = bookUi.book
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(5f / 7f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onBookClick?.invoke(book) }
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = bookAccessibilityLabel(
                                        book.name,
                                        book.author,
                                    )
                                    role = Role.Button
                                }
                        ) {
                            CoilBookCover(
                                name = book.name,
                                author = book.author,
                                path = book.getDisplayCover(),
                                bookUrl = book.bookUrl,
                                preferCache = true,
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
        if (settings.bookshelfShowDivider)
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
    settings: BookshelfSettings,
    customTagColors: ImmutableList<TagColorPair>,
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
    /** 私密书籍且尚未解锁：只渲染模糊封面与占位内容，不渲染任何真实文字 */
    locked: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val lockedHiddenLabel = stringResource(R.string.private_hidden_label)
    // 锁定 ⇄ 解锁：封面走模糊深度、其余文字直接隐藏。
    // 之所以不整体模糊卡片，是因为整体 blur 会把封面的圆角与阴影一起糊掉。
    // 低版本运行时模糊不可用，这条动画自然退化成瞬间切换（此时脱敏走 Coil 静态模糊）。
    val coverBlur by animateDpAsState(
        targetValue = if (locked && RuntimeBlurSupported) PrivateLockedCoverBlurRadius else 0.dp,
        animationSpec = tween(320),
        label = "private-cover-blur",
    )
    val lockOverlayAlpha by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = tween(260),
        label = "private-lock-overlay",
    )
    // 低版本 Modifier.blur 是 no-op，脱敏只能靠 Coil 变换出的模糊封面兜底；
    // 这条路径没有模糊动画，但同样不糊整卡，圆角完好。
    if (locked && !RuntimeBlurSupported) {
        PrivateLockedBookItem(
            settings = settings,
            bookUi = bookUi,
            layoutMode = layoutMode,
            modifier = modifier,
            isSelected = isSelected,
            gridStyle = gridStyle,
            isCompact = isCompact,
            titleSmallFont = titleSmallFont,
            titleCenter = titleCenter,
            titleMaxLines = titleMaxLines,
            coverShadow = coverShadow,
            // 脱敏封面也要带上与正常封面相同的共享元素参数，
            // 否则源端没有 sharedBounds，进详情页的封面转场动画就无从接起
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedCoverKey = sharedCoverKey,
            onClick = onClick,
            onLongClick = onLongClick
        )
        return
    }
    val book = bookUi.book
    val showListDetails = layoutMode == 0 && !isCompact && settings.showBookIntro
    val showIntroText = showListDetails && settings.bookshelfShowIntro
    // 简介排版包含 Jsoup 解析 + 多轮正则，是主线程重活；LazyList 回收后 remember 会丢失，
    // 来回滚动就会反复重解析。这里只在列表模式真要显示时才算，并用跨回收的 LRU 缓存兜住。
    val intro = remember(showIntroText, book.intro, settings.bookshelfIntroMaxLines) {
        if (showIntroText) {
            formattedIntroCache.getOrFormat(book.intro, settings.bookshelfIntroMaxLines)
        } else {
            null
        }
    }
    val showIntro = showIntroText && intro != null
    val showIntroBelowContent = showIntro && settings.bookshelfListIntroBelowContent
    val unreadCount = book.getUnreadChapterNum()
    val unreadText = if (settings.showUnread && unreadCount > 0) unreadCount.toString() else null
    val showUpdateBadge = settings.showUnread && settings.showUnreadNew && book.isNew
    val bookTypeLabel = if (settings.showTip) {
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

    val cover: @Composable (Modifier) -> Unit = { coverModifier ->
        Box(modifier = coverModifier) {
            BookshelfCover(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                // 锁定态不露更新进度指示：它同样是在透露"这本书有更新"
                isUpdating = isUpdating && !locked,
                modifier = Modifier.fillMaxSize(),
                coverModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f),
                sourceOrigin = book.origin,
                // 传本书 bookUrl：封面命中本地（含别名）缓存时
                // 不解析书源规则、不弹登录提示、不重新下载
                bookUrl = book.bookUrl,
                // 锁定态不露未读角标与来源标签：它们都是真实信息
                badgeText = if (locked) null else if (layoutMode != 0) unreadText else null,
                showBadgeDot = !locked && showUpdateBadge,
                leftBottomText = if (locked) null else matchedSourceLabel ?: bookTypeLabel,
                showLoadingPlaceholder = true,
                // 模糊与"已隐藏"叠加层都交给封面组件在**共享元素节点内部**渲染。
                // 这是关键：转场时 overlay 只搬运 sharedBounds 节点自己的内容，
                // 加在祖先上的 blur、放在外面的兄弟节点都会被落下，
                // 表现就是"动画一开始模糊和点阵就没了"。
                contentBlur = coverBlur,
                // 与详情页脱敏态共用同一份"已隐藏"叠层，只是整体做淡入淡出，
                // 避免"糊一下、锁一下"的跳变
                overlayContent = if (lockOverlayAlpha > 0f) {
                    { PrivateLockedCoverOverlay(modifier = Modifier.alpha(lockOverlayAlpha)) }
                } else {
                    null
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                sharedCoverKey = sharedCoverKey,
            )
        }
    }
    val accessibilityLabel = bookAccessibilityLabel(
        name = book.name,
        author = book.author,
        unreadText?.let { "$it ${stringResource(R.string.is_unread)}" },
        if (isUpdating) stringResource(R.string.loading) else null,
        book.durChapterTitle,
        book.latestChapterTitle,
        matchedSourceLabel,
        bookTypeLabel,
    )

    if (layoutMode != 0) {
        BookshelfGridItem(
            cover = cover,
            // 锁定态不渲染书名：占位条与"已隐藏"字样都不再需要，封面那套
            // （模糊 + 遮罩 + 点阵 + 锁标）已经说明了状态
            title = if (locked) "" else book.name,
            gridStyle = gridStyle,
            modifier = modifier,
            isSelected = isSelected,
            titleSmallFont = titleSmallFont,
            titleCenter = titleCenter,
            titleMaxLines = titleMaxLines,
            coverShadow = coverShadow,
            accessibilityLabel = if (locked) lockedHiddenLabel else accessibilityLabel,
            coverWidth = settings.bookshelfGridCoverWidth,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }

    BookshelfListItem(
        settings = settings,
        isCompact = isCompact,
        cover = cover,
        title = if (locked) "" else book.name,
        modifier = modifier,
        isSelected = isSelected,
        titleEnd = if (locked) null else if (unreadText != null) {
            {
                TextCard(
                    text = unreadText,
                    icon = if (showUpdateBadge) Icons.Default.Update else null,
                    iconSize = 12.dp,
                    cornerRadius = 4.dp,
                    horizontalPadding = 4.dp,
                    verticalPadding = 0.dp
                )
            }
        } else null,
        subTitle = if (locked) null else if (isCompact) {
            stringResource(R.string.author_read, book.author, unreadCount)
        } else {
            book.author
        },
        desc = if (locked) "" else book.durChapterTitle ?: "",
        // 锁定态什么都不铺：不显示比"假装有内容"的占位条更干净
        columnContent = if (locked) {
            null
        } else if (showListDetails) {
            {
                val kindList = bookUi.displayTags
                if (settings.bookshelfShowTag && kindList.isNotEmpty()) {
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
                                backgroundColor = if (colorPair != null && colorPair.bgColor != 0) Color(
                                    colorPair.bgColor
                                ) else LegadoTheme.colorScheme.secondaryContainer,
                                contentColor = if (colorPair != null && colorPair.textColor != 0) Color(
                                    colorPair.textColor
                                ) else LegadoTheme.colorScheme.primary,
                                cornerRadius = 4.dp,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp,
                                textStyle = LegadoTheme.typography.labelSmallEmphasized
                            )
                        }
                    }
                }
                if (showIntro && !showIntroBelowContent) {
                    BookItemIntro(
                        intro = intro,
                        maxLines = settings.bookshelfIntroMaxLines,
                    )
                }
            }
        } else null,
        bottomContent = if (locked) null else if (showIntroBelowContent) {
            {
                GlassCard(
                    modifier = Modifier.padding(all = 4.dp),
                    cornerRadius = 4.dp,
                    containerColor = LegadoTheme.colorScheme.cardContainer
                ) {
                    BookItemIntro(
                        intro = intro,
                        maxLines = settings.bookshelfIntroMaxLines,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        } else null,
        extra = if (locked) null else if (showListDetails && settings.bookshelfShowLatestChapter) {
            {
                if (settings.showLastUpdateTime && !book.isLocal) {
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
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        accessibilityLabel = if (locked) lockedHiddenLabel else accessibilityLabel,
        coverWidth = settings.bookshelfListCoverWidth,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/**
 * 私密书籍的脱敏卡片。
 *
 * 刻意复用 BookshelfGridItem / BookshelfListItem 的骨架，只替换三处内容：
 * 封面换成强模糊版、标题换成"已隐藏"、正文区域换成占位条。
 * 未读角标、最新章节、标签、简介一律不渲染——它们都是真实信息。
 */
@Composable
private fun PrivateLockedBookItem(
    settings: BookshelfSettings,
    bookUi: BookUiItem,
    layoutMode: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    gridStyle: Int = 0,
    isCompact: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val book = bookUi.book
    val hiddenLabel = stringResource(R.string.private_hidden_label)
    val cover: @Composable (Modifier) -> Unit = { coverModifier ->
        PrivateLockedCover(
            name = book.name,
            author = book.author,
            path = book.getDisplayCover(),
            modifier = coverModifier
                .fillMaxWidth()
                .aspectRatio(5f / 7f),
            sharedCoverKey = sharedCoverKey,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
    if (layoutMode != 0) {
        BookshelfGridItem(
            cover = cover,
            // 与正常态锁定卡片一致：不渲染书名文字，只留封面
            title = "",
            gridStyle = gridStyle,
            modifier = modifier,
            isSelected = isSelected,
            titleSmallFont = titleSmallFont,
            titleCenter = titleCenter,
            titleMaxLines = titleMaxLines,
            coverShadow = coverShadow,
            accessibilityLabel = hiddenLabel,
            coverWidth = settings.bookshelfGridCoverWidth,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }
    BookshelfListItem(
        settings = settings,
        isCompact = isCompact,
        cover = cover,
        title = "",
        modifier = modifier,
        isSelected = isSelected,
        titleMaxLines = titleMaxLines,
        coverShadow = coverShadow,
        accessibilityLabel = hiddenLabel,
        coverWidth = settings.bookshelfListCoverWidth,
        columnContent = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun BookItemIntro(
    intro: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = intro,
        style = LegadoTheme.typography.bodySmall,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
        maxLines = if (maxLines == 0) Int.MAX_VALUE else maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}

private fun groupAccessibilityLabel(
    groupName: String,
    countText: String?,
    detail: String? = null,
): String {
    return listOfNotNull(
        groupName.takeIf { it.isNotBlank() },
        countText?.takeIf { it.isNotBlank() },
        detail?.takeIf { it.isNotBlank() },
    ).joinToString(separator = ", ")
}

private fun bookAccessibilityLabel(
    name: String,
    author: String,
    vararg details: String?,
): String {
    return buildList {
        name.takeIf { it.isNotBlank() }?.let(::add)
        author.takeIf { it.isNotBlank() }?.let(::add)
        details.forEach { detail ->
            detail?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.distinct().joinToString(separator = ", ")
}
