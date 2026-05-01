package io.legado.app.ui.book.explore

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.widget.components.explore.ExploreKindUiUseCase
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.button.AnimatedTextButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@SuppressLint("LocalContextConfigurationRead", "ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ExploreShowScreen(
    title: String,
    sourceUrl: String?,
    exploreUrl: String?,
    onBack: () -> Unit,
    onBookClick: (SearchBook) -> Unit,
    viewModel: ExploreShowViewModel = koinViewModel()
) {

    LaunchedEffect(sourceUrl, exploreUrl, viewModel) {
        viewModel.initData(sourceUrl, exploreUrl)
    }

    val books by viewModel.uiBooks.collectAsState()
    val isBookEnd by viewModel.isEnd.collectAsState()
    val shouldTriggerAutoLoad by viewModel.shouldTriggerAutoLoad.collectAsState()
    val kinds by viewModel.kinds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val selectedTitle by viewModel.selectedKindTitle.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showKindSheet by remember { mutableStateOf(false) }
    val layoutState by viewModel.layoutState.collectAsState()
    val isGridMode = layoutState == 1
    var showGridCountSheet by remember { mutableStateOf(false) }
    val gridColumnCount by viewModel.gridCount.collectAsState()
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(sourceUrl) {
        exploreKindUseCase.warmUp(sourceUrl)
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    val hazeState = remember { HazeState() }
    val shouldLoadMoreList = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 3
        }
    }

    val shouldLoadMoreGrid = remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 1
        }
    }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(shouldLoadMoreList.value, isGridMode) {
        if (!isGridMode && shouldLoadMoreList.value) viewModel.loadMore()
    }

    LaunchedEffect(shouldLoadMoreGrid.value, isGridMode) {
        if (isGridMode && shouldLoadMoreGrid.value) viewModel.loadMore()
    }

    LaunchedEffect(shouldTriggerAutoLoad) {
        if (shouldTriggerAutoLoad) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(isGridMode) {
        if (isGridMode) {
            if (listState.firstVisibleItemIndex > 0) {
                gridState.scrollToItem(listState.firstVisibleItemIndex)
            }
        } else {
            if (gridState.firstVisibleItemIndex > 0) {
                listState.scrollToItem(gridState.firstVisibleItemIndex)
            }
        }
    }


    AppModalBottomSheet(
        show = showGridCountSheet,
        modifier = Modifier
            .padding(16.dp),
        onDismissRequest = { showGridCountSheet = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppText(
                text = "布局列数",
                style = LegadoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.width(12.dp))

            TextCard(
                text = "$gridColumnCount 列",
                textStyle = LegadoTheme.typography.titleSmall,
                verticalPadding = 4.dp,
                horizontalPadding = 12.dp,
                cornerRadius = 12.dp
            )
        }

        Slider(
            value = gridColumnCount.toFloat(),
            onValueChange = {
                val col = it.toInt().coerceIn(1, 10)
                viewModel.saveGridCount(col)
            },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showGridCountSheet = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            AppText("完成")
        }
    }


    AppModalBottomSheet(
        show = showKindSheet,
        onDismissRequest = { showKindSheet = false }
    ) {

        var kindQuery by remember { mutableStateOf("") }

        SearchBar(
            query = kindQuery,
            backgroundColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.5f),
            onQueryChange = { kindQuery = it },
            placeholder = "选择或搜索分类",
        )

        val filteredKinds = remember(kindQuery, kinds) {
            if (kindQuery.isBlank()) kinds
            else kinds.filter { kind ->
                kind.title.contains(kindQuery, ignoreCase = true) ||
                        (kind.url?.contains(kindQuery, ignoreCase = true) == true)
            }
        }
        val kindRows = remember(filteredKinds) {
            calculateExploreKindRows(filteredKinds, 6)
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(kindRows) { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (kind, span) ->
                        ExploreKindMultiTypeItem(
                            modifier = Modifier
                                .weight(span.toFloat())
                                .animateItem(),
                            kind = kind,
                            sourceUrl = sourceUrl,
                            activity = activity,
                            onOpenUrl = { url ->
                                showKindSheet = false
                                viewModel.switchExploreUrl(kind.copy(url = url))
                            },
                            onRefreshKinds = viewModel::refreshKinds,
                            backgroundColor = LegadoTheme.colorScheme.surface.copy(alpha = 0.5f),
                            isMiuix = isMiuix,
                            useCase = exploreKindUseCase
                        )
                    }

                    val totalSpan = rowItems.sumOf { it.second }
                    if (totalSpan < 6) {
                        Spacer(
                            modifier = Modifier.weight((6 - totalSpan).toFloat())
                        )
                    }
                }
            }
        }
    }


    AppScaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                modifier = Modifier.responsiveHazeEffect(
                    state = hazeState
                ),
                title = selectedTitle ?: title,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.animateContentSize(tween(300))
                    ) {
                        TopBarActionButton(
                            onClick = { showMenu = true },
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )

                        TopBarActionButton(
                            onClick = { showKindSheet = true },
                            imageVector = Icons.Outlined.FilterAlt,
                            contentDescription = "分类"
                        )

                        AnimatedVisibility(
                            visible = isGridMode,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(300))
                        ) {
                            TopBarActionButton(
                                onClick = { showGridCountSheet = true },
                                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                contentDescription = "列数设置"
                            )
                        }
                    }

                    TopBarActionButton(
                        onClick = { viewModel.setLayout() },
                        imageVector = if (!isGridMode) Icons.AutoMirrored.Outlined.FormatListBulleted else Icons.Default.GridView,
                        contentDescription = "切换布局"
                    )

                    RoundDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        RoundDropdownMenuItem(
                            text = "全部显示",
                            onClick = {
                                viewModel.setFilterState(BookFilterState.SHOW_ALL)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.SHOW_ALL)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = "隐藏已在书架的同源书籍",
                            onClick = {
                                viewModel.setFilterState(BookFilterState.HIDE_IN_SHELF)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.HIDE_IN_SHELF)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = "隐藏已在书架的非同源书籍",
                            onClick = {
                                viewModel.setFilterState(BookFilterState.HIDE_SAME_NAME_AUTHOR)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.HIDE_SAME_NAME_AUTHOR)
                                    Icon(Icons.Default.Check, null)
                            }
                        )

                        RoundDropdownMenuItem(
                            text = "只显示不在书架的书籍",
                            onClick = {
                                viewModel.setFilterState(BookFilterState.SHOW_NOT_IN_SHELF_ONLY)
                                showMenu = false
                            },
                            trailingIcon = {
                                if (filterState == BookFilterState.SHOW_NOT_IN_SHELF_ONLY)
                                    Icon(Icons.Default.Check, null)
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = { viewModel.loadMore(isRefresh = true) },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            Crossfade(
                targetState = isGridMode,
                animationSpec = tween(250),
                label = "LayoutCrossfade"
            ) { isGrid ->
                if (isGrid) {
                    LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .responsiveHazeSource(hazeState),
                        columns = GridCells.Fixed(gridColumnCount),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + 12.dp,
                            bottom = paddingValues.calculateBottomPadding() + 12.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = books,
                            key = { it.book.bookUrl }
                        ) { item ->
                            ExploreBookGridItem(
                                book = item.book,
                                shelfState = item.shelfState,
                                onClick = { onBookClick(item.book) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreFooter(
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                isEnd = isBookEnd,
                                onRetry = viewModel::loadMore
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .responsiveHazeSource(hazeState),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 16.dp
                        )
                    ) {
                        items(
                            items = books,
                            key = { it.book.bookUrl }
                        ) { item ->
                            ExploreBookItem(
                                book = item.book,
                                shelfState = item.shelfState,
                                onClick = { onBookClick(item.book) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item {
                            LoadMoreFooter(
                                isLoading = isLoading,
                                errorMsg = errorMsg,
                                isEnd = isBookEnd,
                                onRetry = viewModel::loadMore
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun ExploreBookItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ExploreBookGridItem(
    book: SearchBook,
    onClick: () -> Unit,
    shelfState: BookShelfState,
    modifier: Modifier = Modifier
) {
    SearchBookGridItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreFooter(
    isLoading: Boolean,
    errorMsg: String?,
    isEnd: Boolean,
    onRetry: () -> Unit
) {

    LaunchedEffect(isLoading, errorMsg, isEnd) {
        if (!isLoading && errorMsg == null && !isEnd) {
            while (true) {
                onRetry()
                delay(1000L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            AnimatedContent(
                targetState = when {
                    isLoading -> "加载中…"
                    errorMsg != null -> "加载失败: $errorMsg"
                    isEnd -> "已经到底了~"
                    else -> "我爱你"
                },
                label = "FooterTextChange"
            ) { text ->
                AppText(
                    text = text,
                    color = when {
                        errorMsg != null -> Color.Red
                        else -> Color.Gray
                    },
                    style = LegadoTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedTextButton(
                isLoading = isLoading,
                onClick = onRetry,
                text = if (errorMsg != null) "重试" else "再试一次",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
