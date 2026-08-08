package io.legado.app.ui.book.read.sheet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.ui.book.toc.DownloadState
import io.legado.app.ui.book.toc.TocActivity
import io.legado.app.ui.book.toc.TocBookmarkItemUi
import io.legado.app.ui.book.toc.TocEffect
import io.legado.app.ui.book.toc.TocIntent
import io.legado.app.ui.book.toc.TocItemUi
import io.legado.app.ui.book.toc.TocMarkingItemUi
import io.legado.app.ui.book.toc.TocUiState
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButtonMenu
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.FabMenuItem
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReaderBookSheetTab {
    Information,
    Toc,
    Bookmarks,
    Marks,
}

@Composable
fun ReaderBookSheetRoute(
    show: Boolean,
    bookUrl: String,
    initialTab: ReaderBookSheetTab,
    onDismissRequest: () -> Unit,
    onChapterClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    onOpenFullBookInfo: () -> Unit,
    /** 书签页跳转：携带完整书签供跳转前校验。 */
    onBookmarkNavigate: (Bookmark) -> Unit = { _ -> },
    /** 笔记页跳转：携带完整展示项供跳转前校验。 */
    onMarkingNavigate: (TocMarkingItemUi) -> Unit = { _ -> },
    /** 笔记页点击进入 MarkingSheet 编辑。 */
    onMarkingEdit: (markingId: String) -> Unit = {},
    bookSource: BookSource? = null,
    onOpenChapterUrl: () -> Unit = {},
    onToggleReadUrlInBrowser: () -> Unit = {},
    sourceActions: ReaderBookSourceActions? = null,
    viewModel: TocViewModel = koinViewModel(key = "reader-book-sheet-$bookUrl"),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExportMarkdown by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { viewModel.onIntent(TocIntent.ExportBookmarks(it, pendingExportMarkdown)) }
    }
    val tocRegexLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onIntent(
                TocIntent.SaveTocRegex(result.data?.getStringExtra("tocRegex").orEmpty())
            )
        }
    }
    val fullTocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onChapterClick(
                result.data?.getIntExtra("index", 0) ?: 0,
                result.data?.getIntExtra("chapterPos", 0) ?: 0,
            )
        }
    }

    LaunchedEffect(bookUrl) {
        viewModel.onIntent(TocIntent.LoadBook(bookUrl))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TocEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ReaderBookSheet(
        show = show,
        state = state,
        initialTab = initialTab,
        onDismissRequest = {
            viewModel.onIntent(TocIntent.ClearSelection)
            viewModel.onIntent(TocIntent.SetSearchQuery(""))
            onDismissRequest()
        },
        onIntent = viewModel::onIntent,
        onChapterClick = { index -> onChapterClick(index, 0) },
        onBookmarkNavigate = onBookmarkNavigate,
        onMarkingNavigate = onMarkingNavigate,
        onMarkingEdit = onMarkingEdit,
        onOpenFullScreen = { tab ->
            when (tab) {
                ReaderBookSheetTab.Information -> onOpenFullBookInfo()
                ReaderBookSheetTab.Toc,
                ReaderBookSheetTab.Bookmarks -> {
                    fullTocLauncher.launch(
                        Intent(context, TocActivity::class.java)
                            .putExtra("bookUrl", bookUrl)
                            .putExtra(
                                "initialPage",
                                if (tab == ReaderBookSheetTab.Bookmarks) 1 else 0,
                            )
                    )
                }

                // 笔记页无全屏落地（TocActivity 暂无对应页）
                ReaderBookSheetTab.Marks -> Unit
            }
        },
        onEditLocalTocRule = { regex ->
            tocRegexLauncher.launch(
                Intent(context, TxtTocRulePreviewActivity::class.java)
                    .putExtra("bookUrl", bookUrl)
                    .putExtra("tocRegex", regex)
            )
        },
        onExportBookmarks = { isMarkdown, fileName ->
            pendingExportMarkdown = isMarkdown
            exportLauncher.launch(fileName)
        },
        bookSource = bookSource,
        onOpenFullBookInfo = onOpenFullBookInfo,
        onOpenChapterUrl = onOpenChapterUrl,
        onToggleReadUrlInBrowser = onToggleReadUrlInBrowser,
        sourceActions = sourceActions,
    )
}

