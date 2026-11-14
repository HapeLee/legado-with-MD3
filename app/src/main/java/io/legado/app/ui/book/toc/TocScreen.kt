package io.legado.app.ui.book.toc

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isLocal
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPaddingOnlyVertical
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AppFloatingActionButtonMenu
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.ChapterStatusIcons
import io.legado.app.ui.widget.components.CollapsibleHeader
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.FabMenuItem
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.bookmark.BookmarkItem
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.button.series.ToggleStyle
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.progressIndicator.AppContainedLoadingIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 卷管理菜单的稳定 ID：两条整卷命令 + 每个卷一条跳转项。 */
private enum class VolumeMenuAction(val id: String) {
    ExpandAll("expand_all_volumes"),
    CollapseAll("collapse_all_volumes"),
}

private const val VolumeJumpIdPrefix = "jump_volume_"

/**
 * 卷管理菜单：整卷命令一组，卷跳转项一组（组间自动补分段线）。
 *
 * @param volumes 卷标题与它在 `state.items` 中的下标；跳转按下标滚动。
 */
@Composable
private fun volumeMenuGroups(volumes: List<Pair<Int, String>>): ImmutableList<AppActionGroup> {
    val groups = mutableListOf(
        AppActionGroup(
            id = "toc_volume_commands",
            actions = persistentListOf(
                AppAction(VolumeMenuAction.ExpandAll.id, stringResource(R.string.expand_volume)),
                AppAction(VolumeMenuAction.CollapseAll.id, stringResource(R.string.coll_volume)),
            ),
        ),
    )
    if (volumes.isNotEmpty()) {
        groups += AppActionGroup(
            id = "toc_volume_jump",
            actions = volumes.map { (index, title) ->
                AppAction(id = "$VolumeJumpIdPrefix$index", label = title)
            }.toImmutableList(),
        )
    }
    return groups.toImmutableList()
}

