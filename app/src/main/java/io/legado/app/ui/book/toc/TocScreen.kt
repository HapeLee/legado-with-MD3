package io.legado.app.ui.book.toc

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isLocal
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.widget.CollapsibleHeader
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.bookmark.BookmarkItem
import io.legado.app.ui.widget.components.button.SmallOutlinedIconToggleButton
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TocScreen(
    viewModel: TocViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onChapterClick: (Int) -> Unit,
    onOpenReplaceRule: (ReplaceEditRoute?) -> Unit,
    onBookmarkClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
) {

    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val book by viewModel.bookState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState { 2 }
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

    val useReplace = viewModel.useReplace
    val showWordCount = viewModel.showWordCount

    val subtitle = remember(pagerState.currentPage, book?.durChapterTitle) {
        when (pagerState.currentPage) {
            0 -> book?.durChapterTitle
            1 -> "书签管理"
            else -> null
        }
    }

    val isOnTocPage = pagerState.currentPage == 0

    val fabItems = remember(state.items) {
        listOf(
            FabAction(Icons.Default.LocationOn, "定位至当前阅读") {
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
            FabAction(Icons.Default.VerticalAlignTop, "移至顶部") {
                scope.launch { listState.animateScrollToItem(0) }
            },
            FabAction(Icons.Default.VerticalAlignBottom, "移至底部") {
                scope.launch { listState.animateScrollToItem(state.items.size) }
            },
            FabAction(Icons.Default.DownloadForOffline, "下载全部") {
                viewModel.downloadAll()
            }
        )
    }

    val selectionSecondaryActions = remember(state.selectedIds) {
        listOf(
            ActionItem(
                text = "反选",
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { viewModel.invertSelection() }
            ),
            ActionItem(
                text = "选择后续",
                icon = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
                onClick = { viewModel.selectFromLast() }
            ),
            ActionItem(
                text = "添加书签",
                icon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                onClick = { /* TODO: viewModel.addBookmarksForSelected() */ }
            )
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            val isActuallyMd = it.toString().endsWith(".md", ignoreCase = true)
            viewModel.exportCurrentBookBookmarks(it, isActuallyMd)
        }
    }

    val tocRegexLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newRegex = result.data?.getStringExtra("tocRegex")
            viewModel.saveTocRegex(newRegex ?: "")
        }
    }

    var hasAutoScrolled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.items) {
        if (!hasAutoScrolled && state.items.isNotEmpty()) {
            val targetIndex = state.items.indexOfFirst { it.isDur }
            if (targetIndex != -1) {
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
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DynamicTopAppBar(
                title = book?.name ?: "",
                subtitle = subtitle,
                state = state,
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                onSearchToggle = { viewModel.setSearchMode(it) },
                onSearchQueryChange = { viewModel.setSearchKey(it) },
                searchPlaceholder = "搜索章节...",
                onClearSelection = { viewModel.clearSelection() },
                dropDownMenuContent = { dismiss ->
                    when (pagerState.currentPage) {
                        0 -> {
                            RoundDropdownMenuItem(
                                text = "使用替换规则",
                                trailingIcon = {
                                    Checkbox(checked = useReplace, onCheckedChange = null)
                                },
                                onClick = { viewModel.toggleUseReplace() }
                            )
                            RoundDropdownMenuItem(
                                text = "显示字数",
                                trailingIcon = {
                                    Checkbox(checked = showWordCount, onCheckedChange = null)
                                },
                                onClick = { viewModel.toggleShowWordCount() }
                            )
                            RoundDropdownMenuItem(
                                text = "反转目录",
                                onClick = { viewModel.reverseToc() }
                            )
                            PillDivider()
                            RoundDropdownMenuItem(
                                text = "替换规则",
                                onClick = {
                                    onOpenReplaceRule(null)
                                    dismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = "新建替换规则",
                                onClick = {
                                    val scopes = mutableListOf<String>()
                                    book?.name?.let { scopes.add(it) }
                                    book?.origin?.let { scopes.add(it) }

                                    val editRoute = ReplaceEditRoute(
                                        id = -1,
                                        pattern = "",
                                        scope = scopes.joinToString(";"),
                                        isScopeTitle = true,
                                        isScopeContent = false
                                    )
                                    onOpenReplaceRule(editRoute)
                                    dismiss()
                                }
                            )
                            if (book?.isLocal == true) {
                                PillHeaderDivider(title = "本地书籍选项")
                                RoundDropdownMenuItem(
                                    text = "本地书籍目录规则",
                                    onClick = {
                                        val intent =
                                            Intent(context, TxtTocRuleActivity::class.java).apply {
                                                putExtra("tocRegex", book?.tocUrl)
                                            }
                                        tocRegexLauncher.launch(intent)
                                        dismiss()
                                    }
                                )
                                RoundDropdownMenuItem(
                                    text = "拆分超长章节",
                                    trailingIcon = {
                                        Checkbox(
                                            checked = viewModel.isSplitLongChapter,
                                            onCheckedChange = null
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleSplitLongChapter()
                                        dismiss()
                                    }
                                )
                            }
                        }

                        else -> {
                            RoundDropdownMenuItem(
                                text = "导出书签为JSON",
                                onClick = {
                                    val dateFormat = SimpleDateFormat(
                                        "yyyyMMdd_HHmm",
                                        Locale.getDefault()
                                    ).format(Date())
                                    val initialName = "${book?.name ?: "书签"}_$dateFormat.json"
                                    exportLauncher.launch(initialName)
                                    dismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = "导出书签为MarkDown",
                                onClick = {
                                    val dateFormat = SimpleDateFormat(
                                        "yyyyMMdd_HHmm",
                                        Locale.getDefault()
                                    ).format(Date())
                                    val initialName = "${book?.name ?: "书签"}_$dateFormat.md"
                                    exportLauncher.launch(initialName)
                                    dismiss()
                                }
                            )
                        }
                    }
                },
                bottomContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            edgePadding = 0.dp,
                            divider = {},
                            modifier = Modifier.weight(1f)
                        ) {
                            Tab(
                                selected = pagerState.currentPage == 0,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                text = {
                                    Text(
                                        text = "目录",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            Tab(
                                selected = pagerState.currentPage == 1,
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                text = {
                                    Text(
                                        text = "书签",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }

                        if (pagerState.currentPage == 0 && hasVolumes) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                            ) {
                                SmallOutlinedIconToggleButton(
                                    checked = showVolumeMenu,
                                    onCheckedChange = { showVolumeMenu = it },
                                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "卷管理"
                                )
                                RoundDropdownMenu(
                                    expanded = showVolumeMenu,
                                    onDismissRequest = { showVolumeMenu = false }
                                ) {
                                    RoundDropdownMenuItem(
                                        text = "展开所有卷",
                                        onClick = {
                                            viewModel.expandAllVolumes(); showVolumeMenu = false
                                        }
                                    )
                                    RoundDropdownMenuItem(
                                        text = "收起所有卷",
                                        onClick = {
                                            viewModel.collapseAllVolumes(); showVolumeMenu = false
                                        }
                                    )

                                    val volumeItems =
                                        remember(state.items) { state.items.filter { it.isVolume } }
                                    if (volumeItems.isNotEmpty()) {
                                        PillHeaderDivider(title = "快速跳转")
                                        volumeItems.forEach { uiItem ->
                                            RoundDropdownMenuItem(
                                                text = uiItem.title,
                                                onClick = {
                                                    scope.launch {
                                                        val targetIndex =
                                                            state.items.indexOf(uiItem)
                                                        if (targetIndex != -1) {
                                                            listState.animateScrollToItem(
                                                                index = targetIndex
                                                            )
                                                        }
                                                    }
                                                    showVolumeMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                modifier = Modifier
                    .offset(x = 16.dp, y = 16.dp),
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier = Modifier
                            .animateFloatingActionButton(
                                visible = shouldShowFab,
                                alignment = Alignment.BottomEnd,
                            )
                            .focusRequester(focusRequester),
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.AutoMirrored.Filled.MenuOpen
                            }
                        }
                        Icon(
                            imageVector = imageVector,
                            contentDescription = "Menu",
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                }
            ) {
                fabItems.forEach { (icon, label, action) ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            action()
                            fabMenuExpanded = false
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        text = { Text(text = label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    onSelectAll = { viewModel.selectAll() },
                    onSelectInvert = { viewModel.invertSelection() },
                    primaryAction = ActionItem(
                        text = "下载已选 (${state.selectedIds.size})",
                        icon = { Icon(Icons.Default.Download, null) },
                        onClick = { viewModel.downloadSelected() }
                    ),
                    secondaryActions = selectionSecondaryActions
                )
            }

            Column(modifier = Modifier.padding(padding)) {
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> ChapterListContent(
                            viewModel = viewModel,
                            listState = listState,
                            onChapterClick = onChapterClick,
                            contentPadding = PaddingValues(bottom = if (isSelectionMode) 80.dp else 0.dp)
                        )

                        1 -> BookmarkListContent(
                            viewModel = viewModel,
                            onBookmarkLongClick = onBookmarkClick,
                            onBookmarkClick = { bookmark ->
                                editingBookmark = bookmark
                            },
                            contentPadding = PaddingValues(bottom = if (isSelectionMode) 80.dp else 0.dp)
                        )
                    }
                }
            }
        }

        if (editingBookmark != null) {
            BookmarkEditSheet(
                bookmark = editingBookmark!!,
                onDismiss = { editingBookmark = null },
                onSave = { updatedBookmark ->
                    viewModel.updateBookmark(updatedBookmark)
                    editingBookmark = null
                },
                onDelete = { bookmarkToDelete ->
                    viewModel.deleteBookmark(bookmarkToDelete)
                    editingBookmark = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterListContent(
    viewModel: TocViewModel,
    listState: LazyListState,
    onChapterClick: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collapsedVolumes by viewModel.collapsedVolumes.collectAsStateWithLifecycle()

    FastScrollLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {

        state.items.forEach { uiItem ->

            if (uiItem.isVolume) {

                stickyHeader(key = "volume-${uiItem.id}") {
                    CollapsibleHeader(
                        modifier = Modifier.animateItem(),
                        title = uiItem.title,
                        isCollapsed = collapsedVolumes.contains(uiItem.id),
                        onToggle = { viewModel.toggleVolume(uiItem.id) }
                    )
                }

            } else {

                item(key = uiItem.id) {
                    ChapterItem(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth(),
                        item = uiItem,
                        showWordCount = viewModel.showWordCount,
                        onClick = {
                            if (state.selectedIds.isNotEmpty())
                                viewModel.toggleSelection(uiItem.id)
                            else
                                onChapterClick(uiItem.id)
                        },
                        onLongClick = {
                            viewModel.toggleSelection(uiItem.id)
                        },
                        onDownloadClick = {
                            viewModel.downloadChapter(uiItem.id)
                        }
                    )
                }
            }
        }
    }
}

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
            item.isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            item.isDur -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else -> Color.Transparent
        }, label = "BgColor"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> MaterialTheme.colorScheme.onSurface
            item.isDur -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }, label = "BgColor"
    )

    val detailColor by animateColorAsState(
        targetValue = when {
            item.isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
            item.isDur -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }, label = "BgColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isVip && !item.isPay) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMediumEmphasized.copy(fontWeight = FontWeight.Medium),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!item.tag.isNullOrEmpty()) {
                    Text(
                        text = item.tag,
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = detailColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.downloadState != DownloadState.LOCAL) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentSize()
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = {
                                if (item.downloadState == DownloadState.NONE) {
                                    onDownloadClick()
                                }
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
    viewModel: TocViewModel,
    onBookmarkLongClick: (chapterIndex: Int, chapterPos: Int) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    contentPadding: PaddingValues
) {
    val bookmarks by viewModel.bookmarkUiList.collectAsStateWithLifecycle()
    val book by viewModel.bookState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(bookmarks, book?.durChapterIndex) {
        if (bookmarks.isNotEmpty() && book != null) {
            val durIndex = book!!.durChapterIndex
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
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyMessageView(
                message = "暂无书签"
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

@Composable
private fun StatusIcon(
    isDur: Boolean,
    downloadState: DownloadState,
    wordCount: String?,
    showWordCount: Boolean
) {

    val targetState = when {
        isDur -> "DUR"
        downloadState == DownloadState.DOWNLOADING -> "LOADING"
        downloadState == DownloadState.SUCCESS && showWordCount && !wordCount.isNullOrEmpty() -> "SUCCESS_WORD_COUNT"
        downloadState == DownloadState.SUCCESS -> "SUCCESS_ICON"
        downloadState == DownloadState.ERROR -> "ERROR"
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

            "DUR" -> {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            "LOADING" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            "SUCCESS_WORD_COUNT" -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    ),
                    color = Color.Transparent
                ) {
                    if (wordCount != null) {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            text = wordCount,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            "SUCCESS_ICON" -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            "ERROR" -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Outlined.DownloadForOffline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        }
    }
}