@Composable
private fun ReaderBookSheet(
    show: Boolean,
    state: TocUiState,
    initialTab: ReaderBookSheetTab,
    onDismissRequest: () -> Unit,
    onIntent: (TocIntent) -> Unit,
    onChapterClick: (Int) -> Unit,
    onBookmarkNavigate: (Bookmark) -> Unit,
    onMarkingNavigate: (TocMarkingItemUi) -> Unit,
    onMarkingEdit: (String) -> Unit,
    onOpenFullScreen: (ReaderBookSheetTab) -> Unit,
    onEditLocalTocRule: (String?) -> Unit,
    onExportBookmarks: (isMarkdown: Boolean, fileName: String) -> Unit,
    bookSource: BookSource? = null,
    onOpenFullBookInfo: () -> Unit = {},
    onOpenChapterUrl: () -> Unit = {},
    onToggleReadUrlInBrowser: () -> Unit = {},
    sourceActions: ReaderBookSourceActions? = null,
) {
    val initialPage = initialTab.ordinal
    val pagerState = rememberPagerState(initialPage = initialPage) { ReaderBookSheetTab.entries.size }
    val scope = rememberCoroutineScope()
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    val density = LocalDensity.current
    val maxHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.72f
    }

    LaunchedEffect(show, initialPage) {
        if (show) pagerState.scrollToPage(initialPage)
    }
    BackHandler(enabled = show && state.action.selectedIds.isNotEmpty()) {
        onIntent(TocIntent.ClearSelection)
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        animateContentSize = false,
        contentPaddingEnabled = false,
        modifier = Modifier
            .heightIn(max = maxHeight),
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(
                book = state.book,
                bookSource = bookSource,
                onOpenBookInfo = onOpenFullBookInfo,
                onOpenChapterUrl = onOpenChapterUrl,
                onToggleReadUrlInBrowser = onToggleReadUrlInBrowser,
                sourceActions = sourceActions,
            )
        }
        CardTabRow(
            tabTitles = listOf(
                stringResource(R.string.information),
                stringResource(R.string.chapter_list),
                stringResource(R.string.bookmark),
                stringResource(R.string.marks),
            ),
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = {
                onIntent(TocIntent.SetSearchQuery(""))
                onIntent(TocIntent.ClearSelection)
                scope.launch { pagerState.animateScrollToPage(it) }
            },
            onTabLongClick = { index ->
                val tab = ReaderBookSheetTab.entries[index]
                if (tab != ReaderBookSheetTab.Marks) {
                    onDismissRequest()
                    onOpenFullScreen(tab)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                when (ReaderBookSheetTab.entries[page]) {
                    ReaderBookSheetTab.Information -> ReaderBookInformation(
                        book = state.book,
                    )

                    ReaderBookSheetTab.Toc -> ReaderBookTocPage(
                        state = state,
                        onIntent = onIntent,
                        onChapterClick = onChapterClick,
                        onEditLocalTocRule = onEditLocalTocRule,
                    )

                    ReaderBookSheetTab.Bookmarks -> ReaderBookBookmarksPage(
                        state = state,
                        onIntent = onIntent,
                        onBookmarkNavigate = onBookmarkNavigate,
                        onEditBookmark = { editingBookmark = it },
                        onExportBookmarks = onExportBookmarks,
                    )

                    ReaderBookSheetTab.Marks -> ReaderBookMarkingsPage(
                        markings = state.markings,
                        currentChapterIndex = state.book?.durChapterIndex,
                        currentBookUrl = state.book?.bookUrl,
                        onMarkingNavigate = onMarkingNavigate,
                        onMarkingEdit = onMarkingEdit,
                    )
                }
            }
        }
    }

    val bookmark = editingBookmark
    BookmarkEditSheet(
        show = bookmark != null,
        bookmark = bookmark ?: remember { Bookmark() },
        onDismiss = { editingBookmark = null },
        onSave = {
            onIntent(TocIntent.UpdateBookmark(it))
            editingBookmark = null
        },
        onDelete = {
            onIntent(TocIntent.DeleteBookmark(it))
            editingBookmark = null
        },
    )
}

