package io.legado.app.ui.book.searchContent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.series.MediumOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.button.series.ToggleStyle
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarAnimatedActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContentRouteScreen(
    onBack: () -> Unit,
    viewModel: SearchContentViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SearchContentEffect.NavigateBack -> onBack()
            }
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onIntent(SearchContentIntent.LeaveSearch) }
    }
    SearchContentScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContentScreen(
    state: SearchContentUiState,
    onIntent: (SearchContentIntent) -> Unit,
    onBack: () -> Unit,
) {
    val searchQuery = state.searchQuery
    val replaceEnabled = state.replaceEnabled
    val regexReplace = state.regexReplace
    val searchHistory = state.searchHistory
    val historyOnlyThisBook = state.historyOnlyThisBook

    val isSearching = state.isSearching
    val searchResults = state.searchResults
    val durChapterIndex = state.durChapterIndex
    val error = state.error

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val scrollToCurrentChapter = {
        val targetIndex = searchResults.indexOfFirst { it.chapterIndex == durChapterIndex }
        if (targetIndex != -1) {
            scope.launch {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty() && state.shouldAutoScroll) {
            val targetIndex = searchResults.indexOfFirst { it.chapterIndex == durChapterIndex }
            if (targetIndex != -1) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.collect { count ->
                    if (count > targetIndex) {
                        listState.animateScrollToItem(targetIndex)
                        onIntent(SearchContentIntent.MarkAutoScrollDone)
                        return@collect
                    }
                }
            }
        }
    }

    val contentState = when {
        error != null -> SearchContentState.Error(error)
        isSearching -> SearchContentState.Loading
        searchQuery.isBlank() -> SearchContentState.History
        searchResults.isEmpty() -> SearchContentState.EmptyResult
        else -> null
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                        "共 ${searchResults.size} 条结果"
                    } else "搜索内容",
                    navigationIcon = { TopBarNavigationButton(onClick = onBack) },
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TopBarAnimatedActionButton(
                                checked = replaceEnabled,
                                onCheckedChange = { onIntent(SearchContentIntent.ToggleReplace(it)) },
                                iconChecked = Icons.Default.FindReplace,
                                iconUnchecked = Icons.Default.FindReplace,
                                activeText = "替换开启",
                                inactiveText = "替换关闭"
                            )

                            TopBarAnimatedActionButton(
                                checked = regexReplace,
                                onCheckedChange = { onIntent(SearchContentIntent.ToggleRegex(it)) },
                                iconChecked = Icons.Default.Code,
                                iconUnchecked = Icons.Default.Code,
                                activeText = "正则开启",
                                inactiveText = "正则关闭"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                Box(
                    modifier = Modifier.adaptiveHorizontalPadding()
                ) {
                    SearchBar(
                        query = searchQuery,
                        scrollState = listState,
                        onQueryChange = { onIntent(SearchContentIntent.UpdateQuery(it)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                SmallPlainButton(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onClick = {
                                        onIntent(SearchContentIntent.UpdateQuery(""))
                                    },
                                    icon = AppIcons.Close,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = isSearching) {
                    AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            val fabVisible = (isSearching || searchResults.isNotEmpty()) && searchQuery.isNotBlank()
            AppFloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(
                    visible = fabVisible,
                    alignment = Alignment.BottomEnd,
                ),
                onClick = {
                    if (isSearching) {
                        onIntent(SearchContentIntent.StopSearch)
                    } else {
                        scrollToCurrentChapter()
                    }
                },
                tooltipText = if (isSearching) "停止搜索" else "跳转到当前章节"
            ) {
                AnimatedContent(
                    targetState = isSearching,
                    label = "FabIconTransition"
                ) { searching ->
                    if (searching) {
                        AppIcon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop))
                    } else {
                        AppIcon(
                            Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.a11y_locate_current_chapter)
                        )
                    }
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
                targetState = contentState,
                label = "SearchContentTransition",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    is SearchContentState.Error -> {
                        EmptyMessage(
                            message = state.throwable.localizedMessage ?: "发生未知错误",
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize()
                        )
                    }

                    SearchContentState.History -> {
                        SearchHistoryList(
                            history = searchHistory,
                            onlyThisBook = historyOnlyThisBook,
                            onHistoryClick = {
                                onIntent(SearchContentIntent.UpdateQuery(it.query))
                            },
                            onDeleteHistory = {
                                onIntent(SearchContentIntent.DeleteHistory(it))
                            },
                            onClearHistory = { onIntent(SearchContentIntent.ClearHistory) },
                            onToggleScope = { onIntent(SearchContentIntent.ToggleHistoryScope) }
                        )
                    }
                    SearchContentState.EmptyResult -> {
                        EmptyMessage(
                            message = "没有找到相关内容！",
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize()
                        )
                    }

                    null -> {
                        FastScrollLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(searchResults) { index, result ->
                                SearchResultItem(
                                    modifier = Modifier.animateItem(),
                                    result = result,
                                    isCurrentChapter = result.chapterIndex == durChapterIndex,
                                    onClick = {
                                        onIntent(SearchContentIntent.OpenResult(result))
                                    }
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchHistoryList(
    history: List<SearchContentHistory>,
    onlyThisBook: Boolean,
    onHistoryClick: (SearchContentHistory) -> Unit,
    onDeleteHistory: (SearchContentHistory) -> Unit,
    onClearHistory: () -> Unit,
    onToggleScope: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding()
                .padding(vertical = 4.dp),
        ) {
            AppText(
                text = "搜索历史",
                style = LegadoTheme.typography.titleSmallEmphasized,
                color = LegadoTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            SmallToggleButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                checked = onlyThisBook,
                onCheckedChange = { onToggleScope() },
                style = ToggleStyle.Tonal,
                iconChecked = Icons.Default.Book,
                icon = Icons.Default.CollectionsBookmark,
                text = "仅本书"
            )
        }

        if (history.isEmpty()) {
            EmptyMessage(
                message = "暂无搜索历史",
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize()
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { item ->
                    ListItem(
                        modifier = Modifier
                            .clickable { onHistoryClick(item) }
                            .animateItem(),
                        headlineContent = {
                            AppText(
                                text = item.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.History, contentDescription = null)
                        },
                        trailingContent = {
                            SmallPlainButton(
                                onClick = { onDeleteHistory(item) },
                                icon = Icons.Default.Close,
                                contentDescription = stringResource(R.string.delete)
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = LegadoTheme.colorScheme.surface,
                            contentColor = LegadoTheme.colorScheme.onSurface
                        )
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                            .animateItem(),
                        contentAlignment = Alignment.Center
                    ) {
                        MediumOutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier.fillMaxWidth(0.6f),
                            icon = Icons.Outlined.DeleteSweep,
                            text = "清除搜索历史"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    modifier: Modifier,
    result: SearchResult,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                LegadoTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {

            Column {
                AppText(
                    text = buildAnnotatedString {
                        append(
                            result.getTitleSpannable(
                                LegadoTheme.colorScheme.primary.toArgb()
                            )
                        )
                    },
                    style = LegadoTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = LegadoTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppText(
                    text = buildAnnotatedString {
                        append(
                            result.getContentSpannable(
                                textColor = LegadoTheme.colorScheme.onSurface.toArgb(),
                                accentColor = LegadoTheme.colorScheme.primary.toArgb(),
                                bgColor = LegadoTheme.colorScheme.primaryContainer.toArgb()
                            )
                        )
                    },
                    style = LegadoTheme.typography.bodyMedium
                )
            }

            Row (
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                if (isCurrentChapter) {
                    TextCard(
                        text = "当前章节",
                        backgroundColor = LegadoTheme.colorScheme.secondaryContainer,
                        contentColor = LegadoTheme.colorScheme.onSecondaryContainer,
                        cornerRadius = 8.dp,
                        horizontalPadding = 4.dp,
                        verticalPadding = 2.dp,
                    )
                }

                if (result.progressPercent > 0f) {
                    TextCard(
                        text = String.format("%.1f%%", result.progressPercent),
                        backgroundColor = LegadoTheme.colorScheme.secondaryContainer,
                        contentColor = LegadoTheme.colorScheme.onSecondaryContainer,
                        cornerRadius = 8.dp,
                        horizontalPadding = 4.dp,
                        verticalPadding = 2.dp,
                    )
                }
            }
        }
    }
}
