package io.legado.app.ui.main.bookshelf

import android.content.ClipData
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.help.security.BiometricUnlockLauncher
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPaddingBookshelf
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.theme.adaptiveHorizontalPaddingTab
import io.legado.app.ui.widget.components.AppPullToRefresh
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.button.series.ToggleStyle
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyVerticalGrid
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.privacy.PrivateLockedPage
import io.legado.app.ui.widget.components.privacy.findFragmentActivity
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun BookshelfRouteScreen(
    viewModel: BookshelfViewModel = koinViewModel(),
    scrollToTopRequest: Long = 0L,
    onScrollToTopRequestHandled: (Long) -> Unit = {},
    onBookClick: (BookShelfItem, String?) -> Unit,
    onBookLongClick: (book: BookShelfItem, sharedCoverKey: String?) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroupsFlow.collectAsStateWithLifecycle()
    BookshelfScreen(
        uiState = state,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        allGroups = allGroups,
        scrollToTopRequest = scrollToTopRequest,
        onScrollToTopRequestHandled = onScrollToTopRequestHandled,
        onBookClick = onBookClick,
        onBookLongClick = onBookLongClick,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToRemoteImport = onNavigateToRemoteImport,
        onNavigateToLocalImport = onNavigateToLocalImport,
        onNavigateToCache = onNavigateToCache,
        onNavigateToSettings = onNavigateToSettings,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun BookshelfScreen(
    uiState: BookshelfUiState,
    onIntent: (BookshelfIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<BookshelfEffect>,
    allGroups: List<io.legado.app.data.entities.BookGroup>,
    scrollToTopRequest: Long = 0L,
    onScrollToTopRequestHandled: (Long) -> Unit = {},
    onBookClick: (BookShelfItem, String?) -> Unit,
    onBookLongClick: (book: BookShelfItem, sharedCoverKey: String?) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    // 私密功能需要本地密码，未设置时引导去设置页
    onNavigateToSettings: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scope = rememberCoroutineScope()

    ReportDrawnWhen { !uiState.isInitialLoading }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // 资源串在 composable 作用域内解析：LocalContext.current.getString 不感知配置变化，
    // 语言/字体缩放切换后可能拿到过期值（LocalContextGetResourceValueCall）。
    val unlockTitle = stringResource(R.string.private_unlock_title)
    val unlockSubtitle = stringResource(R.string.private_unlock_subtitle)
    val unlockUsePassword = stringResource(R.string.private_unlock_use_password)

    /** 系统生物验证框必须由 FragmentActivity 承载；拿不到宿主就直接用应用内密码 */
    fun launchBiometricUnlock(target: PrivateUnlockTarget) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.PrivatePassword(target)))
            return
        }
        BiometricUnlockLauncher.launch(
            activity = activity,
            title = unlockTitle,
            subtitle = unlockSubtitle,
            negativeButtonText = unlockUsePassword,
            onPassword = { password ->
                onIntent(BookshelfIntent.UnlockPrivateWithBiometricPassword(target, password))
            },
            onFallback = {
                onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.PrivatePassword(target)))
            },
            onError = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }

    /**
     * 点击脱敏的私密书籍：等同于点了一次"验证"。
     *
     * 验证通过后由 ViewModel 打开阅读，而不是在这里直接调 onBookClick——解锁是异步的，
     * 要等列表重新包含这本书再打开，否则事件会早于状态更新而丢失。
     */
    val requestBookUnlock: (String) -> Unit = { bookUrl ->
        onIntent(BookshelfIntent.RequestPrivateUnlock(PrivateUnlockTarget.Book(bookUrl)))
    }

    /**
     * 打开书籍的统一出口。
     *
     * 不在这里撤销授权：撤销的所有权已经移到阅读器入口的私密闸门——
     * 它是"授权存活到离开阅读器为止"的唯一事实来源。在这里提前撤销会让紧随其后的
     * 闸门把刚验证过的用户再拦一次。
     */
    val openBook: (BookShelfItem, String?) -> Unit = { book, sharedCoverKey ->
        onBookClick(book, sharedCoverKey)
    }

    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                is BookshelfEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed && effect.url != null) {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText(
                                    "url",
                                    effect.url
                                )
                            )
                        )
                    }
                }

                is BookshelfEffect.RequestBiometricUnlock -> launchBiometricUnlock(effect.target)

                BookshelfEffect.NavigateToLocalPasswordSettings -> onNavigateToSettings()
            }
        }
    }

    // 解锁是"一致性关键"的动作：等这本书不再是锁定态、且重新出现在列表里之后再打开，
    // 否则事件会早于状态更新而丢失。
    //
    // 两个坑都在 key 与判定上：
    // 1. privateAccess 必须进 key。"每次都要验证"频率下，验证成功只把目标写进
    //    grantedBookUrls，列表内容一眼不变——只拿列表当 key 的话这次授权压根不会触发重启；
    // 2. 判定用"这本书还锁不锁"而不是 privateAccess.isUnlocked，后者的只在
    //    "本次进程验证一次"频率下才置位，用它当门槛会让"每次都要验证"的用户永远打不开。
    LaunchedEffect(
        uiState.pendingOpenBookUrl,
        uiState.privateAccess,
        uiState.items,
        uiState.visibleGroupBooks
    ) {
        val url = uiState.pendingOpenBookUrl ?: return@LaunchedEffect
        val bookUi = uiState.items.firstOrNull { it.book.bookUrl == url }
            ?: uiState.visibleGroupBooks[uiState.selectedGroupId]
                ?.firstOrNull { it.book.bookUrl == url }
            ?: return@LaunchedEffect
        if (uiState.isBookLocked(bookUi)) return@LaunchedEffect
        onIntent(BookshelfIntent.ConsumePendingOpenBook)
        openBook(bookUi.book, null)
    }

    LaunchedEffect(uiState.pendingUploadUrl) {
        val url = uiState.pendingUploadUrl ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "上传成功: $url",
            actionLabel = "复制链接",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) {
            clipboardManager.setClipEntry(
                ClipEntry(ClipData.newPlainText("url", url))
            )
        }
        onIntent(BookshelfIntent.UploadResultConsumed)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val groupId = uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId ?: -1L
                onIntent(BookshelfIntent.ImportFromUri(it, groupId))
            }
        }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(BookshelfIntent.ExportToUri(it, uiState.items)) }
        }
    )

    val activeOverlay = uiState.activeOverlay
    val showGroupMenu = activeOverlay == BookshelfOverlay.GroupMenu
    val isEditMode = uiState.isEditMode
    val selectedBookUrls = uiState.selectedBookUrls
    val isInFolderRoot = uiState.isInFolderRoot
    val bookGroupStyle = uiState.bookGroupStyle

    val transitionState = remember { SeekableTransitionState(isInFolderRoot) }
    val folderTransition = rememberTransition(transitionState, label = "FolderTransition")
    LaunchedEffect(isInFolderRoot) {
        if (transitionState.targetState != isInFolderRoot) {
            transitionState.animateTo(isInFolderRoot)
        }
    }

    val groupOrderKey = remember(uiState.groups) { uiState.groups.map { it.groupId } }
    val pagerState = key(groupOrderKey) {
        rememberPagerState(
            initialPage = uiState.selectedGroupIndex.coerceAtLeast(0),
            pageCount = { uiState.groups.size }
        )
    }
    val folderGridState = rememberLazyGridState()
    val standaloneSearchGridState = rememberLazyGridState()
    val groupGridStates = mutableMapOf<Long, LazyGridState>()
    uiState.groups.forEach { group ->
        key(group.groupId) {
            groupGridStates[group.groupId] = rememberLazyGridState()
        }
    }
    val latestGroups by rememberUpdatedState(uiState.groups)
    val latestSelectedGroupId by rememberUpdatedState(uiState.selectedGroupId)

    LaunchedEffect(uiState.selectedGroupIndex, uiState.groups.size) {
        if (uiState.groups.isNotEmpty()
            && uiState.selectedGroupIndex in uiState.groups.indices
            && pagerState.currentPage != uiState.selectedGroupIndex
        ) {
            pagerState.scrollToPage(uiState.selectedGroupIndex)
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
                        onIntent(BookshelfIntent.ChangeGroup(targetGroupId))
                    }
                }
            }
    }

    val currentTabGroupId by remember {
        derivedStateOf {
            uiState.groups.getOrNull(pagerState.settledPage)?.groupId ?: BookGroup.IdAll
        }
    }
    val searchGroupExists by remember {
        derivedStateOf { uiState.allGroups.any { it.groupId == uiState.selectedGroupId } }
    }
    val currentGroupId by remember {
        derivedStateOf {
            if (uiState.isSearch && searchGroupExists) {
                uiState.selectedGroupId
            } else {
                currentTabGroupId
            }
        }
    }
    val isUsingStandaloneSearchGroup by remember {
        derivedStateOf {
            uiState.isSearch && uiState.groups.none { it.groupId == currentGroupId }
        }
    }
    val isShowingFolderRoot =
        bookGroupStyle == 2 && isInFolderRoot && !isUsingStandaloneSearchGroup
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0L) {
            val targetGridState = when {
                isShowingFolderRoot -> folderGridState
                isUsingStandaloneSearchGroup -> standaloneSearchGridState
                else -> groupGridStates[currentGroupId]
            }
            try {
                targetGridState?.animateScrollToItem(0)
            } finally {
                onScrollToTopRequestHandled(scrollToTopRequest)
            }
        }
    }
    val currentGroupBookCount by remember { derivedStateOf { uiState.currentGroupBookCount } }

    val clearSelection = {
        onIntent(BookshelfIntent.ClearSelection)
    }
    val exitEditMode = {
        onIntent(BookshelfIntent.ExitEditMode)
    }
    val toggleEditMode = {
        onIntent(BookshelfIntent.ToggleEditMode)
    }
    val toggleBookSelection: (String) -> Unit = { bookUrl ->
        onIntent(BookshelfIntent.ToggleBookSelection(bookUrl))
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

    val currentGroupName by remember { derivedStateOf { uiState.currentGroupName } }

    PredictiveBackHandler(enabled = bookGroupStyle == 2 && !isInFolderRoot && !isEditMode) { progress ->
        try {
            progress.collect { backEvent ->
                transitionState.seekTo(backEvent.progress, targetState = true)
            }
            onIntent(BookshelfIntent.SetInFolderRoot(true))
            transitionState.animateTo(true)
        } catch (e: CancellationException) {
            transitionState.animateTo(false)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bookshelfFolderLayoutMode = if (isLandscape) {
        uiState.settings.bookshelfFolderLayoutModeLandscape
    } else {
        uiState.settings.bookshelfFolderLayoutModePortrait
    }
    val bookshelfFolderLayoutGrid = if (isLandscape) {
        uiState.settings.bookshelfFolderLayoutGridLandscape
    } else {
        uiState.settings.bookshelfFolderLayoutGridPortrait
    }
    val bookshelfFolderLayoutList = if (isLandscape) {
        uiState.settings.bookshelfFolderLayoutListLandscape
    } else {
        uiState.settings.bookshelfFolderLayoutListPortrait
    }
    val currentMenuGroupId by remember {
        derivedStateOf { if (uiState.isSearch) uiState.selectedGroupId else currentTabGroupId }
    }
    val editStickySummary by remember {
        derivedStateOf {
            if (uiState.isEditMode) {
                BookshelfEditStickySummary(
                    selectedCount = uiState.selectedBookUrls.size,
                    currentGroupTotalCount = currentGroupBookCount,
                    groupName = currentGroupName,
                    showGroupName = uiState.bookGroupStyle != 0
                )
            } else {
                null
            }
        }
    }

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showTopBarMenu by remember { mutableStateOf(false) }
    val onSearchClick = {
        if (uiState.settings.bookshelfSearchActionDirectToSearch) {
            onNavigateToSearch(uiState.searchKey.trim())
        } else {
            val active = !uiState.isSearch
            onIntent(BookshelfIntent.SetSearchMode(active))
            if (!active && uiState.selectedGroupId != currentTabGroupId) {
                onIntent(BookshelfIntent.ChangeGroup(currentTabGroupId))
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(
                        bottom = 72.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                    )
            )
        },
        topBar = {
            BookshelfTopBar(
                uiState = uiState,
                scrollBehavior = scrollBehavior,
                onSearchClick = onSearchClick,
                onSearchQueryChange = { onIntent(BookshelfIntent.SetSearchKey(it)) },
                onSearchSubmit = { rawQuery ->
                    rawQuery.trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let(onNavigateToSearch)
                },
                onClearSearch = { onIntent(BookshelfIntent.SetSearchKey("")) },
                onExitEditMode = exitEditMode,
                actions = {
                    AnimatedVisibility(visible = isEditMode) {
                        TopBarActionButton(
                            onClick = { onIntent(BookshelfIntent.SelectAllVisible) },
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = stringResource(R.string.select_all)
                        )
                    }
                    AnimatedVisibility(visible = isEditMode) {
                        TopBarActionButton(
                            onClick = { onIntent(BookshelfIntent.InvertVisibleSelection) },
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.revert_selection)
                        )
                    }
                    AnimatedVisibility(visible = isEditMode) {
                        TopBarActionButton(
                            onClick = {
                                if (selectedBookUrls.isNotEmpty()) {
                                    onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.BatchDownloadConfirmDialog))
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
                                    onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupSelectSheet))
                                }
                            },
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = stringResource(R.string.move_to_group)
                        )
                    }
                    AnimatedVisibility(visible = isEditMode && uiState.privateAccess.hasPassword) {
                        val selected = uiState.items.filter { it.book.bookUrl in selectedBookUrls }
                        val targetPrivate = selected.none { it.isPrivate }
                        TopBarActionButton(
                            onClick = {
                                if (selectedBookUrls.isNotEmpty()) {
                                    onIntent(
                                        BookshelfIntent.SetBooksPrivate(
                                            selectedBookUrls,
                                            targetPrivate
                                        )
                                    )
                                }
                            },
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(
                                if (targetPrivate) {
                                    R.string.private_mark_book
                                } else {
                                    R.string.private_unmark_book
                                }
                            )
                        )
                    }

                    if (!isEditMode) {
                        Box {
                            TopBarActionButton(
                                onClick = { showTopBarMenu = true },
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(R.string.more_menu)
                            )
                            RoundDropdownMenu(
                                expanded = showTopBarMenu,
                                onDismissRequest = { showTopBarMenu = false }
                            ) { dismiss ->
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
                                    onClick = {
                                        onIntent(BookshelfIntent.RefreshToc(uiState.items))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.layout_setting),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.ConfigSheet))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.GridView, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.group_manage),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupManageSheet))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.ViewCarousel, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.add_url),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.AddUrlDialog))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Link, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.selection_mode),
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
                                            uiState.groups.getOrNull(uiState.selectedGroupIndex)?.groupId
                                                ?: -1L
                                        onNavigateToCache(groupId)
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Bookmarks, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.export_bookshelf),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.ExportSheet))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.UploadFile, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.import_bookshelf),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.ImportSheet))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.log),
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.LogSheet))
                                        dismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Default.History, null) }
                                )
                            }
                        }
                    }
                },
                bottomContent = {
                    if (bookGroupStyle == 0 && uiState.groups.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveHorizontalPaddingTab(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val selectedTabIndex =
                                pagerState.currentPage.coerceIn(0, uiState.groups.size - 1)
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

                            val showExpandButton = uiState.settings.shouldShowExpandButton
                            if (showExpandButton) {
                                Box(modifier = Modifier) {
                                    SmallToggleButton(
                                        checked = showGroupMenu,
                                        onCheckedChange = {
                                            if (it) {
                                                onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupMenu))
                                            } else {
                                                onIntent(BookshelfIntent.DismissOverlay)
                                            }
                                        },
                                        style = ToggleStyle.Outlined,
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = stringResource(R.string.group_manage)
                                    )
                                    RoundDropdownMenu(
                                        expanded = showGroupMenu,
                                        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
                                    ) { dismiss ->
                                        uiState.groups.forEachIndexed { index, group ->
                                            RoundDropdownMenuItem(
                                                text = group.groupName,
                                                onClick = {
                                                    if (uiState.isSearch) {
                                                        onIntent(BookshelfIntent.ChangeGroup(group.groupId))
                                                    }
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(
                                                            index
                                                        )
                                                    }
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
                                                    title = "${stringResource(R.string.all)} / ${
                                                        stringResource(
                                                            R.string.hide
                                                        )
                                                    }"
                                                )

                                                allGroup?.let { group ->
                                                    RoundDropdownMenuItem(
                                                        text = group.groupName,
                                                        onClick = {
                                                            onIntent(BookshelfIntent.ChangeGroup(group.groupId))
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
                                                            onIntent(BookshelfIntent.ChangeGroup(group.groupId))
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
            )
        }
    ) { paddingValues ->
        val currentGroup by remember {
            derivedStateOf {
                if (uiState.isSearch) {
                    uiState.allGroups.firstOrNull { it.groupId == currentGroupId }
                } else {
                    uiState.groups.getOrNull(pagerState.settledPage)
                }
            }
        }
        val pullToRefreshEnabled by remember {
            derivedStateOf { (currentGroup?.enableRefresh ?: true) && !isEditMode }
        }

        Box(Modifier.fillMaxSize()) {
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { onIntent(BookshelfIntent.RefreshBooks(uiState.items)) },
                enabled = pullToRefreshEnabled,
                topPadding = paddingValues.calculateTopPadding(),
                scrollBehavior = scrollBehavior
            ) {
                folderTransition.AnimatedContent(
                transitionSpec = {
                    val easing = FastOutSlowInEasing
                    val duration = 480
                    if (targetState) {
                        (fadeIn(animationSpec = tween(duration, easing = easing)) +
                                scaleIn(
                                    initialScale = 1.2f,
                                    animationSpec = tween(duration, easing = easing)
                                ))
                            .togetherWith(fadeOut(animationSpec = tween(duration, easing = easing)) +
                                    scaleOut(
                                        targetScale = 0.8f,
                                        animationSpec = tween(duration, easing = easing)
                                    )
                            )
                    } else {
                        (fadeIn(animationSpec = tween(duration, easing = easing)) +
                                scaleIn(
                                    initialScale = 0.8f,
                                    animationSpec = tween(duration, easing = easing)
                                ))
                            .togetherWith(fadeOut(animationSpec = tween(duration, easing = easing)) +
                                    scaleOut(
                                        targetScale = 1.2f,
                                        animationSpec = tween(duration, easing = easing)
                                    )
                            )
                    }
                }
            ) { isRoot ->
                if (uiState.groups.isEmpty() && !uiState.isSearch) {
                    if (!uiState.isInitialLoading) {
                        EmptyMessage(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = paddingValues.calculateTopPadding(),
                                    bottom = 120.dp
                                ),
                            messageResId = R.string.bookshelf_empty
                        )
                    }
                } else if (bookGroupStyle == 2 && isRoot && !isUsingStandaloneSearchGroup) {
                    val folderColumns =
                        if (bookshelfFolderLayoutMode == 0) bookshelfFolderLayoutList else bookshelfFolderLayoutGrid
                    val isGridMode = bookshelfFolderLayoutMode != 0
                    FastScrollLazyVerticalGrid(
                        columns = GridCells.Fixed(folderColumns.coerceAtLeast(1)),
                        state = folderGridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                with(sharedTransitionScope) {
                                    if (this != null) Modifier.skipToLookaheadSize() else Modifier
                                }
                            ),
                        contentPadding = adaptiveContentPaddingBookshelf(
                            top = paddingValues.calculateTopPadding(),
                            bottom = if (uiState.useRaisedBottomInset) 120.dp else 8.dp,
                            horizontal = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
                        showFastScroll = uiState.settings.showBookshelfFastScroller
                    ) {
                        itemsIndexed(
                            uiState.groups,
                            key = { _, it -> it.groupId }) { index, group ->
                            // 私密分组在文件夹根目录也不露出预览封面与书籍数量
                            val groupLocked = uiState.isGroupLocked(group)
                            val countText = if (uiState.settings.showBookCount && !groupLocked) {
                                uiState.groupBookCounts[group.groupId]?.let {
                                    stringResource(R.string.book_count, it)
                                }
                            } else {
                                null
                            }
                            val previewBooks = if (groupLocked) {
                                emptyList()
                            } else {
                                uiState.groupPreviews[group.groupId] ?: emptyList()
                            }
                            if (bookshelfFolderLayoutMode == 0) {
                                BookGroupItemList(
                                    settings = uiState.settings,
                                    group = group,
                                    previewBooks = previewBooks,
                                    countText = countText,
                                    isCompact = uiState.settings.bookshelfLayoutCompact,
                                    titleMaxLines = uiState.settings.bookshelfTitleMaxLines,
                                    coverShadow = uiState.settings.bookshelfCoverShadow,
                                    onClick = {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        onIntent(BookshelfIntent.SetInFolderRoot(false))
                                    },
                                    onLongClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupEditSheet(group.groupId)))
                                    },
                                    onBookClick = { book -> openBook(book, null) }
                                )
                            } else {
                                BookGroupItemGrid(
                                    settings = uiState.settings,
                                    group = group,
                                    previewBooks = previewBooks,
                                    countText = countText,
                                    gridStyle = uiState.settings.bookshelfGridLayout,
                                    titleSmallFont = uiState.settings.bookshelfTitleSmallFont,
                                    titleCenter = uiState.settings.bookshelfTitleCenter,
                                    titleMaxLines = uiState.settings.bookshelfTitleMaxLines,
                                    coverShadow = uiState.settings.bookshelfCoverShadow,
                                    onClick = {
                                        scope.launch { pagerState.scrollToPage(index) }
                                        onIntent(BookshelfIntent.SetInFolderRoot(false))
                                    },
                                    onLongClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupEditSheet(group.groupId)))
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // 注意：锁定态只替换"某个分组自己的内容区"，不能替换整个分页容器，
                    // 否则用户会被卡在锁定页里、连相邻分组都切不过去。
                    if (isUsingStandaloneSearchGroup) {
                        if (uiState.selectedGroupLocked) {
                            PrivateGroupLockedPage(
                                groupId = uiState.selectedGroupId,
                                uiState = uiState,
                                onIntent = onIntent
                            )
                        } else {
                            BookshelfPage(
                                gridState = standaloneSearchGridState,
                                paddingValues = paddingValues,
                                books = uiState.items,
                                uiState = uiState,
                                selectedBookUrls = selectedBookUrls,
                                canReorderBooks = false,
                                onToggleBookSelection = { toggleBookSelection(it.book.bookUrl) },
                                draggingBooks = null,
                                pendingSavedBooks = null,
                                onDragStarted = {},
                                onMoveBook = { _, _, _ -> },
                                onDragFinished = {},
                                onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                                onBookClick = openBook,
                                onBookLongClick = onBookLongClick,
                                onLockedBookClick = requestBookUnlock,
                                isCurrentPage = true,
                                sharedCoverGroupId = currentGroupId,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    with(sharedTransitionScope) {
                                        if (this != null) Modifier.skipToLookaheadSize() else Modifier
                                    }
                                ),
                            beyondViewportPageCount = 0,
                            key = { if (it < uiState.groups.size) uiState.groups[it].groupId else it }
                        ) { pageIndex ->
                            val group = uiState.groups.getOrNull(pageIndex)
                            if (group != null) {
                                // 锁定 ↔ 解锁之间交叉淡化。只锁这个分组的内容区，
                                // 左右相邻分组仍可正常滑动切换。
                                Crossfade(
                                    targetState = uiState.isGroupLocked(group),
                                    animationSpec = tween(220),
                                ) { locked ->
                                    if (locked) {
                                        PrivateGroupLockedPage(
                                            groupId = group.groupId,
                                            uiState = uiState,
                                            onIntent = onIntent
                                        )
                                    } else {
                                        val isSelectedGroup =
                                            group.groupId == uiState.selectedGroupId
                                        val books = uiState.visibleGroupBooks[group.groupId]
                                            ?: persistentListOf()
                                        val canReorderBooks = isEditMode &&
                                                !uiState.isSearch &&
                                                (group.bookSort.takeIf { it >= 0 }
                                                    ?: uiState.bookshelfSort) == 3 &&
                                                isSelectedGroup
                                        BookshelfPage(
                                            gridState = groupGridStates.getValue(group.groupId),
                                            paddingValues = paddingValues,
                                            books = books,
                                            uiState = uiState,
                                            selectedBookUrls = selectedBookUrls,
                                            canReorderBooks = canReorderBooks,
                                            onToggleBookSelection = { toggleBookSelection(it.book.bookUrl) },
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
                                                if (isSelectedGroup) onIntent(
                                                    BookshelfIntent.StartDragging(
                                                        it
                                                    )
                                                )
                                            },
                                            onMoveBook = { from, to, currentBooks ->
                                                if (isSelectedGroup) {
                                                    onIntent(
                                                        BookshelfIntent.MoveDragging(
                                                            from,
                                                            to,
                                                            currentBooks
                                                        )
                                                    )
                                                }
                                            },
                                            onDragFinished = {
                                                if (isSelectedGroup) onIntent(BookshelfIntent.FinishDragging)
                                            },
                                            onGlobalSearch = { onNavigateToSearch(uiState.searchKey.trim()) },
                                            onBookClick = openBook,
                                            onBookLongClick = onBookLongClick,
                                            onLockedBookClick = requestBookUnlock,
                                            isCurrentPage = isSelectedGroup,
                                            sharedCoverGroupId = group.groupId,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                }
                            }
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
                val closeDescription = stringResource(R.string.close)
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
                                modifier = Modifier.semantics {
                                    contentDescription = closeDescription
                                    role = Role.Button
                                },
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
                                    modifier = Modifier.semantics {
                                        role = Role.Button
                                    },
                                    onClick = {
                                        onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupMenu))
                                    }
                                )
                            }
                        }
                    }


                    if (summary.showGroupName) {
                        RoundDropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
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
                                            onIntent(BookshelfIntent.ChangeGroup(group.groupId))
                                        }
                                        if (bookGroupStyle == 2) {
                                            onIntent(BookshelfIntent.SetInFolderRoot(false))
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

        }
    }

    BookshelfOverlays(
        activeOverlay = activeOverlay,
        uiState = uiState,
        onIntent = onIntent,
        allGroups = allGroups,
        selectedBookUrls = selectedBookUrls,
        importLauncher = importLauncher,
        exportLauncher = exportLauncher,
        clearSelection = clearSelection
    )
}

@Composable
private fun BookshelfTopBar(
    uiState: BookshelfUiState,
    scrollBehavior: GlassTopAppBarScrollBehavior,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onClearSearch: () -> Unit,
    onExitEditMode: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable ColumnScope.() -> Unit = {}
) {
    val searchContentDescription = stringResource(R.string.search)
    val exitEditModeDescription = stringResource(R.string.cancel_select)
    GlassMediumFlexibleTopAppBar(
        modifier = Modifier.fillMaxWidth(),
        navigationIcon = {
            if (uiState.isEditMode) {
                TopBarNavigationButton(
                    onClick = onExitEditMode,
                    imageVector = AppIcons.Close,
                    contentDescription = exitEditModeDescription
                )
            }
        },
        title = if (uiState.isLoading) {
            stringResource(R.string.loading)
        } else {
            uiState.title.ifEmpty { stringResource(R.string.bookshelf) }
        },
        useCharMode = uiState.isLoading,
        subtitle = uiState.subtitle,
        scrollBehavior = scrollBehavior,
        actions = {
            TopBarActionButton(
                onClick = onSearchClick,
                imageVector = AppIcons.Search,
                contentDescription = searchContentDescription
            )
            actions()
        },
        bottomContent = {
            AnimatedVisibility(
                modifier = Modifier.adaptiveHorizontalPadding(),
                visible = uiState.isSearch,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SearchBar(
                    query = uiState.searchKey,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearchSubmit,
                    trailingIcon = {
                        if (uiState.searchKey.isNotEmpty()) {
                            SmallPlainButton(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                onClick = onClearSearch,
                                icon = AppIcons.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                )
            }
            bottomContent()
        }
    )
}

@Composable
private fun BookshelfOverlays(
    activeOverlay: BookshelfOverlay?,
    uiState: BookshelfUiState,
    onIntent: (BookshelfIntent) -> Unit,
    allGroups: List<BookGroup>,
    selectedBookUrls: Set<String>,
    importLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
    exportLauncher: ManagedActivityResultLauncher<String, Uri?>,
    clearSelection: () -> Unit
) {
    BookshelfConfigSheet(
        show = activeOverlay == BookshelfOverlay.ConfigSheet,
        settings = uiState.settings,
        onUpdate = { onIntent(BookshelfIntent.UpdateSetting(it)) },
        enableCustomTagColors = uiState.enableCustomTagColors,
        customTagColors = uiState.customTagColors,
        themeColor = uiState.themeColor,
        onCustomTagColorsEnabledChange = {
            onIntent(BookshelfIntent.SetCustomTagColorsEnabled(it))
        },
        onCustomTagColorsChange = {
            onIntent(BookshelfIntent.SetCustomTagColors(it))
        },
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
    )

    GroupManageSheet(
        show = activeOverlay == BookshelfOverlay.GroupManageSheet,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
    )

    // 应用内密码解锁：生物不可用、用户点了"使用密码"，或设备不支持时的统一兜底
    if (activeOverlay is BookshelfOverlay.PrivatePassword) {
        val target = activeOverlay.target
        var password by remember(target) { mutableStateOf("") }
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
            title = stringResource(R.string.private_unlock_password_title),
            content = {
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.private_unlock_password_title),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = {
                onIntent(BookshelfIntent.SubmitPrivatePassword(target, password))
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { onIntent(BookshelfIntent.DismissOverlay) },
        )
    }

    if (activeOverlay is BookshelfOverlay.GroupEditSheet) {
        val editGroup = allGroups.firstOrNull { it.groupId == activeOverlay.groupId }
        if (editGroup != null) {
            GroupEditSheet(
                show = true,
                group = editGroup,
                onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
            )
        }
    }

    GroupSelectSheet(
        show = activeOverlay == BookshelfOverlay.GroupSelectSheet,
        groups = allGroups.filter { it.groupId > 0 },
        currentGroupId = 0L,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
        onConfirm = { groupId ->
            onIntent(BookshelfIntent.MoveBooksToGroup(selectedBookUrls, groupId))
            onIntent(BookshelfIntent.DismissOverlay)
            clearSelection()
        }
    )

    SourceInputDialog(
        show = activeOverlay == BookshelfOverlay.AddUrlDialog,
        title = stringResource(R.string.add_book_url),
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
        onConfirm = { url ->
            onIntent(BookshelfIntent.AddBookByUrl(url))
            onIntent(BookshelfIntent.DismissOverlay)
        }
    )

    FilePickerSheet(
        show = activeOverlay == BookshelfOverlay.ImportSheet,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
        title = stringResource(R.string.import_bookshelf),
        onSelectSysFile = { types ->
            importLauncher.launch(types)
            onIntent(BookshelfIntent.DismissOverlay)
        },
        onManualInput = {
            onIntent(BookshelfIntent.ShowOverlay(BookshelfOverlay.AddUrlDialog))
        },
        allowExtensions = arrayOf("json", "txt")
    )

    FilePickerSheet(
        show = activeOverlay == BookshelfOverlay.ExportSheet,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
        title = stringResource(R.string.export_bookshelf),
        onSelectSysDir = {
            onIntent(BookshelfIntent.DismissOverlay)
            exportLauncher.launch("bookshelf.json")
        },
        onUpload = {
            onIntent(BookshelfIntent.DismissOverlay)
            onIntent(BookshelfIntent.UploadBookshelf(uiState.items))
        }
    )

    AppLogSheet(
        show = activeOverlay == BookshelfOverlay.LogSheet,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) }
    )

    AppAlertDialog(
        show = activeOverlay == BookshelfOverlay.BatchDownloadConfirmDialog,
        onDismissRequest = { onIntent(BookshelfIntent.DismissOverlay) },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            onIntent(BookshelfIntent.DismissOverlay)
            onIntent(BookshelfIntent.DownloadBooks(selectedBookUrls))
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(BookshelfIntent.DismissOverlay) }
    )

    if (uiState.isLoading) {
        val loadingDescription = uiState.loadingText ?: stringResource(R.string.loading)
        Dialog(onDismissRequest = {}) {
            ProvideAppDensity {
                NormalCard(
                    modifier = Modifier.semantics {
                        contentDescription = loadingDescription
                    },
                    cornerRadius = 12.dp,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppCircularProgressIndicator()
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
}

private data class BookshelfEditStickySummary(
    val selectedCount: Int,
    val currentGroupTotalCount: Int,
    val groupName: String?,
    val showGroupName: Boolean,
)

/**
 * 私密分组的锁定态内容区。
 *
 * 只替换"这个分组自己的内容"，不动分页容器——否则用户会被卡在锁定页里，
 * 连相邻分组都切不过去。提示块复用 [PrivateLockedPage]，与详情页脱敏态是同一套呈现。
 */
@Composable
private fun PrivateGroupLockedPage(
    groupId: Long,
    uiState: BookshelfUiState,
    onIntent: (BookshelfIntent) -> Unit,
) {
    PrivateLockedPage(
        title = stringResource(R.string.private_locked_group_title),
        description = stringResource(
            if (uiState.privateAccess.hasPassword) {
                R.string.private_locked_group_desc
            } else {
                R.string.private_content_no_password
            }
        ),
        actionText = stringResource(
            if (uiState.privateAccess.hasPassword) {
                R.string.private_verify_and_view
            } else {
                R.string.set_local_password
            }
        ),
        onAction = {
            onIntent(BookshelfIntent.RequestPrivateUnlock(PrivateUnlockTarget.Group(groupId)))
        }
    )
}

@Composable
fun BookshelfPage(
    gridState: LazyGridState,
    paddingValues: PaddingValues,
    books: ImmutableList<BookUiItem>,
    uiState: BookshelfUiState,
    selectedBookUrls: ImmutableSet<String>,
    canReorderBooks: Boolean,
    onToggleBookSelection: (BookUiItem) -> Unit,
    draggingBooks: ImmutableList<BookUiItem>?,
    pendingSavedBooks: ImmutableList<BookUiItem>?,
    onDragStarted: (ImmutableList<BookUiItem>) -> Unit,
    onMoveBook: (fromIndex: Int, toIndex: Int, currentBooks: ImmutableList<BookUiItem>) -> Unit,
    onDragFinished: () -> Unit,
    onGlobalSearch: () -> Unit,
    onBookClick: (BookShelfItem, String?) -> Unit,
    onBookLongClick: (BookShelfItem, String?) -> Unit,
    /** 点击处于脱敏状态的私密书籍：请求验证，验证通过后自动打开阅读 */
    onLockedBookClick: (String) -> Unit = {},
    isCurrentPage: Boolean = true,
    sharedCoverGroupId: Long,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    if (books.isEmpty()) {
        if (!isCurrentPage) return
        if (uiState.isSearch) {
            EmptyMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 120.dp
                    ),
                message = stringResource(R.string.bookshelf_empty_global_search),
                buttonText = stringResource(R.string.global_search),
                onButtonClick = onGlobalSearch
            )
        } else if (!uiState.isInitialLoading) {
            EmptyMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 120.dp
                    ),
                messageResId = R.string.bookshelf_empty
            )
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bookshelfLayoutMode = if (isLandscape) {
        uiState.settings.bookshelfLayoutModeLandscape
    } else {
        uiState.settings.bookshelfLayoutModePortrait
    }
    val bookshelfLayoutGrid = if (isLandscape) {
        uiState.settings.bookshelfLayoutGridLandscape
    } else {
        uiState.settings.bookshelfLayoutGridPortrait
    }
    val bookshelfLayoutList = if (isLandscape) {
        uiState.settings.bookshelfLayoutListLandscape
    } else {
        uiState.settings.bookshelfLayoutListPortrait
    }
    val columns = if (bookshelfLayoutMode == 0) bookshelfLayoutList else bookshelfLayoutGrid
    val isGridMode = bookshelfLayoutMode != 0
    val bookItemGridStyle = uiState.settings.bookshelfGridLayout
    val bookItemIsCompact = uiState.settings.bookshelfLayoutCompact
    val bookItemTitleSmallFont = uiState.settings.bookshelfTitleSmallFont
    val bookItemTitleCenter = uiState.settings.bookshelfTitleCenter
    val bookItemTitleMaxLines = uiState.settings.bookshelfTitleMaxLines
    val bookItemCoverShadow = uiState.settings.bookshelfCoverShadow
    val showFastScroll = uiState.settings.showBookshelfFastScroller
    val listContentDescription = stringResource(R.string.bookshelf)
    val totalHorizontalPadding =
        if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) 12.dp else 16.dp
    val gridContentHorizontalPadding = totalHorizontalPadding / 2
    val gridInnerHorizontalPadding = totalHorizontalPadding / 2
    val hapticFeedback = LocalHapticFeedback.current
    val displayBooks = draggingBooks ?: pendingSavedBooks ?: books
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                with(sharedTransitionScope) {
                    if (this != null) Modifier.skipToLookaheadSize() else Modifier
                }
            )
    ) {
        FastScrollLazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = listContentDescription }
                .then(
                    with(sharedTransitionScope) {
                        if (this != null) Modifier.skipToLookaheadSize() else Modifier
                    }
                ),
            contentPadding = adaptiveContentPaddingBookshelf(
                top = paddingValues.calculateTopPadding(),
                bottom = if (uiState.useRaisedBottomInset) 120.dp else 8.dp,
                horizontal = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
            showFastScroll = showFastScroll
        ) {
            itemsIndexed(displayBooks, key = { _, item -> item.book.bookUrl }) { index, bookUi ->
                val isSelected = selectedBookUrls.contains(bookUi.book.bookUrl)
                val sharedCoverKey = bookCoverSharedElementKey(
                    bookUi.book.bookUrl,
                    "bookshelf:$sharedCoverGroupId"
                )
                ReorderableItem(
                    state = reorderableState,
                    key = bookUi.book.bookUrl,
                    enabled = canReorderBooks
                ) { isDragging ->
                    val bookLocked = uiState.isBookLocked(bookUi)
                    // 锁定 ⇄ 解锁的过渡在 BookItem 内部完成：封面走模糊深度、文字走占位替换。
                    // 不在这一层整体模糊，是因为整体 blur 会把封面圆角与阴影一起糊掉。
                    BookItem(
                        settings = uiState.settings,
                        customTagColors = if (uiState.enableCustomTagColors) {
                            uiState.customTagColors
                        } else {
                            persistentListOf()
                        },
                        bookUi = bookUi,
                        modifier = Modifier
                            .reorderAccessibility(
                                index = index,
                                itemCount = displayBooks.size,
                                enabled = canReorderBooks,
                            ) { from, to ->
                                onDragStarted(displayBooks)
                                onMoveBook(from, to, displayBooks)
                                onDragFinished()
                            }
                            .then(
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
                            )
                            .graphicsLayer {
                                alpha = if (isDragging) 0.5f else 1f
                            },
                        layoutMode = bookshelfLayoutMode,
                        isSelected = isSelected,
                        gridStyle = bookItemGridStyle,
                        isCompact = bookItemIsCompact,
                        isUpdating = uiState.updatingBooks.contains(bookUi.book.bookUrl),
                        locked = bookLocked,
                        titleSmallFont = bookItemTitleSmallFont,
                        titleCenter = bookItemTitleCenter,
                        titleMaxLines = bookItemTitleMaxLines,
                        coverShadow = bookItemCoverShadow,
                        isSearchMode = uiState.isSearch,
                        searchKey = uiState.searchKey,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedCoverKey = sharedCoverKey,
                        onClick = {
                            if (uiState.isEditMode) {
                                onToggleBookSelection(bookUi)
                            } else if (bookLocked) {
                                onLockedBookClick(bookUi.book.bookUrl)
                            } else {
                                onBookClick(bookUi.book, sharedCoverKey)
                            }
                        },
                        onLongClick = if (canReorderBooks) {
                            null
                        } else {
                            {
                                if (uiState.isEditMode) {
                                    onToggleBookSelection(bookUi)
                                } else {
                                    // 长按一律进详情页：脱敏渲染在详情页内完成，
                                    // 不给"长按只弹验证、进不去详情"的分叉
                                    onBookLongClick(bookUi.book, sharedCoverKey)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
