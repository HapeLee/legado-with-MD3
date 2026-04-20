package io.legado.app.ui.main.bookshelf

import android.content.ClipData
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.SmallOutlinedIconToggleButton
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyVerticalGrid
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.readText
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel = koinViewModel(),
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddUrlDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var showGroupManageSheet by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showGroupSelectSheet by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedBookUrls by remember { mutableStateOf<Set<String>>(emptySet()) }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BaseRuleEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed && event.url != null) {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText(
                                    "url",
                                    event.url
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                runCatching {
                    val text = it.readText(context)
                    val groupId =
                        uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                    viewModel.importBookshelf(text, groupId)
                }.onFailure {
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }
        }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { viewModel.exportToUri(it, uiState.items) }
        }
    )

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedGroupIndex,
        pageCount = { uiState.groups.size }
    )

    LaunchedEffect(uiState.groups) {
        if (uiState.groups.isNotEmpty()) {
            val savedGroupId = BookshelfConfig.saveTabPosition
            val savedGroupIndex = uiState.groups.indexOfFirst { it.groupId == savedGroupId }
            if (savedGroupIndex >= 0 && savedGroupIndex != pagerState.currentPage) {
                viewModel.changeGroup(savedGroupId)
                pagerState.scrollToPage(savedGroupIndex)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (uiState.groups.isNotEmpty() && page in uiState.groups.indices) {
                    val targetGroupId = uiState.groups[page].groupId
                    val currentGroupId =
                        uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId
                    if (currentGroupId != targetGroupId) {
                        viewModel.changeGroup(targetGroupId)
                    }
                }
            }
    }

    val bookGroupStyle = BookshelfConfig.bookGroupStyle
    // 控制是否处于“文件夹列表”根视图，还是“文件夹内部”书籍视图
    var isInFolderRoot by remember(bookGroupStyle) { mutableStateOf(bookGroupStyle == 2) }

    val clearSelection = {
        selectedBookUrls = emptySet()
    }
    val exitEditMode = {
        isEditMode = false
        clearSelection()
    }
    val toggleEditMode = {
        if (isEditMode) {
            exitEditMode()
        } else {
            if (bookGroupStyle == 2 && isInFolderRoot) {
                isInFolderRoot = false
            }
            isEditMode = true
            clearSelection()
        }
    }
    val toggleBookSelection: (String) -> Unit = { bookUrl ->
        selectedBookUrls = if (selectedBookUrls.contains(bookUrl)) {
            selectedBookUrls - bookUrl
        } else {
            selectedBookUrls + bookUrl
        }
    }

    LaunchedEffect(pagerState.currentPage, isInFolderRoot) {
        clearSelection()
    }

    LaunchedEffect(uiState.items) {
        val visibleBookUrls = uiState.items.mapTo(hashSetOf()) { it.bookUrl }
        selectedBookUrls = selectedBookUrls.intersect(visibleBookUrls)
    }

    BackHandler(enabled = isEditMode) {
        if (selectedBookUrls.isNotEmpty()) {
            clearSelection()
        } else {
            exitEditMode()
        }
    }

    val baseTitle = when (bookGroupStyle) {
        1 -> {
            uiState.groups.getOrNull(pagerState.currentPage)?.groupName
                ?: stringResource(R.string.bookshelf)
        }
        2 if uiState.groups.isNotEmpty() -> {
            if (isInFolderRoot) stringResource(R.string.bookshelf)
            else uiState.groups.getOrNull(pagerState.currentPage)?.groupName
                ?: stringResource(R.string.bookshelf)
        }

        else -> stringResource(R.string.bookshelf)
    }
    val title = if (uiState.upBooksCount > 0) {
        "$baseTitle (${uiState.upBooksCount})"
    } else {
        baseTitle
    }

    if (bookGroupStyle == 2 && !isInFolderRoot && !isEditMode) {
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
    val bookshelfLayoutList =
        if (isLandscape) BookshelfConfig.bookshelfLayoutListLandscape else BookshelfConfig.bookshelfLayoutListPortrait
    val totalHorizontalPadding =
        if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) 12.dp else 16.dp
    val gridContentHorizontalPadding = totalHorizontalPadding / 2
    val gridInnerHorizontalPadding = totalHorizontalPadding / 2

    ListScaffold(
        title = title,
        state = uiState,
        onBackClick = if (isEditMode) exitEditMode else null,
        backNavigationIcon = if (isEditMode) AppIcons.Close else AppIcons.Back,
        showSearchAction = !isEditMode,
        onSearchToggle = { context.startActivity<SearchActivity>() },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        topBarActions = {
            if (isEditMode) {
                TopBarActionButton(
                    onClick = {
                        selectedBookUrls = uiState.items.mapTo(hashSetOf()) { it.bookUrl }
                    },
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all)
                )
                TopBarActionButton(
                    onClick = {
                        val visibleBookUrls = uiState.items.mapTo(hashSetOf()) { it.bookUrl }
                        selectedBookUrls = visibleBookUrls - selectedBookUrls
                    },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.revert_selection)
                )
                TopBarActionButton(
                    onClick = {
                        if (selectedBookUrls.isNotEmpty()) {
                            showGroupSelectSheet = true
                        }
                    },
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = stringResource(R.string.move_to_group)
                )
            }
        },
        dropDownMenuContent = if (!isEditMode) {
            { dismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.add_remote_book),
                    onClick = { onNavigateToRemoteImport(); dismiss() },
                    leadingIcon = { Icon(Icons.Default.Wifi, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.book_local),
                    onClick = { onNavigateToLocalImport(); dismiss() },
                    leadingIcon = { Icon(Icons.Default.Save, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.update_toc),
                    onClick = { viewModel.upToc(uiState.items); dismiss() },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.layout_setting),
                    onClick = { showConfigSheet = true; dismiss() },
                    leadingIcon = { Icon(Icons.Default.GridView, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.group_manage),
                    onClick = { showGroupManageSheet = true; dismiss() },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.add_url),
                    onClick = { showAddUrlDialog = true; dismiss() },
                    leadingIcon = { Icon(Icons.Default.Link, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.edit),
                    onClick = {
                        toggleEditMode()
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.cache_export),
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
                    text = stringResource(R.string.export_bookshelf),
                    onClick = {
                        showExportSheet = true
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.ImportExport, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.import_bookshelf),
                    onClick = { showImportSheet = true; dismiss() }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.log),
                    onClick = {
                        showLogSheet = true
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.History, null) }
                )
            }
        } else null,
        snackbarHostState = snackbarHostState,
        bottomContent = if (bookGroupStyle == 0) {
            {
                if (uiState.groups.isNotEmpty()) {
                    val selectedTabIndex =
                        pagerState.currentPage.coerceIn(0, uiState.groups.size - 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveHorizontalPaddingTab(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabTitles = remember(uiState.groups) {
                            uiState.groups.map { it.groupName }
                        }

                        AppTabRow(
                            tabTitles = tabTitles,
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (BookshelfConfig.shouldShowExpandButton) {
                            Box(modifier = Modifier) {
                                SmallOutlinedIconToggleButton(
                                    checked = showGroupMenu,
                                    onCheckedChange = { showGroupMenu = it },
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = stringResource(R.string.group_manage)
                                )
                                RoundDropdownMenu(
                                    expanded = showGroupMenu,
                                    onDismissRequest = { showGroupMenu = false }
                                ) { dismiss ->
                                    uiState.groups.forEachIndexed { index, group ->
                                        RoundDropdownMenuItem(
                                            text = group.groupName,
                                            onClick = {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                                dismiss()
                                            },
                                            trailingIcon = {
                                                if (selectedTabIndex == index) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else null
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val pullToRefreshState = rememberPullToRefreshState()
        val currentGroup = uiState.groups.getOrNull(pagerState.currentPage)
        val pullToRefreshEnabled = (currentGroup?.enableRefresh ?: true) && !isEditMode

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
            AnimatedContent(
                targetState = isInFolderRoot,
                label = "FolderTransition"
            ) { isRoot ->
                if (bookGroupStyle == 2 && isRoot) {
                    val folderColumns =
                        if (bookshelfLayoutMode == 0) bookshelfLayoutList else bookshelfLayoutGrid
                    val isGridMode = bookshelfLayoutMode != 0
                    FastScrollLazyVerticalGrid(
                        columns = GridCells.Fixed(folderColumns.coerceAtLeast(1)),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isGridMode) gridInnerHorizontalPadding else 0.dp),
                        contentPadding = adaptiveContentPadding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = 120.dp,
                            horizontal = if (isGridMode) gridContentHorizontalPadding else 0.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
                        showFastScroll = BookshelfConfig.showBookshelfFastScroller
                    ) {
                        itemsIndexed(
                            uiState.groups,
                            key = { _, it -> it.groupId }) { index, group ->
                            val countText = if (BookshelfConfig.showBookCount) {
                                uiState.groupBookCounts[group.groupId]?.let {
                                    stringResource(R.string.book_count, it)
                                }
                            } else {
                                null
                            }
                            if (bookshelfLayoutMode == 0) {
                                BookGroupItemList(
                                    group = group,
                                    previewBooks = uiState.groupPreviews[group.groupId]
                                        ?: emptyList(),
                                    countText = countText,
                                    isCompact = BookshelfConfig.bookshelfLayoutCompact,
                                    titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                                    titleCenter = BookshelfConfig.bookshelfTitleCenter,
                                    titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                                    onClick = {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        isInFolderRoot = false
                                    },
                                    onLongClick = { showGroupManageSheet = true }
                                )
                            } else {
                                BookGroupItemGrid(
                                    group = group,
                                    previewBooks = uiState.groupPreviews[group.groupId]
                                        ?: emptyList(),
                                    countText = countText,
                                    gridStyle = BookshelfConfig.bookshelfGridLayout,
                                    titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                                    titleCenter = BookshelfConfig.bookshelfTitleCenter,
                                    titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                                    coverShadow = BookshelfConfig.bookshelfCoverShadow,
                                    onClick = {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        isInFolderRoot = false
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
                            val booksFlow = remember(group.groupId) {
                                viewModel.getBooksFlow(group.groupId)
                            }
                            val books by booksFlow.collectAsState(emptyList())
                            BookshelfPage(
                                paddingValues = paddingValues,
                                books = books,
                                uiState = uiState,
                                bookshelfLayoutMode = bookshelfLayoutMode,
                                bookshelfLayoutGrid = bookshelfLayoutGrid,
                                bookshelfLayoutList = bookshelfLayoutList,
                                isEditMode = isEditMode,
                                selectedBookUrls = selectedBookUrls,
                                onToggleBookSelection = { toggleBookSelection(it.bookUrl) },
                                onBookClick = onBookClick,
                                onBookLongClick = onBookLongClick
                            )
                        }
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

    BookshelfConfigSheet(
        show = showConfigSheet,
        onDismissRequest = { showConfigSheet = false }
    )

    GroupManageSheet(
        show = showGroupManageSheet,
        onDismissRequest = { showGroupManageSheet = false }
    )

    GroupSelectSheet(
        show = showGroupSelectSheet,
        currentGroupId = 0L,
        onDismissRequest = { showGroupSelectSheet = false },
        onConfirm = { groupId ->
            viewModel.moveBooksToGroup(selectedBookUrls, groupId)
            showGroupSelectSheet = false
            clearSelection()
        }
    )

    SourceInputDialog(
        show = showAddUrlDialog,
        title = stringResource(R.string.add_book_url),
        onDismissRequest = { showAddUrlDialog = false },
        onConfirm = { url ->
            viewModel.addBookByUrl(url)
            showAddUrlDialog = false
        }
    )

    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.import_bookshelf),
        onSelectSysFile = { types ->
            importLauncher.launch(types)
            showImportSheet = false
        },
        onManualInput = {
            showAddUrlDialog = true
            showImportSheet = false
        },
        allowExtensions = arrayOf("json", "txt")
    )

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(R.string.export_bookshelf),
        onSelectSysDir = {
            showExportSheet = false
            exportLauncher.launch("bookshelf.json")
        },
        onUpload = {
            showExportSheet = false
            viewModel.uploadBookshelf(uiState.items)
        }
    )

    AppLogSheet(
        show = showLogSheet,
        onDismissRequest = { showLogSheet = false }
    )

    if (uiState.isLoading) {
        Dialog(onDismissRequest = {}) {
            NormalCard(
                cornerRadius = 12.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    uiState.loadingText?.let {
                        AppText(
                            text = it,
                            modifier = Modifier.padding(top = 16.dp),
                            style = LegadoTheme.typography.bodyMedium
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
    books: List<BookShelfItem>,
    uiState: BookshelfUiState,
    bookshelfLayoutMode: Int,
    bookshelfLayoutGrid: Int,
    bookshelfLayoutList: Int,
    isEditMode: Boolean,
    selectedBookUrls: Set<String>,
    onToggleBookSelection: (BookShelfItem) -> Unit,
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit
) {
    if (books.isEmpty()) {
        EmptyMessage(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                ),
            messageResId = R.string.bookshelf_empty
        )
        return
    }

    val columns = if (bookshelfLayoutMode == 0) bookshelfLayoutList else bookshelfLayoutGrid
    val isGridMode = bookshelfLayoutMode != 0
    val totalHorizontalPadding =
        if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) 12.dp else 16.dp
    val gridContentHorizontalPadding = totalHorizontalPadding / 2
    val gridInnerHorizontalPadding = totalHorizontalPadding / 2
    FastScrollLazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isGridMode) gridInnerHorizontalPadding else 0.dp),
        contentPadding = adaptiveContentPadding(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp,
            horizontal = if (isGridMode) gridContentHorizontalPadding else 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
        showFastScroll = BookshelfConfig.showBookshelfFastScroller
    ) {
        items(books, key = { it.bookUrl }) { book ->
            val isSelected = selectedBookUrls.contains(book.bookUrl)
            BookItem(
                book = book,
                modifier = Modifier.animateItem(),
                layoutMode = bookshelfLayoutMode,
                isSelected = isSelected,
                gridStyle = BookshelfConfig.bookshelfGridLayout,
                isCompact = BookshelfConfig.bookshelfLayoutCompact,
                isUpdating = uiState.updatingBooks.contains(book.bookUrl),
                titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                titleCenter = BookshelfConfig.bookshelfTitleCenter,
                titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                coverShadow = BookshelfConfig.bookshelfCoverShadow,
                onClick = {
                    if (isEditMode) {
                        onToggleBookSelection(book)
                    } else {
                        onBookClick(book)
                    }
                },
                onLongClick = {
                    if (isEditMode) {
                        onToggleBookSelection(book)
                    } else {
                        onBookLongClick(book)
                    }
                }
            )
        }
    }
}
