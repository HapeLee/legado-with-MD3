package io.legado.app.ui.main.homepage

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.homepage.modules.BannerModule
import io.legado.app.ui.main.homepage.modules.ButtonGroupModule
import io.legado.app.ui.main.homepage.modules.CardModule
import io.legado.app.ui.main.homepage.modules.GridModule
import io.legado.app.ui.main.homepage.modules.GridRankingModule
import io.legado.app.ui.main.homepage.modules.HomepageModuleSkeleton
import io.legado.app.ui.main.homepage.modules.RankingModule
import io.legado.app.ui.main.homepage.modules.WaterfallItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.LoadMoreFooter
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomepageModuleFeed(
    modules: List<HomepageModuleUi>,
    actions: HomepageFeedActions,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    verticalItemSpacing: Dp = 16.dp,
    @StringRes emptyMessageRes: Int = R.string.homepage_add_module_definition,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBookLongClick: (SearchBook, String?) -> Unit = { _, _ -> },
    onErrorClick: (String) -> Unit
) {
    if (modules.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            AppText(stringResource(emptyMessageRes))
        }
    } else {
        val processedModules = remember(modules) {
            fun isInfinite(m: HomepageModuleUi): Boolean {
                return m.type == HomepageModuleType.Waterfall ||
                        m.type == HomepageModuleType.InfiniteGrid
            }

            val infinite = modules.firstOrNull { isInfinite(it) }
            val others = modules.filter { !isInfinite(it) }
            if (infinite != null) others + infinite else others
        }
        var selectedRankingSources by rememberSaveable {
            mutableStateOf(hashMapOf<String, String>())
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
            verticalItemSpacing = verticalItemSpacing,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = if (
                    LocalAppUiConfiguration.current.appShell.useFloatingBottomBar ||
                    LocalAppUiConfiguration.current.theme.enableBlur
                ) {
                    120.dp
                } else {
                    8.dp
                },
            ),
        ) {
            processedModules.forEach { moduleUi ->
                val rankingSources =
                    (moduleUi.state as? ModuleLoadState.Rankings)?.sources
                val selectedRankingSource = rankingSources?.firstOrNull {
                    it.title == selectedRankingSources[moduleUi.globalId]
                } ?: rankingSources?.firstOrNull()
                val displayState = selectedRankingSource?.state ?: moduleUi.state

                item(key = "header_${moduleUi.globalId}", span = StaggeredGridItemSpan.FullLine) {
                    ModuleHeader(
                        title = rankingSources?.firstOrNull()?.title ?: moduleUi.title,
                        sourceTabs = rankingSources,
                        selectedSourceTitle = selectedRankingSource?.title,
                        onSourceSelected = { sourceTitle ->
                            selectedRankingSources = HashMap(selectedRankingSources).apply {
                                put(moduleUi.globalId, sourceTitle)
                            }
                        },
                        onNavigate = if (moduleUi.type == HomepageModuleType.ButtonGroup) {
                            null
                        } else {
                            {
                                actions.onModuleHeaderClick(
                                    moduleUi.sourceUrl,
                                    selectedRankingSource?.url ?: moduleUi.exploreUrl,
                                    selectedRankingSource?.title ?: moduleUi.title,
                                )
                            }
                        },
                    )
                }

                when (val state = displayState) {
                    is ModuleLoadState.Loading,
                    is ModuleLoadState.Error -> {
                        item(
                            key = "status_${moduleUi.globalId}",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            AnimatedContent(
                                targetState = state,
                                transitionSpec = {
                                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                },
                                contentKey = { it::class },
                            ) { targetState ->
                                when (targetState) {
                                    is ModuleLoadState.Loading -> {
                                        HomepageModuleSkeleton(
                                            type = moduleUi.type,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    is ModuleLoadState.Error -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            GlassCard(
                                                onClick = { onErrorClick(targetState.message) },
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
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            12.dp
                                                        )
                                                    ) {
                                                        AppIcon(
                                                            imageVector = Icons.Outlined.Info,
                                                            contentDescription = null,
                                                            tint = LegadoTheme.colorScheme.error
                                                        )

                                                        AppText(
                                                            text = targetState.message,
                                                            color = LegadoTheme.colorScheme.error,
                                                            style = LegadoTheme.typography.bodySmall,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }

                                                    HorizontalDivider(
                                                        color = LegadoTheme.colorScheme.error.copy(
                                                            alpha = 0.3f
                                                        )
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable(
                                                                role = Role.Button,
                                                                onClickLabel = stringResource(R.string.retry),
                                                            ) {
                                                                actions.onRetryModule(moduleUi.globalId)
                                                            }
                                                            .padding(vertical = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                8.dp
                                                            ),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            AppIcon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = null,
                                                                tint = LegadoTheme.colorScheme.error
                                                            )

                                                            AppText(
                                                                text = stringResource(R.string.retry),
                                                                color = LegadoTheme.colorScheme.error,
                                                                style = LegadoTheme.typography.labelMedium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    else -> {}
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
                                onOpenKind = actions.onKindUrlClick,
                                onRefreshKinds = actions.onRefreshButtonGroup,
                                modifier = Modifier.fillMaxWidth(),
                                columns = moduleUi.config["layout_columns"]?.toIntOrNull() ?: 5,
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
                                    key = { index, item ->
                                        "wf_${moduleUi.globalId}_${item.book.bookUrl}_$index"
                                    },
                                ) { index, item ->
                                    val sharedCoverKey = bookCoverSharedElementKey(
                                        item.book.bookUrl,
                                        "home:${moduleUi.globalId}:waterfall:$index"
                                    )
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        WaterfallItem(
                                            item = item,
                                            onClick = {
                                                actions.onBookClick(
                                                    item.book,
                                                    sharedCoverKey
                                                )
                                            },
                                            onLongClick = onBookLongClick,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKey = sharedCoverKey,
                                        )
                                    }
                                }

                                item(
                                    key = "wf_more_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        errorMsg = null,
                                        isEnd = !state.hasMore,
                                        onRetry = { actions.onLoadMoreModule(moduleUi.globalId) }
                                    )
                                }
                            }

                            HomepageModuleType.InfiniteGrid -> {
                                itemsIndexed(
                                    state.books,
                                    key = { index, item ->
                                        "inf_grid_${moduleUi.globalId}_${item.book.bookUrl}_$index"
                                    },
                                ) { index, item ->
                                    val sharedCoverKey = bookCoverSharedElementKey(
                                        item.book.bookUrl,
                                        "home:${moduleUi.globalId}:infinite:$index"
                                    )
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        SearchBookGridItem(
                                            book = item.book,
                                            shelfState = item.shelfState,
                                            onClick = {
                                                actions.onBookClick(
                                                    item.book,
                                                    sharedCoverKey
                                                )
                                            },
                                            onLongClick = onBookLongClick,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKey = sharedCoverKey
                                        )
                                    }
                                }

                                item(
                                    key = "inf_grid_more_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    LoadMoreFooter(
                                        isLoading = state.isLoadingMore,
                                        errorMsg = null,
                                        isEnd = !state.hasMore,
                                        onRetry = { actions.onLoadMoreModule(moduleUi.globalId) }
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
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        GridModule(
                                            books = state.books,
                                            onClick = { book, sharedCoverKey ->
                                                actions.onBookClick(book, sharedCoverKey)
                                            },
                                            onLongClick = onBookLongClick,
                                            modifier = Modifier.fillMaxWidth(),
                                            columns = columns,
                                            maxRows = rows,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKeySourceId = "home:${moduleUi.globalId}:grid",
                                        )
                                    }
                                }
                            }

                            HomepageModuleType.Banner -> {
                                item(
                                    key = "content_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        BannerModule(
                                            books = state.books,
                                            onClick = { book, sharedCoverKey ->
                                                actions.onBookClick(book, sharedCoverKey)
                                            },
                                            onLongClick = onBookLongClick,
                                            modifier = Modifier.fillMaxWidth(),
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKeySourceId = "home:${moduleUi.globalId}:banner",
                                        )
                                    }
                                }
                            }

                            HomepageModuleType.Ranking -> {
                                item(
                                    key = "content_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        RankingModule(
                                            books = state.books,
                                            onClick = { book, sharedCoverKey ->
                                                actions.onBookClick(book, sharedCoverKey)
                                            },
                                            onLongClick = onBookLongClick,
                                            modifier = Modifier.fillMaxWidth(),
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKeySourceId = "home:${moduleUi.globalId}:ranking",
                                        )
                                    }
                                }
                            }

                            HomepageModuleType.GridRanking -> {
                                item(
                                    key = "content_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        GridRankingModule(
                                            books = state.books,
                                            onClick = { book, sharedCoverKey ->
                                                actions.onBookClick(book, sharedCoverKey)
                                            },
                                            onLongClick = onBookLongClick,
                                            modifier = Modifier.fillMaxWidth(),
                                            rows = config["layout_rows"]?.toIntOrNull() ?: 4,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKeySourceId = "home:${moduleUi.globalId}:grid-ranking",
                                        )
                                    }
                                }
                            }

                            HomepageModuleType.Card -> {
                                item(
                                    key = "content_${moduleUi.globalId}",
                                    span = StaggeredGridItemSpan.FullLine
                                ) {
                                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                                        CardModule(
                                            books = state.books,
                                            onClick = { book, sharedCoverKey ->
                                                actions.onBookClick(book, sharedCoverKey)
                                            },
                                            onLongClick = onBookLongClick,
                                            modifier = Modifier.fillMaxWidth(),
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            sharedCoverKeySourceId = "home:${moduleUi.globalId}:card",
                                        )
                                    }
                                }
                            }

                            else -> {}
                        }
                    }

                    is ModuleLoadState.Rankings -> Unit
                }
            }
        }
    }
}

@Composable
private fun ModuleHeader(
    title: String,
    sourceTabs: ImmutableList<HomepageRankingSourceUi>?,
    selectedSourceTitle: String?,
    onSourceSelected: (String) -> Unit,
    onNavigate: (() -> Unit)? = null,
) {
    val sourceTabItems = sourceTabs.orEmpty()
    val hasSourceTabs = sourceTabItems.size > 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasSourceTabs) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(
                    items = sourceTabItems,
                    key = { it.title },
                ) { source ->
                    val isSelected = selectedSourceTitle == source.title
                    ModuleHeaderTab(
                        title = source.title,
                        selected = isSelected,
                        onClick = { onSourceSelected(source.title) },
                    )
                }
            }
        } else {
            ModuleHeaderTab(
                title = title,
                selected = true,
                highlightSelected = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
        if (onNavigate != null) {
            SmallTonalButton(
                onClick = onNavigate,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.homepage_show_all),
            )
        }
    }
}

@Composable
private fun ModuleHeaderTab(
    title: String,
    selected: Boolean,
    highlightSelected: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = when {
            selected && highlightSelected -> LegadoTheme.colorScheme.primary
            selected -> Color.Unspecified
            else -> LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    )
}