data class ReaderBookSourceActions(
    val onLogin: () -> Unit = {},
    val onPay: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDisable: () -> Unit = {},
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderBookHeader(
    book: Book?,
    bookSource: BookSource? = null,
    onOpenBookInfo: (() -> Unit)? = null,
    onOpenChapterUrl: (() -> Unit)? = null,
    onToggleReadUrlInBrowser: (() -> Unit)? = null,
    sourceActions: ReaderBookSourceActions? = null,
) {
    val current = ((book?.durChapterIndex ?: -1) + 1).coerceAtLeast(0)
    val total = book?.totalChapterNum?.coerceAtLeast(0) ?: 0
    val isLocal = book?.isLocal == true
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoilBookCover(
            name = book?.name,
            author = book?.author,
            path = book?.getDisplayCover(),
            sourceOrigin = book?.origin,
            modifier = Modifier.width(40.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            AppText(
                text = book?.name.orEmpty(),
                modifier = if (onOpenBookInfo != null) {
                    Modifier.clickable { onOpenBookInfo() }
                } else {
                    Modifier
                },
                style = LegadoTheme.typography.labelLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .basicMarquee()
                        .weight(1f)
                        .then(
                            if (!isLocal && onOpenChapterUrl != null) {
                                Modifier.combinedClickable(
                                    onClick = { onOpenChapterUrl() },
                                    onLongClick = onToggleReadUrlInBrowser,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    text = book?.durChapterTitle.orEmpty(),
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextCard(
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    text = if (total > 0) "$current / $total" else "--",
                )
            }
            if (!isLocal && sourceActions != null && !book?.originName.isNullOrBlank()) {
                Box {
                    AppText(
                        text = book.originName,
                        modifier = Modifier
                            .clickable { sourceMenuExpanded = true }
                            .padding(top = 4.dp),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReaderBookSourceDropdown(
                        expanded = sourceMenuExpanded,
                        onDismiss = { sourceMenuExpanded = false },
                        bookSource = bookSource,
                        sourceActions = sourceActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBookSourceDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    bookSource: BookSource?,
    sourceActions: ReaderBookSourceActions,
) {
    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (!bookSource?.loginUrl.isNullOrBlank()) {
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Filled.Login) },
                text = stringResource(R.string.login),
                onClick = {
                    onDismiss()
                    sourceActions.onLogin()
                },
            )
        }
        if (!bookSource?.getContentRule()?.payAction.isNullOrBlank()) {
            RoundDropdownMenuItem(
                leadingIcon = { MenuItemIcon(Icons.Default.Payment) },
                text = stringResource(R.string.chapter_pay),
                onClick = {
                    onDismiss()
                    sourceActions.onPay()
                },
            )
        }
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.Default.Edit) },
            text = stringResource(R.string.edit_source),
            onClick = {
                onDismiss()
                sourceActions.onEdit()
            },
        )
        RoundDropdownMenuItem(
            leadingIcon = { MenuItemIcon(Icons.Default.Block) },
            text = stringResource(R.string.disable_source),
            onClick = {
                onDismiss()
                sourceActions.onDisable()
            },
        )
    }
}

@Composable
private fun ReaderBookInformation(
    book: Book?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { InformationRow(stringResource(R.string.author), book?.author.orEmpty()) }
        item {
            InformationRow(
                stringResource(R.string.book_source),
                book?.originName?.ifBlank { book.origin }.orEmpty(),
            )
        }
        book?.remark?.takeIf { it.isNotBlank() }?.let { remark ->
            item { InformationRow(stringResource(R.string.book_remark), remark) }
        }
        book?.getDisplayIntro()?.takeIf { it.isNotBlank() }?.let { intro ->
            item { InformationRow(stringResource(R.string.book_intro), intro) }
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    NormalCard(
        cornerRadius = 14.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AppText(
                text = label,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = LegadoTheme.colorScheme.primary,
            )
            AppText(
                text = value.ifBlank { "--" },
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(LegadoTheme.colorScheme.surfaceContainerLow),
        singleLine = true,
        textStyle = LegadoTheme.typography.bodySmall.copy(
            color = LegadoTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(LegadoTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        AppText(
                            text = placeholder,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun CompactToolIconBox(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        LegadoTheme.colorScheme.secondaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        LegadoTheme.colorScheme.onSecondaryContainer
    } else {
        LegadoTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ReaderBookTocPage(
    state: TocUiState,
    onIntent: (TocIntent) -> Unit,
    onChapterClick: (Int) -> Unit,
    onEditLocalTocRule: (String?) -> Unit,
) {
    val action = state.action
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val selected = action.selectedIds.isNotEmpty()
    val selectAllText = stringResource(R.string.select_all)
    val invertText = stringResource(R.string.invert_selection)
    val bookmarkText = stringResource(R.string.bookmark_add)
    val downloadSelectedText = stringResource(
        R.string.download_selected_count,
        action.selectedIds.size,
    )
    val cancelText = stringResource(R.string.cancel)
    val fabItems = remember(
        selected,
        action.selectedIds,
        selectAllText,
        invertText,
        bookmarkText,
        downloadSelectedText,
        cancelText,
    ) {
        listOf(
            FabMenuItem(Icons.Default.SelectAll, selectAllText) {
                onIntent(TocIntent.SelectAll)
            },
            FabMenuItem(Icons.Default.SwapVert, invertText) {
                onIntent(TocIntent.InvertSelection)
            },
            FabMenuItem(Icons.Default.BookmarkAdd, bookmarkText) {
                onIntent(TocIntent.AddBookmarksForSelected)
            },
            FabMenuItem(Icons.Default.Download, downloadSelectedText) {
                onIntent(TocIntent.DownloadSelected)
            },
            FabMenuItem(Icons.Default.Clear, cancelText) {
                onIntent(TocIntent.ClearSelection)
            },
        )
    }
    LaunchedEffect(selected) {
        fabExpanded = false
    }
    LaunchedEffect(state.book?.totalChapterNum, state.isReverse, action.items.isNotEmpty()) {
        val currentIndex = action.items.indexOfFirst { it.isDur }
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactSearchField(
                query = action.searchKey,
                onQueryChange = { onIntent(TocIntent.SetSearchQuery(it)) },
                placeholder = stringResource(R.string.search_chapters),
                modifier = Modifier.weight(1f),
            )
            CompactToolIconBox(
                selected = action.showWordCount,
                icon = Icons.Default.Numbers,
                contentDescription = stringResource(R.string.show_word_count),
                onClick = { onIntent(TocIntent.ToggleShowWordCount) },
            )
            CompactToolIconBox(
                selected = state.isReverse,
                icon = Icons.Default.SwapVert,
                contentDescription = stringResource(R.string.reverse_toc),
                onClick = { onIntent(TocIntent.ReverseToc) },
            )
            Box {
                CompactToolIconBox(
                    selected = menuExpanded,
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu),
                    onClick = { menuExpanded = !menuExpanded },
                )
                RoundDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    ReaderBookTocMenu(
                        state = state,
                        onIntent = onIntent,
                        onDismiss = { menuExpanded = false },
                        onEditLocalTocRule = onEditLocalTocRule,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            ReaderSheetChapterList(
                state = state,
                listState = listState,
                onIntent = onIntent,
                onChapterClick = onChapterClick,
            )
            AppFloatingActionButtonMenu(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                items = fabItems,
                visible = selected,
                focusRequester = focusRequester,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderSheetChapterList(
    state: TocUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onIntent: (TocIntent) -> Unit,
    onChapterClick: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = state.action.items,
            key = { item -> if (item.isVolume) "sheet-volume-${item.id}" else "sheet-chapter-${item.id}" },
            contentType = { item -> if (item.isVolume) "volume" else "chapter" },
        ) { item ->
            if (item.isVolume) {
                ReaderSheetVolumeItem(
                    item = item,
                    collapsed = item.id in state.collapsedVolumes,
                    onClick = { onIntent(TocIntent.ToggleVolume(item.id)) },
                    modifier = Modifier.animateItem(),
                )
            } else {
                ReaderSheetChapterItem(
                    item = item,
                    showWordCount = state.action.showWordCount,
                    selectionMode = state.action.selectedIds.isNotEmpty(),
                    onClick = {
                        if (state.action.selectedIds.isNotEmpty()) {
                            onIntent(TocIntent.ToggleSelection(item.id))
                        } else {
                            onChapterClick(item.id)
                        }
                    },
                    onLongClick = { onIntent(TocIntent.ToggleSelection(item.id)) },
                    onDownloadClick = { onIntent(TocIntent.DownloadChapter(item.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ReaderSheetVolumeItem(
    item: TocItemUi,
    collapsed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NormalCard(
        onClick = onClick,
        cornerRadius = 12.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (item.tocLevel.coerceIn(0, 6) * 10).dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (collapsed) -90f else 0f),
                tint = LegadoTheme.colorScheme.primary,
            )
            AppText(
                text = item.title,
                style = LegadoTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderSheetChapterItem(
    item: TocItemUi,
    showWordCount: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        item.isSelected -> LegadoTheme.colorScheme.primaryContainer
        item.isDur -> LegadoTheme.colorScheme.secondaryContainer
        else -> LegadoTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        item.isSelected -> LegadoTheme.colorScheme.onPrimaryContainer
        item.isDur -> LegadoTheme.colorScheme.onSecondaryContainer
        else -> LegadoTheme.colorScheme.onSurface
    }
    NormalCard(
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = 12.dp,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (item.tocLevel.coerceIn(0, 6) * 10).dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isVip && !item.isPay) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.a11y_vip_locked),
                            tint = LegadoTheme.colorScheme.error,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp),
                        )
                    }
                    AppText(
                        text = item.title,
                        style = LegadoTheme.typography.labelMediumEmphasized,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!item.tag.isNullOrBlank()) {
                    AppText(
                        text = item.tag,
                        style = LegadoTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ReaderSheetChapterStatus(
                item = item,
                showWordCount = showWordCount,
                enabled = !selectionMode,
                onDownloadClick = onDownloadClick,
            )
        }
    }
}

@Composable
private fun ReaderSheetChapterStatus(
    item: TocItemUi,
    showWordCount: Boolean,
    enabled: Boolean,
    onDownloadClick: () -> Unit,
) {
    val showCount = showWordCount &&
        !item.wordCount.isNullOrBlank() &&
        (item.downloadState == DownloadState.LOCAL || item.downloadState == DownloadState.SUCCESS)
    when {
        showCount -> NormalCard(
            cornerRadius = 8.dp,
            containerColor = if (item.isDur) {
                LegadoTheme.colorScheme.primaryContainer
            } else {
                LegadoTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            AppText(
                text = item.wordCount,
                style = LegadoTheme.typography.labelSmallEmphasized.copy(fontSize = 8.sp),
                color = if (item.isDur) {
                    LegadoTheme.colorScheme.onPrimaryContainer
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        item.isDur -> ReaderSheetStatusIcon(Icons.Default.LocationOn)
        item.downloadState == DownloadState.DOWNLOADING -> AppContainedLoadingIndicator(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(16.dp),
        )
        item.downloadState == DownloadState.SUCCESS -> ReaderSheetStatusIcon(
            imageVector = Icons.Default.CheckCircle,
        )
        item.downloadState == DownloadState.ERROR -> IconButton(
            onClick = onDownloadClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.a11y_retry_chapter, item.title),
                tint = LegadoTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        }
        item.downloadState == DownloadState.NONE -> IconButton(
            onClick = onDownloadClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DownloadForOffline,
                contentDescription = stringResource(R.string.download_chapter, item.title),
                tint = LegadoTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ReaderSheetStatusIcon(
    imageVector: ImageVector,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = LegadoTheme.colorScheme.secondary,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(16.dp),
    )
}

@Composable
private fun ReaderBookTocMenu(
    state: TocUiState,
    onIntent: (TocIntent) -> Unit,
    onDismiss: () -> Unit,
    onEditLocalTocRule: (String?) -> Unit,
) {
    val book = state.book
    fun dispatch(intent: TocIntent) {
        onDismiss()
        onIntent(intent)
    }
    RoundDropdownMenuItem(
        text = stringResource(R.string.download_all),
        onClick = { dispatch(TocIntent.DownloadAll) },
    )
    if (state.action.items.any { it.isVolume }) {
        RoundDropdownMenuItem(
            text = stringResource(R.string.expand_volume),
            onClick = { dispatch(TocIntent.ExpandAllVolumes) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.coll_volume),
            onClick = { dispatch(TocIntent.CollapseAllVolumes) },
        )
    }
    RoundDropdownMenuItem(
        text = stringResource(R.string.update_toc),
        onClick = { dispatch(TocIntent.UpdateToc) },
    )
    if (book?.isLocal == true) {
        if (book.isLocalTxt) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.txt_toc_rule),
                onClick = {
                    onDismiss()
                    onEditLocalTocRule(book.tocUrl)
                },
            )
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.split_long_chapters),
            isSelected = state.isSplitLongChapter,
            onClick = { dispatch(TocIntent.ToggleSplitLongChapter) },
        )
    }
}

@Composable
private fun ReaderBookBookmarksPage(
    state: TocUiState,
    onIntent: (TocIntent) -> Unit,
    onBookmarkNavigate: (Bookmark) -> Unit,
    onEditBookmark: (Bookmark) -> Unit,
    onExportBookmarks: (Boolean, String) -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val date = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactSearchField(
                query = state.action.searchKey,
                onQueryChange = { onIntent(TocIntent.SetSearchQuery(it)) },
                placeholder = stringResource(R.string.search),
                modifier = Modifier.weight(1f),
            )
            Box {
                CompactToolIconBox(
                    selected = menuExpanded,
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu),
                    onClick = { menuExpanded = !menuExpanded },
                )
                RoundDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.export_bookmarks_json),
                        onClick = {
                            menuExpanded = false
                            onExportBookmarks(false, "${state.book?.name ?: "bookmark"}_$date.json")
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.export_bookmarks_markdown),
                        onClick = {
                            menuExpanded = false
                            onExportBookmarks(true, "${state.book?.name ?: "bookmark"}_$date.md")
                        },
                    )
                }
            }
        }
        ReaderSheetBookmarkList(
            bookmarks = state.bookmarks,
            currentChapterIndex = state.book?.durChapterIndex,
            onBookmarkNavigate = onBookmarkNavigate,
            onEditBookmark = onEditBookmark,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderSheetBookmarkList(
    bookmarks: List<TocBookmarkItemUi>,
    currentChapterIndex: Int?,
    onBookmarkNavigate: (Bookmark) -> Unit,
    onEditBookmark: (Bookmark) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(bookmarks, currentChapterIndex) {
        if (bookmarks.isNotEmpty() && currentChapterIndex != null) {
            val currentPosition = bookmarks
                .indexOfLast { it.chapterIndex <= currentChapterIndex }
                .coerceAtLeast(0)
            listState.scrollToItem(currentPosition)
        }
    }

    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyMessage(message = stringResource(R.string.no_bookmark))
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = bookmarks,
            key = { it.id },
            contentType = { "bookmark" },
        ) { bookmark ->
            ReaderSheetBookmarkItem(
                item = bookmark,
                onClick = { onEditBookmark(bookmark.raw) },
                onLongClick = {
                    onBookmarkNavigate(bookmark.raw)
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ReaderSheetBookmarkItem(
    item: TocBookmarkItemUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (item.isDur) {
        LegadoTheme.colorScheme.secondaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (item.isDur) {
        LegadoTheme.colorScheme.onSecondaryContainer
    } else {
        LegadoTheme.colorScheme.onSurface
    }

    NormalCard(
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = 12.dp,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = item.chapterName,
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.isDur) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (item.raw.bookText.isNotBlank()) {
                AppText(
                    text = item.raw.bookText,
                    style = LegadoTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.content.isNotBlank()) {
                AppText(
                    text = item.content,
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 划线/高亮笔记页：与书签一致，点按进入编辑（MarkingSheet），长按跳转到标记位置。
 */
@Composable
private fun ReaderBookMarkingsPage(
    markings: List<TocMarkingItemUi>,
    currentChapterIndex: Int?,
    currentBookUrl: String?,
    onMarkingNavigate: (TocMarkingItemUi) -> Unit,
    onMarkingEdit: (String) -> Unit,
) {
    if (markings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyMessage(message = stringResource(R.string.marks_empty))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = markings,
            key = { it.id },
            contentType = { "marking" },
        ) { marking ->
            ReaderSheetMarkingItem(
                item = marking,
                isOtherSource = currentBookUrl != null &&
                        marking.bookUrl.isNotBlank() &&
                        marking.bookUrl != currentBookUrl,
                onClick = { onMarkingEdit(marking.id) },
                onLongClick = { onMarkingNavigate(marking) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ReaderSheetMarkingItem(
    item: TocMarkingItemUi,
    isOtherSource: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (item.isDur) {
        LegadoTheme.colorScheme.secondaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (item.isDur) {
        LegadoTheme.colorScheme.onSecondaryContainer
    } else {
        LegadoTheme.colorScheme.onSurface
    }
    val createdTime = remember(item.raw.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(item.raw.createdAt))
    }

    NormalCard(
        onClick = onClick,
        onLongClick = onLongClick,
        cornerRadius = 12.dp,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = createdTime,
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = item.chapterName.ifBlank {
                        stringResource(R.string.chapter_index_format, item.chapterIndex + 1)
                    },
                    style = LegadoTheme.typography.labelSmallEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
                if (isOtherSource) {
                    AppText(
                        text = stringResource(R.string.marks_other_source),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                if (item.isDur) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (item.text.isNotBlank()) {
                AppText(
                    text = item.text,
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.note.isNotBlank()) {
                AppText(
                    text = item.note,
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
