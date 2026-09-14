package io.legado.app.feature.txttocrules

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
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.feature.txttocrules.res.Res
import io.legado.app.feature.txttocrules.res.a11y_delete_named
import io.legado.app.feature.txttocrules.res.a11y_edit_named
import io.legado.app.feature.txttocrules.res.a11y_long_press_reorder
import io.legado.app.feature.txttocrules.res.a11y_rule_enabled_switch
import io.legado.app.feature.txttocrules.res.cancel
import io.legado.app.feature.txttocrules.res.cannot_empty
import io.legado.app.feature.txttocrules.res.chapter_rule
import io.legado.app.feature.txttocrules.res.delete
import io.legado.app.feature.txttocrules.res.disable_selection
import io.legado.app.feature.txttocrules.res.disabled
import io.legado.app.feature.txttocrules.res.enable
import io.legado.app.feature.txttocrules.res.enabled
import io.legado.app.feature.txttocrules.res.example
import io.legado.app.feature.txttocrules.res.export
import io.legado.app.feature.txttocrules.res.import_built_in_rules
import io.legado.app.feature.txttocrules.res.import_on_line
import io.legado.app.feature.txttocrules.res.import_str
import io.legado.app.feature.txttocrules.res.import_txt_toc_rule
import io.legado.app.feature.txttocrules.res.invalid_format
import io.legado.app.feature.txttocrules.res.ok
import io.legado.app.feature.txttocrules.res.search_txt_toc_rule
import io.legado.app.feature.txttocrules.res.select_toc_rule
import io.legado.app.feature.txttocrules.res.sure_del
import io.legado.app.feature.txttocrules.res.txt_toc_rule
import io.legado.app.feature.txttocrules.res.volume_rule
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.util.plainTextClipEntry
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleEditFields
import io.legado.app.ui.widget.components.rules.RuleEditSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.rules.TestLineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * TXT 目录规则的**纯 Screen**（M1-3y 从 `:core:ui` 风格的 Android Screen 拆出）。
 *
 * 与 Android 相关的四件事全部走参数/回调，本文件因此零 `android.*`：
 *   1. 文件选择（`rememberDocumentPicker()`）与导出目标（SAF `CreateDocument`）由 Android Route
 *      传 [onPickImportSource] / [onPickExportTarget]（`ActivityResult` launcher 必须在
 *      Composition 里注册，共享层无法提供）；
 *   2. `ToasterProvider.current.toast(...)` → [onShowToast]（编辑弹层的三条校验提示，与迁移前
 *      同样是 toast、同样用 `cannot_empty` / `invalid_format` 文案）；
 *   3. `R.string.*` → CMP 的 `Res.string.*`（`composeResources`，四语言随本模块走）；
 *   4. 导入文本的读取（原 `context.contentResolver.openInputStream`）下沉给 VM 的
 *      `RuleTransferPlatform.readImportSource`——选中的只是「不透明引用字符串」。
 *
 * Route / Screen 的职责切分是既有设计（同 `tagrules` / `replacerules`），不是为迁移新造的。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtRuleScreen(
    state: TxtTocRuleUiState,
    importState: BaseImportUiState<TxtTocRule>,
    importJsonEditor: ImportJsonEditor,
    events: Flow<RuleTransferEvent>,
    effects: Flow<TxtTocRuleEffect>,
    onIntent: (TxtTocRuleIntent) -> Unit,
    onPasteRule: () -> TxtTocRule?,
    onPickImportSource: (Array<String>) -> Unit,
    onPickExportTarget: (String) -> Unit,
    onShowToast: (String) -> Unit,
    initialRule: String? = null,
    onPickRule: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {

    val cannotEmptyText = stringResource(Res.string.cannot_empty)
    val invalidFormatText = stringResource(Res.string.invalid_format)

    val rules = state.items
    val selectedIds = state.selectedIds
    val isPickMode = onPickRule != null
    val inSelectionMode = if (isPickMode) false else selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<TxtTocRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<TxtTocRule?>(null) }

    var showUrlInput by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(TxtTocRuleIntent.MoveItem(from.index, to.index))
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
                is TxtTocRuleEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(Res.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(TxtTocRuleIntent.ImportSource(it))
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
            onIntent(TxtTocRuleIntent.UploadSelection)
        },
        allowExtensions = arrayOf("json")
    )


    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(Res.string.import_txt_toc_rule),
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
        title = stringResource(Res.string.import_txt_toc_rule),
        importState = importState,
        importJsonEditor = importJsonEditor,
        onDismissRequest = { onIntent(TxtTocRuleIntent.CancelImport) },
        onToggleItem = { onIntent(TxtTocRuleIntent.ToggleImportSelection(it)) },
        onToggleAll = { onIntent(TxtTocRuleIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, rule -> onIntent(TxtTocRuleIntent.UpdateImportItem(index, rule)) },
        onConfirm = { onIntent(TxtTocRuleIntent.SaveImportedRules) },
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.chapterRule.takeIf { it.isNotBlank() }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onIntent(TxtTocRuleIntent.SaveSortOrder)
        }
    }

    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(Res.string.delete),
        text = stringResource(Res.string.sure_del),
        confirmText = stringResource(Res.string.ok),
        onConfirm = { rule ->
            onIntent(TxtTocRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleEditSheet(
        show = showEditSheet,
        rule = editingRule,
        title = stringResource(Res.string.txt_toc_rule),
        label1 = stringResource(Res.string.chapter_rule),
        label2 = stringResource(Res.string.example),
        label3 = stringResource(Res.string.volume_rule),
        onDismissRequest = {
            showEditSheet = false
            editingRule = null
        },
        onSave = { updatedRule ->
            when {
                updatedRule.name.isBlank() -> {
                    onShowToast(cannotEmptyText)
                    return@RuleEditSheet
                }

                updatedRule.chapterRule.isBlank() -> {
                    onShowToast(cannotEmptyText)
                    return@RuleEditSheet
                }

                runCatching { Regex(updatedRule.chapterRule) }.isFailure -> {
                    onShowToast(invalidFormatText)
                    return@RuleEditSheet
                }
            }
            onIntent(TxtTocRuleIntent.SaveRule(updatedRule, isNew = editingRule == null))
            showEditSheet = false
            editingRule = null
        },
        onCopy = { onIntent(TxtTocRuleIntent.CopyRule(it)) },
        onPaste = onPasteRule,
        toFields = { r ->
            RuleEditFields(
                name = r?.name ?: "",
                rule1 = r?.chapterRule ?: "",
                rule2 = r?.example ?: "",
                rule3 = r?.volumeRule ?: ""
            )
        },
        fromFields = { fields, old ->
            old?.copy(
                name = fields.name,
                chapterRule = fields.rule1,
                volumeRule = fields.rule3,
                example = fields.rule2
            ) ?: TxtTocRule(
                name = fields.name,
                chapterRule = fields.rule1,
                volumeRule = fields.rule3,
                example = fields.rule2
            )
        },
        showTestButton = true,
        onTest = { rule, example ->
            val regex = Regex(rule, RegexOption.MULTILINE)
            withContext(Dispatchers.Default) {
                val normalized = example.replace("\r\n", "\n").replace("\r", "\n")
                val lines = normalized.lines()
                val fullText = "\n$normalized"
                val matchRanges = regex.findAll(fullText)
                    .map { it.range.first..it.range.last + 1 }
                    .toMutableList()
                var offset = 1
                lines.map { line ->
                    val lineStart = offset
                    val lineEnd = offset + line.length
                    val matched = matchRanges.any { range ->
                        if (range.first == range.last) {
                            range.first in lineStart..lineEnd
                        } else {
                            range.first < lineEnd && range.last > lineStart
                        }
                    }
                    offset = lineEnd + 1
                    TestLineResult(
                        line = line,
                        matched = matched,
                        matchResult = if (matched) line else null
                    )
                }
            }
        },
    )

    RuleListScaffold(
        title = if (isPickMode) {
            stringResource(Res.string.select_toc_rule)
        } else {
            stringResource(Res.string.txt_toc_rule)
        },
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(TxtTocRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(TxtTocRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(Res.string.search_txt_toc_rule),
        onClearSelection = { onIntent(TxtTocRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(TxtTocRuleIntent.SelectAll) },
        onSelectInvert = { onIntent(TxtTocRuleIntent.InvertSelection) },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(Res.string.enable), onClick = {
                onIntent(TxtTocRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(Res.string.disable_selection), onClick = {
                onIntent(TxtTocRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(Res.string.export),
                onClick = { showExportSheet = true })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            onIntent(TxtTocRuleIntent.SetSelection(ids as Set<Long>))
            onIntent(TxtTocRuleIntent.DeleteSelection)
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
            RoundDropdownMenuItem(
                text = stringResource(Res.string.import_built_in_rules),
                onClick = { onIntent(TxtTocRuleIntent.ImportBuiltInRules); dismiss() }
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

                    val isItemHighLighted = if (isPickMode) {
                        item.rule.chapterRule == initialRule
                    } else {
                        selectedIds.contains(item.id)
                    }
                    val enabledState = stringResource(
                        if (item.isEnabled) Res.string.enabled else Res.string.disabled
                    )
                    val itemDescription = listOfNotNull(
                        item.name,
                        item.example.takeIf { it.isNotBlank() },
                        enabledState,
                        if (!isPickMode && !inSelectionMode) {
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
                        onMoveItem = { from, to -> onIntent(TxtTocRuleIntent.MoveItem(from, to)) },
                        title = item.name,
                        subtitle = item.example,
                        isEnabled = item.isEnabled,
                        isSelected = isItemHighLighted,
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = {
                            if (isPickMode) {
                                onPickRule.invoke(item.rule.chapterRule)
                                onBackClick()
                            } else {
                                onIntent(TxtTocRuleIntent.ToggleSelection(item.id))
                            }
                        },
                        onEnabledChange = { enabled ->
                            onIntent(TxtTocRuleIntent.SetRuleEnabled(item.rule, enabled))
                        },
                        contentDescription = itemDescription,
                        enableSwitchContentDescription = stringResource(
                            Res.string.a11y_rule_enabled_switch,
                            item.name
                        ),
                        editContentDescription = stringResource(
                            Res.string.a11y_edit_named,
                            item.name
                        ),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    Res.string.a11y_delete_named,
                                    item.name
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
                    onSelectionChange = { onIntent(TxtTocRuleIntent.SetSelection(it)) },
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
