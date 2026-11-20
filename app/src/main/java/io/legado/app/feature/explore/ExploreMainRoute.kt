package io.legado.app.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.main.LocalMainPageIndex
import io.legado.app.ui.main.LocalMainPagerBottomBarState
import io.legado.app.ui.main.LocalMainPagerTopBarHost
import io.legado.app.ui.main.MainPagerBottomTabBar
import io.legado.app.ui.main.MainPagerPageChromeEffect
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreRouteScreen
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.tabRow.AppTabRowIconButton
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExploreMainRoute(
    onOpenExploreShow: (String?, String, String?) -> Unit,
    onOpenLogin: (String) -> Unit,
    onOpenEdit: (String) -> Unit,
    onOpenSearch: (String) -> Unit,
    onBookClick: (SearchBook, String?) -> Unit,
    viewModel: ExploreMainViewModel = koinViewModel(),
    sourceViewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceState by sourceViewModel.uiState.collectAsStateWithLifecycle()
    if (!state.ready) return

    key(state.selectedSourceUrl) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModel) {
            val kinds = remember(state.kinds) {
                loadableExploreMainKinds(state.kinds)
            }
            val pagerState = rememberPagerState { kinds.size }
            val scope = rememberCoroutineScope()
            val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
            val pagerTopBarHost = LocalMainPagerTopBarHost.current
            val pagerBottomBarState = LocalMainPagerBottomBarState.current
            val mainPageIndex = LocalMainPageIndex.current
            val currentKind = kinds.getOrNull(pagerState.currentPage)
            val selectedSource = state.sources.firstOrNull { it.url == state.selectedSourceUrl }
            // 恢复该书上上次浏览的分类，并把之后的稳定翻页写回偏好。
            LaunchedEffect(kinds, state.selectedKindKey) {
                val savedIndex = kinds.indexOfFirst { exploreKindKey(it) == state.selectedKindKey }
                if (savedIndex > 0 && pagerState.currentPage != savedIndex) {
                    pagerState.scrollToPage(savedIndex)
                }
                snapshotFlow { pagerState.settledPage }
                    .drop(1)
                    .collect { page ->
                        kinds.getOrNull(page)?.let {
                            viewModel.onIntent(ExploreMainIntent.SelectKind(exploreKindKey(it)))
                        }
                        pagerBottomBarState?.reveal()
                    }
            }
            val activeViewModel: ExploreShowViewModel? = currentKind?.let {
                koinViewModel(key = exploreKindKey(it))
            }
            // 只订阅列表/网格布局字段供书源 sheet 呈现，其余状态仍由各分类界面自己持有。
            val showViewModel = activeViewModel
            val showLayoutMode = showViewModel?.let { vm ->
                vm.layoutState.collectAsStateWithLifecycle().value
            }
            val showGridCount = showViewModel?.let { vm ->
                vm.gridCount.collectAsStateWithLifecycle().value
            } ?: 3
            var sourceSheetVisible by remember { mutableStateOf(false) }
            var kindSheetVisible by remember { mutableStateOf(false) }
            val exploreKindTabs: @Composable () -> Unit = {
                if (kinds.isNotEmpty()) {
                    AppTabRow(
                        tabTitles = kinds.map { it.title },
                        selectedTabIndex = pagerState.targetPage,
                        onTabSelected = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        horizontalPadding = 12.dp,
                        trailingAction = {
                            AppTabRowIconButton(
                                onClick = { kindSheetVisible = true },
                                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                contentDescription = stringResource(R.string.select_or_search_category),
                            )
                        },
                    )
                }
            }
            // 旧顶栏模式把分类页签挂在顶栏下方；横划模式改由页面底部承载。
            val exploreTopBarBottom: @Composable ColumnScope.() -> Unit = {
                AnimatedVisibility(
                    visible = kinds.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    exploreKindTabs()
                }
            }

            AppScaffold(
                modifier = if (pagerTopBarHost != null) {
                    Modifier
                } else {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                },
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    // 横划模式把分类页签移到页面底部；源列表模式没有分类页签。
                    if (pagerTopBarHost != null && state.showContent) {
                        MainPagerBottomTabBar(
                            visible = kinds.isNotEmpty() && pagerBottomBarState?.hidden != true,
                        ) {
                            exploreKindTabs()
                        }
                    }
                },
                topBar = {
                    if (state.showContent) {
                        val exploreTopBarActions: @Composable RowScope.() -> Unit = {
                            // 等价旧版长按书源的「搜索」：打开搜索界面并只使用当前书源搜索。
                            TopBarActionButton(
                                onClick = {
                                    selectedSource?.let { source ->
                                        onOpenSearch(
                                            SearchScope(
                                                BookSourcePart(
                                                    bookSourceUrl = source.url,
                                                    bookSourceName = source.name
                                                )
                                            ).toString()
                                        )
                                    }
                                },
                                imageVector = AppIcons.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                            // 模式切换放在右侧第二个，最右是书源 / 分组入口。
                            TopBarActionButton(
                                onClick = {
                                    viewModel.onIntent(
                                        ExploreMainIntent.ShowContent(
                                            false
                                        )
                                    )
                                },
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = stringResource(R.string.explore_source_list),
                            )
                            TopBarActionButton(
                                onClick = { sourceSheetVisible = true },
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(R.string.explore_select_source),
                            )
                        }
                        if (pagerTopBarHost != null) {
                            MainPagerPageChromeEffect(
                                host = pagerTopBarHost,
                                pageIndex = mainPageIndex,
                                subtitle = selectedSource?.name,
                                actions = exploreTopBarActions,
                            )
                        } else {
                            GlassMediumFlexibleTopAppBar(
                                title = stringResource(R.string.discovery),
                                subtitle = selectedSource?.name,
                                scrollBehavior = scrollBehavior,
                                actions = exploreTopBarActions,
                                bottomContent = exploreTopBarBottom,
                            )
                        }
                    } else {
                        // 源列表模式同样由主壳渲染顶栏：横划切页时主壳顶栏高度必须恒定，
                        // 让出再恢复会让 pager 重新测量并掐断手势。
                        val allGroupLabel = stringResource(R.string.all)
                        val sourceMenuGroups = persistentListOf(
                            AppActionGroup(
                                id = "explore_main_groups",
                                actions = buildList {
                                    add(
                                        AppAction(
                                            "group:",
                                            allGroupLabel,
                                            icon = Icons.Default.Group,
                                        )
                                    )
                                    sourceState.groups.forEach { group ->
                                        add(
                                            AppAction(
                                                "group:$group",
                                                group,
                                                icon = Icons.AutoMirrored.Outlined.Label,
                                            )
                                        )
                                    }
                                }.toImmutableList(),
                            ),
                        )
                        val onSourceMenuAction: (String) -> Unit = { id ->
                            if (id.startsWith("group:")) {
                                sourceViewModel.onIntent(
                                    ExploreIntent.SetGroup(id.removePrefix("group:"))
                                )
                            }
                        }
                        if (pagerTopBarHost != null) {
                            MainPagerPageChromeEffect(
                                host = pagerTopBarHost,
                                pageIndex = mainPageIndex,
                                subtitle = sourceState.selectedGroup.ifEmpty { allGroupLabel },
                                actions = {
                                    TopBarActionButton(
                                        onClick = {
                                            viewModel.onIntent(
                                                ExploreMainIntent.ShowContent(true)
                                            )
                                        },
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = stringResource(R.string.explore_main_content),
                                    )
                                    TopBarActionButton(
                                        onClick = {
                                            sourceViewModel.onIntent(
                                                ExploreIntent.ToggleSearch(!sourceState.isSearch)
                                            )
                                        },
                                        imageVector = AppIcons.Search,
                                        contentDescription = stringResource(R.string.search),
                                    )
                                    AppActionMenu(
                                        groups = sourceMenuGroups,
                                        onAction = onSourceMenuAction,
                                    ) {
                                        AppIcon(
                                            imageVector = AppIcons.MoreVert,
                                            contentDescription = stringResource(R.string.more_menu),
                                            modifier = this.modifier,
                                        )
                                    }
                                },
                                bottomContent = {
                                    AnimatedVisibility(
                                        visible = sourceState.isSearch,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        SearchBar(
                                            query = sourceState.searchKey,
                                            onQueryChange = {
                                                sourceViewModel.onIntent(ExploreIntent.Search(it))
                                            },
                                            onSearch = {
                                                sourceViewModel.onIntent(ExploreIntent.Search(it))
                                            },
                                            placeholder = stringResource(R.string.search),
                                            onClear = {
                                                sourceViewModel.onIntent(ExploreIntent.Search(""))
                                            },
                                        )
                                    }
                                },
                            )
                        } else {
                            DynamicTopAppBar(
                                title = stringResource(R.string.discovery),
                                subtitle = sourceState.selectedGroup.ifEmpty { allGroupLabel },
                                state = sourceState,
                                scrollBehavior = scrollBehavior,
                                onSearchToggle = {
                                    sourceViewModel.onIntent(
                                        ExploreIntent.ToggleSearch(
                                            it
                                        )
                                    )
                                },
                                onSearchQueryChange = { sourceViewModel.onIntent(ExploreIntent.Search(it)) },
                                searchPlaceholder = stringResource(R.string.search),
                                onClearSelection = {},
                                topBarActions = {
                                    TopBarActionButton(
                                        onClick = {
                                            viewModel.onIntent(
                                                ExploreMainIntent.ShowContent(
                                                    true
                                                )
                                            )
                                        },
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = stringResource(R.string.explore_main_content),
                                    )
                                },
                                menuGroups = sourceMenuGroups,
                                onMenuAction = { id ->
                                    if (id.startsWith("group:")) {
                                        sourceViewModel.onIntent(
                                            ExploreIntent.SetGroup(id.removePrefix("group:"))
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            ) { padding ->
                Crossfade(
                    targetState = state.showContent,
                    label = "ExploreMainMode"
                ) { showContent ->
                    if (!showContent) {
                        ExploreRouteScreen(
                            viewModel = sourceViewModel,
                            onOpenExploreShow = onOpenExploreShow,
                            onOpenLogin = onOpenLogin,
                            onOpenEdit = onOpenEdit,
                            onOpenSearch = onOpenSearch,
                            embeddedPadding = padding,
                        )
                    } else if (kinds.isEmpty() || selectedSource == null) {
                        EmptyMessage(
                            message = state.kindsError ?: stringResource(R.string.explore_empty),
                            isLoading = state.kindsLoading,
                            buttonText = if (state.kindsError != null) stringResource(R.string.refresh) else null,
                            onButtonClick = { viewModel.onIntent(ExploreMainIntent.RetryKinds) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            // 分类列表缩短的那一帧，pager 仍可能按旧的页数请求 key。
                            key = { index ->
                                kinds.getOrNull(index)?.let(::exploreKindKey)
                                    ?: "missing-kind:$index"
                            },
                        ) { page ->
                            val kind = kinds.getOrNull(page) ?: return@HorizontalPager
                            val pageViewModel: ExploreShowViewModel =
                                koinViewModel(key = exploreKindKey(kind))
                            LaunchedEffect(pageViewModel, selectedSource.url, kind.url) {
                                pageViewModel.onIntent(
                                    ExploreShowIntent.InitData(
                                        selectedSource.url,
                                        kind.url
                                    )
                                )
                            }
                            ExploreShowRouteScreen(
                                viewModel = pageViewModel,
                                title = kind.title,
                                onBack = {},
                                onBookClick = onBookClick,
                                embeddedInMain = true,
                                embeddedPadding = padding,
                                embeddedScrollBehavior = scrollBehavior,
                            )
                        }
                    }
                }
            }
            ExploreSourceSelectSheet(
                show = sourceSheetVisible,
                sources = state.sources,
                selectedSourceUrl = state.selectedSourceUrl,
                layoutMode = showLayoutMode,
                gridCount = showGridCount,
                onDismissRequest = { sourceSheetVisible = false },
                onSelectSource = { source ->
                    viewModel.onIntent(ExploreMainIntent.SelectSource(source.url))
                },
                onToggleLayout = {
                    showViewModel?.onIntent(ExploreShowIntent.ToggleLayout)
                },
                onSaveGridCount = { count ->
                    showViewModel?.onIntent(ExploreShowIntent.SaveGridCount(count))
                },
            )
            ExploreKindSelectSheet(
                show = kindSheetVisible,
                sourceUrl = state.selectedSourceUrl,
                initialSelectedTitles = listOfNotNull(currentKind?.title),
                onDismissRequest = { kindSheetVisible = false },
                onKindsChanged = { viewModel.onIntent(ExploreMainIntent.RetryKinds) },
                onSelected = { selected ->
                    val index = kinds.indexOfFirst { it.url == selected.firstOrNull()?.url }
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}
