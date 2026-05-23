package io.legado.app.ui.main.homepage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppPullToRefresh
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun HomepageScreen(
    viewModel: HomepageViewModel = koinViewModel(),
    onBookClick: (name: String?, author: String?, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onModuleHeaderClick: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Removed allSets and browseSources as they are now part of uiState.manageState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val layoutMode = HomepageConfig.homepageLayoutModeState.value

    val selectedSets = remember(uiState.manageState.sets) {
        uiState.manageState.sets.filter { it.isSelected }
    }
    val pagerState = rememberPagerState(pageCount = {
        selectedSets.size.coerceAtLeast(1)
    })

    val homeString = stringResource(R.string.home)
    val currentTitle by remember(
        layoutMode,
        pagerState.currentPage,
        selectedSets,
    ) {
        derivedStateOf {
            if (layoutMode == 1) {
                homeString
            } else {
                selectedSets.getOrNull(pagerState.currentPage)?.sourceName ?: homeString
            }
        }
    }

    BackHandler(enabled = uiState.isManageMode || uiState.isConfigMode) {
        if (uiState.isManageMode) viewModel.toggleManageMode()
        else viewModel.toggleConfigMode()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomepageEffect.NavigateToBookInfo ->
                    onBookClick(
                        effect.name,
                        effect.author,
                        effect.bookUrl,
                        effect.origin,
                        effect.coverPath,
                        effect.sharedCoverKey
                    )

                is HomepageEffect.NavigateToExploreShow ->
                    onModuleHeaderClick(effect.title, effect.sourceUrl, effect.exploreUrl)

                is HomepageEffect.ShowSnackbar -> {}
            }
        }
    }

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = currentTitle,
                scrollBehavior = scrollBehavior,
                actions = {
                    TopBarActionButton(
                        onClick = { viewModel.toggleConfigMode() },
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Layout Settings",
                    )
                    TopBarActionButton(
                        onClick = { viewModel.toggleManageMode() },
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Manage Modules",
                    )
                },
                bottomContent = {
                    if (layoutMode == 1 && selectedSets.isNotEmpty()) {
                        AppTabRow(
                            tabTitles = selectedSets.map { it.sourceName },
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        AppPullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (selectedSets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppText(stringResource(R.string.homepage_no_source_sets_selected))
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { index -> selectedSets.getOrNull(index)?.sourceUrl ?: index }
                ) { pageIndex ->
                    val source = selectedSets.getOrNull(pageIndex)
                    val sourceModules = remember(uiState.modules, source) {
                        uiState.modules.filter { module ->
                            if (source?.isCustomSet == true) {
                                val setId =
                                    HomepageViewModel.customSetIdFromUrl(source.sourceUrl)
                                module.customSetId == setId
                            } else {
                                module.sourceUrl == source?.sourceUrl
                            }
                        }
                    }
                    ModuleList(
                        modules = sourceModules,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onErrorClick = { errorMsg = it },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        AppAlertDialog(
            data = errorMsg,
            onDismissRequest = { errorMsg = null },
            title = stringResource(R.string.homepage_module_error),
            text = errorMsg,
            confirmText = stringResource(R.string.copy_text),
            onConfirm = {
                context.sendToClip(it)
                errorMsg = null
            },
            dismissText = stringResource(R.string.close),
            onDismiss = { errorMsg = null }
        )

        HomepageModuleManageSheet(
            data = if (uiState.isManageMode) Unit else null,
            onDismissRequest = { viewModel.toggleManageMode() },
            state = uiState.manageState,
            actions = remember {
                HomepageManageActions(
                    onToggleSet = { url, isEnabled ->
                        viewModel.toggleSourceFilter(
                            url,
                            isEnabled
                        )
                    },
                    onGetSourceModules = { url, setId -> viewModel.getSourceModules(url, setId) },
                    onSyncSourceModules = { viewModel.syncSourceModules(it) },
                    onToggleModule = { id, visible -> viewModel.setModuleVisible(id, visible) },
                    onJoinModule = { sourceUrl, targetSetId, def ->
                        viewModel.joinModule(sourceUrl, targetSetId, def)
                    },
                    onAddCustomModule = { sourceUrl, targetSetId, def ->
                        viewModel.addCustomModule(sourceUrl, targetSetId, def)
                    },
                    onAddButtonGroupFromKinds = { sourceUrl, targetSetId, title, kinds ->
                        viewModel.addButtonGroupFromKinds(sourceUrl, targetSetId, title, kinds)
                    },
                    onGetExploreKinds = { viewModel.getSourceExploreKinds(it) },
                    onUpdateModule = { globalId, def -> viewModel.updateModule(globalId, def) },
                    onDeleteModule = { viewModel.deleteModule(it) },
                    onReorderModules = { ids -> viewModel.reorderJoinedModules(ids) },
                    onReorderSets = { urls -> viewModel.reorderCustomSets(urls) },
                    onSetCustomSetTitle = { id, title ->
                        viewModel.setModuleCustomSetTitle(
                            id,
                            title
                        )
                    },
                    onCreateCustomSet = { viewModel.createCustomSet(it) },
                    onRenameCustomSet = { id, name -> viewModel.renameCustomSet(id, name) },
                    onDeleteCustomSet = { viewModel.deleteCustomSet(it) },
                    onAssignModuleToCustomSet = { id, setId ->
                        viewModel.assignModuleToCustomSet(id, setId)
                    }
                )
            }
        )

        HomepageLayoutSheet(
            data = if (uiState.isConfigMode) Unit else null,
            onDismissRequest = { viewModel.toggleConfigMode() },
            layoutMode = layoutMode,
            onLayoutModeChange = { viewModel.setLayoutMode(it) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ModuleList(
    modules: List<HomepageModuleUi>,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onErrorClick: (String) -> Unit
) {
    if (modules.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AppText(stringResource(R.string.homepage_add_module_definition))
        }
    } else {
        // 1. 过滤和重排模块：每个集只能有一个无限流模块，且必须在最下面
        val processedModules = remember(modules) {
            fun isInfinite(m: HomepageModuleUi): Boolean {
                return m.type == HomepageModuleType.Waterfall ||
                        m.type == HomepageModuleType.InfiniteGrid
            }

            val infinite = modules.firstOrNull { isInfinite(it) }
            val others = modules.filter { !isInfinite(it) }
            if (infinite != null) others + infinite else others
        }

        val gridColumns = remember(processedModules) {
            val infiniteModule = processedModules.find { m ->
                m.type == HomepageModuleType.Waterfall ||
                        m.type == HomepageModuleType.InfiniteGrid
            }
            infiniteModule?.config?.get("layout_columns")?.toIntOrNull() ?: 2
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(gridColumns),
            state = gridState,
            modifier = modifier,
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        ) {
            processedModules.forEach { moduleUi ->
                // 1. 头部 (全宽)
                item(key = "header_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                    ModuleHeader(
                        title = moduleUi.title,
                        onNavigate = if (moduleUi.type == HomepageModuleType.ButtonGroup) null else {
                            {
                                viewModel.onModuleHeaderClick(
                                    moduleUi.sourceUrl,
                                    moduleUi.exploreUrl,
                                    moduleUi.title,
                                )
                            }
                        },
                    )
                }

                // 2. 内容正文
                when (val state = moduleUi.state) {
                    is ModuleLoadState.Loading -> {
                        item(
                            key = "loading_${moduleUi.globalId}",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppCircularProgressIndicator()
                            }
                        }
                    }

                    is ModuleLoadState.Error -> {
                        item(
                            key = "error_${moduleUi.globalId}",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                GlassCard(
                                    onClick = { onErrorClick(state.message) },
                                    containerColor = LegadoTheme.colorScheme.errorContainer.copy(
                                        alpha = 0.6f
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = 16.dp,
                                                    vertical = 16.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {

                                            AppIcon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = LegadoTheme.colorScheme.error
                                            )

                                            AppText(
                                                text = state.message,
                                                color = LegadoTheme.colorScheme.error,
                                                style = LegadoTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        HorizontalDivider(
                                            color = LegadoTheme.colorScheme.error.copy(alpha = 0.3f)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.retryModule(moduleUi.globalId)
                                                }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppIcon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = LegadoTheme.colorScheme.error
                                                )

                                                AppText(
                                                    text = "重试",
                                                    color = LegadoTheme.colorScheme.error,
                                                    style = LegadoTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is ModuleLoadState.Buttons -> {
                        item(
                            key = "buttons_${moduleUi.globalId}",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            ButtonGroupModule(
                                kinds = state.kinds,
                                sourceUrl = moduleUi.sourceUrl,
                                globalId = moduleUi.globalId,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth(),
                                layoutConfig = moduleUi.layoutConfig
                            )
                        }
                    }

                    is ModuleLoadState.Loaded -> {
                        val config = moduleUi.config
                        when (moduleUi.type) {
                            HomepageModuleType.Waterfall -> {
                                itemsIndexed(
                                    state.books,
                                    key = { index, book -> "wf_${moduleUi.globalId}_${book.bookUrl}_$index" }) { index, book ->
                                    val sharedCoverKey = bookCoverSharedElementKey(
                                        book.bookUrl,
                                        "home:${moduleUi.globalId}:waterfall:$index"
                                    )
                                    WaterfallItem(
                                        book = book,
                                        onClick = { viewModel.onBookClick(book, sharedCoverKey) },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedCoverKey = sharedCoverKey,
                                    )
                                }

                                item(
                                    key = "wf_more_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        errorMsg = null,
                                        isEnd = !state.hasMore,
                                        onRetry = { viewModel.loadMoreModule(moduleUi.globalId) }
                                    )
                                }
                            }

                            HomepageModuleType.InfiniteGrid -> {
                                itemsIndexed(
                                    state.books,
                                    key = { index, book -> "inf_grid_${moduleUi.globalId}_${book.bookUrl}_$index" }) { index, book ->
                                    val sharedCoverKey = bookCoverSharedElementKey(
                                        book.bookUrl,
                                        "home:${moduleUi.globalId}:infinite:$index"
                                    )
                                    SearchBookGridItem(
                                        book = book,
                                        shelfState = io.legado.app.domain.model.BookShelfState.NOT_IN_SHELF,
                                        onClick = { viewModel.onBookClick(book, sharedCoverKey) },
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedCoverKey = sharedCoverKey
                                    )
                                }

                                item(
                                    key = "inf_grid_more_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        errorMsg = null,
                                        isEnd = !state.hasMore,
                                        onRetry = { viewModel.loadMoreModule(moduleUi.globalId) }
                                    )
                                }
                            }

                            HomepageModuleType.Grid -> {
                                val rows = config["layout_rows"]?.toIntOrNull() ?: 2
                                val columns = config["layout_columns"]?.toIntOrNull() ?: 3
                                item(
                                    key = "content_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    GridModule(
                                        books = state.books,
                                        onClick = { book, sharedCoverKey ->
                                            viewModel.onBookClick(book, sharedCoverKey)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        columns = columns,
                                        maxRows = rows,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        sharedCoverKeySourceId = "home:${moduleUi.globalId}:grid",
                                    )
                                }
                            }

                            else -> {
                                when (moduleUi.type) {
                                    HomepageModuleType.Banner -> {
                                        item(
                                            key = "content_${moduleUi.globalId}",
                                            span = StaggeredGridItemSpan.FullLine
                                        ) {
                                            BannerModule(
                                                books = state.books,
                                                onClick = { book, sharedCoverKey ->
                                                    viewModel.onBookClick(book, sharedCoverKey)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                sharedCoverKeySourceId = "home:${moduleUi.globalId}:banner",
                                            )
                                        }
                                    }

                                    HomepageModuleType.Ranking -> {
                                        item(
                                            key = "content_${moduleUi.globalId}",
                                            span = StaggeredGridItemSpan.FullLine
                                        ) {
                                            RankingModule(
                                                books = state.books,
                                                onClick = { book, sharedCoverKey ->
                                                    viewModel.onBookClick(book, sharedCoverKey)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                sharedCoverKeySourceId = "home:${moduleUi.globalId}:ranking",
                                            )
                                        }
                                    }

                                    HomepageModuleType.GridRanking -> {
                                        item(
                                            key = "content_${moduleUi.globalId}",
                                            span = StaggeredGridItemSpan.FullLine
                                        ) {
                                            GridRankingModule(
                                                books = state.books,
                                                onClick = { book, sharedCoverKey ->
                                                    viewModel.onBookClick(book, sharedCoverKey)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                rows = config["layout_rows"]?.toIntOrNull() ?: 4,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                sharedCoverKeySourceId = "home:${moduleUi.globalId}:grid-ranking",
                                            )
                                        }
                                    }

                                    HomepageModuleType.Card -> {
                                        item(
                                            key = "content_${moduleUi.globalId}",
                                            span = StaggeredGridItemSpan.FullLine
                                        ) {
                                            CardModule(
                                                books = state.books,
                                                onClick = { book, sharedCoverKey ->
                                                    viewModel.onBookClick(book, sharedCoverKey)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                sharedCoverKeySourceId = "home:${moduleUi.globalId}:card",
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleHeader(
    title: String,
    onNavigate: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onNavigate != null) {
            SmallTonalIconButton(
                onClick = onNavigate,
                imageVector = Icons.AutoMirrored.Filled.ArrowForward
            )
        }
    }
}
