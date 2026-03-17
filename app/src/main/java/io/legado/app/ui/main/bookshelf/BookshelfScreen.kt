package io.legado.app.ui.main.bookshelf

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.cover.BookCoverWithProgress
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.utils.readText
import io.legado.app.utils.startActivity
import io.legado.app.utils.toTimeAgo
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel = koinViewModel(),
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddUrlDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showGroupManageSheet by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(HandleFileContract()) {
        runCatching {
            it.uri?.readText(context)?.let { text ->
                val groupId = uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedGroupIndex,
        pageCount = { uiState.groups.size }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (uiState.groups.isNotEmpty() && page < uiState.groups.size) {
                viewModel.changeGroup(uiState.groups[page].groupId)
            }
        }
    }

    LaunchedEffect(uiState.selectedGroupIndex) {
        if (uiState.selectedGroupIndex != pagerState.currentPage && uiState.selectedGroupIndex < pagerState.pageCount) {
            pagerState.scrollToPage(uiState.selectedGroupIndex)
        }
    }

    val bookGroupStyle = BookshelfConfig.bookGroupStyle
    // 控制是否处于“文件夹列表”根视图，还是“文件夹内部”书籍视图
    var isInFolderRoot by remember(bookGroupStyle) { mutableStateOf(bookGroupStyle == 2) }
    val title = when {
        bookGroupStyle == 1 -> {
            uiState.groups.getOrNull(pagerState.currentPage)?.groupName
                ?: stringResource(R.string.bookshelf)
        }

        bookGroupStyle == 2 && uiState.groups.isNotEmpty() -> {
            if (isInFolderRoot) stringResource(R.string.bookshelf)
            else uiState.groups.getOrNull(pagerState.currentPage)?.groupName
                ?: stringResource(R.string.bookshelf)
        }

        else -> stringResource(R.string.bookshelf)
    }

    if (bookGroupStyle == 2 && !isInFolderRoot) {
        BackHandler {
            isInFolderRoot = true
        }
    }

    ListScaffold(
        title = title,
        state = uiState,
        onSearchToggle = { context.startActivity<SearchActivity>() },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        topBarActions = { },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.add_remote_book)) },
                onClick = { context.startActivity<RemoteBookActivity>(); dismiss() },
                leadingIcon = { Icon(Icons.Default.Wifi, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.book_local)) },
                onClick = { context.startActivity<ImportBookActivity>(); dismiss() },
                leadingIcon = { Icon(Icons.Default.Save, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.update_toc)) },
                onClick = { viewModel.upToc(uiState.items); dismiss() },
                leadingIcon = { Icon(Icons.Default.Refresh, null) }
            )
            RoundDropdownMenuItem(
                text = { Text("布局设置") },
                onClick = { showConfigSheet = true; dismiss() },
                leadingIcon = { Icon(Icons.Default.GridView, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.group_manage)) },
                onClick = { showGroupManageSheet = true; dismiss() },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.add_url)) },
                onClick = { showAddUrlDialog = true; dismiss() },
                leadingIcon = { Icon(Icons.Default.Link, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.bookshelf_management)) },
                onClick = {
                    val groupId =
                        uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                    context.startActivity<BookshelfManageActivity> {
                        putExtra("groupId", groupId)
                    }
                    dismiss()
                },
                leadingIcon = { Icon(Icons.Default.Settings, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.cache_export)) },
                onClick = {
                    val groupId =
                        uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                    context.startActivity<CacheActivity> {
                        putExtra("groupId", groupId)
                    }
                    dismiss()
                },
                leadingIcon = { Icon(Icons.Default.Download, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.export_bookshelf)) },
                onClick = {
                    dismiss()
                },
                leadingIcon = { Icon(Icons.Default.ImportExport, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.import_bookshelf)) },
                onClick = { showImportSheet = true; dismiss() },
                leadingIcon = { Icon(Icons.Default.FileOpen, null) }
            )
            RoundDropdownMenuItem(
                text = { Text(stringResource(R.string.log)) },
                onClick = {
                    dismiss()
                },
                leadingIcon = { Icon(Icons.Default.History, null) }
            )
        },
        bottomContent = if (bookGroupStyle == 0) {
            {
                if (uiState.groups.isNotEmpty()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 0.dp,
                        divider = { },
                        containerColor = GlassTopAppBarDefaults.containerColor(),
                        minTabWidth = 0.dp
                    ) {
                        uiState.groups.forEachIndexed { index, group ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        text = group.groupName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        } else null
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.upAllBookToc()
                    delay(1000)
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            if (bookGroupStyle == 2 && isInFolderRoot) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(uiState.bookshelfLayoutGrid.coerceAtLeast(1)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 120.dp,
                        start = if (uiState.bookshelfLayoutMode != 0) 12.dp else 0.dp,
                        end = if (uiState.bookshelfLayoutMode != 0) 12.dp else 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (uiState.bookshelfLayoutMode != 0) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (uiState.bookshelfLayoutMode != 0) 8.dp else 0.dp)
                ) {
                    items(uiState.groups) { group ->
                        if (uiState.bookshelfLayoutMode == 0) {
                            BookGroupItemList(
                                group = group,
                                previewBooks = uiState.groupPreviews[group.groupId] ?: emptyList(),
                                isCompact = uiState.bookshelfLayoutCompact,
                                titleSmallFont = uiState.titleSmallFont,
                                titleCenter = uiState.titleCenter,
                                titleMaxLines = uiState.titleMaxLines,
                                onClick = {
                                    val index = uiState.groups.indexOf(group)
                                    if (index != -1) {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        isInFolderRoot = false
                                    }
                                },
                                onLongClick = { showGroupManageSheet = true }
                            )
                        } else {
                            BookGroupItemGrid(
                                group = group,
                                previewBooks = uiState.groupPreviews[group.groupId] ?: emptyList(),
                                isCompact = uiState.bookshelfLayoutCompact,
                                titleSmallFont = uiState.titleSmallFont,
                                titleCenter = uiState.titleCenter,
                                titleMaxLines = uiState.titleMaxLines,
                                coverShadow = uiState.coverShadow,
                                onClick = {
                                    val index = uiState.groups.indexOf(group)
                                    if (index != -1) {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        isInFolderRoot = false
                                    }
                                },
                                onLongClick = { showGroupManageSheet = true }
                            )
                        }
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 3,
                    key = { if (it < uiState.groups.size) uiState.groups[it].groupId else it }
                ) { pageIndex ->
                    val group = uiState.groups.getOrNull(pageIndex)
                    if (group != null) {
                        val books by viewModel.getBooksFlow(group.groupId)
                            .collectAsState(emptyList())
                        BookshelfPage(
                            paddingValues = paddingValues,
                            books = books,
                            uiState = uiState,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick
                        )
                    }
                }
            }
        }
    }

    if (showConfigSheet) {
        BookshelfConfigSheet(onDismissRequest = { showConfigSheet = false })
    }

    if (showGroupManageSheet) {
        GroupManageSheet(onDismissRequest = { showGroupManageSheet = false })
    }

    if (showAddUrlDialog) {
        SourceInputDialog(
            title = stringResource(R.string.add_book_url),
            onDismissRequest = { showAddUrlDialog = false },
            onConfirm = { url ->
                viewModel.addBookByUrl(url)
                showAddUrlDialog = false
            }
        )
    }

    if (showImportSheet) {
        FilePickerSheet(
            onDismissRequest = { showImportSheet = false },
            title = stringResource(R.string.import_bookshelf),
            onSelectSysFile = { types ->
                importLauncher.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = types
                }
                showImportSheet = false
            },
            onManualInput = {
                showAddUrlDialog = true
                showImportSheet = false
            }
        )
    }

    if (uiState.isLoading) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    uiState.loadingText?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookshelfPage(
    paddingValues: PaddingValues,
    books: List<Book>,
    uiState: BookshelfUiState,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(uiState.bookshelfLayoutGrid.coerceAtLeast(1)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp,
            start = if (uiState.bookshelfLayoutMode != 0) 12.dp else 0.dp,
            end = if (uiState.bookshelfLayoutMode != 0) 12.dp else 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (uiState.bookshelfLayoutMode != 0) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (uiState.bookshelfLayoutMode != 0) 8.dp else 0.dp)
    ) {
        items(books, key = { it.bookUrl }) { book ->
            BookItem(
                book = book,
                layoutMode = uiState.bookshelfLayoutMode,
                isCompact = uiState.bookshelfLayoutCompact,
                isUpdating = uiState.updatingBooks.contains(book.bookUrl),
                titleSmallFont = uiState.titleSmallFont,
                titleCenter = uiState.titleCenter,
                titleMaxLines = uiState.titleMaxLines,
                coverShadow = uiState.coverShadow,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) }
            )
        }
    }
}

@Composable
fun BookItem(
    book: Book,
    layoutMode: Int,
    isCompact: Boolean = false,
    isUpdating: Boolean = false,
    titleSmallFont: Boolean = false,
    titleCenter: Boolean = true,
    titleMaxLines: Int = 2,
    coverShadow: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val unreadCount = (book.totalChapterNum - book.durChapterIndex).coerceAtLeast(0)

    BookshelfItem(
        isGrid = layoutMode != 0,
        isCompact = isCompact,
        cover = { modifier ->
            BookCoverWithProgress(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                isUpdating = isUpdating,
                modifier = modifier
            )
        },
        title = book.name,
        subTitle = if (isCompact && layoutMode == 0) {
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
                    color = if (!isCompact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
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
