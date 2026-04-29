package io.legado.app.ui.replace

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import io.legado.app.ui.widget.components.AppFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReplaceRuleScreen(
    viewModel: ReplaceRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rules = uiState.items
    val groups by viewModel.allGroups.collectAsStateWithLifecycle()
    val selectedIds = uiState.selectedIds
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

    val importState by viewModel.importState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabItems = remember(groups) { listOf("全部") + groups }
    val filteredRules = remember(uiState.items, selectedTabIndex, tabItems) {
        val targetGroup = tabItems.getOrNull(selectedTabIndex)
        if (targetGroup == null || selectedTabIndex == 0) {
            uiState.items
        } else {
            uiState.items.filter { it.group == targetGroup }
        }
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val canReorder = remember(uiState.sortMode) {
        uiState.sortMode == "asc" || uiState.sortMode == "desc"
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
            viewModel.uploadSelectedRules(selectedIds, rules)
        },
        allowExtensions = arrayOf("json")
    )

    BatchImportDialog(
        title = stringResource(R.string.import_replace_rule),
        importState = importState,
        onDismissRequest = { viewModel.cancelImport() },
        onToggleItem = { viewModel.toggleImportSelection(it) },
        onToggleAll = { viewModel.toggleImportAll(it) },
        onUpdateItem = { index, rule -> viewModel.updateImportItem(index, rule) },
        onConfirm = { viewModel.saveImportedRules() },
        topBarActions = {},
        itemTitle = { rule -> rule.name },
        itemSubtitle = { rule ->
            rule.group?.takeIf { it.isNotBlank() }
        }
    )

    if (importState is BaseImportUiState.Loading) {
        Dialog(onDismissRequest = { viewModel.cancelImport() }) { LoadingIndicator() }
    }

    LaunchedEffect(importState) {
        (importState as? BaseImportUiState.Error)?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it.msg)
            }
            viewModel.cancelImport()
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            viewModel.saveSortOrder()
        }
    }

    LaunchedEffect(groups) {
        val maxIndex = groups.size
        if (selectedTabIndex > maxIndex) {
            selectedTabIndex = 0
            viewModel.setGroup("全部")
        }
    }

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


    GroupManageBottomSheet(
        show = showGroupManageSheet,
        groups = groups,
        onDismissRequest = { showGroupManageSheet = false },
        onUpdateGroup = { old, new -> viewModel.upGroup(old, new) },
        onDeleteGroup = { viewModel.delGroup(it) }
    )


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

    RuleListScaffold(
        title = "替换规则",
        state = uiState,
        onBackClick = { onBackClick() },
        onSearchToggle = { viewModel.setSearchMode(!uiState.isSearch) },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        searchPlaceholder = stringResource(R.string.replace_purify_search),
        onClearSelection = { viewModel.setSelection(emptySet()) },
        onSelectAll = { viewModel.setSelection(rules.map { it.id }.toSet()) },
        onSelectInvert = { viewModel.setSelection(rules.map { it.id }.toSet() - selectedIds) },
        selectionSecondaryActions = listOf(
            ActionItem(
                text = stringResource(R.string.enable),
                onClick = {
                    viewModel.enableSelectionByIds(selectedIds)
                    viewModel.setSelection(emptySet())
                }
            ),
            ActionItem(
                text = stringResource(R.string.disable_selection),
                onClick = {
                    viewModel.disableSelectionByIds(selectedIds)
                    viewModel.setSelection(emptySet())
                }
            ),
            ActionItem(
                text = stringResource(R.string.to_top),
                onClick = {
                    viewModel.topSelectByIds(selectedIds)
                    viewModel.setSelection(emptySet())
                }
            ),
            ActionItem(
                text = stringResource(R.string.to_bottom),
                onClick = {
                    viewModel.bottomSelectByIds(selectedIds)
                    viewModel.setSelection(emptySet())
                }
            ),
            ActionItem(
                text = stringResource(R.string.export),
                onClick = { showExportSheet = true }
            )
        ),
        onDeleteSelected = { ids ->
            @Suppress("UNCHECKED_CAST")
            viewModel.delSelectionByIds(ids as Set<Long>)
            viewModel.setSelection(emptySet())
        },
        bottomContent = {
            if (tabItems.size > 1) {
                AppTabRow(
                    modifier = Modifier
                        .adaptiveHorizontalPadding(),
                    tabTitles = tabItems,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index ->
                        selectedTabIndex = index
                        viewModel.setGroup(tabItems[index])
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
                tooltipText = "Localized description"
            ) {
                AppIcon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        },
        snackbarHostState = snackbarHostState,
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_str),
                onClick = { showImportSheet = true; dismiss() }
            )
            RoundDropdownMenuItem(
                text = "分组管理",
                onClick = { showGroupManageSheet = true; dismiss() }
            )
            RoundDropdownMenuItem(
                text = "帮助",
                onClick = { /*TODO*/ dismiss() }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = "旧的在前",
                onClick = { viewModel.setSortMode("asc"); dismiss() }
            )
            RoundDropdownMenuItem(
                text = "新的在前",
                onClick = { viewModel.setSortMode("desc"); dismiss() }
            )
            RoundDropdownMenuItem(
                text = "名称升序",
                onClick = {
                    viewModel.setSortMode("name_asc")
                    dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar("当前排序模式下禁用拖动")
                    }
                }
            )
            RoundDropdownMenuItem(
                text = "名称降序",
                onClick = {
                    viewModel.setSortMode("name_desc")
                    dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar("当前排序模式下禁用拖动")
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
                items(filteredRules, key = { it.id }) { ui ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = ui.id,
                        title = ui.name,
                        isEnabled = ui.isEnabled,
                        isSelected = selectedIds.contains(ui.id),
                        inSelectionMode = inSelectionMode,
                        canReorder = canReorder,
                        onToggleSelection = {
                            viewModel.toggleSelection(ui.id)
                        },
                        onEnabledChange = { enabled ->
                            viewModel.update(ui.rule.copy(isEnabled = enabled))
                        },
                        onClickEdit = {
                            onNavigateToEdit(
                                ReplaceEditRoute(
                                    id = ui.id,
                                    pattern = ui.rule.pattern
                                )
                            )
                        },
                        modifier = Modifier,
                        dropdownContent = { dismiss ->
                            RoundDropdownMenuItem(
                                text = "移至顶部",
                                onClick = { viewModel.toTop(ui.rule); dismiss() }
                            )
                            RoundDropdownMenuItem(
                                text = "移至底部",
                                onClick = { viewModel.toBottom(ui.rule); dismiss() }
                            )
                            RoundDropdownMenuItem(
                                text = "删除",
                                onClick = { showDeleteRuleDialog = ui.rule; dismiss() }
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

