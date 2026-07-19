package io.legado.app.ui.book.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.MatchMode
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveContentPaddingOnlyVertical
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.book.SearchBookPreviewSheet
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.TopBarAnimatedActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchRouteScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onOpenSourceManage: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenBookInfo -> {
                    onOpenBookInfo(
                        effect.name,
                        effect.author,
                        effect.bookUrl,
                        effect.origin,
                        effect.coverPath,
                        effect.sharedCoverKey
                    )
                }

                SearchEffect.OpenSourceManage -> onOpenSourceManage()
                is SearchEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    SearchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onIntent: (SearchIntent) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val searchLayoutMode = state.layoutMode
    val isSourceGroupedMode = searchLayoutMode == 1
    var previewBook by remember { mutableStateOf<SearchBook?>(null) }
    var previewSharedCoverKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val groupedListState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryInput by rememberSaveable { mutableStateOf(state.query) }
    var ignoreNextDebouncedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var keepResultsPinnedToTop by rememberSaveable { mutableStateOf(true) }
    val showSuggestionPanel = state.showSuggestions
    val latestQuery by rememberUpdatedState(state.query)
    val scrollBehavior = if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        GlassTopAppBarDefaults.defaultScrollBehavior()
    } else {
        M3GlassScrollBehavior(TopAppBarDefaults.enterAlwaysScrollBehavior())
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalCount > 0 && lastVisible >= totalCount - 3
        }
    }
    val shouldLoadMoreGrouped by remember {
        derivedStateOf {
            val totalCount = groupedListState.layoutInfo.totalItemsCount
            val lastVisible = groupedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalCount > 0 && lastVisible >= totalCount - 3
        }
    }
    LaunchedEffect(state.query) {
        if (state.query != queryInput) queryInput = state.query
    }
    LaunchedEffect(Unit) {
        snapshotFlow { queryInput }
            .distinctUntilChanged()
            .debounce(200)
            .collect { newQuery ->
                if (ignoreNextDebouncedQuery == newQuery) {
                    ignoreNextDebouncedQuery = null
                    return@collect
                }
                if (newQuery != latestQuery) onIntent(SearchIntent.UpdateQuery(newQuery))
            }
    }
    LaunchedEffect(
        shouldLoadMore,
        shouldLoadMoreGrouped,
        state.isSearching,
        state.hasMore,
        state.isManualStop,
        state.showSuggestions,
        isSourceGroupedMode,
    ) {
        val readyToLoad = !state.isSearching && state.hasMore &&
            !state.isManualStop && !state.showSuggestions
        val nearEnd = if (isSourceGroupedMode) shouldLoadMoreGrouped else shouldLoadMore
        if (readyToLoad && nearEnd) onIntent(SearchIntent.LoadMore)
    }

    // Activity lifecycle (e.g., Home button, switching apps)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onIntent(SearchIntent.ResumeEngine)
                Lifecycle.Event.ON_PAUSE -> onIntent(SearchIntent.PauseEngine)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Composition lifecycle (e.g., navigating to BookInfo and back)
    DisposableEffect(Unit) {
        onDispose {
            onIntent(SearchIntent.PauseEngine)
        }
    }

    LaunchedEffect(Unit) {
        onIntent(SearchIntent.ResumeEngine)
    }

    // Save scroll position before composable leaves composition (e.g., navigating to BookInfo)
    DisposableEffect(Unit) {
        onDispose {
            val first = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (first > 0 || offset > 0) {
                onIntent(SearchIntent.SaveScrollState(first, offset))
            }
        }
    }

    // Restore scroll position when composable re-enters with saved state
    LaunchedEffect(state.savedScrollIndex, state.savedScrollOffset) {
        val idx = state.savedScrollIndex
        val off = state.savedScrollOffset
        if (idx > 0 || off > 0) {
            listState.scrollToItem(idx, off)
            onIntent(SearchIntent.SaveScrollState(0, 0))
        }
    }

    LaunchedEffect(state.committedQuery) {
        keepResultsPinnedToTop = true
    }

    LaunchedEffect(isSourceGroupedMode, listState, groupedListState) {
        snapshotFlow {
            val activeState = if (isSourceGroupedMode) groupedListState else listState
            Triple(
                activeState.firstVisibleItemIndex,
                activeState.firstVisibleItemScrollOffset,
                activeState.isScrollInProgress
            )
        }.collect { (index, offset, isScrollInProgress) ->
            if (index == 0 && offset == 0) {
                keepResultsPinnedToTop = true
            } else if (isScrollInProgress) {
                keepResultsPinnedToTop = false
            }
        }
    }

    val firstResultKey = state.results.firstOrNull()?.let {
        "${it.book.origin}:${it.book.bookUrl}"
    }
    LaunchedEffect(firstResultKey, state.results.size, state.isSearching) {
        if (state.isSearching && keepResultsPinnedToTop && state.results.isNotEmpty()) {
            listState.scrollToItem(0)
            groupedListState.scrollToItem(0)
        }
    }

    val submitSearch: (String) -> Unit = { rawQuery ->
        val normalized = rawQuery.trim()
        if (normalized.isNotBlank()) {
            ignoreNextDebouncedQuery = normalized
            queryInput = normalized
            if (normalized != state.query) {
                onIntent(SearchIntent.UpdateQuery(normalized))
            }
            onIntent(SearchIntent.SubmitSearch)
        }
    }

    BackHandler {
        onBack()
    }

    val searchLabel = stringResource(R.string.search_book_key)
    val showResultCountCard = state.committedQuery.isNotBlank() || state.isSearching
    val showSourceProgressCard = state.totalSources > 0

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = stringResource(R.string.search),
                    navigationIcon = {
                        TopBarNavigationButton(
                            onClick = onBack,
                            imageVector = AppIcons.Back
                        )
                    },
                    actions = {
                        TopBarAnimatedActionButton(
                            checked = isSourceGroupedMode || state.selectedSourceTypes.isNotEmpty(),
                            onCheckedChange = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onIntent(SearchIntent.SetSettingsSheetVisible(true))
                            },
                            iconChecked = AppIcons.Settings,
                            iconUnchecked = AppIcons.Settings,
                            activeText = stringResource(R.string.setting),
                            inactiveText = stringResource(R.string.setting),
                        )
                        TopBarAnimatedActionButton(
                            checked = state.matchMode == MatchMode.EXACT,
                            onCheckedChange = {
                                val newMode = if (state.matchMode == MatchMode.EXACT) MatchMode.DEFAULT else MatchMode.EXACT
                                onIntent(SearchIntent.SetMatchMode(newMode))
                            },
                            iconChecked = AppIcons.PrecisionSearch,
                            iconUnchecked = AppIcons.UnPrecisionSearch,
                            activeText = stringResource(R.string.precision_search),
                            inactiveText = stringResource(R.string.precision_search),
                        )
                        TopBarAnimatedActionButton(
                            checked = !state.isAllScope,
                            onCheckedChange = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                onIntent(SearchIntent.SetScopeSheetVisible(true))
                            },
                            iconChecked = AppIcons.Filter,
                            iconUnchecked = AppIcons.Filter,
                            activeText = stringResource(R.string.screen),
                            inactiveText = stringResource(R.string.screen)
                        )
                    },
                    scrollBehavior = scrollBehavior
                )

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveHorizontalPadding()
                ) {
                    SearchBar(
                        query = queryInput,
                        onQueryChange = { queryInput = it },
                        onSearch = submitSearch,
                        placeholder = searchLabel,
                        trailingIcon = {
                            if (queryInput.isNotEmpty()) {
                                SmallPlainButton(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onClick = {
                                        queryInput = ""
                                        onIntent(SearchIntent.UpdateQuery(""))
                                    },
                                    icon = AppIcons.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            val showFab = state.isSearching || (state.committedQuery.isNotBlank() && state.hasMore)
            if (showFab) {
                AppFloatingActionButton(
                    onClick = {
                        if (state.isSearching) {
                            onIntent(SearchIntent.StopSearch)
                        } else {
                            onIntent(SearchIntent.LoadMore)
                        }
                    },
                    icon = if (state.isSearching) Icons.Default.Stop else Icons.Default.PlayArrow,
                    tooltipText = if (state.isSearching) stringResource(R.string.stop) else stringResource(R.string.start)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = showSuggestionPanel,
                label = "SearchBodyTransition",
                modifier = Modifier.fillMaxSize()
            ) { isSuggestionVisible ->
                if (isSuggestionVisible) {
                    SearchSuggestionPanel(
                        state = state,
                        onUseHistory = { keyword ->
                            queryInput = keyword
                            onIntent(SearchIntent.UseHistoryKeyword(keyword))
                        },
                        onDeleteHistory = { onIntent(SearchIntent.DeleteHistory(it)) },
                        onOpenBook = {
                            onIntent(SearchIntent.OpenBookshelfBook(it))
                        },
                        onClearHistory = {
                            onIntent(SearchIntent.SetClearHistoryDialogVisible(true))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.results.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SearchResultFooter(
                                        isSearching = state.isSearching,
                                        hasMore = state.hasMore,
                                        hasResult = false,
                                        committedQuery = state.committedQuery,
                                        onLoadMore = { onIntent(SearchIntent.LoadMore) },
                                    )
                                }
                            }
                        }

                        if (state.results.isNotEmpty()) {
                            val sourceGroupedResults = remember(state.results) {
                                state.results
                                    .groupBy { it.book.origin }
                                    .map { (origin, books) ->
                                        SourceGroup(
                                            origin = origin,
                                            sourceName = books.firstOrNull()?.book?.originName?.takeIf { it.isNotBlank() }
                                                ?: origin,
                                            items = books
                                        )
                                    }
                            }

                            AnimatedContent(
                                targetState = isSourceGroupedMode,
                                label = "SearchLayoutTransition",
                                modifier = Modifier.fillMaxSize(),
                            ) { isSourceGrouped ->
                                if (isSourceGrouped) {
                                    LazyColumn(
                                        state = groupedListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = adaptiveContentPaddingOnlyVertical(
                                            top = 48.dp,
                                            bottom = 8.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        sourceGroupedResults.forEachIndexed { groupIndex, group ->
                                            item(key = "header_${group.origin}") {
                                                SearchSourceSection(
                                                    sourceName = group.sourceName,
                                                    items = group.items,
                                                    onClickBook = { book, coverKey ->
                                                        onIntent(
                                                            SearchIntent.OpenSearchBook(
                                                                book,
                                                                coverKey
                                                            )
                                                        )
                                                    },
                                                    onLongClickBook = { book, coverKey ->
                                                        previewBook = book
                                                        previewSharedCoverKey = coverKey
                                                    },
                                                    onViewAll = {
                                                        onIntent(
                                                            SearchIntent.ExpandSource(
                                                                group.origin,
                                                                group.sourceName
                                                            )
                                                        )
                                                    },
                                                    sharedTransitionScope = sharedTransitionScope,
                                                    animatedVisibilityScope = animatedVisibilityScope,
                                                    sourceSectionIndex = groupIndex,
                                                )
                                            }
                                        }

                                        item {
                                            SearchResultFooter(
                                                isSearching = state.isSearching,
                                                hasMore = state.hasMore,
                                                hasResult = true,
                                                committedQuery = state.committedQuery,
                                                onLoadMore = { onIntent(SearchIntent.LoadMore) },
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = listState,
                                        contentPadding = adaptiveContentPaddingOnlyVertical(
                                            top = 48.dp,
                                            bottom = 8.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(
                                            items = state.results,
                                            key = { _, item -> "${item.book.origin}:${item.book.bookUrl}" }
                                        ) { index, item ->
                                            val sharedCoverKey = bookCoverSharedElementKey(
                                                item.book.bookUrl,
                                                "search:${item.book.origin}"
                                            )
                                            SearchBookListItem(
                                                book = item.book,
                                                shelfState = item.shelfState,
                                                onClick = {
                                                    onIntent(
                                                        SearchIntent.OpenSearchBook(
                                                            item.book,
                                                            sharedCoverKey
                                                        )
                                                    )
                                                },
                                                onLongClick = { book, coverKey ->
                                                    previewBook = book
                                                    previewSharedCoverKey = coverKey
                                                },
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                sharedCoverKey = sharedCoverKey,
                                                sourceCount = item.book.origins.size,
                                            )
                                        }

                                        item {
                                            SearchResultFooter(
                                                isSearching = state.isSearching,
                                                hasMore = state.hasMore,
                                                hasResult = true,
                                                committedQuery = state.committedQuery,
                                                onLoadMore = { onIntent(SearchIntent.LoadMore) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TopFloatingStickyItem(
                            item = if (showResultCountCard || showSourceProgressCard) {
                                SearchFloatingSummary(
                                    resultText = if (showResultCountCard) stringResource(R.string.search_result_count, state.results.size) else null,
                                    sourceText = if (showSourceProgressCard) stringResource(R.string.search_source_progress, state.processedSources, state.totalSources) else null,
                                )
                            } else {
                                null
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp),
                        ) { summary ->
                            NormalCard(
                                cornerRadius = 16.dp,
                                containerColor = LegadoTheme.colorScheme.surfaceContainer,
                                contentColor = LegadoTheme.colorScheme.onCardContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    summary.resultText?.let { text ->
                                        AppText(text = text, style = LegadoTheme.typography.labelSmallEmphasized)
                                    }
                                    summary.sourceText?.let { text ->
                                        AppText(text = text, style = LegadoTheme.typography.labelSmallEmphasized)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AppAlertDialog(
        show = state.showClearHistoryDialog,
        onDismissRequest = {
            onIntent(SearchIntent.SetClearHistoryDialogVisible(false))
        },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_clear_search_history),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(SearchIntent.ConfirmClearHistory) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = {
            onIntent(SearchIntent.SetClearHistoryDialogVisible(false))
        },
    )

    AppAlertDialog(
        data = state.emptyScopeAction,
        onDismissRequest = {
            onIntent(SearchIntent.DismissEmptyScopeAction)
        },
        title = stringResource(R.string.draw),
        textProvider = {
            if (wasMatchMode == MatchMode.EXACT) {
                stringResource(R.string.search_empty_scope_disable_precision, scopeDisplay)
            } else {
                stringResource(R.string.search_empty_scope_switch_all, scopeDisplay)
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(SearchIntent.ConfirmEmptyScopeAction) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(SearchIntent.DismissEmptyScopeAction) },
    )

    ScopeSelectSheet(
        show = state.showScopeSheet,
        onDismissRequest = { onIntent(SearchIntent.SetScopeSheetVisible(false)) },
        isAll = state.isAllScope,
        onSelectAll = { onIntent(SearchIntent.SelectAllScope) },
        groups = state.enabledGroups,
        selectedGroups = state.scopeDisplayNames,
        onToggleGroup = { onIntent(SearchIntent.ToggleScopeGroup(it)) },
        sources = state.enabledSources,
        selectedSources = state.selectedScopeSourceUrls,
        onToggleSource = { onIntent(SearchIntent.ToggleScopeSource(it)) },
        isSourceScope = state.isSourceScope,
        onConfirm = { onIntent(SearchIntent.OpenSourceManage) },
        onApplyScope = { selection ->
            onIntent(
                SearchIntent.ApplyScopeSelection(
                    groupNames = selection.groupNames,
                    sources = selection.sources,
                    isSourceScope = selection.isSourceScope,
                )
            )
        },
    )

    AppModalBottomSheet(
        show = state.showSettingsSheet,
        onDismissRequest = { onIntent(SearchIntent.SetSettingsSheetVisible(false)) },
        title = stringResource(R.string.setting),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactDropdownSettingItem(
                title = stringResource(R.string.layout_mode),
                selectedValue = searchLayoutMode.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.search_layout_source_grouped),
                    stringResource(R.string.search_layout_list)
                ),
                entryValues = arrayOf("1", "0"),
                imageVector = if (isSourceGroupedMode) Icons.Default.GridView else Icons.AutoMirrored.Outlined.FormatListBulleted,
                onValueChange = { newValue ->
                    if (newValue.toInt() != searchLayoutMode) {
                        onIntent(SearchIntent.SetLayoutMode(newValue.toInt()))
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(Icons.Default.Layers, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = stringResource(R.string.search_type),
                    style = LegadoTheme.typography.titleSmall
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SelectionItemCard(
                    title = stringResource(R.string.all),
                    isSelected = state.selectedSourceTypes.isEmpty(),
                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                    inSelectionMode = true,
                    onToggleSelection = {
                        if (state.selectedSourceTypes.isNotEmpty()) {
                            state.selectedSourceTypes.forEach {
                                onIntent(SearchIntent.ToggleSourceType(it))
                            }
                        }
                    }
                )

                listOf(
                    0 to stringResource(R.string.noval),
                    2 to stringResource(R.string.manga),
                    1 to stringResource(R.string.audio),
                ).forEach { (type, label) ->
                    SelectionItemCard(
                        title = label,
                        isSelected = state.selectedSourceTypes.contains(type),
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        inSelectionMode = true,
                        onToggleSelection = {
                            onIntent(SearchIntent.ToggleSourceType(type))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    val resultsByBookUrl = remember(state.results) {
        state.results.associateBy { it.book.bookUrl }
    }
    val previewShelfState = previewBook?.let { book ->
        resultsByBookUrl[book.bookUrl]?.shelfState ?: BookShelfState.NOT_IN_SHELF
    }
    SearchBookPreviewSheet(
        data = previewBook,
        shelfState = previewShelfState,
        sharedCoverKey = previewSharedCoverKey,
        onDismissRequest = { previewBook = null },
        onOpenDetail = { book, sharedCoverKey ->
            previewBook = null
            onIntent(SearchIntent.OpenSearchBook(book, sharedCoverKey))
        },
        onAddToShelf = { book ->
            onIntent(SearchIntent.AddToShelf(book))
        },
    )

    ExpandedSourceSheet(
        show = state.showExpandedSource,
        sourceName = state.expandedSourceName ?: "",
        books = state.expandedSourceBooks,
        isLoading = state.expandedSourceLoading,
        isEnd = state.expandedSourceEnd,
        errorMsg = state.expandedSourceError,
        savedScrollIndex = state.expandedSourceSavedScrollIndex,
        savedScrollOffset = state.expandedSourceSavedScrollOffset,
        onDismiss = { onIntent(SearchIntent.DismissExpandedSource) },
        onLoadMore = { onIntent(SearchIntent.LoadMoreExpandedSource) },
        onSaveScrollState = { index, offset ->
            onIntent(SearchIntent.SaveExpandedSourceScrollState(index, offset))
        },
        onBookClick = { book, coverKey ->
            onIntent(SearchIntent.OpenExpandedSourceBook(book, coverKey))
        },
        onBookLongClick = { book, coverKey ->
            previewBook = book
            previewSharedCoverKey = coverKey
        },
    )
}

private data class SearchFloatingSummary(
    val resultText: String?,
    val sourceText: String?,
)

@Composable
private fun SearchSuggestionPanel(
    state: SearchUiState,
    onUseHistory: (String) -> Unit,
    onDeleteHistory: (SearchKeyword) -> Unit,
    onOpenBook: (BookShelfItem) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = adaptiveContentPadding(
            top = 8.dp,
            bottom = 8.dp
        )
    ) {
        if (state.bookshelfHints.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(Icons.Default.Book, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(
                        text = stringResource(R.string.bookshelf),
                        style = LegadoTheme.typography.titleSmall,
                    )
                }
            }

            items(state.bookshelfHints, key = { it.bookUrl }) { book ->
                SelectionItemCard(
                    modifier = Modifier.animateItem(),
                    title = book.name,
                    subtitle = book.author,
                    onToggleSelection = { onOpenBook(book) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(AppIcons.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(
                        text = stringResource(R.string.searchHistory),
                        style = LegadoTheme.typography.titleSmall,
                    )
                }

                if (state.history.isNotEmpty()) {
                    SmallPlainButton(
                        onClick = onClearHistory,
                        text = stringResource(R.string.clear_all),
                        icon = Icons.Default.Close
                    )
                }
            }
        }

        if (state.history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(state.history, key = { it.word }) { history ->
                SelectionItemCard(
                    modifier = Modifier.animateItem(),
                    title = history.word,
                    onToggleSelection = { onUseHistory(history.word) },
                    trailingAction = {
                        SmallPlainButton(
                            onClick = { onDeleteHistory(history) },
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultFooter(
    isSearching: Boolean,
    hasMore: Boolean,
    hasResult: Boolean,
    committedQuery: String,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isSearching -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppCircularProgressIndicator()
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    AppText(text = stringResource(R.string.is_loading))
                }
            }

            !hasResult && committedQuery.isNotBlank() -> {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }

            hasMore -> {
                Text(
                    text = stringResource(R.string.search_has_more),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class SourceGroup(
    val origin: String,
    val sourceName: String,
    val items: List<SearchResultItemUi>,
)

@Composable
private fun ExpandedSourceSheet(
    show: Boolean,
    sourceName: String,
    books: List<SearchBook>,
    isLoading: Boolean,
    isEnd: Boolean,
    errorMsg: String?,
    savedScrollIndex: Int,
    savedScrollOffset: Int,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
    onSaveScrollState: (Int, Int) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    onBookLongClick: ((SearchBook, String?) -> Unit)? = null,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = sourceName,
    ) {
        val listState = rememberLazyListState()
        val showLoadMoreFooter = isLoading || errorMsg != null || isEnd
        val currentOnSaveScrollState by rememberUpdatedState(onSaveScrollState)
        val shouldLoadMore by remember {
            derivedStateOf {
                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 3
            }
        }

        LaunchedEffect(shouldLoadMore, isLoading, isEnd) {
            if (shouldLoadMore && !isLoading && !isEnd) {
                onLoadMore()
            }
        }

        LaunchedEffect(savedScrollIndex, savedScrollOffset) {
            if (savedScrollIndex > 0 || savedScrollOffset > 0) {
                listState.scrollToItem(savedScrollIndex, savedScrollOffset)
                onSaveScrollState(0, 0)
            }
        }

        DisposableEffect(listState) {
            onDispose {
                val first = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                if (first > 0 || offset > 0) {
                    currentOnSaveScrollState(first, offset)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPaddingOnlyVertical(
                top = 8.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = books,
                key = { it.bookUrl },
            ) { book ->
                SearchBookListItem(
                    book = book,
                    shelfState = BookShelfState.NOT_IN_SHELF,
                    onClick = { onBookClick(book, null) },
                    onLongClick = onBookLongClick,
                )
            }

            if (showLoadMoreFooter) {
                item {
                    LoadMoreFooter(
                        isLoading = isLoading,
                        errorMsg = errorMsg,
                        isEnd = isEnd,
                        onRetry = onLoadMore,
                        autoLoad = false,
                    )
                }
            }
        }
    }
}
