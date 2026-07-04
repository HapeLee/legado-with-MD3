package io.legado.app.ui.book.toc.rule.preview

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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.rules.RuleEditFields
import io.legado.app.ui.widget.components.rules.RuleEditSheet
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtTocRulePreviewRouteScreen(
    bookUrl: String,
    currentTocRegex: String?,
    viewModel: TxtTocRulePreviewViewModel = koinViewModel(),
    onBack: () -> Unit,
    onApplyRule: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl, currentTocRegex)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TxtTocRulePreviewEffect.ApplyRule -> onApplyRule(effect.rule)
                is TxtTocRulePreviewEffect.ShowToast -> { /* no-op */ }
            }
        }
    }

    TxtTocRulePreviewScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onApplyRule = onApplyRule,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtTocRulePreviewScreen(
    state: TxtTocRulePreviewUiState,
    onIntent: (TxtTocRulePreviewIntent) -> Unit,
    onBack: () -> Unit,
    onApplyRule: (String) -> Unit,
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

    AppScaffold(
        topBar = { _ ->
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.select_toc_rule),
                subtitle = stringResource(R.string.select_rule_to_preview_chapters),
                scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior(),
                navigationIcon = { TopBarNavigationButton(onBack) },
                actions = {
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
                },
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ConfirmDismissButtonsRow(
                    onDismiss = onBack,
                    onConfirm = {
                        if (state.hasSelection) {
                            onApplyRule(state.selectedRule)
                        }
                    },
                    dismissText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.ok),
                    confirmEnabled = state.hasSelection,
                    modifier = Modifier.fillMaxWidth(),
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = adaptiveContentPadding(
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.rules, key = { index, item -> "${index}_${item.rule.id}" }) { index, item ->
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = adaptiveContentPadding(
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.rules, key = { index, item -> "${index}_${item.rule.id}" }) { index, item ->
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
    NormalCard(
        onClick = onClick,
        border = if (isSelected) {
            BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .padding(bottom = 28.dp),
            ) {
                // Rule name
                Text(
                    text = item.rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Example text (subtitle)
                item.rule.example?.let { example ->
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Chapter count badge - bottom right
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
    NormalCard(
        onClick = onClick,
        border = if (isSelected) {
            BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            null
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
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.rule.example?.let { example ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                style = MaterialTheme.typography.titleMedium,
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
