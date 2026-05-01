package io.legado.app.ui.main.bookshelf

import android.content.ClipData
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveContentPaddingBookshelf
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SelectionActions
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel = koinViewModel(),
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val activeOverlay = uiState.activeOverlay
    val showGroupMenu = activeOverlay == BookshelfOverlay.GroupMenu
    val isEditMode = uiState.isEditMode
    val selectedBookUrls = uiState.selectedBookUrls
    val isInFolderRoot = uiState.isInFolderRoot
    val bookGroupStyle = uiState.bookGroupStyle

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
                val groupId = uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                viewModel.importBookshelf(it, groupId)
            }
        }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { viewModel.exportToUri(it, uiState.items) }
        }
    )

    if (uiState.groups.isEmpty()) {
        ListScaffold(
            title = uiState.title.ifEmpty { stringResource(R.string.bookshelf) },
            subtitle = uiState.subtitle,
            state = uiState,
            showSearchAction = true,
            onSearchToggle = { viewModel.setSearchMode(it) },
            onSearchQueryChange = { viewModel.setSearchKey(it) },
            snackbarHostState = snackbarHostState
        ) { paddingValues ->
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

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedGroupIndex,
        pageCount = { uiState.groups.size }
    )
    val latestGroups by rememberUpdatedState(uiState.groups)
    val latestSelectedGroupId by rememberUpdatedState(uiState.selectedGroupId)

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
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val groups = latestGroups
                if (groups.isNotEmpty() && page in groups.indices) {
                    val targetGroupId = groups[page].groupId
                    if (latestSelectedGroupId != targetGroupId) {
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

    val clearSelection = {
        viewModel.clearSelection()
    }
    val exitEditMode = {
        viewModel.exitEditMode()
    }
    val toggleEditMode = {
        viewModel.toggleEditMode()
    }
    val toggleBookSelection: (String) -> Unit = { bookUrl ->
        viewModel.toggleBookSelection(bookUrl)
    }

    LaunchedEffect(pagerState.currentPage, isInFolderRoot) {
        clearSelection()
    }

    BackHandler(enabled = isEditMode) {
        if (selectedBookUrls.isNotEmpty()) {
            clearSelection()
        } else {
            exitEditMode()
        }
    }

    val currentGroupName = uiState.currentGroupName

    if (bookGroupStyle == 2 && !isInFolderRoot && !isEditMode) {
        BackHandler {
            viewModel.setInFolderRoot(true)
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
        title = uiState.title.ifEmpty { stringResource(R.string.bookshelf) },
        subtitle = uiState.subtitle,
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
                    onClick = { viewModel.selectAllVisible() },
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all)
                )
            }
            AnimatedVisibility(visible = isEditMode) {
                TopBarActionButton(
                    onClick = { viewModel.invertVisibleSelection() },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.revert_selection)
                )
            }
            AnimatedVisibility(visible = isEditMode) {
                TopBarActionButton(
                    onClick = {
                        if (selectedBookUrls.isNotEmpty()) {
                            viewModel.showOverlay(BookshelfOverlay.BatchDownloadConfirmDialog)
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
                            viewModel.showOverlay(BookshelfOverlay.GroupSelectSheet)
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
                    onClick = {
                        viewModel.showOverlay(BookshelfOverlay.ConfigSheet)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.GridView, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.group_manage),
                    onClick = {
                        viewModel.showOverlay(BookshelfOverlay.GroupManageSheet)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.add_url),
                    onClick = {
                        viewModel.showOverlay(BookshelfOverlay.AddUrlDialog)
                        dismiss()
                    },
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
                        viewModel.showOverlay(BookshelfOverlay.ExportSheet)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.UploadFile, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.import_bookshelf),
                    onClick = {
                        viewModel.showOverlay(BookshelfOverlay.ImportSheet)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.log),
                    onClick = {
                        viewModel.showOverlay(BookshelfOverlay.LogSheet)
                        dismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.History, null) }
                )
            }
        } else null,
        selectionActions = if (isEditMode) {
            SelectionActions(
                primaryAction = ActionItem(
                    text = stringResource(R.string.action_download),
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = {
                        if (selectedBookUrls.isNotEmpty()) {
                            viewModel.showOverlay(BookshelfOverlay.BatchDownloadConfirmDialog)
                        }
                    }
                ),
                secondaryActions = listOf(
                    ActionItem(
                        text = stringResource(R.string.move_to_group),
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        onClick = {
                            if (selectedBookUrls.isNotEmpty()) {
                                viewModel.showOverlay(BookshelfOverlay.GroupSelectSheet)
                            }
                        }
                    )
                ),
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllVisible() },
                onSelectInvert = { viewModel.invertVisibleSelection() }
            )
        } else {
            null
        },
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
                                    onCheckedChange = {
                                        if (it) {
                                            viewModel.showOverlay(BookshelfOverlay.GroupMenu)
                                        } else {
                                            viewModel.dismissOverlay()
                                        }
                                    },
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = stringResource(R.string.group_manage)
                                )
                                RoundDropdownMenu(
                                    expanded = showGroupMenu,
                                    onDismissRequest = { viewModel.dismissOverlay() }
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
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refreshBooks(uiState.items) },
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
                                        viewModel.setInFolderRoot(false)
                                    },
                                    onLongClick = {
                                        viewModel.showOverlay(BookshelfOverlay.GroupManageSheet)
                                    }
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
                                        viewModel.setInFolderRoot(false)
                                    },
                                    onLongClick = {
                                        viewModel.showOverlay(BookshelfOverlay.GroupManageSheet)
                                    }
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
                            draggingBooks = null,
                            pendingSavedBooks = null,
                            onDragStarted = {},
                            onMoveBook = { _, _, _ -> },
                            onDragFinished = {},
                            onSyncDragState = { _, _ -> },
                            onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            key = { if (it < uiState.groups.size) uiState.groups[it].groupId else it }
                        ) { pageIndex ->
                            val group = uiState.groups.getOrNull(pageIndex)
                            if (group != null) {
                                val isSelectedGroup = group.groupId == uiState.selectedGroupId
                                val books = if (isSelectedGroup) {
                                    uiState.items
                                } else {
                                    emptyList()
                                }
                                val canReorderBooks = isEditMode &&
                                        !uiState.isSearch &&
                                        (group.bookSort.takeIf { it >= 0 }
                                            ?: uiState.bookshelfSort) == 3 &&
                                        isSelectedGroup
                                BookshelfPage(
                                    paddingValues = paddingValues,
                                    books = books,
                                    uiState = uiState,
                                    bookshelfLayoutMode = bookshelfLayoutMode,
                                    bookshelfLayoutGrid = bookshelfLayoutGrid,
                                    bookshelfLayoutList = bookshelfLayoutList,
                                    isEditMode = isEditMode,
                                    selectedBookUrls = selectedBookUrls,
                                    canReorderBooks = canReorderBooks,
                                    onToggleBookSelection = { toggleBookSelection(it.bookUrl) },
                                    draggingBooks = if (isSelectedGroup) {
                                        uiState.draggingBooks
                                    } else {
                                        null
                                    },
                                    pendingSavedBooks = if (isSelectedGroup) {
                                        uiState.pendingSavedBooks
                                    } else {
                                        null
                                    },
                                    onDragStarted = {
                                        if (isSelectedGroup) viewModel.startDraggingBooks(it)
                                    },
                                    onMoveBook = { from, to, currentBooks ->
                                        if (isSelectedGroup) {
                                            viewModel.moveDraggingBook(from, to, currentBooks)
                                        }
                                    },
                                    onDragFinished = {
                                        if (isSelectedGroup) viewModel.finishDraggingBooks()
                                    },
                                    onSyncDragState = { currentBooks, canReorder ->
                                        if (isSelectedGroup) {
                                            viewModel.syncDragState(currentBooks, canReorder)
                                        }
                                    },
                                    onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                                    onBookClick = onBookClick,
                                    onBookLongClick = onBookLongClick,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
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
                                text = stringResource(
                                    R.string.bookshelf_selected_count,
                                    summary.selectedCount
                                ),
                                style = LegadoTheme.typography.labelSmallEmphasized
                            )
                            AppText(
                                text = " · ${
                                    stringResource(
                                        R.string.bookshelf_total_count,
                                        summary.currentGroupTotalCount
                                    )
                                }",
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
                                    onClick = {
                                        viewModel.showOverlay(BookshelfOverlay.GroupMenu)
                                    }
                                )
                            }
                        }
                    }
                    

                    if (summary.showGroupName) {
                        RoundDropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { viewModel.dismissOverlay() }
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
                                            viewModel.setInFolderRoot(false)
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
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = paddingValues.calculateTopPadding())
            )
        }
    }

    BookshelfConfigSheet(
        show = activeOverlay == BookshelfOverlay.ConfigSheet,
        onDismissRequest = { viewModel.dismissOverlay() }
    )

    GroupManageSheet(
        show = activeOverlay == BookshelfOverlay.GroupManageSheet,
        onDismissRequest = { viewModel.dismissOverlay() }
    )

    GroupSelectSheet(
        show = activeOverlay == BookshelfOverlay.GroupSelectSheet,
        currentGroupId = 0L,
        onDismissRequest = { viewModel.dismissOverlay() },
        onConfirm = { groupId ->
            viewModel.moveBooksToGroup(selectedBookUrls, groupId)
            viewModel.dismissOverlay()
            clearSelection()
        }
    )

    SourceInputDialog(
        show = activeOverlay == BookshelfOverlay.AddUrlDialog,
        title = stringResource(R.string.add_book_url),
        onDismissRequest = { viewModel.dismissOverlay() },
        onConfirm = { url ->
            viewModel.addBookByUrl(url)
            viewModel.dismissOverlay()
        }
    )

    FilePickerSheet(
        show = activeOverlay == BookshelfOverlay.ImportSheet,
        onDismissRequest = { viewModel.dismissOverlay() },
        title = stringResource(R.string.import_bookshelf),
        onSelectSysFile = { types ->
            importLauncher.launch(types)
            viewModel.dismissOverlay()
        },
        onManualInput = {
            viewModel.showOverlay(BookshelfOverlay.AddUrlDialog)
        },
        allowExtensions = arrayOf("json", "txt")
    )

    FilePickerSheet(
        show = activeOverlay == BookshelfOverlay.ExportSheet,
        onDismissRequest = { viewModel.dismissOverlay() },
        title = stringResource(R.string.export_bookshelf),
        onSelectSysDir = {
            viewModel.dismissOverlay()
            exportLauncher.launch("bookshelf.json")
        },
        onUpload = {
            viewModel.dismissOverlay()
            viewModel.uploadBookshelf(uiState.items)
        }
    )

    AppLogSheet(
        show = activeOverlay == BookshelfOverlay.LogSheet,
        onDismissRequest = { viewModel.dismissOverlay() }
    )

    AppAlertDialog(
        show = activeOverlay == BookshelfOverlay.BatchDownloadConfirmDialog,
        onDismissRequest = { viewModel.dismissOverlay() },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            viewModel.dismissOverlay()
            viewModel.downloadBooks(selectedBookUrls)
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { viewModel.dismissOverlay() }
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
    draggingBooks: List<BookShelfItem>?,
    pendingSavedBooks: List<BookShelfItem>?,
    onDragStarted: (List<BookShelfItem>) -> Unit,
    onMoveBook: (fromIndex: Int, toIndex: Int, currentBooks: List<BookShelfItem>) -> Unit,
    onDragFinished: () -> Unit,
    onSyncDragState: (books: List<BookShelfItem>, canReorderBooks: Boolean) -> Unit,
    onGlobalSearch: () -> Unit,
    onBookClick: (BookShelfItem) -> Unit,
    onBookLongClick: (BookShelfItem) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
                message = stringResource(R.string.bookshelf_empty_global_search),
                buttonText = stringResource(R.string.global_search),
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
    val displayBooks = draggingBooks ?: pendingSavedBooks ?: books
    LaunchedEffect(books, pendingSavedBooks, canReorderBooks) {
        onSyncDragState(books, canReorderBooks)
    }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        if (canReorderBooks) {
            onMoveBook(from.index, to.index, displayBooks)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onDragFinished()
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
                                    onDragStarted(displayBooks)
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
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedCoverKey = bookCoverSharedElementKey(book.bookUrl),
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
