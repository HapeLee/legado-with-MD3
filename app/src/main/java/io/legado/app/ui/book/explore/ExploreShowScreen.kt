package io.legado.app.ui.book.explore

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource
import io.legado.app.ui.widget.components.AppPullToRefresh
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.book.SearchBookListItem
import io.legado.app.ui.widget.components.book.SearchBookPreviewSheet
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

private enum class BookFilterState(val id: Int) {
    SHOW_ALL(0),
    HIDE_IN_SHELF(1),
    HIDE_SAME_NAME_AUTHOR(2),
    SHOW_NOT_IN_SHELF_ONLY(3);

    companion object {
        fun fromId(id: Int) = entries.getOrElse(id) { SHOW_ALL }
    }
}

@SuppressLint("LocalContextConfigurationRead", "ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class
)
@Composable
fun ExploreShowScreen(
    viewModel: ExploreShowViewModel = koinViewModel(),
    title: String = "",
    onBack: () -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExploreShowEffect.OpenBookInfo -> onBookClick(
                    SearchBook(
                        name = effect.name,
                        author = effect.author,
                        bookUrl = effect.bookUrl,
                        origin = effect.origin ?: "",
                        coverUrl = effect.coverPath,
                    ),
                    effect.sharedCoverKey,
                )

                is ExploreShowEffect.ShowMessage -> {}
            }
        }
    }

    var previewBook by remember { mutableStateOf<SearchBook?>(null) }
    var previewSharedCoverKey by remember { mutableStateOf<String?>(null) }

    val filterStateId = CoverConfig.exploreFilterState
    val books = remember(state.books, filterStateId) {
        val filter = BookFilterState.fromId(filterStateId)
        when (filter) {
            BookFilterState.SHOW_ALL -> state.books
            BookFilterState.HIDE_IN_SHELF -> state.books.filter { it.shelfState != BookShelfState.IN_SHELF }
            BookFilterState.HIDE_SAME_NAME_AUTHOR -> state.books.filter { it.shelfState != BookShelfState.SAME_NAME_AUTHOR }
            BookFilterState.SHOW_NOT_IN_SHELF_ONLY -> state.books.filter { it.shelfState == BookShelfState.NOT_IN_SHELF }
        }
    }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val isGridMode = state.layoutState == 1
    val hazeState = remember { HazeState() }
    val showLoadMoreFooter = !state.isRefreshing &&
        (state.isLoading || state.errorMsg != null || state.isEnd)
    val canLoadMore = state.books.isNotEmpty() &&
        !state.isLoading &&
        !state.isRefreshing &&
        !state.isEnd &&
        state.errorMsg == null
    val shouldLoadMore by remember(isGridMode) {
        derivedStateOf {
            if (isGridMode) {
                val total = gridState.layoutInfo.totalItemsCount
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 1
            } else {
                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 0 && last >= total - 3
            }
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        isGridMode,
        canLoadMore,
        state.books.size,
    ) {
        if (shouldLoadMore && canLoadMore) {
            viewModel.onIntent(ExploreShowIntent.LoadMore)
        }
    }

    // Auto-load next page when filter removes all books on the current page
    // but the ViewModel hasn't reached the end of data yet.
    LaunchedEffect(books.isEmpty(), state.isLoading, state.isEnd, state.books.size) {
        if (books.isEmpty() && !state.isLoading && !state.isEnd && state.books.isNotEmpty()) {
            viewModel.onIntent(ExploreShowIntent.ForceLoadNext)
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
        show = state.sheet == ExploreShowSheet.GridCount,
        onDismissRequest = { viewModel.onIntent(ExploreShowIntent.DismissSheet) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppText(
                text = "布局列数",
                style = LegadoTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextCard(
                text = "${state.gridCount} 列",
                textStyle = LegadoTheme.typography.titleSmall,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                verticalPadding = 4.dp,
                horizontalPadding = 12.dp,
                cornerRadius = 12.dp
            )
        }

        AppSlider(
            value = state.gridCount.toFloat(),
            onValueChange = {
                viewModel.onIntent(ExploreShowIntent.SaveGridCount(it.toInt().coerceIn(1, 10)))
            },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

    }

    ExploreKindSelectSheet(
        show = state.sheet == ExploreShowSheet.KindSelect,
        onDismissRequest = { viewModel.onIntent(ExploreShowIntent.DismissSheet) },
        sourceUrl = state.sourceUrl,
        onSelected = { selectedKinds ->
            selectedKinds.firstOrNull()?.let { kind ->
                viewModel.onIntent(ExploreShowIntent.SwitchKind(kind))
            }
        }
    )

    AppScaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                modifier = Modifier.responsiveHazeEffect(state = hazeState),
                title = state.selectedKindTitle ?: title,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {

                    AnimatedVisibility(
                        visible = isGridMode,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        TopBarActionButton(
                            onClick = {
                                viewModel.onIntent(
                                    ExploreShowIntent.ShowSheet(
                                        ExploreShowSheet.GridCount
                                    )
                                )
                            },
                            imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            contentDescription = "列数设置"
                        )
                    }

                    TopBarActionButton(
                        onClick = { viewModel.onIntent(ExploreShowIntent.ShowSheet(ExploreShowSheet.KindSelect)) },
                        imageVector = Icons.Outlined.FilterAlt,
                        contentDescription = "分类"
                    )

                    TopBarActionButton(
                        onClick = { viewModel.onIntent(ExploreShowIntent.ToggleLayout) },
                        imageVector = if (!isGridMode) Icons.AutoMirrored.Outlined.FormatListBulleted else Icons.Default.GridView,
                        contentDescription = "切换布局"
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        AppPullToRefresh(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onIntent(ExploreShowIntent.Refresh) },
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
                        columns = GridCells.Fixed(state.gridCount),
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
                                onClick = {
                                    viewModel.onIntent(
                                        ExploreShowIntent.OpenBook(
                                            item.book,
                                            sharedCoverKey
                                        )
                                    )
                                },
                                onLongClick = { book, coverKey ->
                                    previewBook = book
                                    previewSharedCoverKey = coverKey
                                },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedCoverKey,
                            )
                        }

                        if (showLoadMoreFooter) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ExploreShowLoadMoreFooter(
                                    state = state,
                                    onRetry = { viewModel.onIntent(ExploreShowIntent.LoadMore) },
                                    onLoadMore = { viewModel.onIntent(ExploreShowIntent.ForceLoadNext) },
                                )
                            }
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
                                onClick = {
                                    viewModel.onIntent(
                                        ExploreShowIntent.OpenBook(
                                            item.book,
                                            sharedCoverKey
                                        )
                                    )
                                },
                                onLongClick = { book, coverKey ->
                                    previewBook = book
                                    previewSharedCoverKey = coverKey
                                },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                sharedCoverKey = sharedCoverKey,
                            )
                        }

                        if (showLoadMoreFooter) {
                            item {
                                ExploreShowLoadMoreFooter(
                                    state = state,
                                    onRetry = { viewModel.onIntent(ExploreShowIntent.LoadMore) },
                                    onLoadMore = { viewModel.onIntent(ExploreShowIntent.ForceLoadNext) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val previewShelfState = previewBook?.let { book ->
        books.find { it.book.bookUrl == book.bookUrl }?.shelfState
            ?: BookShelfState.NOT_IN_SHELF
    }
    SearchBookPreviewSheet(
        data = previewBook,
        shelfState = previewShelfState,
        sharedCoverKey = previewSharedCoverKey,
        onDismissRequest = { previewBook = null },
        onOpenDetail = { book, sharedCoverKey ->
            previewBook = null
            onBookClick(book, sharedCoverKey)
        },
        onAddToShelf = { book ->
            viewModel.onIntent(ExploreShowIntent.AddToShelf(book))
        },
    )
}

@Composable
private fun ExploreShowLoadMoreFooter(
    state: ExploreShowUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    LoadMoreFooter(
        isLoading = state.isLoading,
        errorMsg = state.errorMsg,
        isEnd = state.isEnd,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        autoLoad = false,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExploreBookItem(
    book: SearchBook,
    shelfState: BookShelfState,
    onClick: () -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    SearchBookListItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        onLongClick = onLongClick,
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
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    SearchBookGridItem(
        book = book,
        shelfState = shelfState,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.padding(4.dp),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey
    )
}
