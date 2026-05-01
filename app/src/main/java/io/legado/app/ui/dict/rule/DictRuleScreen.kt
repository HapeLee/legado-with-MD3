package io.legado.app.ui.dict.rule

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleEditFields
import io.legado.app.ui.widget.components.rules.RuleEditSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleScreen(
    viewModel: DictRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val rules = uiState.items
    val selectedIds = uiState.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DictRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<DictRule?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }

    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }


    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BaseRuleEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed && event.url != null) {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText(
                                    "url",
                                    event.url
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    val importDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.reader().readText()
                    viewModel.importSource(text)
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { viewModel.exportToUri(it, rules, selectedIds) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            viewModel.importSource(it)
        }
    )

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(R.string.export),
        onSelectSysDir = {
            showExportSheet = false
            exportDoc.launch("exportDictRule.json")
        },
        onUpload = {
            showExportSheet = false
            viewModel.uploadSelectedRules(selectedIds, rules)
        },
        allowExtensions = arrayOf("json")
    )


    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.import_dict_rule),
        onSelectSysFile = { types ->
            importDoc.launch(types)
            showImportSheet = false
        },
        onManualInput = {
            showUrlInput = true
            showImportSheet = false
        },
        allowExtensions = arrayOf("json", "txt")
    )

    BatchImportDialog(
        title = "导入词典规则",
        importState = importState,
        onDismissRequest = { viewModel.cancelImport() },
        onToggleItem = { viewModel.toggleImportSelection(it) },
        onToggleAll = { viewModel.toggleImportAll(it) },
        onUpdateItem = { index, rule -> viewModel.updateImportItem(index, rule) },
        onConfirm = { viewModel.saveImportedRules() },
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.urlRule.takeIf { it.isNotBlank() }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            viewModel.saveSortOrder()
        }
    }

    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            viewModel.delete(rule)
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleEditSheet(
        show = showEditSheet,
        rule = editingRule,
        title = stringResource(R.string.dict_rule),
        label1 = stringResource(R.string.url_rule),
        label2 = stringResource(R.string.show_rule),
        onDismissRequest = {
            showEditSheet = false
            editingRule = null
        },
        onSave = { updatedRule ->
            if (editingRule == null) {
                viewModel.insert(updatedRule)
            } else {
                viewModel.update(updatedRule)
            }
            showEditSheet = false
            editingRule = null
        },
        onCopy = { viewModel.copyRule(it) },
        onPaste = { viewModel.pasteRule() },
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
        title = "字典规则",
        state = uiState,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            viewModel.setSearchMode(active)
        },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        searchPlaceholder = stringResource(R.string.replace_purify_search),
        onClearSelection = { viewModel.setSelection(emptySet()) },
        onSelectAll = { viewModel.setSelection(rules.map { it.id }.toSet()) },
        onSelectInvert = {
            val allIds = rules.map { it.id }.toSet()
            viewModel.setSelection(allIds - selectedIds)
        },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(R.string.enable), onClick = {
                viewModel.enableSelectionByIds(selectedIds)
                viewModel.setSelection(emptySet())
            }),
            ActionItem(text = stringResource(R.string.disable_selection), onClick = {
                viewModel.disableSelectionByIds(selectedIds)
                viewModel.setSelection(emptySet())
            }),
            ActionItem(
                text = stringResource(R.string.export),
                onClick = { showFilePickerSheet = true })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            viewModel.delSelectionByIds(ids as Set<String>)
            viewModel.setSelection(emptySet())
        },
        onAddClick = {
            editingRule = null
            showEditSheet = true
        },
        snackbarHostState = snackbarHostState,
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_str),
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
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        title = item.id,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { viewModel.toggleSelection(item.id) },
                        onEnabledChange = { enabled -> viewModel.update(item.rule.copy(enabled = enabled)) },
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallIconButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                imageVector = AppIcons.Delete
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
                    onSelectionChange = { viewModel.setSelection(it) },
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
