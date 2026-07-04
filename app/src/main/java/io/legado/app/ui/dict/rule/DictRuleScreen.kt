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
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleRouteScreen(
    viewModel: DictRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    DictRuleScreen(
        state = uiState,
        importState = importState,
        events = viewModel.events,
        onIntent = viewModel::onIntent,
        onPasteRule = viewModel::pasteRule,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleScreen(
    state: DictRuleUiState,
    importState: BaseImportUiState<DictRule>,
    events: Flow<BaseRuleEvent>,
    onIntent: (DictRuleIntent) -> Unit,
    onPasteRule: () -> DictRule?,
    onBackClick: () -> Unit,
) {

    val context = LocalContext.current

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
                    onIntent(DictRuleIntent.ImportSource(text))
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(DictRuleIntent.ExportSelection(it)) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(DictRuleIntent.ImportSource(it))
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
            onIntent(DictRuleIntent.UploadSelection)
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
        title = stringResource(R.string.import_dict_rule),
        importState = importState,
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
        title = stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            onIntent(DictRuleIntent.DeleteRule(rule))
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
        title = stringResource(R.string.dict_rule),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(DictRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(DictRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.search_dict_rule),
        onClearSelection = { onIntent(DictRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(DictRuleIntent.SelectAll) },
        onSelectInvert = {
            onIntent(DictRuleIntent.InvertSelection)
        },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(R.string.enable), onClick = {
                onIntent(DictRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(R.string.disable_selection), onClick = {
                onIntent(DictRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(R.string.export),
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
                    val enabledState = stringResource(
                        if (item.isEnabled) R.string.enabled else R.string.disabled
                    )
                    val selectedState = stringResource(
                        if (selectedIds.contains(item.id)) {
                            R.string.a11y_selected
                        } else {
                            R.string.a11y_not_selected
                        }
                    )
                    val itemDescription = listOfNotNull(
                        item.id,
                        item.urlRule.takeIf { it.isNotBlank() },
                        enabledState,
                        selectedState,
                        stringResource(R.string.a11y_long_press_reorder)
                    ).joinToString()
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        title = item.id,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { onIntent(DictRuleIntent.ToggleSelection(item.id)) },
                        onEnabledChange = { enabled ->
                            onIntent(DictRuleIntent.SetRuleEnabled(item.rule, enabled))
                        },
                        contentDescription = itemDescription,
                        stateDescription = selectedState,
                        enableSwitchContentDescription = stringResource(
                            R.string.a11y_rule_enabled_switch,
                            item.id
                        ),
                        editContentDescription = stringResource(R.string.a11y_edit_named, item.id),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    R.string.a11y_delete_named,
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
