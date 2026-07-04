package io.legado.app.ui.highlightTagRule

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
import io.legado.app.data.entities.HighlightTagRule
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
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighlightTagRuleScreen(
    viewModel: HighlightTagRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    HighlightTagRuleContent(
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
private fun HighlightTagRuleContent(
    state: HighlightTagRuleUiState,
    importState: BaseImportUiState<HighlightTagRule>,
    events: Flow<BaseRuleEvent>,
    onIntent: (HighlightTagRuleIntent) -> Unit,
    onPasteRule: () -> HighlightTagRule?,
    onBackClick: () -> Unit,
) {

    val context = LocalContext.current

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
                    onIntent(HighlightTagRuleIntent.ImportSource(text))
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(HighlightTagRuleIntent.ExportSelection(it)) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(HighlightTagRuleIntent.ImportSource(it))
        }
    )

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(R.string.export),
        onSelectSysDir = {
            showExportSheet = false
            exportDoc.launch("exportHighlightTagRule.json")
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
        title = stringResource(R.string.import_highlight_tag_rule),
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
        title = stringResource(R.string.import_highlight_tag_rule),
        importState = importState,
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
        title = stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            onIntent(HighlightTagRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
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
        title = stringResource(R.string.highlight_tag_config),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(HighlightTagRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(HighlightTagRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.search_highlight_tag_rule),
        onClearSelection = { onIntent(HighlightTagRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(HighlightTagRuleIntent.SelectAll) },
        onSelectInvert = {
            onIntent(HighlightTagRuleIntent.InvertSelection)
        },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(R.string.enable), onClick = {
                onIntent(HighlightTagRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(R.string.disable_selection), onClick = {
                onIntent(HighlightTagRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(R.string.export),
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
                        item.displayName,
                        item.pattern.takeIf { it.isNotBlank() },
                        enabledState,
                        selectedState,
                        if (!inSelectionMode) {
                            stringResource(R.string.a11y_long_press_reorder)
                        } else {
                            null
                        }
                    ).joinToString()
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
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
                        stateDescription = selectedState,
                        enableSwitchContentDescription = stringResource(
                            R.string.a11y_rule_enabled_switch,
                            item.displayName
                        ),
                        editContentDescription = stringResource(
                            R.string.a11y_edit_named,
                            item.displayName
                        ),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    R.string.a11y_delete_named,
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
