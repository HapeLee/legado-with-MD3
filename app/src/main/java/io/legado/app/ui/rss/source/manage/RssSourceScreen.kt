package io.legado.app.ui.rss.source.manage

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
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
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.dialog.TextListInputDialog
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RssSourceScreen(
    viewModel: RssSourceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onEditSource: (RssSource) -> Unit,
    onAddSource: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val groups by viewModel.groupsFlow.collectAsStateWithLifecycle()

    val rules = uiState.items
    val selectedIds = uiState.selectedIds
    val inSelectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current

    var showDeleteRuleDialog by remember { mutableStateOf<RssSource?>(null) }
    var showUrlInput by remember { mutableStateOf(false) }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showAddToGroupDialog by remember { mutableStateOf(false) }
    var showRemoveFromGroupDialog by remember { mutableStateOf(false) }
    var showGroupManageSheet by remember { mutableStateOf(false) }

    var showImportMenu by remember { mutableStateOf(false) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importState by viewModel.importState.collectAsStateWithLifecycle()

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
                            ClipEntry(ClipData.newPlainText("url", event.url))
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


    TextListInputDialog(
        show = showAddToGroupDialog,
        title = stringResource(R.string.add_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showAddToGroupDialog = false },
        onConfirm = { text ->
            viewModel.selectionAddToGroups(selectedIds, text)
            showAddToGroupDialog = false
            viewModel.setSelection(emptySet())
        }
    )

    TextListInputDialog(
        show = showRemoveFromGroupDialog,
        title = stringResource(R.string.remove_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showRemoveFromGroupDialog = false },
        onConfirm = { text ->
            viewModel.selectionRemoveFromGroups(selectedIds, text)
            showRemoveFromGroupDialog = false
            viewModel.setSelection(emptySet())
        }
    )

    GroupManageBottomSheet(
        show = showGroupManageSheet,
        groups = groups,
        onDismissRequest = { showGroupManageSheet = false },
        onUpdateGroup = { old, new -> viewModel.upGroup(old, new) },
        onDeleteGroup = { viewModel.delGroup(it) }
    )


    FilePickerSheet(
        show = showFilePickerSheet,
        onDismissRequest = { showFilePickerSheet = false },
        onSelectSysDir = {
            showFilePickerSheet = false
            exportDoc.launch("exportRssSource.json")
        },
        onUpload = {
            showFilePickerSheet = false
            viewModel.uploadSelectedRules(selectedIds, rules)
        },
        allowExtensions = arrayOf("json")
    )

    BatchImportDialog(
        title = stringResource(R.string.import_rss_source),
        importState = importState,
        onDismissRequest = { viewModel.cancelImport() },
        onToggleItem = { viewModel.toggleImportSelection(it) },
        onToggleAll = { viewModel.toggleImportAll(it) },
        onUpdateItem = { index, source -> viewModel.updateImportItem(index, source) },
        onConfirm = { viewModel.saveImportedRules() },
        itemTitle = { rule -> rule.sourceName },
        itemSubtitle = { rule ->
            rule.sourceUrl.takeIf { it.isNotBlank() }
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
            viewModel.del(rule)
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleListScaffold(
        title = stringResource(R.string.rss_source),
        subtitle = uiState.groupFilterName ?: stringResource(R.string.all),
        state = uiState,
        onBackClick = { onBackClick() },
        onSearchToggle = { active -> viewModel.setSearchMode(active) },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        searchPlaceholder = stringResource(R.string.search_rss_source),
        onClearSelection = { viewModel.setSelection(emptySet()) },
        onSelectAll = { viewModel.setSelection(rules.map { it.id }.toSet()) },
        onSelectInvert = {
            val allIds = rules.map { it.id }.toSet()
            viewModel.setSelection(allIds - selectedIds)
        },
        topBarActions = {},
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
                text = stringResource(R.string.add_group),
                onClick = { showAddToGroupDialog = true }),
            ActionItem(
                text = stringResource(R.string.remove_group),
                onClick = { showRemoveFromGroupDialog = true }),
            ActionItem(
                text = stringResource(R.string.export),
                onClick = { showFilePickerSheet = true }),
            ActionItem(text = stringResource(R.string.check_selected_interval), onClick = {
                viewModel.checkSelectedInterval(selectedIds, rules)
            })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            viewModel.delSelectionByIds(ids as Set<String>)
            viewModel.setSelection(emptySet())
        },
        onAddClick = { onAddSource() },
        snackbarHostState = snackbarHostState,
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                onClick = { showGroupManageSheet = true },
                text = "分组管理",
            )
            Box {
                RoundDropdownMenuItem(
                    text = stringResource(R.string.import_rss_source),
                    onClick = { showImportMenu = true }
                )
                RoundDropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_on_line),
                        onClick = {
                            showImportMenu = false
                            dismiss()
                            showUrlInput = true
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_local),
                        onClick = {
                            showImportMenu = false
                            dismiss()
                            importDoc.launch(arrayOf("text/plain", "application/json"))
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.import_default_rule),
                        onClick = {
                            showImportMenu = false
                            dismiss()
                            viewModel.importDefault()
                        }
                    )
                }
            }
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.all),
                onClick = { dismiss(); viewModel.setGroupFilter(null) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.enabled),
                onClick = { dismiss(); viewModel.setGroupFilter(RssSourceViewModel.FILTER_ENABLED) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.disabled),
                onClick = { dismiss(); viewModel.setGroupFilter(RssSourceViewModel.FILTER_DISABLED) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.need_login),
                onClick = { dismiss(); viewModel.setGroupFilter(RssSourceViewModel.FILTER_LOGIN) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.no_group),
                onClick = { dismiss(); viewModel.setGroupFilter(RssSourceViewModel.FILTER_NO_GROUP) }
            )
            PillDivider()
            groups.forEach { group ->
                RoundDropdownMenuItem(
                    text = group,
                    onClick = { dismiss(); viewModel.setGroupFilter("${RssSourceViewModel.PREFIX_GROUP}$group") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { item ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.id,
                        title = item.name,
                        subtitle = item.group,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { viewModel.toggleSelection(item.id) },
                        onEnabledChange = { enabled -> viewModel.update(item.source.copy(enabled = enabled)) },
                        onClickEdit = { onEditSource(item.source) },
                        trailingAction = {
                            SmallIconButton(
                                onClick = { showDeleteRuleDialog = item.source },
                                imageVector = Icons.Default.Delete
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

