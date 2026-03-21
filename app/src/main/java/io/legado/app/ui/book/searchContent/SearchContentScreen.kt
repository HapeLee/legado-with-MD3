package io.legado.app.ui.book.searchContent

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.button.SmallAnimatedActionButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.button.TopBarAnimatedActionButton
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContentScreen(
    onBack: () -> Unit,
    viewModel: SearchContentViewModel = koinViewModel()
) {
    val activity = LocalActivity.current

    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replaceEnabled by viewModel.replaceEnabled.collectAsState()
    val regexReplace by viewModel.regexReplace.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val historyOnlyThisBook by viewModel.historyOnlyThisBook.collectAsState()

    val isSearching = uiState.isSearching
    val searchResults = uiState.searchResults
    val durChapterIndex = uiState.durChapterIndex
    val error = uiState.error

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
        if (searchResults.isNotEmpty() && viewModel.shouldAutoScroll()) {
            val targetIndex = searchResults.indexOfFirst { it.chapterIndex == durChapterIndex }
            if (targetIndex != -1) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.collect { count ->
                    if (count > targetIndex) {
                        listState.animateScrollToItem(targetIndex)
                        viewModel.markScrollDone()
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
                    title = {
                        val title = if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                            "共 ${searchResults.size} 条结果"
                        } else "搜索内容"
                        AnimatedText(text = title)
                    },
                    navigationIcon = { TopbarNavigationButton(onClick = onBack) },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TopBarAnimatedActionButton(
                                checked = replaceEnabled,
                                onCheckedChange = { viewModel.toggleReplace(it) },
                                iconChecked = Icons.Default.FindReplace,
                                iconUnchecked = Icons.Default.FindReplace,
                                activeText = "替换开启",
                                inactiveText = "替换关闭"
                            )

                            TopBarAnimatedActionButton(
                                checked = regexReplace,
                                onCheckedChange = { viewModel.toggleRegex(it) },
                                iconChecked = Icons.Default.Code,
                                iconUnchecked = Icons.Default.Code,
                                activeText = "正则开启",
                                inactiveText = "正则关闭"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                SearchBarSection(
                    query = searchQuery,
                    scrollState = listState,
                    onQueryChange = { viewModel.onQueryChange(it) }
                )
                AnimatedVisibility(visible = isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        floatingActionButton = {
            val fabVisible = (isSearching || searchResults.isNotEmpty()) && searchQuery.isNotBlank()
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = {
                    PlainTooltip {
                        Text(if (isSearching) "停止搜索" else "跳转到当前章节")
                    }
                },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = fabVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = {
                        if (isSearching) {
                            viewModel.stopSearch()
                        } else {
                            scrollToCurrentChapter()
                        }
                    }
                ) {
                    AnimatedContent(
                        targetState = isSearching,
                        label = "FabIconTransition"
                    ) { searching ->
                        if (searching) {
                            Icon(Icons.Default.Stop, contentDescription = "停止搜索")
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = "定位当前章节")
                        }
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
                        EmptyMessageView(
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
                            onHistoryClick = { viewModel.onQueryChange(it.query) },
                            onDeleteHistory = { viewModel.deleteHistory(it) },
                            onClearHistory = { viewModel.clearHistory() },
                            onToggleScope = { viewModel.toggleHistoryScope() }
                        )
                    }
                    SearchContentState.EmptyResult -> {
                        EmptyMessageView(
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
                                        viewModel.onSearchResultClick(result) { key ->
                                            val intent = Intent().apply {
                                                putExtra("key", key)
                                                putExtra("index", index)
                                            }
                                            activity?.setResult(Activity.RESULT_OK, intent)
                                            activity?.finish()
                                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            SmallAnimatedActionButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                checked = onlyThisBook,
                onCheckedChange = { onToggleScope() },
                iconChecked = Icons.Default.Book,
                iconUnchecked = Icons.Default.CollectionsBookmark,
                activeText = "仅本书",
                inactiveText = "所有记录"
            )
        }

        if (history.isEmpty()) {
            EmptyMessageView(
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
                            Text(
                                text = item.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.History, contentDescription = null)
                        },
                        trailingContent = {
                            SmallIconButton(
                                onClick = { onDeleteHistory(item) },
                                icon = Icons.Default.Close,
                                contentDescription = "删除"
                            )
                        }
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
                        OutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("清除搜索历史")
                        }
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
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {

            Column {
                Text(
                    text = buildAnnotatedString {
                        append(
                            result.getTitleSpannable(
                                MaterialTheme.colorScheme.primary.toArgb()
                            )
                        )
                    },
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = buildAnnotatedString {
                        append(
                            result.getContentSpannable(
                                textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                                accentColor = MaterialTheme.colorScheme.primary.toArgb(),
                                bgColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
                            )
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row (
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                if (isCurrentChapter) {
                    TextCard(
                        text = "当前章节",
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        cornerRadius = 8.dp,
                        horizontalPadding = 4.dp,
                        verticalPadding = 2.dp,
                    )
                }

                if (result.progressPercent > 0f) {
                    TextCard(
                        text = String.format("%.1f%%", result.progressPercent),
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        cornerRadius = 8.dp,
                        horizontalPadding = 4.dp,
                        verticalPadding = 2.dp,
                    )
                }
            }
        }
    }
}
