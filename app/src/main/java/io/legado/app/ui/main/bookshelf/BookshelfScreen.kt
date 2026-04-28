package io.legado.app.ui.main.bookshelf

import android.content.ClipData
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveContentPaddingBookshelf
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.SmallOutlinedIconToggleButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyVerticalGrid
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel = koinViewModel(),
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit
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
    var showBatchDownloadConfirmDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState.groups, uiState.isSearch) {
        if (!uiState.isSearch && uiState.groups.isNotEmpty()) {
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

    val currentTabGroupId =
        uiState.groups.getOrNull(pagerState.currentPage)?.groupId ?: BookGroup.IdAll
    val searchGroupExists = uiState.allGroups.any { it.groupId == uiState.selectedGroupId }
    val currentGroupId = if (uiState.isSearch && searchGroupExists) {
        uiState.selectedGroupId
    } else {
        currentTabGroupId
    }
    val isUsingStandaloneSearchGroup = uiState.isSearch &&
            uiState.groups.none { it.groupId == currentGroupId }
    val currentGroupBookCount = uiState.currentGroupBookCount
    val allGroupsBookCount = uiState.allBooksCount

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

    val currentGroupName = uiState.allGroups.firstOrNull { it.groupId == currentGroupId }?.groupName
        ?: uiState.groups.getOrNull(pagerState.currentPage)?.groupName

    val baseTitle = when {
        uiState.isSearch && bookGroupStyle == 0 -> stringResource(R.string.bookshelf)
        uiState.isSearch -> currentGroupName ?: stringResource(R.string.bookshelf)
        bookGroupStyle == 1 -> currentGroupName ?: stringResource(R.string.bookshelf)
        bookGroupStyle == 2 && uiState.groups.isNotEmpty() -> {
            if (isInFolderRoot) stringResource(R.string.bookshelf)
            else currentGroupName ?: stringResource(R.string.bookshelf)
        }

        else -> stringResource(R.string.bookshelf)
    }
    val title = if (isEditMode) {
        stringResource(R.string.bookshelf)
    } else if (uiState.upBooksCount > 0) {
        "$baseTitle (${uiState.upBooksCount})"
    } else {
        baseTitle
    }
    val subtitle = if (isEditMode) {
        "共${allGroupsBookCount}本"
    } else {
        null
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
    val currentMenuGroupId = if (uiState.isSearch) uiState.selectedGroupId else currentTabGroupId
    val editStickySummary = if (isEditMode) {
        BookshelfEditStickySummary(
            selectedCount = selectedBookUrls.size,
            currentGroupTotalCount = currentGroupBookCount,
            groupName = currentGroupName,
            showGroupName = bookGroupStyle != 0
        )
    } else {
        null
    }

    ListScaffold(
        title = title,
        subtitle = subtitle,
        state = uiState,
        showSearchAction = true,
        onSearchToggle = { active ->
            if (BookshelfConfig.bookshelfSearchActionDirectToSearch) {
                onNavigateToSearch(uiState.searchKey.trim())
            } else {
                viewModel.setSearchMode(active)
                if (!active && uiState.selectedGroupId != currentTabGroupId) {
                    viewModel.changeGroup(currentTabGroupId)
                }
            }
        },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        onSearchSubmit = { rawQuery ->
            rawQuery.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(onNavigateToSearch)
        },
        searchTrailingIcon = {
            if (uiState.searchKey.isNotEmpty()) {
                TopBarActionButton(
                    onClick = { viewModel.setSearchKey("") },
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.clear)
                )
            }
        },
        topBarActions = {
            AnimatedVisibility(visible = isEditMode) {
                TopBarActionButton(
                    onClick = {
                        selectedBookUrls = uiState.items.mapTo(hashSetOf()) { it.bookUrl }
                    },
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all)
                )
            }
            AnimatedVisibility(visible = isEditMode) {
                TopBarActionButton(
                    onClick = {
                        val visibleBookUrls = uiState.items.mapTo(hashSetOf()) { it.bookUrl }
                        selectedBookUrls = visibleBookUrls - selectedBookUrls
                    },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.revert_selection)
                )
            }
            AnimatedVisibility(visible = isEditMode) {
                TopBarActionButton(
                    onClick = {
                        if (selectedBookUrls.isNotEmpty()) {
                            showBatchDownloadConfirmDialog = true
                        }
                    },
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.action_download)
                )
            }
            AnimatedVisibility(visible = isEditMode) {
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
                    text = stringResource(R.string.bookshelf_management),
                    onClick = {
                        val groupId =
                            uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                        onNavigateToCache(groupId)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Bookmarks, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.export_bookshelf),
                    onClick = {
                        showExportSheet = true
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.UploadFile, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.import_bookshelf),
                    onClick = { showImportSheet = true; dismiss() },
                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
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
                                                if (uiState.isSearch) {
                                                    viewModel.changeGroup(group.groupId)
                                                }
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                                dismiss()
                                            },
                                            trailingIcon = {
                                                val isSelected = if (uiState.isSearch) {
                                                    uiState.selectedGroupId == group.groupId
                                                } else {
                                                    selectedTabIndex == index
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    if (uiState.isSearch) {
                                        val allGroup = uiState.allGroups.firstOrNull {
                                            it.groupId == BookGroup.IdAll
                                        }
                                        val hiddenGroups = uiState.allGroups.filter {
                                            !it.show && it.groupId != BookGroup.IdAll
                                        }

                                        if (allGroup != null || hiddenGroups.isNotEmpty()) {
                                            PillHeaderDivider(
                                                title = "${stringResource(R.string.all)} / ${stringResource(R.string.hide)}"
                                            )

                                            allGroup?.let { group ->
                                                RoundDropdownMenuItem(
                                                    text = group.groupName,
                                                    onClick = {
                                                        viewModel.changeGroup(group.groupId)
                                                        dismiss()
                                                    },
                                                    trailingIcon = {
                                                        if (uiState.selectedGroupId == group.groupId) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                )
                                            }

                                            hiddenGroups.forEach { group ->
                                                RoundDropdownMenuItem(
                                                    text = group.groupName,
                                                    onClick = {
                                                        viewModel.changeGroup(group.groupId)
                                                        dismiss()
                                                    },
                                                    trailingIcon = {
                                                        if (uiState.selectedGroupId == group.groupId) {
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
                }
            }
        } else null
    ) { paddingValues ->
        var isRefreshing by remember { mutableStateOf(false) }
        val pullToRefreshState = rememberPullToRefreshState()
        val currentGroup = if (uiState.isSearch) {
            uiState.allGroups.firstOrNull { it.groupId == currentGroupId }
        } else {
            uiState.groups.getOrNull(pagerState.currentPage)
        }
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
                if (bookGroupStyle == 2 && isRoot && !isUsingStandaloneSearchGroup) {
                    val folderColumns =
                        if (bookshelfLayoutMode == 0) bookshelfLayoutList else bookshelfLayoutGrid
                    val isGridMode = bookshelfLayoutMode != 0
                    FastScrollLazyVerticalGrid(
                        columns = GridCells.Fixed(folderColumns.coerceAtLeast(1)),
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = adaptiveContentPaddingBookshelf(
                            top = paddingValues.calculateTopPadding(),
                            bottom = 120.dp,
                            horizontal = if (isGridMode) 8.dp else 4.dp
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
                    if (isUsingStandaloneSearchGroup) {
                        BookshelfPage(
                            paddingValues = paddingValues,
                            books = uiState.items,
                            uiState = uiState,
                            bookshelfLayoutMode = bookshelfLayoutMode,
                            bookshelfLayoutGrid = bookshelfLayoutGrid,
                            bookshelfLayoutList = bookshelfLayoutList,
                            isEditMode = isEditMode,
                            selectedBookUrls = selectedBookUrls,
                            canReorderBooks = false,
                            onToggleBookSelection = { toggleBookSelection(it.bookUrl) },
                            onSaveBookOrder = {},
                            onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick
                        )
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
                                    canReorderBooks = isEditMode &&
                                            !uiState.isSearch &&
                                            group.getRealBookSort() == 3,
                                    onToggleBookSelection = { toggleBookSelection(it.bookUrl) },
                                    onSaveBookOrder = { reorderedBooks ->
                                        viewModel.saveBookOrder(reorderedBooks)
                                    },
                                    onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                                    onBookClick = onBookClick,
                                    onBookLongClick = onBookLongClick
                                )
                            }
                        }
                    }
                }
            }

            TopFloatingStickyItem(
                item = editStickySummary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = paddingValues.calculateTopPadding() + 6.dp),
            ) { summary ->
                Box {
                    NormalCard(
                        cornerRadius = 32.dp,
                        containerColor = LegadoTheme.colorScheme.surfaceContainer,
                        contentColor = LegadoTheme.colorScheme.onCardContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(all = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextCard(
                                icon = AppIcons.Close,
                                backgroundColor = LegadoTheme.colorScheme.surfaceContainerHighest,
                                cornerRadius = 16.dp,
                                verticalPadding = 8.dp,
                                horizontalPadding = 8.dp,
                                onClick = exitEditMode
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppText(
                                text = "已选${summary.selectedCount}本",
                                style = LegadoTheme.typography.labelSmallEmphasized
                            )
                            AppText(
                                text = " · 共${summary.currentGroupTotalCount}本",
                                style = LegadoTheme.typography.labelSmallEmphasized
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (summary.showGroupName && !summary.groupName.isNullOrBlank()) {
                                TextCard(
                                    text = summary.groupName,
                                    textStyle = LegadoTheme.typography.labelSmallEmphasized,
                                    backgroundColor = LegadoTheme.colorScheme.surfaceContainerHighest,
                                    cornerRadius = 16.dp,
                                    verticalPadding = 8.dp,
                                    horizontalPadding = 12.dp,
                                    onClick = { showGroupMenu = true }
                                )
                            }
                        }
                    }
                    

                    if (summary.showGroupName) {
                        RoundDropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false }
                        ) { dismiss ->
                            uiState.groups.forEach { group ->
                                RoundDropdownMenuItem(
                                    text = group.groupName,
                                    onClick = {
                                        val targetIndex =
                                            uiState.groups.indexOfFirst { it.groupId == group.groupId }
                                        if (targetIndex >= 0) {
                                            scope.launch {
                                                if (pagerState.currentPage != targetIndex) {
                                                    pagerState.animateScrollToPage(targetIndex)
                                                }
                                            }
                                        }
                                        if (uiState.isSearch || uiState.selectedGroupId != group.groupId) {
                                            viewModel.changeGroup(group.groupId)
                                        }
                                        if (bookGroupStyle == 2) {
                                            isInFolderRoot = false
                                        }
                                        dismiss()
                                    },
                                    trailingIcon = {
                                        if (currentMenuGroupId == group.groupId) {
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

    AppAlertDialog(
        show = showBatchDownloadConfirmDialog,
        onDismissRequest = { showBatchDownloadConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showBatchDownloadConfirmDialog = false
            viewModel.downloadBooks(selectedBookUrls)
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showBatchDownloadConfirmDialog = false }
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

private data class BookshelfEditStickySummary(
    val selectedCount: Int,
    val currentGroupTotalCount: Int,
    val groupName: String?,
    val showGroupName: Boolean,
)

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
    canReorderBooks: Boolean,
    onToggleBookSelection: (BookShelfItem) -> Unit,
    onSaveBookOrder: (books: List<BookShelfItem>) -> Unit,
    onGlobalSearch: () -> Unit,
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit
) {
    if (books.isEmpty()) {
        if (uiState.isSearch) {
            EmptyMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                message = "没有书籍，尝试全局搜索",
                buttonText = "全局搜索",
                onButtonClick = onGlobalSearch
            )
        } else {
            EmptyMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                messageResId = R.string.bookshelf_empty
            )
        }
        return
    }

    val columns = if (bookshelfLayoutMode == 0) bookshelfLayoutList else bookshelfLayoutGrid
    val isGridMode = bookshelfLayoutMode != 0
    val totalHorizontalPadding =
        if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) 12.dp else 16.dp
    val gridContentHorizontalPadding = totalHorizontalPadding / 2
    val gridInnerHorizontalPadding = totalHorizontalPadding / 2
    val hapticFeedback = LocalHapticFeedback.current
    var draggingBooks by remember { mutableStateOf<List<BookShelfItem>?>(null) }
    var pendingSavedBooks by remember { mutableStateOf<List<BookShelfItem>?>(null) }
    val displayBooks = draggingBooks ?: pendingSavedBooks ?: books
    LaunchedEffect(books, pendingSavedBooks, canReorderBooks) {
        if (!canReorderBooks) {
            draggingBooks = null
            pendingSavedBooks = null
            return@LaunchedEffect
        }
        val pending = pendingSavedBooks ?: return@LaunchedEffect
        if (books.map { it.bookUrl } == pending.map { it.bookUrl }) {
            pendingSavedBooks = null
        }
    }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        if (canReorderBooks) {
            draggingBooks = displayBooks.toMutableList().apply {
                move(from.index, to.index)
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            draggingBooks?.let { reorderedBooks ->
                pendingSavedBooks = reorderedBooks
                onSaveBookOrder(reorderedBooks)
                draggingBooks = null
            }
        }
    }
    FastScrollLazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = gridState,
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = adaptiveContentPaddingBookshelf(
            top = paddingValues.calculateTopPadding(),
            bottom = 120.dp,
            horizontal = if (isGridMode) 8.dp else 4.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
        showFastScroll = BookshelfConfig.showBookshelfFastScroller
    ) {
        items(displayBooks, key = { it.bookUrl }) { book ->
            val isSelected = selectedBookUrls.contains(book.bookUrl)
            ReorderableItem(
                state = reorderableState,
                key = book.bookUrl,
                enabled = canReorderBooks
            ) {
                BookItem(
                    book = book,
                    modifier = Modifier.then(
                        if (canReorderBooks) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    draggingBooks = displayBooks
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureEnd
                                    )
                                }
                            )
                        } else {
                            Modifier
                        }
                    ),
                    layoutMode = bookshelfLayoutMode,
                    isSelected = isSelected,
                    gridStyle = BookshelfConfig.bookshelfGridLayout,
                    isCompact = BookshelfConfig.bookshelfLayoutCompact,
                    isUpdating = uiState.updatingBooks.contains(book.bookUrl),
                    titleSmallFont = BookshelfConfig.bookshelfTitleSmallFont,
                    titleCenter = BookshelfConfig.bookshelfTitleCenter,
                    titleMaxLines = BookshelfConfig.bookshelfTitleMaxLines,
                    coverShadow = BookshelfConfig.bookshelfCoverShadow,
                    isSearchMode = uiState.isSearch,
                    searchKey = uiState.searchKey,
                    onClick = {
                        if (isEditMode) {
                            onToggleBookSelection(book)
                        } else {
                            onBookClick(book)
                        }
                    },
                    onLongClick = if (canReorderBooks) {
                        null
                    } else {
                        {
                            if (isEditMode) {
                                onToggleBookSelection(book)
                            } else {
                                onBookLongClick(book)
                            }
                        }
                    }
                )
            }
        }
    }
}
