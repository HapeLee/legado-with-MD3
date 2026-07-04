package io.legado.app.ui.replace

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReplaceRuleRouteScreen(
    viewModel: ReplaceRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()

    ReplaceRuleScreen(
        state = uiState,
        importState = importState,
        events = viewModel.events,
        groups = groups,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToEdit = onNavigateToEdit,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReplaceRuleScreen(
    state: ReplaceRuleUiState,
    importState: BaseImportUiState<ReplaceRule>,
    events: Flow<BaseRuleEvent>,
    groups: List<String>,
    onIntent: (ReplaceRuleIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rules = state.items
    val selectedIds = state.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticFeedback = LocalHapticFeedback.current

    var showUrlInput by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    var showDeleteRuleDialog by remember { mutableStateOf<ReplaceRule?>(null) }
    var showGroupManageSheet by remember { mutableStateOf(false) }

    val tabItems = remember(groups) { listOf("全部") + groups }
    val selectedTabIndex = state.selectedGroup
        ?.let(tabItems::indexOf)
        ?.takeIf { it >= 0 }
        ?: 0

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onIntent(ReplaceRuleIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val canReorder = remember(state.sortMode) {
        state.sortMode == "asc" || state.sortMode == "desc"
    }

    val importDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.reader().readText()
                    onIntent(ReplaceRuleIntent.ImportSource(text))
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(ReplaceRuleIntent.ExportSelection(it)) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(ReplaceRuleIntent.ImportSource(it))
        }
    )

    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.import_replace_rule),
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

    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        onSelectSysDir = {
            showExportSheet = false
            exportDoc.launch("exportReplaceRule.json")
        },
        onUpload = {
            showExportSheet = false
            onIntent(ReplaceRuleIntent.UploadSelection)
        },
        allowExtensions = arrayOf("json")
    )

    BatchImportDialog(
        title = stringResource(R.string.import_replace_rule),
        importState = importState,
        onDismissRequest = { onIntent(ReplaceRuleIntent.CancelImport) },
        onToggleItem = { onIntent(ReplaceRuleIntent.ToggleImportSelection(it)) },
        onToggleAll = { onIntent(ReplaceRuleIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, rule -> onIntent(ReplaceRuleIntent.UpdateImportItem(index, rule)) },
        onConfirm = { onIntent(ReplaceRuleIntent.SaveImportedRules) },
        topBarActions = {},
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.group?.takeIf { it.isNotBlank() }
        }
    )

    if (importState is BaseImportUiState.Loading) {
        Dialog(onDismissRequest = { onIntent(ReplaceRuleIntent.CancelImport) }) { LoadingIndicator() }
    }

    LaunchedEffect(importState) {
        (importState as? BaseImportUiState.Error)?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it.msg)
            }
            onIntent(ReplaceRuleIntent.CancelImport)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onIntent(ReplaceRuleIntent.SaveSortOrder)
        }
    }

    LaunchedEffect(groups, state.selectedGroup) {
        if (state.selectedGroup != null && state.selectedGroup !in groups) {
            onIntent(ReplaceRuleIntent.SetGroup("全部"))
        }
    }

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


    GroupManageBottomSheet(
        show = showGroupManageSheet,
        groups = groups,
        onDismissRequest = { showGroupManageSheet = false },
        onUpdateGroup = { old, new -> onIntent(ReplaceRuleIntent.UpGroup(old, new)) },
        onDeleteGroup = { onIntent(ReplaceRuleIntent.DeleteGroup(it)) }
    )


    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            onIntent(ReplaceRuleIntent.DeleteRule(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleListScaffold(
        title = stringResource(R.string.replace_purify),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { onIntent(ReplaceRuleIntent.SetSearchMode(!state.isSearch)) },
        onSearchQueryChange = { onIntent(ReplaceRuleIntent.UpdateSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.replace_purify_search),
        onClearSelection = { onIntent(ReplaceRuleIntent.ClearSelection) },
        onSelectAll = { onIntent(ReplaceRuleIntent.SelectAll) },
        onSelectInvert = { onIntent(ReplaceRuleIntent.InvertSelection) },
        selectionSecondaryActions = listOf(
            ActionItem(
                text = stringResource(R.string.enable),
                onClick = { onIntent(ReplaceRuleIntent.EnableSelection) }
            ),
            ActionItem(
                text = stringResource(R.string.disable_selection),
                onClick = { onIntent(ReplaceRuleIntent.DisableSelection) }
            ),
            ActionItem(
                text = stringResource(R.string.to_top),
                onClick = { onIntent(ReplaceRuleIntent.TopSelectByIds(selectedIds)) }
            ),
            ActionItem(
                text = stringResource(R.string.to_bottom),
                onClick = { onIntent(ReplaceRuleIntent.BottomSelectByIds(selectedIds)) }
            ),
            ActionItem(
                text = stringResource(R.string.export),
                onClick = { showExportSheet = true }
            )
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            onIntent(ReplaceRuleIntent.SetSelection(ids as Set<Long>))
            onIntent(ReplaceRuleIntent.DeleteSelection)
        },
        bottomContent = {
            if (tabItems.size > 1) {
                AppTabRow(
                    modifier = Modifier
                        .adaptiveHorizontalPadding(),
                    tabTitles = tabItems,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index ->
                        onIntent(ReplaceRuleIntent.SetGroup(tabItems[index]))
                    }
                )
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(
                    visible = !inSelectionMode,
                    alignment = Alignment.BottomEnd,
                ),
                onClick = {
                    onNavigateToEdit(ReplaceEditRoute(id = -1))
                },
                tooltipText = stringResource(R.string.add),
                icon = Icons.Default.Add
            )
        },
        snackbarHostState = snackbarHostState,
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_str),
                onClick = { showImportSheet = true; dismiss() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.group_management),
                onClick = { showGroupManageSheet = true; dismiss() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.help),
                onClick = { /*TODO*/ dismiss() }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_old_first),
                onClick = { onIntent(ReplaceRuleIntent.SetSortMode("asc")); dismiss() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_new_first),
                onClick = { onIntent(ReplaceRuleIntent.SetSortMode("desc")); dismiss() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_name_asc),
                onClick = {
                    onIntent(ReplaceRuleIntent.SetSortMode("name_asc"))
                    dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.drag_disabled_in_sort_mode))
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_name_desc),
                onClick = {
                    onIntent(ReplaceRuleIntent.SetSortMode("name_desc"))
                    dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.drag_disabled_in_sort_mode))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            FastScrollLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = padding.calculateTopPadding(),
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { ui ->
                    val enabledState = stringResource(
                        if (ui.isEnabled) R.string.enabled else R.string.disabled
                    )
                    val selectedState = stringResource(
                        if (selectedIds.contains(ui.id)) {
                            R.string.a11y_selected
                        } else {
                            R.string.a11y_not_selected
                        }
                    )
                    val reorderHint = if (canReorder && !inSelectionMode) {
                        stringResource(R.string.a11y_long_press_reorder)
                    } else {
                        null
                    }
                    val itemDescription = listOfNotNull(
                        ui.name,
                        ui.pattern.takeIf { it.isNotBlank() },
                        enabledState,
                        selectedState,
                        reorderHint
                    ).joinToString()
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = ui.id,
                        title = ui.name,
                        isEnabled = ui.isEnabled,
                        isSelected = selectedIds.contains(ui.id),
                        inSelectionMode = inSelectionMode,
                        canReorder = canReorder,
                        onToggleSelection = {
                            onIntent(ReplaceRuleIntent.ToggleSelection(ui.id))
                        },
                        onEnabledChange = { enabled ->
                            onIntent(ReplaceRuleIntent.SetRuleEnabled(ui.id, enabled))
                        },
                        contentDescription = itemDescription,
                        stateDescription = selectedState,
                        enableSwitchContentDescription = stringResource(
                            R.string.a11y_rule_enabled_switch,
                            ui.name
                        ),
                        editContentDescription = stringResource(R.string.a11y_edit_named, ui.name),
                        moreContentDescription = stringResource(
                            R.string.a11y_more_actions_for,
                            ui.name
                        ),
                        onClickEdit = {
                            onNavigateToEdit(
                                ReplaceEditRoute(
                                    id = ui.id,
                                    pattern = ui.pattern
                                )
                            )
                        },
                        modifier = Modifier,
                        dropdownContent = { dismiss ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.move_to_top),
                                onClick = {
                                    onIntent(ReplaceRuleIntent.ToTop(ui.toEntity()))
                                    dismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.move_to_bottom),
                                onClick = {
                                    onIntent(ReplaceRuleIntent.ToBottom(ui.toEntity()))
                                    dismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.delete),
                                onClick = {
                                    showDeleteRuleDialog = ui.toEntity()
                                    dismiss()
                                }
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
                    onSelectionChange = { onIntent(ReplaceRuleIntent.SetSelection(it)) },
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