private fun volumeMenuActionFor(
    id: String,
    onIntent: (TocIntent) -> Unit,
    onJump: (Int) -> Unit,
) {
    when (id) {
        VolumeMenuAction.ExpandAll.id -> onIntent(TocIntent.ExpandAllVolumes)
        VolumeMenuAction.CollapseAll.id -> onIntent(TocIntent.CollapseAllVolumes)
        else -> id.removePrefix(VolumeJumpIdPrefix).toIntOrNull()?.let(onJump)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TocRouteScreen(
    viewModel: TocViewModel = koinViewModel(),
    bookUrl: String? = null,
    initialPage: Int = 0,
    onBackClick: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onOpenReplaceRule: (ReplaceEditRoute?) -> Unit,
    onBookmarkClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    /**
     * 「本地目录规则」入口：宿主导航到规则列表的选择模式，选好后由宿主的
     * `ConsumeBookPageResult` 回灌 `TocIntent.SaveTocRegex`。
     */
    onPickLocalTocRule: (String?) -> Unit,
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingExportMarkdown by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { viewModel.onIntent(TocIntent.ExportBookmarks(it, pendingExportMarkdown)) }
    }
    LaunchedEffect(bookUrl) {
        bookUrl?.let { viewModel.onIntent(TocIntent.LoadBook(it)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TocEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    TocScreen(
        uiState = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onChapterClick = onChapterClick,
        onOpenReplaceRule = onOpenReplaceRule,
        onBookmarkClick = onBookmarkClick,
        onEditLocalTocRule = onPickLocalTocRule,
        onExportBookmarks = { isMarkdown, fileName ->
            pendingExportMarkdown = isMarkdown
            exportLauncher.launch(fileName)
        },
        initialPage = initialPage,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TocScreen(
    uiState: TocUiState,
    onIntent: (TocIntent) -> Unit,
    onBackClick: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onOpenReplaceRule: (ReplaceEditRoute?) -> Unit,
    onBookmarkClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    onEditLocalTocRule: (String?) -> Unit,
    onExportBookmarks: (isMarkdown: Boolean, fileName: String) -> Unit,
    initialPage: Int = 0,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val book = uiState.book
    val state = uiState.action

    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 2)) { 3 }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val offset by remember {
        derivedStateOf {
            listState.layoutInfo.viewportEndOffset / 4
        }
    }

    val isSelectionMode = state.selectedIds.isNotEmpty()

    val hasVolumes = remember(state.items) { state.items.any { it.isVolume } }
    var showVolumeMenu by remember { mutableStateOf(false) }

    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    val useReplace = state.useReplace
    val showWordCount = state.showWordCount
    val bookmarkManagementTitle = stringResource(R.string.bookmark_management)
    val locateCurrentReadingText = stringResource(R.string.locate_current_reading)
    val moveToTopText = stringResource(R.string.move_to_top)
    val moveToBottomText = stringResource(R.string.move_to_bottom)
    val downloadAllText = stringResource(R.string.download_all)
    val invertSelectionText = stringResource(R.string.invert_selection)
    val selectFollowingText = stringResource(R.string.select_following)
    val addBookmarkText = stringResource(R.string.bookmark_add)
    val deleteAssignmentText = stringResource(R.string.cast_assignment_delete)
    val deleteAudioText = stringResource(R.string.read_aloud_audio_download_delete)
    val bookmarkDefaultFileName = stringResource(R.string.bookmark)

    val topBarTitle = remember(
        pagerState.currentPage,
        book?.name,
        book?.durChapterTitle,
    ) {
        when (pagerState.currentPage) {
            0 -> {
                book?.durChapterTitle?.takeIf { it.isNotBlank() } ?: (book?.name ?: "")
            }

            1 -> bookmarkManagementTitle
            else -> book?.name ?: ""
        }
    }

    val topBarSubtitle = remember(
        pagerState.currentPage,
        book?.durChapterIndex,
        book?.totalChapterNum
    ) {
        when (pagerState.currentPage) {
            0 -> {
                val durIndex = (book?.durChapterIndex ?: -1) + 1
                val totalNum = book?.totalChapterNum ?: 0
                if (durIndex > 0 && totalNum > 0) {
                    "$durIndex / $totalNum"
                } else {
                    null
                }
            }

            else -> null
        }
    }

    val isOnTocPage = pagerState.currentPage == 0
    val collapsedVolumes = uiState.collapsedVolumes
    val stickyVolume by remember(state.items, collapsedVolumes, isOnTocPage, listState) {
        derivedStateOf {
            if (!isOnTocPage || state.items.isEmpty()) return@derivedStateOf null
            val firstVisibleIndex = listState.firstVisibleItemIndex
            if (firstVisibleIndex !in state.items.indices) return@derivedStateOf null

            val firstVisibleItem = state.items[firstVisibleIndex]
            val volumeIndex = if (firstVisibleItem.isVolume) {
                firstVisibleIndex
            } else {
                (firstVisibleIndex - 1 downTo 0).firstOrNull {
                    val candidate = state.items[it]
                    candidate.isVolume && candidate.tocLevel < firstVisibleItem.tocLevel
                }
            } ?: return@derivedStateOf null
            val volumeItem = state.items[volumeIndex]
            val isCollapsed = collapsedVolumes.contains(volumeItem.id)
            val shouldStick =
                firstVisibleIndex > volumeIndex || listState.firstVisibleItemScrollOffset > 24

            if (!isCollapsed && shouldStick) volumeItem else null
        }
    }

    val fabItems = remember(
        state.items,
        locateCurrentReadingText,
        moveToTopText,
        moveToBottomText,
        downloadAllText
    ) {
        listOf(
            FabMenuItem(Icons.Default.LocationOn, locateCurrentReadingText) {
                scope.launch {
                    val target = state.items.indexOfFirst { it.isDur }
                    if (target != -1) {
                        listState.animateScrollToItem(
                            index = target,
                            scrollOffset = -offset
                        )
                    }
                }
            },
            FabMenuItem(Icons.Default.VerticalAlignTop, moveToTopText) {
                scope.launch { listState.animateScrollToItem(0) }
            },
            FabMenuItem(Icons.Default.VerticalAlignBottom, moveToBottomText) {
                scope.launch { listState.animateScrollToItem(state.items.size) }
            },
            FabMenuItem(Icons.Default.DownloadForOffline, downloadAllText) {
                onIntent(TocIntent.DownloadAll)
            }
        )
    }

    val selectionSecondaryActions = remember(
        state.selectedIds,
        invertSelectionText,
        selectFollowingText,
        addBookmarkText,
        deleteAssignmentText,
        deleteAudioText
    ) {
        listOf(
            ActionItem(
                text = invertSelectionText,
                icon = Icons.Default.Refresh,
                onClick = { onIntent(TocIntent.InvertSelection) }
            ),
            ActionItem(
                text = selectFollowingText,
                icon = Icons.Default.ExpandMore,
                onClick = { onIntent(TocIntent.SelectFromLast) }
            ),
            ActionItem(
                text = addBookmarkText,
                icon = Icons.Default.BookmarkAdd,
                onClick = { onIntent(TocIntent.AddBookmarksForSelected) }
            ),
            ActionItem(
                text = deleteAssignmentText,
                icon = Icons.Default.Groups,
                onClick = { onIntent(TocIntent.DeleteAssignmentsForSelected) }
            ),
            ActionItem(
                text = deleteAudioText,
                icon = Icons.Default.Download,
                onClick = { onIntent(TocIntent.DeleteAudioDownloadsForSelected) }
            )
        )
    }

    var hasAutoScrolled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.items, book) {
        if (!hasAutoScrolled && state.items.isNotEmpty() && book != null) {
            val durIndex = book.durChapterIndex
            val targetIndex = state.items.indexOfFirst { it.id == durIndex || it.isDur }
            if (targetIndex != -1) {
                delay(100) 
                listState.scrollToItem(
                    index = targetIndex,
                    scrollOffset = -offset
                )
                hasAutoScrolled = true
            }
        }
    }

    var isFabVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown =
                    index > previousIndex || (index == previousIndex && offset > previousOffset)
                val scrollingUp =
                    index < previousIndex || (index == previousIndex && offset < previousOffset)

                when {
                    scrollingDown -> isFabVisible = false
                    scrollingUp -> isFabVisible = true
                }

                previousIndex = index
                previousOffset = offset
            }
    }

    val shouldShowFab = isOnTocPage && !isSelectionMode && isFabVisible

    LaunchedEffect(shouldShowFab) {
        if (!shouldShowFab) {
            fabMenuExpanded = false
        }
    }

    BackHandler(enabled = isSelectionMode) {
        onIntent(TocIntent.ClearSelection)
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DynamicTopAppBar(
                title = topBarTitle,
                subtitle = topBarSubtitle,
                state = state,
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                onSearchToggle = { onIntent(TocIntent.SetSearchMode(it)) },
                onSearchQueryChange = { onIntent(TocIntent.SetSearchQuery(it)) },
                searchPlaceholder = stringResource(R.string.search_chapters),
                onClearSelection = { onIntent(TocIntent.ClearSelection) },
                menuGroups = if (pagerState.currentPage == 0) {
                    buildList {
                        add(
                            AppActionGroup(
                                id = "toc_display",
                                actions = persistentListOf(
                                    AppAction(
                                        TocMenuAction.UseReplace.id,
                                        stringResource(TocMenuAction.UseReplace.labelRes),
                                        selected = useReplace,
                                    ),
                                    AppAction(
                                        TocMenuAction.ShowWordCount.id,
                                        stringResource(TocMenuAction.ShowWordCount.labelRes),
                                        selected = showWordCount,
                                    ),
                                    AppAction(
                                        TocMenuAction.ReverseToc.id,
                                        stringResource(TocMenuAction.ReverseToc.labelRes),
                                    ),
                                    AppAction(
                                        TocMenuAction.UpdateToc.id,
                                        stringResource(TocMenuAction.UpdateToc.labelRes),
                                    ),
                                ),
                            )
                        )
                        add(
                            AppActionGroup(
                                id = "toc_replace",
                                actions = persistentListOf(
                                    AppAction(
                                        TocMenuAction.ReplaceRule.id,
                                        stringResource(TocMenuAction.ReplaceRule.labelRes),
                                    ),
                                    AppAction(
                                        TocMenuAction.AddReplaceRule.id,
                                        stringResource(TocMenuAction.AddReplaceRule.labelRes),
                                    ),
                                ),
                            )
                        )
                        // 原 PillHeaderDivider(local_book_options) 按口径降级为组间纯分段线。
                        if (book?.isLocal == true) {
                            add(
                                AppActionGroup(
                                    id = "toc_local_book",
                                    actions = persistentListOf(
                                        AppAction(
                                            TocMenuAction.LocalTocRule.id,
                                            stringResource(TocMenuAction.LocalTocRule.labelRes),
                                        ),
                                        AppAction(
                                            TocMenuAction.SplitLongChapters.id,
                                            stringResource(TocMenuAction.SplitLongChapters.labelRes),
                                            selected = uiState.isSplitLongChapter,
                                        ),
                                    ),
                                )
                            )
                        }
                    }.toImmutableList()
                } else {
                    persistentListOf(
                        AppActionGroup(
                            id = "toc_export",
                            actions = persistentListOf(
                                AppAction(
                                    TocMenuAction.ExportBookmarksJson.id,
                                    stringResource(TocMenuAction.ExportBookmarksJson.labelRes),
                                ),
                                AppAction(
                                    TocMenuAction.ExportBookmarksMarkdown.id,
                                    stringResource(TocMenuAction.ExportBookmarksMarkdown.labelRes),
                                ),
                            ),
                        )
                    )
                },
                onMenuAction = { id ->
                    when (val behavior = tocMenuBehaviorFor(id)) {
                        is TocMenuBehavior.Dispatch -> onIntent(behavior.intent)
                        TocMenuBehavior.OpenReplaceRuleList -> onOpenReplaceRule(null)
                        TocMenuBehavior.OpenNewReplaceRule -> {
                            val scopes = mutableListOf<String>()
                            book?.name?.let { scopes.add(it) }
                            book?.origin?.let { scopes.add(it) }
                            onOpenReplaceRule(
                                ReplaceEditRoute(
                                    id = -1,
                                    pattern = "",
                                    scope = scopes.joinToString(";"),
                                    isScopeTitle = true,
                                    isScopeContent = false,
                                )
                            )
                        }
                        TocMenuBehavior.EditLocalTocRule -> onEditLocalTocRule(book?.tocUrl)
                        is TocMenuBehavior.ExportBookmarks -> {
                            val dateFormat = SimpleDateFormat(
                                "yyyyMMdd_HHmm",
                                Locale.getDefault()
                            ).format(Date())
                            val extension = if (behavior.isMarkdown) "md" else "json"
                            val initialName =
                                "${book?.name ?: bookmarkDefaultFileName}_$dateFormat.$extension"
                            onExportBookmarks(behavior.isMarkdown, initialName)
                        }
                        null -> Unit
                    }
                },
                bottomContent = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .adaptiveHorizontalPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTabRow(
                            tabTitles = listOf(
                                stringResource(R.string.chapter_list),
                                stringResource(R.string.bookmark),
                                stringResource(R.string.marks),
                            ),
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (pagerState.currentPage == 0 && hasVolumes) {
                            Box {
                                SmallToggleButton(
                                    checked = showVolumeMenu,
                                    onCheckedChange = { showVolumeMenu = it },
                                    style = ToggleStyle.Outlined,
                                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = stringResource(R.string.volume_management)
                                )
                                // 卷列表取代原「快速跳转」标题：标题文字按口径不再渲染，
                                // 卷项单独成组，组间自动补分段线。
                                AppActionMenu(
                                    groups = volumeMenuGroups(
                                        volumes = remember(state.items) {
                                            state.items.mapIndexedNotNull { index, item ->
                                                if (item.isVolume && item.tocLevel == 0) {
                                                    index to item.title
                                                } else {
                                                    null
                                                }
                                            }
                                        },
                                    ),
                                    onAction = { id ->
                                        volumeMenuActionFor(
                                            id,
                                            onIntent = onIntent,
                                            onJump = { targetIndex ->
                                                scope.launch {
                                                    listState.animateScrollToItem(targetIndex)
                                                }
                                            },
                                        )
                                    },
                                    modifier = Modifier.matchParentSize(),
                                    embeddedExpanded = showVolumeMenu,
                                    onDismissRequest = { showVolumeMenu = false },
                                    trigger = {},
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButtonMenu(
                modifier = Modifier
                    .offset(x = 16.dp, y = 16.dp),
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                items = fabItems,
                visible = shouldShowFab,
                focusRequester = focusRequester
            )
        },
        // 多选栏是页面级浮层：必须画在正文采样源之外，否则 COUI 玻璃会采样到自己上（native 闪退）。
        overlay = {
            AnimatedVisibility(
                visible = isSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -ScreenOffset)
                    .padding(bottom = 16.dp)
                    .zIndex(1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SelectionBottomBar(
                    onSelectAll = { onIntent(TocIntent.SelectAll) },
                    onSelectInvert = { onIntent(TocIntent.InvertSelection) },
                    primaryAction = ActionItem(
                        text = stringResource(
                            R.string.download_selected_count,
                            state.selectedIds.size
                        ),
                        icon = Icons.Default.Download,
                        onClick = { onIntent(TocIntent.DownloadSelected) }
                    ),
                    secondaryActions = selectionSecondaryActions
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> ChapterListContent(
                        state = state,
                        collapsedVolumes = collapsedVolumes,
                        onIntent = onIntent,
                        listState = listState,
                        onChapterClick = onChapterClick,
                        contentPadding = adaptiveContentPaddingOnlyVertical(
                            top = padding.calculateTopPadding(),
                            bottom = 120.dp
                        )
                    )

                    1 -> BookmarkListContent(
                        bookmarks = uiState.bookmarks,
                        book = book,
                        onBookmarkLongClick = onBookmarkClick,
                        onBookmarkClick = { bookmark ->
                            editingBookmark = bookmark
                        },
                        contentPadding = adaptiveContentPaddingOnlyVertical(
                            top = padding.calculateTopPadding(),
                            bottom = 120.dp
                        )
                    )

                    2 -> MarkingListContent(
                        markings = uiState.markings,
                        book = book,
                        onMarkingClick = onBookmarkClick,
                        contentPadding = adaptiveContentPaddingOnlyVertical(
                            top = padding.calculateTopPadding(),
                            bottom = 120.dp
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = isOnTocPage && state.titleReplaceProgress != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxWidth()
                    .zIndex(2f)
            ) {
                AppLinearProgressIndicator(
                    progress = state.titleReplaceProgress ?: 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { }
                )
            }

            TopFloatingStickyItem(
                item = stickyVolume,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = padding.calculateTopPadding() + 4.dp, start = 8.dp)
            ) { volume ->
                TextCard(
                    text = volume.title,
                    textStyle = LegadoTheme.typography.labelLarge,
                    backgroundColor = LegadoTheme.colorScheme.cardContainer,
                    contentColor = LegadoTheme.colorScheme.onCardContainer,
                    cornerRadius = 8.dp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 6.dp,
                    onClick = {
                        scope.launch {
                            val index = state.items.indexOfFirst { it.id == volume.id }
                            if (index >= 0) {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
                )
            }
        }

        val bookmarkForSheet = editingBookmark ?: remember(editingBookmark == null) {
            Bookmark()
        }
        BookmarkEditSheet(
            show = editingBookmark != null,
            bookmark = bookmarkForSheet,
            onDismiss = { editingBookmark = null },
            onSave = { updatedBookmark ->
                onIntent(TocIntent.UpdateBookmark(updatedBookmark))
                editingBookmark = null
            },
            onDelete = { bookmarkToDelete ->
                onIntent(TocIntent.DeleteBookmark(bookmarkToDelete))
                editingBookmark = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterListContent(
    state: TocActionState,
    collapsedVolumes: Set<Int>,
    onIntent: (TocIntent) -> Unit,
    listState: LazyListState,
    onChapterClick: (Int) -> Unit,
    contentPadding: PaddingValues
) {

    FastScrollLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {

        state.items.forEach { uiItem ->

            if (uiItem.isVolume) {

                item(key = "volume-${uiItem.id}") {
                    CollapsibleHeader(
                        modifier = Modifier
                            .animateItem()
                            .adaptiveHorizontalPadding(),
                        title = uiItem.title,
                        isCollapsed = collapsedVolumes.contains(uiItem.id),
                        onToggle = { onIntent(TocIntent.ToggleVolume(uiItem.id)) },
                        leadingContent = {
                            repeat(uiItem.tocLevel.coerceIn(0, 6) + 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(LegadoTheme.colorScheme.secondary),
                                )
                            }
                        },
                    )
                }

            } else {

                item(key = uiItem.id) {
                    ChapterItem(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .tocIndent(uiItem.tocLevel),
                        item = uiItem,
                        showWordCount = state.showWordCount,
                        onClick = {
                            if (state.selectedIds.isNotEmpty())
                                onIntent(TocIntent.ToggleSelection(uiItem.id))
                            else
                                onChapterClick(uiItem.id)
                        },
                        onLongClick = {
                            onIntent(TocIntent.ToggleSelection(uiItem.id))
                        },
                        onDownloadClick = {
                            onIntent(TocIntent.DownloadChapter(uiItem.id))
                        }
                    )
                }
            }
        }
    }
}

private fun Modifier.tocIndent(level: Int): Modifier = padding(
    start = (level.coerceIn(0, 6) * 16).dp,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChapterItem(
    modifier: Modifier = Modifier,
    item: TocItemUi,
    showWordCount: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            item.isDur -> LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else -> Color.Transparent
        }, label = "BgColor"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> LegadoTheme.colorScheme.onSurface
            item.isDur -> LegadoTheme.colorScheme.primary
            else -> LegadoTheme.colorScheme.onSurface
        }, label = "BgColor"
    )

    val detailColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> LegadoTheme.colorScheme.onSurfaceVariant
            item.isDur -> LegadoTheme.colorScheme.primary
            else -> LegadoTheme.colorScheme.onSurfaceVariant
        }, label = "BgColor"
    )
    val currentReadingDescription = stringResource(R.string.a11y_current_reading)
    val lockedDescription = stringResource(R.string.a11y_vip_locked)
    val downloadedDescription = stringResource(R.string.a11y_downloaded)
    val downloadingDescription = stringResource(R.string.a11y_downloading)
    val downloadFailedDescription = stringResource(R.string.a11y_download_failed)
    val notDownloadedDescription = stringResource(R.string.a11y_not_downloaded)
    val assignmentStateDescription = stringResource(R.string.cast_assignment_state)
    val audioDownloadStateDescription = when (item.audioDownloadState) {
        TocAudioDownloadState.None -> null
        TocAudioDownloadState.Complete -> stringResource(R.string.read_aloud_audio_download_state)
        TocAudioDownloadState.Partial -> stringResource(R.string.read_aloud_audio_download_partial_state)
        TocAudioDownloadState.Unverified -> stringResource(R.string.read_aloud_audio_download_unverified_state)
    }
    val wordCountDescription = item.wordCount?.let {
        stringResource(R.string.a11y_word_count, it)
    }
    val downloadStateDescription = when (item.downloadState) {
        DownloadState.SUCCESS -> downloadedDescription
        DownloadState.DOWNLOADING -> downloadingDescription
        DownloadState.ERROR -> downloadFailedDescription
        DownloadState.NONE -> notDownloadedDescription
        DownloadState.LOCAL -> null
    }
    val chapterContentDescription = buildList {
        add(item.title)
        item.tag?.takeIf { it.isNotBlank() }?.let(::add)
        if (item.isDur) add(currentReadingDescription)
        if (item.isVip && !item.isPay) add(lockedDescription)
        if (showWordCount) wordCountDescription?.let(::add)
        downloadStateDescription?.let(::add)
        if (item.hasCastAssignment) add(assignmentStateDescription)
        audioDownloadStateDescription?.let(::add)
    }.joinToString(", ")
    val canDownload = item.downloadState == DownloadState.NONE ||
            item.downloadState == DownloadState.ERROR
    val downloadActionDescription = stringResource(
        if (item.downloadState == DownloadState.ERROR) {
            R.string.a11y_retry_chapter
        } else {
            R.string.download_chapter
        },
        item.title
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = chapterContentDescription
                selected = item.isSelected
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .adaptiveHorizontalPadding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isVip && !item.isPay) {
                        AppIcon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.error,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }

                    AppText(
                        text = item.title,
                        style = LegadoTheme.typography.bodyMediumEmphasized.copy(fontWeight = FontWeight.Medium),
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.tag.isNullOrEmpty()) {
                    AppText(
                        text = item.tag,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = detailColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val showStatusIcon =
                remember(item.isDur, item.downloadState, item.wordCount, showWordCount) {
                    if (item.downloadState == DownloadState.LOCAL) {
                        item.isDur || (showWordCount && !item.wordCount.isNullOrEmpty())
                    } else {
                        true
                    }
                }

            ChapterStatusIcons(
                item = item,
                iconSize = 16.dp,
                spacing = 4.dp,
                modifier = Modifier.padding(start = 6.dp),
            )

            if (showStatusIcon) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (canDownload) {
                                Modifier
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = onDownloadClick
                                    )
                                    .semantics {
                                        contentDescription = downloadActionDescription
                                    }
                            } else {
                                Modifier.clearAndSetSemantics { }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StatusIcon(
                        isDur = item.isDur,
                        downloadState = item.downloadState,
                        wordCount = item.wordCount,
                        showWordCount = showWordCount
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListContent(
    bookmarks: List<TocBookmarkItemUi>,
    book: Book?,
    onBookmarkLongClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    contentPadding: PaddingValues
) {
    val listState = rememberLazyListState()

    LaunchedEffect(bookmarks, book?.durChapterIndex) {
        if (bookmarks.isNotEmpty() && book != null) {
            val durIndex = book.durChapterIndex
            var scrollPos = 0
            for ((index, bookmark) in bookmarks.withIndex()) {
                if (bookmark.chapterIndex >= durIndex) break
                scrollPos = index
            }
            listState.scrollToItem(scrollPos)
        }
    }

    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()
                ),
            contentAlignment = Alignment.Center
        ) {
            EmptyMessage(
                message = stringResource(R.string.no_bookmark)
            )
        }
    } else {
        FastScrollLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            items(
                items = bookmarks,
                key = { it.id }
            ) { bookmark ->
                BookmarkItem(
                    bookmark = bookmark.raw,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth(),
                    isDur = book?.durChapterIndex == bookmark.chapterIndex,
                    onClick = {
                        onBookmarkClick(bookmark.raw)
                    },
                    onLongClick = {
                        onBookmarkLongClick(bookmark.chapterIndex, bookmark.chapterPos)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarkingListContent(
    markings: List<TocMarkingItemUi>,
    book: Book?,
    onMarkingClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    contentPadding: PaddingValues,
) {
    if (markings.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            EmptyMessage(message = stringResource(R.string.marks_empty))
        }
        return
    }

    FastScrollLazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(items = markings, key = { it.id }) { marking ->
            val contentColor = if (marking.isDur) {
                LegadoTheme.colorScheme.onSecondaryContainer
            } else {
                LegadoTheme.colorScheme.onSurface
            }
            NormalCard(
                onClick = { onMarkingClick(marking.chapterIndex, marking.chapterPos) },
                containerColor = if (marking.isDur) {
                    LegadoTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                } else {
                    LegadoTheme.colorScheme.surface
                },
                contentColor = contentColor,
                cornerRadius = 0.dp,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .adaptiveHorizontalPadding(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            text = marking.chapterName.ifBlank {
                                stringResource(
                                    R.string.chapter_index_format,
                                    marking.chapterIndex + 1
                                )
                            },
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (marking.bookUrl.isNotBlank() && marking.bookUrl != book?.bookUrl) {
                            AppText(
                                text = stringResource(R.string.marks_other_source),
                                style = LegadoTheme.typography.labelSmall,
                                color = LegadoTheme.colorScheme.error,
                            )
                        }
                    }
                    AppText(
                        text = marking.text,
                        style = LegadoTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (marking.note.isNotBlank()) {
                        AppText(
                            text = marking.note,
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(
    isDur: Boolean,
    downloadState: DownloadState,
    wordCount: String?,
    showWordCount: Boolean
) {

    val targetState = when {
        showWordCount && !wordCount.isNullOrEmpty() && (downloadState == DownloadState.LOCAL || downloadState == DownloadState.SUCCESS) -> "SUCCESS_WORD_COUNT"
        isDur -> "DUR"
        downloadState == DownloadState.DOWNLOADING -> "LOADING"
        downloadState == DownloadState.SUCCESS -> "SUCCESS_ICON"
        downloadState == DownloadState.ERROR -> "ERROR"
        downloadState == DownloadState.LOCAL -> "EMPTY"
        else -> "NONE"
    }

    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.8f))
        },
        label = "StatusIconAnim"
    ) { state ->

        when (state) {

            "EMPTY" -> {
                Box(modifier = Modifier.size(24.dp))
            }

            "DUR" -> {
                AppIcon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = LegadoTheme.colorScheme.secondary
                )
            }

            "LOADING" -> {
                AppContainedLoadingIndicator(
                    modifier = Modifier.size(20.dp)
                )
            }

            "SUCCESS_WORD_COUNT" -> {
                NormalCard(
                    cornerRadius = 12.dp,
                    containerColor = if (isDur) LegadoTheme.colorScheme.primaryContainer else LegadoTheme.colorScheme.surfaceContainer
                ) {
                    if (wordCount != null) {
                        AppText(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            text = wordCount,
                            style = LegadoTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isDur) LegadoTheme.colorScheme.onPrimaryContainer else LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            "SUCCESS_ICON" -> {
                AppIcon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = LegadoTheme.colorScheme.secondary
                )
            }

            "ERROR" -> {
                AppIcon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = LegadoTheme.colorScheme.error
                )
            }

            else -> {
                AppIcon(
                    imageVector = Icons.Outlined.DownloadForOffline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = LegadoTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        }
    }
}
