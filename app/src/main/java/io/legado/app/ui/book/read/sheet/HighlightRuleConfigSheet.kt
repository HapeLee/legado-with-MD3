package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.configNames
import io.legado.app.ui.book.read.HighlightRuleConfigUiState
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HighlightRuleConfigSheet(
    show: Boolean,
    state: HighlightRuleConfigUiState,
    allConfigNames: List<String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var showImportSheet by remember { mutableStateOf(false) }
    var showSourceInput by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    var orderChanged by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        orderChanged = true
        onIntent(ReadBookIntent.MoveHighlightRule(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && orderChanged) {
            orderChanged = false
            onIntent(ReadBookIntent.SaveHighlightRuleOrder)
        }
    }

    SourceInputDialog(
        show = showSourceInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showSourceInput = false },
        onConfirm = {
            showSourceInput = false
            onIntent(ReadBookIntent.ImportHighlightRuleSource(it))
        },
    )
    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.highlight_rule_config),
        onSelectSysFile = {
            showImportSheet = false
            onIntent(ReadBookIntent.OpenHighlightRuleImportPicker)
        },
        onManualInput = {
            showImportSheet = false
            showSourceInput = true
        },
        allowExtensions = arrayOf("json", "txt"),
    )
    BatchImportDialog(
        title = stringResource(R.string.highlight_rule_config),
        importState = state.importState,
        onDismissRequest = { onIntent(ReadBookIntent.CancelHighlightRuleImport) },
        onToggleItem = {
            onIntent(ReadBookIntent.ToggleHighlightRuleImportSelection(it))
        },
        onToggleAll = {
            onIntent(ReadBookIntent.ToggleHighlightRuleImportAll(it))
        },
        onUpdateItem = { index, rule ->
            onIntent(ReadBookIntent.UpdateHighlightRuleImportItem(index, rule))
        },
        onConfirm = {
            onIntent(ReadBookIntent.SaveImportedHighlightRules)
        },
        itemTitle = { it.name.ifBlank { it.displayPattern() } },
        itemSubtitle = { it.pattern.takeIf(String::isNotBlank) },
    )

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.highlight_rule_config),
        startAction = {
            SmallTonalButton(
                onClick = { onIntent(ReadBookIntent.AddHighlightRule) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add)
            )
        },
        endAction = {
            var expanded by remember { mutableStateOf(false) }
            Box {
                SmallTonalButton(
                    onClick = { expanded = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_action),
                        onClick = {
                            expanded = false
                            showImportSheet = true
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.export),
                        onClick = {
                            expanded = false
                            onIntent(ReadBookIntent.ExportHighlightRules)
                        },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_url),
                        onClick = {
                            expanded = false
                            onIntent(ReadBookIntent.ExportHighlightRulesAsUrl)
                        },
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                    ReorderableItem(reorderableState, key = rule.id) { isDragging ->
                        HighlightRuleItem(
                            rule = rule,
                            onToggle = { enabled ->
                                onIntent(ReadBookIntent.ToggleHighlightRule(rule, enabled))
                            },
                            onEditClick = {
                                onIntent(ReadBookIntent.EditHighlightRule(rule))
                            },
                            onDeleteClick = {
                                onIntent(ReadBookIntent.RequestDeleteHighlightRule(rule))
                            },
                            modifier = Modifier
                                .reorderAccessibility(
                                    index = index,
                                    itemCount = state.rules.size,
                                ) { from, to ->
                                    onIntent(ReadBookIntent.MoveHighlightRule(from, to))
                                    onIntent(ReadBookIntent.SaveHighlightRuleOrder)
                                }
                                .longPressDraggableHandle()
                                .zIndex(if (isDragging) 1f else 0f)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }

    val editingRuleValue = state.editingRule

    HighlightRuleEditSheet(
        show = show && editingRuleValue != null,
        rule = editingRuleValue,
        allConfigNames = allConfigNames,
        onDismissRequest = { onIntent(ReadBookIntent.DismissHighlightRuleEdit) },
        onSave = { updated ->
            onIntent(ReadBookIntent.SaveHighlightRule(updated))
        },
    )

    HighlightRuleEditSheet(
        show = show && state.showNewRule,
        rule = null,
        allConfigNames = allConfigNames,
        onDismissRequest = { onIntent(ReadBookIntent.DismissHighlightRuleEdit) },
        onSave = { newRule ->
            onIntent(ReadBookIntent.SaveHighlightRule(newRule))
        },
    )

    val deletingRule = state.deleteRule
    val sureDeleteText = stringResource(R.string.sure_delete)
    AppAlertDialog(
        show = show && deletingRule != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDeleteHighlightRule) },
        title = stringResource(R.string.delete),
        text = deletingRule?.let { "$sureDeleteText \"${it.name}\"?" },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmDeleteHighlightRule) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDeleteHighlightRule) },
    )
}

@Composable
private fun HighlightRuleItem(
    rule: HighlightRule,
    onToggle: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configLabel = rule.configName?.configNames()?.joinToString("、") ?: "全局"
    TinySettingItem(
        title = rule.name.ifBlank { rule.displayPattern() },
        description = "${rule.styleSummary()} · $configLabel",
        onClick = onEditClick,
        modifier = modifier,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TinySwitch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
                SmallTonalButton(
                    onClick = onEditClick,
                    icon = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit)
                )
                SmallTonalButton(
                    onClick = onDeleteClick,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        },
    )
}
