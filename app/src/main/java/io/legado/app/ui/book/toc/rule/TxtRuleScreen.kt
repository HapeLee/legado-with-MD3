package io.legado.app.ui.book.toc.rule

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
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.theme.adaptiveContentPadding
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
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtRuleRouteScreen(
    viewModel: TxtTocRuleViewModel = koinViewModel(),
    initialRule: String? = null,
    onPickRule: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    TxtRuleScreen(
        state = uiState,
        importState = importState,
        events = viewModel.events,
        onIntent = viewModel::onIntent,
        onPasteRule = viewModel::pasteRule,
        initialRule = initialRule,
        onPickRule = onPickRule,
        onBackClick = onBackClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxtRuleScreen(
    state: TxtTocRuleUiState,
    importState: BaseImportUiState<TxtTocRule>,
    events: Flow<BaseRuleEvent>,
    onIntent: (TxtTocRuleIntent) -> Unit,
    onPasteRule: () -> TxtTocRule?,
    initialRule: String? = null,
    onPickRule: ((String) -> Unit)? = null,
    onBackClick: () -> Unit
) {

    val context = LocalContext.current

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
                    onIntent(TxtTocRuleIntent.ImportSource(text))
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(TxtTocRuleIntent.ExportSelection(it)) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(TxtTocRuleIntent.ImportSource(it))
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
            onIntent(TxtTocRuleIntent.UploadSelection)
        },
        allowExtensions = arrayOf("json")
    )


    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.import_txt_toc_rule),
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
        title = stringResource(R.string.import_txt_toc_rule),
        importState = importState,
        onDismissRequest = { onIntent(TxtTocRuleIntent.CancelImport) },
        onToggleItem = { onIntent(TxtTocRuleIntent.ToggleImportSelection(it)) },
        onToggleAll = { onIntent(TxtTocRuleIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, rule -> onIntent(TxtTocRuleIntent.UpdateImportItem(index, rule)) },
        onConfirm = { onIntent(TxtTocRuleIntent.SaveImportedRules) },
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.rule.takeIf { it.isNotBlank() }
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
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            onIntent(TxtTocRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleEditSheet(
        show = showEditSheet,
        rule = editingRule,
        title = stringResource(R.string.txt_toc_rule),
        label1 = stringResource(R.string.regex),
        label2 = stringResource(R.string.example),
        onDismissRequest = {
            showEditSheet = false
            editingRule = null
        },
        onSave = { updatedRule ->
            when {
                updatedRule.name.isBlank() -> {
                    context.toastOnUi(R.string.cannot_empty)
                    return@RuleEditSheet
                }

                updatedRule.rule.isBlank() -> {
                    context.toastOnUi(R.string.cannot_empty)
                    return@RuleEditSheet
                }

                runCatching { Regex(updatedRule.rule) }.isFailure -> {
                    context.toastOnUi(R.string.invalid_format)
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

    RuleListScaffold(
        title = if (isPickMode) {
            stringResource(R.string.select_toc_rule)
        } else {
            stringResource(R.string.txt_toc_rule)
        },
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active ->
            onIntent(TxtTocRuleIntent.SetSearchMode(active))
        },
        onSearchQueryChange = { onIntent(TxtTocRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.search_txt_toc_rule),
        onClearSelection = { onIntent(TxtTocRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(TxtTocRuleIntent.SelectAll) },
        onSelectInvert = { onIntent(TxtTocRuleIntent.InvertSelection) },
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(R.string.enable), onClick = {
                onIntent(TxtTocRuleIntent.EnableSelection)
            }),
            ActionItem(text = stringResource(R.string.disable_selection), onClick = {
                onIntent(TxtTocRuleIntent.DisableSelection)
            }),
            ActionItem(
                text = stringResource(R.string.export),
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
                text = stringResource(R.string.import_str),
                onClick = { showImportSheet = true; dismiss() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_built_in_rules),
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
                        item.rule.rule == initialRule
                    } else {
                        selectedIds.contains(item.id)
                    }
                    val enabledState = stringResource(
                        if (item.isEnabled) R.string.enabled else R.string.disabled
                    )
                    val selectedState = stringResource(
                        if (isItemHighLighted) {
                            R.string.a11y_selected
                        } else {
                            R.string.a11y_not_selected
                        }
                    )
                    val itemDescription = listOfNotNull(
                        item.name,
                        item.example.takeIf { it.isNotBlank() },
                        enabledState,
                        selectedState,
                        if (!isPickMode && !inSelectionMode) {
                            stringResource(R.string.a11y_long_press_reorder)
                        } else {
                            null
                        }
                    ).joinToString()

                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        title = item.name,
                        subtitle = item.example,
                        isEnabled = item.isEnabled,
                        isSelected = isItemHighLighted,
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = {
                            if (isPickMode) {
                                onPickRule.invoke(item.rule.rule)
                                onBackClick()
                            } else {
                                onIntent(TxtTocRuleIntent.ToggleSelection(item.id))
                            }
                        },
                        onEnabledChange = { enabled ->
                            onIntent(TxtTocRuleIntent.SetRuleEnabled(item.rule, enabled))
                        },
                        contentDescription = itemDescription,
                        stateDescription = selectedState,
                        enableSwitchContentDescription = stringResource(
                            R.string.a11y_rule_enabled_switch,
                            item.name
                        ),
                        editContentDescription = stringResource(R.string.a11y_edit_named, item.name),
                        onClickEdit = { editingRule = item.rule; showEditSheet = true },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.rule },
                                icon = AppIcons.Delete,
                                contentDescription = stringResource(
                                    R.string.a11y_delete_named,
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
