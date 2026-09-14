package io.legado.app.feature.dict.rule

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
import io.legado.app.data.entities.DictRule
import io.legado.app.feature.dict.res.Res
import io.legado.app.feature.dict.res.a11y_delete_named
import io.legado.app.feature.dict.res.a11y_edit_named
import io.legado.app.feature.dict.res.a11y_long_press_reorder
import io.legado.app.feature.dict.res.a11y_rule_enabled_switch
import io.legado.app.feature.dict.res.cancel
import io.legado.app.feature.dict.res.delete
import io.legado.app.feature.dict.res.dict_rule
import io.legado.app.feature.dict.res.disable_selection
import io.legado.app.feature.dict.res.disabled
import io.legado.app.feature.dict.res.enable
import io.legado.app.feature.dict.res.enabled
import io.legado.app.feature.dict.res.export
import io.legado.app.feature.dict.res.import_dict_rule
import io.legado.app.feature.dict.res.import_on_line
import io.legado.app.feature.dict.res.import_str
import io.legado.app.feature.dict.res.ok
import io.legado.app.feature.dict.res.search_dict_rule
import io.legado.app.feature.dict.res.show_rule
import io.legado.app.feature.dict.res.url_rule
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
import io.legado.app.ui.widget.components.rules.RuleEditFields
import io.legado.app.ui.widget.components.rules.RuleEditSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 词典规则管理屏的**纯组合层**（M1-3z 从原 `DictRuleScreen.kt` 拆出）。
 *
 * 零 Android API：文件选择是 `(mimeTypes) -> Unit` / `(fileName) -> Unit` 两个回调，
 * 交给平台 Route（见 `androidMain` 的 `DictRuleRouteScreen`）实现；剪贴板用
 * `LocalClipboard`（CMP 共享层可用，`replacerules` 同一用法）。
 *
 * 文案来自本模块 `composeResources`（`Res.string.*`），与迁移前 `R.string.*` 逐字对应
 * ——原有的 Android res 已整体搬进 `commonMain/composeResources`，两边同名条目值一致。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleScreen(
    state: DictRuleUiState,
    importState: BaseImportUiState<DictRule>,
    importJsonEditor: ImportJsonEditor,
    events: Flow<RuleTransferEvent>,
    effects: Flow<DictRuleEffect>,
    onIntent: (DictRuleIntent) -> Unit,
    onPasteRule: () -> DictRule?,
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
    var editingRule by remember { mutableStateOf<DictRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<DictRule?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }

    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }


    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(DictRuleIntent.MoveItem(from.index, to.index))
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
                is DictRuleEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(Res.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(DictRuleIntent.ImportSource(it))
        }
    )

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(Res.string.export),
        onSelectSysDir = {
            showExportSheet = false
            onPickExportTarget("exportDictRule.json")
        },
        onUpload = {
            showExportSheet = false
            onIntent(DictRuleIntent.UploadSelection)
        },
        allowExtensions = arrayOf("json")
    )


    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(Res.string.import_dict_rule),
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
        title = stringResource(Res.string.import_dict_rule),
        importState = importState,
        importJsonEditor = importJsonEditor,
        onDismissRequest = { onIntent(DictRuleIntent.CancelImport) },
        onToggleItem = { onIntent(DictRuleIntent.ToggleImportSelection(it)) },
        onToggleAll = { onIntent(DictRuleIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, rule -> onIntent(DictRuleIntent.UpdateImportItem(index, rule)) },
        onConfirm = { onIntent(DictRuleIntent.SaveImportedRules) },
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.urlRule.takeIf { it.isNotBlank() }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onIntent(DictRuleIntent.SaveSortOrder)
        }
    }

    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(Res.string.delete),
        confirmText = stringResource(Res.string.ok),
        onConfirm = { rule ->
            onIntent(DictRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleEditSheet(
        show = showEditSheet,
        rule = editingRule,
        title = stringResource(Res.string.dict_rule),
        label1 = stringResource(Res.string.url_rule),
        label2 = stringResource(Res.string.show_rule),
        onDismissRequest = {
            showEditSheet = false
            editingRule = null
        },
        onSave = { updatedRule ->
            onIntent(
                DictRuleIntent.SaveRule(
                    rule = updatedRule,
                    isNew = editingRule == null,
                    originalName = editingRule?.name,
                )
            )
            showEditSheet = false
            editingRule = null
        },
        onCopy = { onIntent(DictRuleIntent.CopyRule(it)) },
        onPaste = onPasteRule,
        toFields = { r ->
            RuleEditFields(
                name = r?.name ?: "",
                rule1 = r?.urlRule ?: "",
                rule2 = r?.showRule ?: ""
            )
        },
        fromFields = { fields, old ->
            old?.copy(
                name = fields.name,
                urlRule = fields.rule1,
                showRule = fields.rule2
            ) ?: DictRule(
                name = fields.name,
                urlRule = fields.rule1,
                showRule = fields.rule2
            )
        }
    )

    RuleListScaffold(
        title = stringResource(Res.string.dict_rule),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(DictRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(DictRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(Res.string.search_dict_rule),
        onClearSelection = { onIntent(DictRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(DictRuleIntent.SelectAll) },
        onSelectInvert = {
            onIntent(DictRuleIntent.InvertSelection)
        },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(Res.string.enable), onClick = {
                onIntent(DictRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(Res.string.disable_selection), onClick = {
                onIntent(DictRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(Res.string.export),
                onClick = { showExportSheet = true })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            onIntent(DictRuleIntent.SetSelection(ids as Set<String>))
            onIntent(DictRuleIntent.DeleteSelection)
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
                        item.id,
                        item.urlRule.takeIf { it.isNotBlank() },
                        enabledState,
                        stringResource(Res.string.a11y_long_press_reorder)
                    ).joinToString()
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        reorderIndex = rules.indexOf(item),
                        reorderItemCount = rules.size,
                        onMoveItem = { from, to -> onIntent(DictRuleIntent.MoveItem(from, to)) },
                        title = item.id,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { onIntent(DictRuleIntent.ToggleSelection(item.id)) },
                        onEnabledChange = { enabled ->
                            onIntent(DictRuleIntent.SetRuleEnabled(item.rule, enabled))
                        },
                        contentDescription = itemDescription,
                        enableSwitchContentDescription = stringResource(
                            Res.string.a11y_rule_enabled_switch,
                            item.id
                        ),
                        editContentDescription = stringResource(
                            Res.string.a11y_edit_named,
                            item.id
                        ),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    Res.string.a11y_delete_named,
                                    item.id
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
                    onSelectionChange = { onIntent(DictRuleIntent.SetSelection(it)) },
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
