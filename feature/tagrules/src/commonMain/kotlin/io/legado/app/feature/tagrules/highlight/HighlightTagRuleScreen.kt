package io.legado.app.feature.tagrules.highlight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.rules.RuleTransferEvent
import io.legado.app.domain.rules.HighlightTagRule
import io.legado.app.feature.tagrules.res.Res
import io.legado.app.feature.tagrules.res.a11y_delete_named
import io.legado.app.feature.tagrules.res.a11y_edit_named
import io.legado.app.feature.tagrules.res.a11y_long_press_reorder
import io.legado.app.feature.tagrules.res.a11y_rule_enabled_switch
import io.legado.app.feature.tagrules.res.cancel
import io.legado.app.feature.tagrules.res.delete
import io.legado.app.feature.tagrules.res.disable_selection
import io.legado.app.feature.tagrules.res.disabled
import io.legado.app.feature.tagrules.res.enable
import io.legado.app.feature.tagrules.res.enabled
import io.legado.app.feature.tagrules.res.export
import io.legado.app.feature.tagrules.res.highlight_tag_config
import io.legado.app.feature.tagrules.res.import_highlight_tag_rule
import io.legado.app.feature.tagrules.res.import_on_line
import io.legado.app.feature.tagrules.res.import_str
import io.legado.app.feature.tagrules.res.ok
import io.legado.app.feature.tagrules.res.search_highlight_tag_rule
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.util.plainTextClipEntry
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighlightTagRuleScreen(
    state: HighlightTagRuleUiState,
    importState: BaseImportUiState<HighlightTagRule>,
    importJsonEditor: ImportJsonEditor,
    events: Flow<RuleTransferEvent>,
    effects: Flow<HighlightTagRuleEffect>,
    onIntent: (HighlightTagRuleIntent) -> Unit,
    onPasteRule: () -> HighlightTagRule?,
    onPickImportSource: (mimeTypes: Array<String>) -> Unit,
    onPickExportTarget: (fileName: String) -> Unit,
    onBackClick: () -> Unit,
) {

    val rules = state.items
    val selectedIds = state.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<HighlightTagRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<HighlightTagRule?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }

    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }


    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(HighlightTagRuleIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                is RuleTransferEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        withDismissAction = true
                    )
                    val url = event.url
                    if (result == SnackbarResult.ActionPerformed && url != null) {
                        clipboardManager.setClipEntry(plainTextClipEntry("url", url))
                    }
                }
            }
        }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is HighlightTagRuleEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(Res.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(HighlightTagRuleIntent.ImportSource(it))
        }
    )

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(Res.string.export),
        onSelectSysDir = {
            showExportSheet = false
            onPickExportTarget("exportHighlightTagRule.json")
        },
        onUpload = {
            showExportSheet = false
            onIntent(HighlightTagRuleIntent.UploadSelection)
        },
        allowExtensions = arrayOf("json")
    )


    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(Res.string.import_highlight_tag_rule),
        onSelectSysFile = { types ->
            onPickImportSource(types)
            showImportSheet = false
        },
        onManualInput = {
            showUrlInput = true
            showImportSheet = false
        },
        allowExtensions = arrayOf("json", "txt")
    )

    BatchImportDialog(
        title = stringResource(Res.string.import_highlight_tag_rule),
        importState = importState,
        importJsonEditor = importJsonEditor,
        onDismissRequest = { onIntent(HighlightTagRuleIntent.CancelImport) },
        onToggleItem = { onIntent(HighlightTagRuleIntent.ToggleImportSelection(it)) },
        onToggleAll = { onIntent(HighlightTagRuleIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, rule -> onIntent(HighlightTagRuleIntent.UpdateImportItem(index, rule)) },
        onConfirm = { onIntent(HighlightTagRuleIntent.SaveImportedRules) },
        itemTitle = { rule -> rule.title.ifBlank { rule.pattern } },
        itemSubtitle = { rule -> rule.pattern.takeIf { it.isNotBlank() } }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onIntent(HighlightTagRuleIntent.SaveSortOrder)
        }
    }

    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(Res.string.delete),
        confirmText = stringResource(Res.string.ok),
        onConfirm = { rule ->
            onIntent(HighlightTagRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    HighlightTagRuleEditSheet(
        show = showEditSheet,
        rule = editingRule,
        onDismissRequest = {
            showEditSheet = false
            editingRule = null
        },
        onSave = { updatedRule ->
            onIntent(HighlightTagRuleIntent.SaveRule(updatedRule, isNew = editingRule == null))
            showEditSheet = false
            editingRule = null
        },
        onCopy = { onIntent(HighlightTagRuleIntent.CopyRule(it)) },
        onPaste = onPasteRule
    )

    RuleListScaffold(
        title = stringResource(Res.string.highlight_tag_config),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(HighlightTagRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(HighlightTagRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(Res.string.search_highlight_tag_rule),
        onClearSelection = { onIntent(HighlightTagRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(HighlightTagRuleIntent.SelectAll) },
        onSelectInvert = {
            onIntent(HighlightTagRuleIntent.InvertSelection)
        },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(Res.string.enable), onClick = {
                onIntent(HighlightTagRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(Res.string.disable_selection), onClick = {
                onIntent(HighlightTagRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(Res.string.export),
                onClick = { showExportSheet = true })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            onIntent(HighlightTagRuleIntent.SetSelection(ids as Set<Long>))
            onIntent(HighlightTagRuleIntent.DeleteSelection)
        },
        onAddClick = {
            editingRule = null
            showEditSheet = true
        },
        snackbarHostState = snackbarHostState,
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(Res.string.import_str),
                onClick = { showImportSheet = true; dismiss() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { item ->
                    val enabledState = stringResource(
                        if (item.isEnabled) Res.string.enabled else Res.string.disabled
                    )
                    val itemDescription = listOfNotNull(
                        item.displayName,
                        item.pattern.takeIf { it.isNotBlank() },
                        enabledState,
                        if (!inSelectionMode) {
                            stringResource(Res.string.a11y_long_press_reorder)
                        } else {
                            null
                        }
                    ).joinToString()
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        reorderIndex = rules.indexOf(item),
                        reorderItemCount = rules.size,
                        onMoveItem = { from, to ->
                            onIntent(HighlightTagRuleIntent.MoveItem(from, to))
                        },
                        title = item.displayName,
                        subtitle = item.pattern,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { onIntent(HighlightTagRuleIntent.ToggleSelection(item.id)) },
                        onEnabledChange = { enabled ->
                            onIntent(HighlightTagRuleIntent.SetRuleEnabled(item.rule, enabled))
                        },
                        contentDescription = itemDescription,
                        enableSwitchContentDescription = stringResource(
                            Res.string.a11y_rule_enabled_switch,
                            item.displayName
                        ),
                        editContentDescription = stringResource(
                            Res.string.a11y_edit_named,
                            item.displayName
                        ),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    Res.string.a11y_delete_named,
                                    item.displayName
                                )
                            )
                        }
                    )
                }
            }
            if (inSelectionMode) {
                DraggableSelectionHandler(
                    listState = listState,
                    items = rules,
                    selectedIds = selectedIds,
                    onSelectionChange = { onIntent(HighlightTagRuleIntent.SetSelection(it)) },
                    idProvider = { it.id },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .align(Alignment.TopStart)
                )
            }
        }
    }
}
