package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyVerticalGrid
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.utils.readText
import io.legado.app.utils.startActivity
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
    var showLogSheet by remember { mutableStateOf(false) }

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bookshelfLayoutMode =
        if (isLandscape) BookshelfConfig.bookshelfLayoutModeLandscape else BookshelfConfig.bookshelfLayoutModePortrait
    val bookshelfLayoutGrid =
        if (isLandscape) BookshelfConfig.bookshelfLayoutGridLandscape else BookshelfConfig.bookshelfLayoutGridPortrait

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
                    showLogSheet = true
                    dismiss()
                },
                leadingIcon = { Icon(Icons.Default.History, null) }
            )
        },
        bottomContent = if (bookGroupStyle == 0) {
            {
                if (uiState.groups.isNotEmpty()) {
                    val selectedTabIndex = remember(pagerState.currentPage, uiState.groups.size) {
                        pagerState.currentPage.coerceIn(0, uiState.groups.size - 1)
                    }
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        divider = { },
                        containerColor = GlassTopAppBarDefaults.containerColor(),
                        minTabWidth = 0.dp
                    ) {
                        uiState.groups.forEachIndexed { index, group ->
                            Tab(
                                selected = selectedTabIndex == index,
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
        val currentGroup = uiState.groups.getOrNull(pagerState.currentPage)
        val pullToRefreshEnabled = currentGroup?.enableRefresh ?: true

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.upToc(uiState.items)
                            delay(1000)
                            isRefreshing = false
                        }
                    },
                    enabled = pullToRefreshEnabled
                )
        ) {
            if (bookGroupStyle == 2 && isInFolderRoot) {
                FastScrollLazyVerticalGrid(
                    columns = GridCells.Fixed(bookshelfLayoutGrid.coerceAtLeast(1)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 120.dp,
                        start = if (bookshelfLayoutMode != 0) 12.dp else 0.dp,
                        end = if (bookshelfLayoutMode != 0) 12.dp else 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (bookshelfLayoutMode != 0) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (bookshelfLayoutMode != 0) 8.dp else 0.dp),
                    showFastScroll = BookshelfConfig.showBookshelfFastScroller
                ) {
                    items(uiState.groups) { group ->
                        if (bookshelfLayoutMode == 0) {
                            BookGroupItemList(
                                group = group,
                                previewBooks = uiState.groupPreviews[group.groupId] ?: emptyList(),
                                isCompact = BookshelfConfig.bookshelfLayoutCompact,
                                titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                                titleCenter = BookshelfConfig.bookshelfTitleCenter,
                                titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
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
                                gridStyle = BookshelfConfig.bookshelfGridLayout,
                                titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                                titleCenter = BookshelfConfig.bookshelfTitleCenter,
                                titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                                coverShadow = BookshelfConfig.bookshelfCoverShadow,
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
                            bookshelfLayoutMode = bookshelfLayoutMode,
                            bookshelfLayoutGrid = bookshelfLayoutGrid,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick
                        )
                    }
                }
            }

            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = paddingValues.calculateTopPadding())
            )
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

    if (showLogSheet) {
        AppLogSheet(onDismissRequest = { showLogSheet = false })
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
    bookshelfLayoutMode: Int,
    bookshelfLayoutGrid: Int,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit
) {
    FastScrollLazyVerticalGrid(
        columns = GridCells.Fixed(bookshelfLayoutGrid.coerceAtLeast(1)),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp,
            start = if (bookshelfLayoutMode != 0) 12.dp else 0.dp,
            end = if (bookshelfLayoutMode != 0) 12.dp else 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (bookshelfLayoutMode != 0) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (bookshelfLayoutMode != 0) 8.dp else 0.dp),
        showFastScroll = BookshelfConfig.showBookshelfFastScroller
    ) {
        items(books, key = { it.bookUrl }) { book ->
            BookItem(
                book = book,
                layoutMode = bookshelfLayoutMode,
                gridStyle = BookshelfConfig.bookshelfGridLayout,
                isCompact = BookshelfConfig.bookshelfLayoutCompact,
                isUpdating = uiState.updatingBooks.contains(book.bookUrl),
                titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                titleCenter = BookshelfConfig.bookshelfTitleCenter,
                titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                coverShadow = BookshelfConfig.bookshelfCoverShadow,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) }
            )
        }
    }
}
