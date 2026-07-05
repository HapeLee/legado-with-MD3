package io.legado.app.ui.book.toc.rule.preview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.utils.toastOnUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.rules.RuleEditFields
import io.legado.app.ui.widget.components.rules.RuleEditSheet
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtTocRulePreviewRouteScreen(
    bookUrl: String,
    currentTocRegex: String?,
    viewModel: TxtTocRulePreviewViewModel = koinViewModel(),
    onBack: () -> Unit,
    onApplyRule: (String) -> Unit,
    onOpenManagePage: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl, currentTocRegex)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TxtTocRulePreviewEffect.ShowToast -> context.toastOnUi(effect.message)
                is TxtTocRulePreviewEffect.OpenManagePage -> onOpenManagePage()
                is TxtTocRulePreviewEffect.ApplyRule -> onApplyRule(effect.rule)
            }
        }
    }

    TxtTocRulePreviewScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtTocRulePreviewScreen(
    state: TxtTocRulePreviewUiState,
    onIntent: (TxtTocRulePreviewIntent) -> Unit,
    onBack: () -> Unit,
) {
    // Chapter list bottom sheet
    when (val sheet = state.activeSheet) {
        is TxtTocRulePreviewSheet.ChapterList -> {
            AppModalBottomSheet(
                show = true,
                onDismissRequest = { onIntent(TxtTocRulePreviewIntent.DismissSheet) },
            ) {
                ChapterListSheetContent(
                    item = sheet.item,
                    onEditRule = { rule ->
                        onIntent(TxtTocRulePreviewIntent.EditRule(rule))
                    },
                )
            }
        }

        null -> { /* no sheet */ }
    }

    // Rule edit sheet
    state.editingRule?.let { rule ->
        RuleEditSheet(
            show = true,
            rule = rule,
            title = stringResource(R.string.txt_toc_rule),
            label1 = stringResource(R.string.regex),
            label2 = stringResource(R.string.example),
            onDismissRequest = { onIntent(TxtTocRulePreviewIntent.DismissEditDialog) },
            onSave = { updatedRule ->
                onIntent(TxtTocRulePreviewIntent.SaveRule(updatedRule))
            },
            onCopy = { /* no-op */ },
            onPaste = { null },
            toFields = { r ->
                RuleEditFields(
                    name = r?.name ?: "",
                    rule1 = r?.rule ?: "",
                    rule2 = r?.example ?: ""
                )
            },
            fromFields = { fields, old ->
                old?.copy(
                    name = fields.name,
                    rule = fields.rule1,
                    example = fields.rule2
                ) ?: TxtTocRule(
                    name = fields.name,
                    rule = fields.rule1,
                    example = fields.rule2
                )
            }
        )
    }

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { _ ->
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.select_toc_rule),
                subtitle = stringResource(R.string.select_rule_to_preview_chapters),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onBack) },
                actions = {
                    // Search button
                    TopBarActionButton(
                        onClick = { onIntent(TxtTocRulePreviewIntent.ToggleSearch) },
                        imageVector = AppIcons.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                    // Layout toggle button
                    TopBarActionButton(
                        onClick = { onIntent(TxtTocRulePreviewIntent.ToggleLayout) },
                        imageVector = if (state.isGridLayout) {
                            Icons.AutoMirrored.Outlined.FormatListBulleted
                        } else {
                            Icons.Default.GridView
                        },
                        contentDescription = stringResource(
                            if (state.isGridLayout) R.string.layout_mode_list else R.string.layout_mode_grid
                        ),
                    )
                    // Manage page button
                    TopBarActionButton(
                        onClick = { onIntent(TxtTocRulePreviewIntent.OpenManagePage) },
                        imageVector = AppIcons.Settings,
                        contentDescription = stringResource(R.string.manage),
                    )
                },
                bottomContent = {
                    AnimatedVisibility(
                        modifier = Modifier.adaptiveHorizontalPadding(),
                        visible = state.showSearch,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        SearchBar(
                            query = state.searchQuery,
                            onQueryChange = { onIntent(TxtTocRulePreviewIntent.UpdateSearchQuery(it)) },
                            onSearch = {},
                            placeholder = stringResource(R.string.search),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.hasSelection) {
                AppFloatingActionButton(
                    onClick = { onIntent(TxtTocRulePreviewIntent.ApplyRule) },
                    icon = Icons.Default.Check,
                    tooltipText = stringResource(R.string.ok),
                )
            }
        },
    ) { contentPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.isGridLayout) {
                val displayRules = state.filteredRules
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = adaptiveContentPadding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(displayRules, key = { _, item -> item.rule.id }) { _, item ->
                        RulePreviewCard(
                            item = item,
                            isSelected = item.rule.rule == state.selectedRule,
                            onClick = {
                                onIntent(TxtTocRulePreviewIntent.SelectRule(item.rule.rule))
                                if (item.totalCount > 0) {
                                    onIntent(TxtTocRulePreviewIntent.ShowChapterList(item))
                                }
                            },
                        )
                    }
                }
            } else {
                val displayRules = state.filteredRules
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = adaptiveContentPadding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding() + 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(displayRules, key = { _, item -> item.rule.id }) { _, item ->
                        RulePreviewListItem(
                            item = item,
                            isSelected = item.rule.rule == state.selectedRule,
                            onClick = {
                                onIntent(TxtTocRulePreviewIntent.SelectRule(item.rule.rule))
                                if (item.totalCount > 0) {
                                    onIntent(TxtTocRulePreviewIntent.ShowChapterList(item))
                                }
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun RulePreviewCard(
    item: TocRulePreviewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) LegadoTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )

    GlassCard(
        onClick = onClick,
        border = if (isSelected) {
            BorderStroke(1.5.dp, LegadoTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, borderColor)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .padding(bottom = 28.dp),
            ) {
                AppText(
                    text = item.rule.name,
                    style = LegadoTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                item.rule.example?.let { example ->
                    AppText(
                        text = example,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                if (item.totalCount < 0) {
                    AppCircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(4.dp)
                    )
                } else {
                    AppText(
                        text = stringResource(R.string.chapter_count_format, item.totalCount),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RulePreviewListItem(
    item: TocRulePreviewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) LegadoTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )

    GlassCard(
        onClick = onClick,
        border = if (isSelected) {
            BorderStroke(1.5.dp, LegadoTheme.colorScheme.primary)
        } else {
            BorderStroke(0.5.dp, borderColor)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.rule.name,
                    style = LegadoTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.rule.example?.let { example ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = example,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            ) {
                if (item.totalCount < 0) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.chapter_count_format,
                            item.totalCount
                        ),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterListSheetContent(
    item: TocRulePreviewItem,
    onEditRule: (TxtTocRule) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.rule.name,
                style = LegadoTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(8.dp))

            TopBarActionButton(
                onClick = { onEditRule(item.rule) },
                imageVector = AppIcons.Edit,
                contentDescription = stringResource(R.string.edit),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(
                    R.string.chapter_count_format,
                    item.totalCount
                ),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chapter list
        FastScrollLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
        ) {
            itemsIndexed(item.chapters.toList()) { _, chapter ->
                Text(
                    text = chapter,
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.chapters.size >= 500) {
                item {
                    Text(
                        text = stringResource(R.string.chapter_list_preview_limit),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
