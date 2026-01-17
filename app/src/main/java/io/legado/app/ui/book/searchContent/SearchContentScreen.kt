package io.legado.app.ui.book.searchContent

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.TextCard
import io.legado.app.ui.widget.components.button.AnimatedActionButton
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchContentScreen(
    bookUrl: String,
    searchWord: String?,
    onBack: () -> Unit,
    viewModel: SearchContentViewModel = koinViewModel()
) {
    val activity = LocalActivity.current
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    val isSearching = uiState.isSearching
    val searchResults = uiState.searchResults
    val durChapterIndex = uiState.durChapterIndex
    val error = uiState.error

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var searchQuery by remember(searchWord) { mutableStateOf(searchWord ?: "") }
    var replaceEnabled by remember { mutableStateOf(false) }
    var regexReplace by remember { mutableStateOf(false) }

    LaunchedEffect(bookUrl, searchWord) {
        viewModel.initBook(bookUrl)
        if (!searchWord.isNullOrBlank()) {
            viewModel.startSearch(searchWord, replaceEnabled, regexReplace)
        }
    }

    /*
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchUiEffect.OpenSearchResult -> {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("searchResult", effect.result)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("searchResultList", effect.allResults)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("searchResultIndex", effect.index)

                    navController.popBackStack()
                }
            }
        }
    }*/

    val contentState = when {
        error != null -> SearchContentState.Error(error)
        isSearching -> SearchContentState.Loading
        searchQuery.isBlank() -> SearchContentState.EmptyQuery
        searchResults.isEmpty() -> SearchContentState.EmptyResult
        else -> null
    }

    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                MediumFlexibleTopAppBar(
                    title = {
                        val title = if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                            "共 ${searchResults.size} 条结果"
                        } else {
                            "搜索内容"
                        }
                        AnimatedText(
                            text = title
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AnimatedActionButton(
                                checked = replaceEnabled,
                                onCheckedChange = {
                                    replaceEnabled = it
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.startSearch(
                                            searchQuery,
                                            replaceEnabled,
                                            regexReplace
                                        )
                                    }
                                },
                                icon = Icons.Default.FindReplace,
                                activeText = "替换开启",
                                inactiveText = "替换关闭"
                            )

                            AnimatedActionButton(
                                checked = regexReplace,
                                onCheckedChange = {
                                    regexReplace = it
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.startSearch(
                                            searchQuery,
                                            replaceEnabled,
                                            regexReplace
                                        )
                                    }
                                },
                                icon = Icons.Default.Code,
                                activeText = "正则开启",
                                inactiveText = "正则关闭"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                SearchBarSection(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.startSearch(searchQuery, replaceEnabled, regexReplace)
                    }
                )
                AnimatedVisibility(visible = contentState == SearchContentState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Localized description") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = isSearching,
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = { viewModel.stopSearch() }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "停止搜索")
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
                label = "SearchContentTransition"
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

                    SearchContentState.EmptyQuery -> {
                        EmptyMessageView(
                            message = "请输入关键词开始搜索",
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize()
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

                    null -> Unit
                    else -> {}
                }
            }

            FastScrollLazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(searchResults) { index, result ->
                    SearchResultItem(
                        modifier = Modifier.animateItem(),
                        result = result,
                        isCurrentChapter = result.chapterIndex == durChapterIndex,
                        onClick = {
                            viewModel.onSearchResultClick(result, index) { key ->
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
