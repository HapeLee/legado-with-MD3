package io.legado.app.ui.book.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveContentPaddingOnlyVertical
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.button.SmallTextButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarAnimatedActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.TopFloatingStickyItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit,
    onOpenSourceManage: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var queryInput by rememberSaveable { mutableStateOf(state.query) }
    var scopeSheetTab by rememberSaveable { mutableStateOf(0) }
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

    LaunchedEffect(state.query) {
        if (state.query != queryInput) {
            queryInput = state.query
        }
    }

    LaunchedEffect(state.showScopeSheet, state.isSourceScope) {
        if (state.showScopeSheet) {
            scopeSheetTab = if (state.isSourceScope) 1 else 0
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { queryInput }
            .distinctUntilChanged()
            .debounce(200)
            .filter { it != latestQuery }
            .collect { newQuery ->
                viewModel.onIntent(SearchIntent.UpdateQuery(newQuery))
            }
    }

    LaunchedEffect(
        shouldLoadMore,
        state.isSearching,
        state.hasMore,
        state.isManualStop,
        state.showSuggestions,
    ) {
        if (
            shouldLoadMore &&
            !state.isSearching &&
            state.hasMore &&
            !state.isManualStop &&
            !state.showSuggestions
        ) {
            viewModel.onIntent(SearchIntent.LoadMore)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.OpenBookInfo -> {
                    onOpenBookInfo(effect.name, effect.author, effect.bookUrl)
                }

                SearchEffect.OpenSourceManage -> onOpenSourceManage()
                is SearchEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onIntent(SearchIntent.StopSearch)
        }
    }

    val submitSearch: (String) -> Unit = { rawQuery ->
        val normalized = rawQuery.trim()
        if (normalized.isNotBlank()) {
            queryInput = normalized
            if (normalized != state.query) {
                viewModel.onIntent(SearchIntent.UpdateQuery(normalized))
            }
            viewModel.onIntent(SearchIntent.SubmitSearch)
        }
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
                            checked = state.isPrecisionSearch,
                            onCheckedChange = { checked ->
                                viewModel.onIntent(SearchIntent.TogglePrecision(checked))
                            },
                            iconChecked = AppIcons.PrecisionSearch,
                            iconUnchecked = AppIcons.UnPrecisionSearch,
                            activeText = stringResource(R.string.precision_search),
                            inactiveText = stringResource(R.string.search),
                        )
                        TopBarActionButton(
                            onClick = {
                                viewModel.onIntent(SearchIntent.SetScopeSheetVisible(true))
                            },
                            imageVector = AppIcons.Filter,
                            contentDescription = "筛选"
                        )
                        TopBarActionButton(
                            onClick = {
                                viewModel.onIntent(SearchIntent.OpenSourceManage)
                            },
                            imageVector = AppIcons.Settings,
                            contentDescription = "书源管理"
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
                                TopBarActionButton(
                                    onClick = {
                                        queryInput = ""
                                        viewModel.onIntent(SearchIntent.UpdateQuery(""))
                                    },
                                    imageVector = AppIcons.Close,
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
                            viewModel.onIntent(SearchIntent.StopSearch)
                        } else {
                            viewModel.onIntent(SearchIntent.LoadMore)
                        }
                    }
                ) {
                    AppIcon(
                        imageVector = if (state.isSearching) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (state.isSearching) stringResource(R.string.stop) else stringResource(R.string.start),
                    )
                }
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
                            viewModel.onIntent(SearchIntent.UseHistoryKeyword(keyword))
                        },
                        onDeleteHistory = { viewModel.onIntent(SearchIntent.DeleteHistory(it)) },
                        onOpenBook = {
                            viewModel.onIntent(SearchIntent.OpenBookshelfBook(it))
                        },
                        onClearHistory = {
                            viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(true))
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
                                        onLoadMore = { viewModel.onIntent(SearchIntent.LoadMore) },
                                    )
                                }
                            }
                        }

                        if (state.results.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = adaptiveContentPaddingOnlyVertical(
                                    top = 48.dp,
                                    bottom = 8.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(
                                    items = state.results,
                                    key = { index, item -> "${item.book.origin}:${item.book.bookUrl}:$index" }
                                ) { _, item ->
                                    SearchBookListItem(
                                        book = item.book,
                                        shelfState = item.shelfState,
                                        onClick = {
                                            viewModel.onIntent(SearchIntent.OpenSearchBook(item.book))
                                        }
                                    )
                                }

                                item {
                                    SearchResultFooter(
                                        isSearching = state.isSearching,
                                        hasMore = state.hasMore,
                                        hasResult = true,
                                        committedQuery = state.committedQuery,
                                        onLoadMore = { viewModel.onIntent(SearchIntent.LoadMore) },
                                    )
                                }
                            }
                        }

                        TopFloatingStickyItem(
                            item = if (showResultCountCard || showSourceProgressCard) {
                                SearchFloatingSummary(
                                    resultText = if (showResultCountCard) "结果 ${state.results.size}" else null,
                                    sourceText = if (showSourceProgressCard) " · 进度 ${state.processedSources}/${state.totalSources}" else null,
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
            viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(false))
        },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_clear_search_history),
        confirmText = stringResource(R.string.ok),
        onConfirm = { viewModel.onIntent(SearchIntent.ConfirmClearHistory) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = {
            viewModel.onIntent(SearchIntent.SetClearHistoryDialogVisible(false))
        },
    )

    AppAlertDialog(
        data = state.emptyScopeAction,
        onDismissRequest = {
            viewModel.onIntent(SearchIntent.DismissEmptyScopeAction)
        },
        title = stringResource(R.string.draw),
        confirmText = stringResource(R.string.ok),
        onConfirm = { viewModel.onIntent(SearchIntent.ConfirmEmptyScopeAction) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { viewModel.onIntent(SearchIntent.DismissEmptyScopeAction) },
        content = {
            Text(
                text = if (it.wasPrecisionSearch) {
                    "${it.scopeDisplay}分组搜索结果为空，是否关闭精准搜索？"
                } else {
                    "${it.scopeDisplay}分组搜索结果为空，是否切换到全部分组？"
                }
            )
        }
    )

    AppModalBottomSheet(
        show = state.showScopeSheet,
        onDismissRequest = { viewModel.onIntent(SearchIntent.SetScopeSheetVisible(false)) },
        title = stringResource(R.string.search_select_group),
    ) {
        Column {
            SelectionItemCard(
                title = stringResource(R.string.all_source),
                isSelected = state.isAllScope,
                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                inSelectionMode = true,
                onToggleSelection = {
                    viewModel.onIntent(SearchIntent.SelectAllScope)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            AppTabRow(
                tabTitles = listOf(
                    stringResource(R.string.group),
                    stringResource(R.string.book_source),
                ),
                selectedTabIndex = scopeSheetTab,
                onTabSelected = { scopeSheetTab = it },
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (scopeSheetTab == 0) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.enabledGroups, key = { it }) {
                        val selected = !state.isSourceScope && state.scopeDisplayNames.contains(it)
                        SelectionItemCard(
                            title = it,
                            isSelected = selected,
                            containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                            inSelectionMode = true,
                            onToggleSelection = {
                                viewModel.onIntent(SearchIntent.ToggleScopeGroup(it))
                            }
                        )
                    }
                }
            } else {
                if (state.enabledSources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.enabledSources, key = { it.bookSourceUrl }) {
                            val selected = state.selectedScopeSourceUrls.contains(it.bookSourceUrl)
                            SelectionItemCard(
                                title = it.bookSourceName,
                                subtitle = it.bookSourceGroup?.takeIf { group -> group.isNotBlank() },
                                containerColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.6f),
                                isSelected = selected,
                                inSelectionMode = true,
                                onToggleSelection = {
                                    viewModel.onIntent(SearchIntent.ToggleScopeSource(it))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
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
                    SmallTextButton(
                        onClick = onClearHistory,
                        text = stringResource(R.string.clear_all),
                        imageVector = Icons.Default.Close
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
                        SmallIconButton(
                            onClick = { onDeleteHistory(history) },
                            imageVector = Icons.Default.Close,
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
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
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
                    text = stringResource(R.string.search_empty),
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
