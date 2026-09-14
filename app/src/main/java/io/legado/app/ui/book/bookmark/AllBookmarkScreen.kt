package io.legado.app.ui.book.bookmark

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val BOOKMARK_TAB = 0
private const val NOTE_TAB = 1
private const val DETAIL_PAGE_COUNT = 2
private const val DETAIL_TEXT_MAX_LINES = 3

/**
 * 书签页主体所处的状态，用于在「总览 / 详情」等状态切换时驱动过渡动画。
 */
private enum class BookmarkBodyState { Loading, EmptyLibrary, Overview, Detail }

@Composable
fun AllBookmarkRouteScreen(
    viewModel: AllBookmarkViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exportMarkdown by remember { mutableStateOf(false) }
    var exportBook by remember { mutableStateOf<BookmarkBookKey?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            val book = exportBook
            if (uri != null && book != null) {
                viewModel.onIntent(AllBookmarkIntent.Export(uri, exportMarkdown, book))
            Toast.makeText(context, context.getString(R.string.export_started), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AllBookmarkEffect.ShowMessage -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    AllBookmarkScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onRequestExport = { book, markdown ->
            exportBook = book
            exportMarkdown = markdown
            exportLauncher.launch(null)
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllBookmarkScreen(
    state: BookmarkUiState,
    onIntent: (AllBookmarkIntent) -> Unit,
    onRequestExport: (BookmarkBookKey, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val selectedBook = state.selectedBook
    var showSearch by remember(selectedBook?.key) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val overviewListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val noteListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val pagerState = rememberPagerState(pageCount = { DETAIL_PAGE_COUNT })
    val selectedTab = pagerState.currentPage
    val bookmarkItems = remember(state.detailItems) {
        state.detailItems.filter { it is BookmarkDetailItemUi.SavedBookmark }
    }
    val noteItems = remember(state.detailItems) {
        state.detailItems.filterIsInstance<BookmarkDetailItemUi.Marking>()
    }
    val activeListState = when {
        selectedBook == null -> overviewListState
        selectedTab == NOTE_TAB -> noteListState
        else -> bookmarkListState
    }
    val bodyState = when {
        state.isLoading -> BookmarkBodyState.Loading
        selectedBook == null && state.books.isEmpty() -> BookmarkBodyState.EmptyLibrary
        selectedBook == null -> BookmarkBodyState.Overview
        else -> BookmarkBodyState.Detail
    }

    BackHandler(enabled = selectedBook != null) { onIntent(AllBookmarkIntent.CloseBook) }
    // 换书时回到该书的初始页：带定位目标直接落在备注页，否则落在书签页
    LaunchedEffect(selectedBook?.key) {
        if (selectedBook == null) return@LaunchedEffect
        val initialPage = if (state.targetMarkingId != null) NOTE_TAB else BOOKMARK_TAB
        if (pagerState.currentPage != initialPage) pagerState.scrollToPage(initialPage)
    }
    // 横滑落位后清掉上一个 tab 留下的搜索词
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { onIntent(AllBookmarkIntent.SetSearchQuery("")) }
    }
    LaunchedEffect(state.targetMarkingId, state.detailItems) {
        val target = state.targetMarkingId ?: return@LaunchedEffect
        if (pagerState.currentPage != NOTE_TAB) pagerState.scrollToPage(NOTE_TAB)
        val index = noteItems.indexOfFirst { it.marking.id == target }
        if (index >= 0) noteListState.scrollToItem(index)
        onIntent(AllBookmarkIntent.TargetConsumed)
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = selectedBook?.key?.name ?: stringResource(R.string.all_bookmark),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarNavigationButton(
                            onClick = {
                                if (selectedBook == null) onBack() else onIntent(AllBookmarkIntent.CloseBook)
                            }
                        )
                    },
                    actions = {
                        TopBarActionButton(
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) onIntent(AllBookmarkIntent.SetSearchQuery(""))
                            },
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                        if (selectedBook != null) {
                            TopBarActionButton(
                                onClick = { showMenu = true },
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(R.string.more_menu),
                            )
                            RoundDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.export_bookmarks_json),
                                    onClick = {
                                        showMenu = false
                                        onRequestExport(selectedBook.key, false)
                                    },
                                )
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.export_bookmarks_markdown),
                                    onClick = {
                                        showMenu = false
                                        onRequestExport(selectedBook.key, true)
                                    },
                                )
                            }
                        }
                    },
                )
                AnimatedVisibility(
                    modifier = Modifier.adaptiveHorizontalPadding(),
                    visible = showSearch,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SearchBar(
                        query = state.searchQuery,
                        onQueryChange = { onIntent(AllBookmarkIntent.SetSearchQuery(it)) },
                        placeholder = if (selectedBook == null) {
                            stringResource(R.string.feature_bookmarks_search_notes)
                        } else {
                            stringResource(
                                R.string.feature_bookmarks_search_current,
                                stringResource(if (selectedTab == BOOKMARK_TAB) R.string.bookmark else R.string.bookmark_mark_note),
                            )
                        },
                        scrollState = activeListState,
                        scope = scope,
                    )
                }
                if (selectedBook != null) {
                    AppTabRow(
                        tabTitles = listOf(
                            "${stringResource(R.string.bookmark)} ${selectedBook.bookmarkCount}",
                            "${stringResource(R.string.bookmark_mark_note)} ${selectedBook.markingCount}",
                        ),
                        selectedTabIndex = selectedTab,
                        onTabSelected = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                            onIntent(AllBookmarkIntent.SetSearchQuery(""))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isScrollable = false,
                    )
                }
            }
        },
    ) { paddingValues ->
        val topPadding = paddingValues.calculateTopPadding()
        AnimatedContent(
            targetState = bodyState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "BookmarkBodyTransition",
            modifier = Modifier.fillMaxSize(),
        ) { body ->
            when (body) {
                BookmarkBodyState.Loading -> EmptyMessage(
                    message = stringResource(R.string.loading),
                    isLoading = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding),
                )

                BookmarkBodyState.EmptyLibrary -> EmptyMessage(
                    message = if (state.searchQuery.isBlank()) stringResource(R.string.no_bookmark)
                    else stringResource(R.string.feature_bookmarks_no_matching_notes),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding),
                )

                BookmarkBodyState.Overview -> BookOverview(
                    books = state.books,
                    listState = overviewListState,
                    topPadding = topPadding,
                    onBookClick = {
                        onIntent(
                            AllBookmarkIntent.OpenBook(
                                it.key,
                                it.matchedMarkingId
                            )
                        )
                    },
                )

                BookmarkBodyState.Detail -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    val pageItems = if (tab == BOOKMARK_TAB) bookmarkItems else noteItems
                    if (pageItems.isEmpty()) {
                        EmptyMessage(
                            message = if (tab == BOOKMARK_TAB) stringResource(R.string.no_bookmark)
                            else stringResource(R.string.feature_bookmarks_no_notes),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topPadding),
                        )
                    } else {
                        DetailList(
                            items = pageItems,
                            listState = if (tab == BOOKMARK_TAB) bookmarkListState else noteListState,
                            topPadding = topPadding,
                            onIntent = onIntent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookOverview(
    books: List<BookmarkBookUi>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    onBookClick: (BookmarkBookUi) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = adaptiveContentPadding(top = topPadding, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(books, key = { "${it.key.name}|${it.key.author}" }) { book ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainer,
                onClick = { onBookClick(book) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = book.key.name,
                            style = LegadoTheme.typography.titleMediumEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.key.author.isNotBlank()) AppText(
                            text = book.key.author,
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        AppText(
                            text = stringResource(
                                R.string.feature_bookmarks_counts,
                                book.bookmarkCount,
                                book.markingCount,
                            ),
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        if (book.matchedMarkingId != null) AppText(
                            text = stringResource(R.string.feature_bookmarks_match_hint),
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.primary,
                        )
                    }
                    CoilBookCover(
                        name = book.key.name,
                        author = book.key.author,
                        path = book.coverPath,
                        sourceOrigin = book.sourceOrigin,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailList(
    items: List<BookmarkDetailItemUi>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    topPadding: androidx.compose.ui.unit.Dp,
    onIntent: (AllBookmarkIntent) -> Unit,
) {
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = adaptiveContentPadding(top = topPadding, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.stableId }, contentType = { it::class }) { item ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                cornerRadius = 14.dp,
                containerColor = LegadoTheme.colorScheme.surfaceContainer,
                onClick = {
                    if (item is BookmarkDetailItemUi.SavedBookmark) editingBookmark = item.bookmark
                },
                onLongClick = {
                    if (item is BookmarkDetailItemUi.SavedBookmark) editingBookmark = item.bookmark
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppText(
                        text = item.chapterName.ifBlank {
                            stringResource(
                                R.string.feature_bookmarks_chapter_fallback,
                                item.chapterIndex + 1
                            )
                        },
                        style = LegadoTheme.typography.labelLarge,
                        color = LegadoTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when (item) {
                        is BookmarkDetailItemUi.SavedBookmark -> {
                            AppText(
                                text = item.bookmark.bookText,
                                style = LegadoTheme.typography.labelMedium,
                                maxLines = DETAIL_TEXT_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.bookmark.content.isNotBlank()) AppText(
                                text = item.bookmark.content,
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                maxLines = DETAIL_TEXT_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        is BookmarkDetailItemUi.Marking -> {
                            AppText(
                                text = item.selectedText,
                                style = LegadoTheme.typography.labelMedium,
                                maxLines = DETAIL_TEXT_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.marking.note.isNotBlank()) AppText(
                                text = item.marking.note,
                                style = LegadoTheme.typography.labelMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant,
                                maxLines = DETAIL_TEXT_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    BookmarkEditSheet(
        show = editingBookmark != null,
        bookmark = editingBookmark ?: Bookmark(),
        onDismiss = { editingBookmark = null },
        onSave = { onIntent(AllBookmarkIntent.UpdateBookmark(it)); editingBookmark = null },
        onDelete = { onIntent(AllBookmarkIntent.DeleteBookmark(it)); editingBookmark = null },
    )
}
