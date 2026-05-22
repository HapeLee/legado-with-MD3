package io.legado.app.ui.book.explore

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource
import io.legado.app.ui.widget.components.AppPullToRefresh
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@SuppressLint("LocalContextConfigurationRead", "ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun ExploreShowScreen(
    title: String,
    sourceUrl: String?,
    exploreUrl: String?,
    onBack: () -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    viewModel: ExploreShowViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {

    LaunchedEffect(sourceUrl, exploreUrl, viewModel) {
        viewModel.initData(sourceUrl, exploreUrl)
    }

    val books by viewModel.uiBooks.collectAsState()
    val isBookEnd by viewModel.isEnd.collectAsState()
    val shouldTriggerAutoLoad by viewModel.shouldTriggerAutoLoad.collectAsState()
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
    val exploreKindUseCase: ExploreKindUiUseCase = koinInject()

    LaunchedEffect(sourceUrl) {
        exploreKindUseCase.warmUp(sourceUrl)
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()

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


    ExploreKindSelectSheet(
        show = showKindSheet,
        onDismissRequest = { showKindSheet = false },
        sourceUrl = sourceUrl,
        onSelected = { selectedKinds ->
            selectedKinds.firstOrNull()?.let { kind ->
                viewModel.switchExploreUrl(kind)
            }
        }
    )

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
        AppPullToRefresh(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadMore(isRefresh = true) },
            topPadding = paddingValues.calculateTopPadding()
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
                        itemsIndexed(
                            items = books,
                            key = { index, item -> "${item.book.bookUrl}:$index" }
                        ) { index, item ->
                            val sharedCoverKey = bookCoverSharedElementKey(
                                item.book.bookUrl,
                                "explore:grid:$index"
                            )
                            ExploreBookGridItem(
                                book = item.book,
                                shelfState = item.shelfState,
                                onClick = { onBookClick(item.book, sharedCoverKey) },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedCoverKey,
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
                        itemsIndexed(
                            items = books,
                            key = { index, item -> "${item.book.bookUrl}:$index" }
                        ) { index, item ->
                            val sharedCoverKey = bookCoverSharedElementKey(
                                item.book.bookUrl,
                                "explore:list:$index"
                            )
                            ExploreBookItem(
                                book = item.book,
                                shelfState = item.shelfState,
                                onClick = { onBookClick(item.book, sharedCoverKey) },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedCoverKey,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreBookItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreBookGridItem(
    book: SearchBook,
    onClick: () -> Unit,
    shelfState: BookShelfState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    SearchBookGridItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey
    )
}

