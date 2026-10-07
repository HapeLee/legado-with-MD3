package io.legado.app.feature.explore

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowSheet
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreRouteScreen
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuLazy
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.DynamicTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.flow.drop
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
                    }
            }
            // 只保留能下发意图的实例，不再订阅它的 uiState：列表/网格状态由 sheet 自己呈现。
            val activeViewModel: ExploreShowViewModel? = currentKind?.let {
                koinViewModel(key = exploreKindKey(it))
            }
            var sourceMenuExpanded by remember { mutableStateOf(false) }
            var kindSheetVisible by remember { mutableStateOf(false) }

            AppScaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    if (state.showContent) {
                        GlassMediumFlexibleTopAppBar(
                            title = stringResource(R.string.discovery),
                            subtitle = selectedSource?.name,
                            scrollBehavior = scrollBehavior,
                            actions = {
                                val contentViewModel = activeViewModel
                                if (contentViewModel != null) {
                                    // 列表 / 网格切换与列数都收进同一个 sheet，顶栏只留一个入口。
                                    TopBarActionButton(
                                        onClick = {
                                            contentViewModel.onIntent(
                                                ExploreShowIntent.ShowSheet(
                                                    ExploreShowSheet.GridCount
                                                )
                                            )
                                        },
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.a11y_switch_layout),
                                    )
                                }
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
                                Box {
                                    TopBarActionButton(
                                        onClick = { sourceMenuExpanded = true },
                                        imageVector = AppIcons.MoreVert,
                                        contentDescription = stringResource(R.string.explore_select_source),
                                    )
                                    RoundDropdownMenuLazy(
                                        expanded = sourceMenuExpanded,
                                        onDismissRequest = { sourceMenuExpanded = false },
                                    ) { dismiss ->
                                        items(state.sources, key = { it.url }) { source ->
                                            RoundDropdownMenuItem(
                                                text = source.name,
                                                isSelected = source.url == state.selectedSourceUrl,
                                                onClick = {
                                                    dismiss()
                                                    viewModel.onIntent(
                                                        ExploreMainIntent.SelectSource(
                                                            source.url
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            bottomContent = {
                                if (kinds.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AppTabRow(
                                            tabTitles = kinds.map { it.title },
                                            selectedTabIndex = pagerState.targetPage,
                                            onTabSelected = { index ->
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            isScrollable = true,
                                        )
                                        SmallOutlinedButton(
                                            onClick = { kindSheetVisible = true },
                                            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                                            contentDescription = stringResource(R.string.select_or_search_category),
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        DynamicTopAppBar(
                            title = stringResource(R.string.discovery),
                            subtitle = sourceState.selectedGroup.ifEmpty { stringResource(R.string.all) },
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
                            dropDownMenuContent = { dismiss ->
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Icons.Default.Group) },
                                    text = stringResource(R.string.all),
                                    onClick = { sourceViewModel.onIntent(ExploreIntent.SetGroup("")); dismiss() },
                                )
                                sourceState.groups.forEach { group ->
                                    RoundDropdownMenuItem(
                                        leadingIcon = { MenuItemIcon(Icons.AutoMirrored.Outlined.Label) },
                                        text = group,
                                        onClick = {
                                            sourceViewModel.onIntent(
                                                ExploreIntent.SetGroup(
                                                    group
                                                )
                                            ); dismiss()
                                        },
                                    )
                                }
                            },
                        )
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
                            key = { exploreKindKey(kinds[it]) },
                        ) { page ->
                            val kind = kinds[page]
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
            ExploreKindSelectSheet(
                show = kindSheetVisible,
                sourceUrl = state.selectedSourceUrl,
                initialSelectedTitles = listOfNotNull(currentKind?.title),
                onDismissRequest = { kindSheetVisible = false },
                onSelected = { selected ->
                    val index = kinds.indexOfFirst { it.url == selected.firstOrNull()?.url }
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }
    }
}
