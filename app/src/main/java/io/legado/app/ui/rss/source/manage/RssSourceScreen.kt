package io.legado.app.ui.rss.source.manage

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
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
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
import kotlinx.coroutines.flow.Flow
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RssSourceRouteScreen(
    viewModel: RssSourceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onEditSource: (RssSource) -> Unit,
    onAddSource: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RssSourceScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        effects = viewModel.effects,
        onBackClick = onBackClick,
        onEditSource = onEditSource,
        onAddSource = onAddSource,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RssSourceScreen(
    state: RssSourceUiState,
    onIntent: (RssSourceIntent) -> Unit,
    effects: Flow<RssSourceEffect>,
    onBackClick: () -> Unit,
    onEditSource: (RssSource) -> Unit,
    onAddSource: () -> Unit,
) {
    val context = LocalContext.current
    val groups = state.groups

    val rules = state.items
    val selectedIds = state.selectedIds
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
        onIntent(RssSourceIntent.MoveItem(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importState = state.importState

    LaunchedEffect(Unit) {
        effects.collect { event ->
            when (event) {
                is RssSourceEffect.ShowSnackbar -> {
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
                    onIntent(RssSourceIntent.Import(text))
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { onIntent(RssSourceIntent.Export(it, rules, selectedIds)) }
        }
    )

    SourceInputDialog(
        show = showUrlInput,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showUrlInput = false },
        onConfirm = {
            showUrlInput = false
            onIntent(RssSourceIntent.Import(it))
        }
    )


    TextListInputDialog(
        show = showAddToGroupDialog,
        title = stringResource(R.string.add_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showAddToGroupDialog = false },
        onConfirm = { text ->
            onIntent(RssSourceIntent.AddSelectionToGroup(selectedIds, text))
            showAddToGroupDialog = false
            onIntent(RssSourceIntent.SetSelection(emptySet()))
        }
    )

    TextListInputDialog(
        show = showRemoveFromGroupDialog,
        title = stringResource(R.string.remove_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showRemoveFromGroupDialog = false },
        onConfirm = { text ->
            onIntent(RssSourceIntent.RemoveSelectionFromGroup(selectedIds, text))
            showRemoveFromGroupDialog = false
            onIntent(RssSourceIntent.SetSelection(emptySet()))
        }
    )

    GroupManageBottomSheet(
        show = showGroupManageSheet,
        groups = groups,
        onDismissRequest = { showGroupManageSheet = false },
        onUpdateGroup = { old, new -> onIntent(RssSourceIntent.UpdateGroup(old, new)) },
        onDeleteGroup = { onIntent(RssSourceIntent.DeleteGroup(it)) }
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
            onIntent(RssSourceIntent.Upload(selectedIds, rules))
        },
        allowExtensions = arrayOf("json")
    )

    BatchImportDialog(
        title = stringResource(R.string.import_rss_source),
        importState = importState,
        onDismissRequest = { onIntent(RssSourceIntent.CancelImport) },
        onToggleItem = { onIntent(RssSourceIntent.ToggleImportItem(it)) },
        onToggleAll = { onIntent(RssSourceIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, source ->
            onIntent(RssSourceIntent.UpdateImportItem(index, source))
        },
        onConfirm = { onIntent(RssSourceIntent.SaveImportedRules) },
        itemTitle = { rule -> rule.sourceName },
        itemSubtitle = { rule ->
            rule.sourceUrl.takeIf { it.isNotBlank() }
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            onIntent(RssSourceIntent.SaveSortOrder)
        }
    }

    AppAlertDialog(
        data = showDeleteRuleDialog,
        onDismissRequest = { showDeleteRuleDialog = null },
        title = stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { rule ->
            onIntent(RssSourceIntent.Delete(rule))
            showDeleteRuleDialog = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteRuleDialog = null }
    )

    RuleListScaffold(
        title = stringResource(R.string.rss_source),
        subtitle = state.groupFilterName ?: stringResource(R.string.all),
        state = state,
        onBackClick = { onBackClick() },
        onSearchToggle = { active -> onIntent(RssSourceIntent.SetSearchMode(active)) },
        onSearchQueryChange = { onIntent(RssSourceIntent.SetSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.search_rss_source),
        onClearSelection = { onIntent(RssSourceIntent.SetSelection(emptySet())) },
        onSelectAll = { onIntent(RssSourceIntent.SetSelection(rules.map { it.id }.toSet())) },
        onSelectInvert = {
            val allIds = rules.map { it.id }.toSet()
            onIntent(RssSourceIntent.SetSelection(allIds - selectedIds))
        },
        topBarActions = {},
        selectionSecondaryActions = listOf(
            ActionItem(text = stringResource(R.string.enable), onClick = {
                onIntent(RssSourceIntent.EnableSelection(selectedIds))
                onIntent(RssSourceIntent.SetSelection(emptySet()))
            }),
            ActionItem(text = stringResource(R.string.disable_selection), onClick = {
                onIntent(RssSourceIntent.DisableSelection(selectedIds))
                onIntent(RssSourceIntent.SetSelection(emptySet()))
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
                onIntent(RssSourceIntent.CheckSelectedInterval(selectedIds, rules))
            })
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            onIntent(RssSourceIntent.DeleteSelection(ids as Set<String>))
            onIntent(RssSourceIntent.SetSelection(emptySet()))
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
                            onIntent(RssSourceIntent.ImportDefault)
                        }
                    )
                }
            }
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.all),
                onClick = { dismiss(); onIntent(RssSourceIntent.SetGroupFilter(null)) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.enabled),
                onClick = { dismiss(); onIntent(RssSourceIntent.SetGroupFilter(RssSourceViewModel.FILTER_ENABLED)) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.disabled),
                onClick = { dismiss(); onIntent(RssSourceIntent.SetGroupFilter(RssSourceViewModel.FILTER_DISABLED)) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.need_login),
                onClick = { dismiss(); onIntent(RssSourceIntent.SetGroupFilter(RssSourceViewModel.FILTER_LOGIN)) }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.no_group),
                onClick = { dismiss(); onIntent(RssSourceIntent.SetGroupFilter(RssSourceViewModel.FILTER_NO_GROUP)) }
            )
            PillDivider()
            groups.forEach { group ->
                RoundDropdownMenuItem(
                    text = group,
                    onClick = {
                        dismiss()
                        onIntent(RssSourceIntent.SetGroupFilter("${RssSourceViewModel.PREFIX_GROUP}$group"))
                    }
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
                        reorderIndex = rules.indexOf(item),
                        reorderItemCount = rules.size,
                        onMoveItem = { from, to -> onIntent(RssSourceIntent.MoveItem(from, to)) },
                        title = item.name,
                        subtitle = item.group,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.id),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = { onIntent(RssSourceIntent.ToggleSelection(item.id)) },
                        onEnabledChange = { enabled ->
                            onIntent(RssSourceIntent.Update(item.source.copy(enabled = enabled)))
                        },
                        onClickEdit = { onEditSource(item.source) },
                        trailingAction = {
                            SmallPlainButton(
                                onClick = { showDeleteRuleDialog = item.source },
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete)
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
                    onSelectionChange = { onIntent(RssSourceIntent.SetSelection(it)) },
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
