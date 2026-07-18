package io.legado.app.ui.book.cache.manage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

private data class BookCacheManageListState(
    override val items: List<BookCacheBookItem> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
) : ListUiState<BookCacheBookItem>

@Composable
fun BookCacheManageRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BookCacheManageViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.onIntent(BookCacheManageIntent.Initialize)
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookCacheManageEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookCacheManageScreen(
        state = state,
        onBackClick = onBackClick,
        onIntent = viewModel::onIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookCacheManageScreen(
    state: BookCacheManageUiState,
    onBackClick: () -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var pendingDeleteBook by remember { mutableStateOf<BookCacheBookItem?>(null) }
    var pendingDeleteChapter by remember { mutableStateOf<Pair<BookCacheBookItem, BookCacheChapterItem>?>(null) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchKey by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf(BookGroup.IdAll) }
    var showGroupMenu by remember { mutableStateOf(false) }
    val allBooks = state.shelfBooks + state.notShelfBooks
    val filteredBooks = remember(allBooks, searchKey, isSearchMode, selectedGroupId) {
        allBooks.filter { book ->
            val matchesGroup = when (selectedGroupId) {
                BookGroup.IdAll -> true
                BookGroup.IdNetNone -> !book.isNotShelf && book.group == 0L
                else -> !book.isNotShelf && (book.group and selectedGroupId) > 0L
            }
            val matchesSearch = !isSearchMode || searchKey.isBlank() ||
                    book.name.contains(searchKey.trim(), ignoreCase = true) ||
                    book.author.contains(searchKey.trim(), ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }
    val filteredShelfBooks = remember(filteredBooks) { filteredBooks.filterNot { it.isNotShelf } }
    val filteredNotShelfBooks = remember(filteredBooks) { filteredBooks.filter { it.isNotShelf } }
    val hasRunningDownload = filteredBooks.any { it.hasActiveDownload }
    val hasDownloadTarget = filteredBooks.any { it.cachedCount < it.totalCount }
    val listUiState = remember(filteredBooks, searchKey, isSearchMode) {
        BookCacheManageListState(
            items = filteredBooks,
            searchKey = searchKey,
            isSearch = isSearchMode,
        )
    }
    val bookshelfSectionTitle = stringResource(R.string.cache_section_bookshelf)
    val bookshelfSectionEmptyText = stringResource(R.string.cache_empty_bookshelf)
    val notBookshelfSectionTitle = stringResource(R.string.cache_section_not_in_bookshelf)
    val notBookshelfSectionEmptyText = stringResource(R.string.cache_empty_not_in_bookshelf)
    val allGroupText = stringResource(R.string.all)
    val noGroupText = stringResource(R.string.net_no_group)
    val groupOptions = remember(state.groups, allGroupText, noGroupText) {
        listOf(
            BookGroup.IdAll to allGroupText,
            BookGroup.IdNetNone to noGroupText,
        ) + state.groups.map { it.groupId to it.groupName }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DynamicTopAppBar(
                title = stringResource(R.string.cache_management),
                subtitle = state.downloadSummary.takeIf { it.isNotBlank() },
                state = listUiState,
                onBackClick = onBackClick,
                onSearchToggle = { active ->
                    isSearchMode = active
                    if (!active) searchKey = ""
                },
                onSearchQueryChange = { searchKey = it },
                searchPlaceholder = "筛选书名/作者",
                onClearSelection = {},
                topBarActions = {
                    Box {
                        TopBarActionButton(
                            onClick = { showGroupMenu = true },
                            imageVector = AppIcons.Filter,
                            contentDescription = stringResource(R.string.a11y_group_filter)
                        )
                        RoundDropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false }
                        ) { dismiss ->
                            groupOptions.forEach { (groupId, groupName) ->
                                RoundDropdownMenuItem(
                                    text = groupName,
                                    isSelected = groupId == selectedGroupId,
                                    onClick = {
                                        selectedGroupId = groupId
                                        dismiss()
                                    }
                                )
                            }
                        }
                    }
                    TopBarActionButton(
                        onClick = { onIntent(BookCacheManageIntent.Refresh) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (hasRunningDownload || hasDownloadTarget) {
                AppFloatingActionButton(
                    onClick = {
                        if (hasRunningDownload) {
                            onIntent(BookCacheManageIntent.StopAllDownloads)
                        } else {
                            onIntent(BookCacheManageIntent.StartAllDownloads)
                        }
                    },
                    icon = if (hasRunningDownload) Icons.Default.Stop else Icons.Default.Download,
                    tooltipText = stringResource(
                        if (hasRunningDownload) {
                            R.string.stop_download
                        } else {
                            R.string.start_download
                        }
                    )
                )
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cacheSection(
                    title = bookshelfSectionTitle,
                    emptyText = bookshelfSectionEmptyText,
                    books = filteredShelfBooks,
                    expandedBookUrls = state.expandedBookUrls,
                    chaptersByBookUrl = state.chaptersByBookUrl,
                    onToggleExpanded = { bookUrl ->
                        onIntent(BookCacheManageIntent.ToggleBookExpanded(bookUrl))
                    },
                    onIntent = onIntent,
                    onDeleteBook = { pendingDeleteBook = it },
                    onDeleteChapter = { book, chapter -> pendingDeleteChapter = book to chapter }
                )
                if (selectedGroupId == BookGroup.IdAll) {
                    cacheSection(
                        title = notBookshelfSectionTitle,
                        emptyText = notBookshelfSectionEmptyText,
                        books = filteredNotShelfBooks,
                        expandedBookUrls = state.expandedBookUrls,
                        chaptersByBookUrl = state.chaptersByBookUrl,
                        onToggleExpanded = { bookUrl ->
                            onIntent(BookCacheManageIntent.ToggleBookExpanded(bookUrl))
                        },
                        onIntent = onIntent,
                        onDeleteBook = { pendingDeleteBook = it },
                        onDeleteChapter = { book, chapter ->
                            pendingDeleteChapter = book to chapter
                        }
                    )
                }
            }
        }
    }

    DeleteBookCacheDialog(
        item = pendingDeleteBook,
        onConfirm = { item ->
            onIntent(BookCacheManageIntent.DeleteBookCache(item.bookUrl))
            pendingDeleteBook = null
        },
        onDismiss = { pendingDeleteBook = null }
    )
    DeleteChapterCacheDialog(
        item = pendingDeleteChapter,
        onConfirm = { book, chapter ->
            onIntent(
                BookCacheManageIntent.DeleteChapterCache(
                    book.bookUrl,
                    chapter.chapterUrl,
                    chapter.title,
                    chapter.index,
                )
            )
            pendingDeleteChapter = null
        },
        onDismiss = { pendingDeleteChapter = null }
    )
}

private fun LazyListScope.cacheSection(
    title: String,
    emptyText: String,
    books: List<BookCacheBookItem>,
    expandedBookUrls: Set<String>,
    chaptersByBookUrl: Map<String, List<BookCacheChapterItem>>,
    onToggleExpanded: (String) -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
    onDeleteBook: (BookCacheBookItem) -> Unit,
    onDeleteChapter: (BookCacheBookItem, BookCacheChapterItem) -> Unit,
) {
    item(key = "$title-header") {
        AppText(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = LegadoTheme.typography.titleSmallEmphasized,
            color = LegadoTheme.colorScheme.primary
        )
    }
    if (books.isEmpty()) {
        item(key = "$title-empty") {
            TextCard(
                text = emptyText,
                modifier = Modifier.fillMaxWidth(),
                verticalPadding = 12.dp,
                horizontalPadding = 12.dp
            )
        }
    } else {
        books.forEach { item ->
            val bookUrl = item.bookUrl
            val expanded = expandedBookUrls.contains(bookUrl)
            item(key = "$title-book-$bookUrl") {
                BookCacheBookCard(
                    item = item,
                    expanded = expanded,
                    onToggleExpanded = { onToggleExpanded(bookUrl) },
                    onIntent = onIntent,
                    onDeleteBook = onDeleteBook,
                    modifier = Modifier.animateItem()
                )
            }
            if (expanded) {
                items(
                    items = chaptersByBookUrl[bookUrl].orEmpty(),
                    key = { chapter -> "$title-chapter-$bookUrl-${chapter.chapterUrl}" }
                ) { chapter ->
                    BookCacheChapterRow(
                        item = chapter,
                        modifier = Modifier.animateItem(),
                        onDownload = {
                            onIntent(
                                BookCacheManageIntent.DownloadChapter(
                                    bookUrl,
                                    chapter.index
                                )
                            )
                        },
                        onStop = {
                            onIntent(
                                BookCacheManageIntent.StopChapterDownload(
                                    bookUrl,
                                    chapter.index
                                )
                            )
                        },
                        onDelete = { onDeleteChapter(item, chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCacheBookCard(
    item: BookCacheBookItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
    onDeleteBook: (BookCacheBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "BookCacheExpandArrow"
    )
    val progressPercent = (item.progress * 100).toInt().coerceIn(0, 100)
    val statusSummary = stringResource(
        R.string.cache_download_status_summary,
        item.downloadingCount,
        item.waitingCount,
        item.pausedCount,
        item.errorCount
    )
    val progressDescription = stringResource(
        R.string.cache_progress_description,
        item.cachedCount,
        item.totalCount,
        progressPercent
    )
    val expandedState = stringResource(
        if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed
    )
    val bookDescription = stringResource(
        R.string.a11y_cache_book_item,
        item.name,
        item.author,
        progressDescription,
        statusSummary
    )
    NormalCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = bookDescription
                stateDescription = expandedState
                role = Role.Button
            },
        onClick = onToggleExpanded,
        containerColor = LegadoTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = item.name,
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppText(
                        text = item.author,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextCard(
                    text = "${item.cachedCount}/${item.totalCount}",
                    backgroundColor = LegadoTheme.colorScheme.cardContainer,
                )
            }
            AppLinearProgressIndicator(
                progress = item.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = progressDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(item.progress, 0f..1f)
                    }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = statusSummary,
                    modifier = Modifier.weight(1f),
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
                if (item.hasDownloadTask || item.cachedCount < item.totalCount) {
                    SmallTonalButton(
                        onClick = {
                            if (item.hasActiveDownload) {
                                onIntent(BookCacheManageIntent.StopBookDownload(item.bookUrl))
                            } else {
                                onIntent(BookCacheManageIntent.StartBookDownload(item.bookUrl))
                            }
                        },
                        icon = if (item.hasActiveDownload) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = when {
                            item.hasActiveDownload -> stringResource(
                                R.string.pause_book_download,
                                item.name
                            )
                            item.isPaused -> stringResource(
                                R.string.resume_book_download,
                                item.name
                            )
                            else -> stringResource(R.string.start_book_download, item.name)
                        }
                    )
                }
                SmallTonalButton(
                    onClick = { onDeleteBook(item) },
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_book_cache, item.name)
                )
            }
        }
    }
}

@Composable
private fun BookCacheChapterRow(
    item: BookCacheChapterItem,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusText = chapterStatusText(item)
    val chapterDescription = stringResource(
        R.string.a11y_cache_chapter_item,
        item.title,
        statusText
    )
    val chapterDownloadProgress = item.downloadProgress ?: 0f
    val downloadProgressDescription = item.progressLabel?.takeIf { it.isNotBlank() }
        ?: stringResource(
            R.string.cache_chapter_progress_description,
            (chapterDownloadProgress * 100).toInt().coerceIn(0, 100)
        )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = chapterDescription
                }
        ) {
            AppText(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.titleSmallEmphasized
            )
            AppText(
                text = statusText,
                maxLines = 1,
                style = LegadoTheme.typography.labelSmall,
                color = if (item.isError) {
                    LegadoTheme.colorScheme.error
                } else if (item.isCached) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                }
            )
            if (item.isDownloading) {
                AppLinearProgressIndicator(
                    progress = item.downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .semantics {
                            contentDescription = downloadProgressDescription
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                chapterDownloadProgress,
                                0f..1f
                            )
                        }
                )
            }
        }
        if (item.isWaiting || item.isDownloading) {
            SmallTonalButton(
                onClick = onStop,
                icon = Icons.Default.Stop,
                contentDescription = stringResource(R.string.pause_chapter_download, item.title)
            )
        } else if (item.isPaused || !item.isCached) {
            SmallTonalButton(
                onClick = onDownload,
                icon = Icons.Default.Download,
                contentDescription = if (item.isPaused) {
                    stringResource(R.string.resume_chapter_download, item.title)
                } else {
                    stringResource(R.string.download_chapter, item.title)
                }
            )
        }
        SmallTonalButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.delete_chapter_cache, item.title)
        )
    }
}

@Composable
private fun chapterStatusText(item: BookCacheChapterItem): String {
    if (item.isDownloading && !item.progressLabel.isNullOrBlank()) {
        return item.progressLabel
    }
    return when {
        item.isDownloading -> stringResource(R.string.downloading)
        item.isWaiting -> stringResource(R.string.wait_download)
        item.isPaused -> stringResource(R.string.download_paused)
        item.isError -> stringResource(R.string.download_error)
        item.isCached -> stringResource(R.string.download_success)
        else -> stringResource(R.string.not_cached)
    }
}

@Composable
private fun DeleteBookCacheDialog(
    item: BookCacheBookItem?,
    onConfirm: (BookCacheBookItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = item != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        text = stringResource(R.string.delete_book_cache_message, item?.name.orEmpty()),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { item?.let(onConfirm) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}

@Composable
private fun DeleteChapterCacheDialog(
    item: Pair<BookCacheBookItem, BookCacheChapterItem>?,
    onConfirm: (BookCacheBookItem, BookCacheChapterItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = item != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        text = stringResource(R.string.delete_chapter_cache_message, item?.second?.title.orEmpty()),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { item?.let { onConfirm(it.first, it.second) } },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}
