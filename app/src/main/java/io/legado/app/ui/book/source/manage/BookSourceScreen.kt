package io.legado.app.ui.book.source.manage

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.dialog.TextListInputDialog
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun BookSourceRouteScreen(
    viewModel: BookSourceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onLoginSource: (String) -> Unit,
    onSearchSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookSourceScreen(
        state,
        viewModel::onIntent,
        onBackClick,
        onAddSource,
        onEditSource,
        onLoginSource,
        onSearchSource,
        onDebugSource,
        viewModel.effects,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookSourceScreen(
    state: BookSourceUiState,
    onIntent: (BookSourceIntent) -> Unit,
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onLoginSource: (String) -> Unit,
    onSearchSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
    effects: Flow<BookSourceEffect>,
) {
    val context = LocalContext.current
    val rules = state.items
    val selectedIds = state.selectedIds
    val listState = rememberLazyListState()
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var addGroup by remember { mutableStateOf(false) }
    var removeGroup by remember { mutableStateOf(false) }
    var groupManage by remember { mutableStateOf(false) }
    var showGroupFilterSheet by remember { mutableStateOf(false) }
    var showOnlineImport by remember { mutableStateOf(false) }
    var checkSourceIds by remember { mutableStateOf<Set<String>?>(null) }
    var checkSheet by remember { mutableStateOf<CheckSheet?>(null) }
    var checkOptionsDraft by remember { mutableStateOf(state.checkOptions) }
    var pendingExportIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }
    var showCustomGroup by remember { mutableStateOf(false) }
    val importSuccess = state.importState as? BaseImportUiState.Success
    var dragOrder by remember { mutableStateOf<List<BookSourceItemUi>?>(null) }
    val displayedRules = dragOrder ?: rules
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val moved = (dragOrder ?: rules).toMutableList()
        if (from.index in moved.indices && to.index in moved.indices) {
            moved.add(to.index, moved.removeAt(from.index))
            dragOrder = moved
        }
    }
    LaunchedEffect(reorderState.isAnyItemDragging, rules) {
        if (!reorderState.isAnyItemDragging) {
            dragOrder?.let { pending ->
                val pendingIds = pending.map { it.id }
                if (rules.map { it.id } == pendingIds) {
                    dragOrder = null
                } else {
                    onIntent(BookSourceIntent.CommitSortOrder(pendingIds, state.sortAscending))
                }
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboard.current
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is BookSourceEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed && effect.url != null) {
                        clipboardManager.setClipEntry(
                            ClipEntry(ClipData.newPlainText("url", effect.url))
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(state.checkProgress) {
        val progress = state.checkProgress ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = progress,
            actionLabel = context.getString(R.string.cancel),
            withDismissAction = false,
            duration = androidx.compose.material3.SnackbarDuration.Indefinite,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            onIntent(BookSourceIntent.CancelCheck)
        }
    }
    val importDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    onIntent(BookSourceIntent.Import(reader.readText()))
                }
            }
        }
    val exportDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { onIntent(BookSourceIntent.Export(it, pendingExportIds)) }
        }
    FilePickerSheet(
        show = showExportSheet,
        onDismissRequest = { showExportSheet = false },
        title = stringResource(R.string.export),
        onSelectSysDir = {
            showExportSheet = false
            exportDocument.launch("bookSource.json")
        },
        onUpload = {
            showExportSheet = false
            onIntent(BookSourceIntent.Upload(pendingExportIds))
        },
        allowExtensions = arrayOf("json"),
    )
    SourceInputDialog(
        show = showOnlineImport,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showOnlineImport = false },
        onConfirm = { text -> showOnlineImport = false; onIntent(BookSourceIntent.Import(text)) })

    BatchImportDialog(
        title = stringResource(R.string.import_book_source),
        importState = state.importState,
        onDismissRequest = { onIntent(BookSourceIntent.CancelImport) },
        onConfirm = { onIntent(BookSourceIntent.SaveImportedSources) },
        onToggleItem = { onIntent(BookSourceIntent.ToggleImportItem(it)) },
        onToggleAll = { onIntent(BookSourceIntent.ToggleImportAll(it)) },
        onUpdateItem = { index, source ->
            onIntent(
                BookSourceIntent.UpdateImportItem(
                    index,
                    source
                )
            )
        },
        topBarActions = {
            Box {
                SmallPlainButton(
                    icon = AppIcons.MoreVert,
                    contentDescription = stringResource(R.string.menu),
                    onClick = { showImportOptions = true },
                )
                RoundDropdownMenu(showImportOptions, { showImportOptions = false }) { dismiss ->
                    RoundDropdownMenuItem(stringResource(R.string.select_new_source), onClick = {
                        dismiss(); onIntent(BookSourceIntent.SelectImportStatus(ImportStatus.New))
                    })
                    RoundDropdownMenuItem(stringResource(R.string.select_update_source), onClick = {
                        dismiss(); onIntent(BookSourceIntent.SelectImportStatus(ImportStatus.Update))
                    })
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.keep_original_name),
                        isSelected = importSuccess?.keepOriginalName == true,
                        onClick = { onIntent(BookSourceIntent.SetImportKeepName(importSuccess?.keepOriginalName != true)) },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.keep_group),
                        isSelected = importSuccess?.keepOriginalGroup == true,
                        onClick = { onIntent(BookSourceIntent.SetImportKeepGroup(importSuccess?.keepOriginalGroup != true)) },
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.keep_enable),
                        isSelected = importSuccess?.keepOriginalEnable == true,
                        onClick = { onIntent(BookSourceIntent.SetImportKeepEnable(importSuccess?.keepOriginalEnable != true)) },
                    )
                    RoundDropdownMenuItem(stringResource(R.string.diy_source_group), onClick = {
                        dismiss(); showCustomGroup = true
                    })
                }
            }
        },
        itemTitle = { it.bookSourceName },
        itemSubtitle = { it.bookSourceUrl },
    )

    BookSourceImportGroupDialog(
        show = showCustomGroup,
        initialGroup = importSuccess?.customGroup.orEmpty(),
        initialAdd = importSuccess?.isAddGroup == true,
        onDismissRequest = { showCustomGroup = false },
        onConfirm = { group, add ->
            showCustomGroup = false
            onIntent(BookSourceIntent.SetImportCustomGroup(group, add))
        },
    )

    CheckBookSourceSheet(
        sourceIds = checkSourceIds.takeIf { checkSheet == CheckSheet.Run },
        options = checkOptionsDraft,
        onDismissRequest = { checkSheet = null },
        onOpenSettings = { checkSheet = CheckSheet.Settings },
        onConfirm = { ids, keyword, options ->
            checkSheet = null
            onIntent(BookSourceIntent.StartCheck(ids, keyword, options))
        },
    )

    CheckSourceBottomSheet(
        show = checkSheet == CheckSheet.Settings,
        timeoutSeconds = checkOptionsDraft.timeoutSeconds,
        checkSearch = checkOptionsDraft.checkSearch,
        checkDiscovery = checkOptionsDraft.checkDiscovery,
        checkInfo = checkOptionsDraft.checkInfo,
        checkCategory = checkOptionsDraft.checkCategory,
        checkContent = checkOptionsDraft.checkContent,
        onTimeoutChange = { checkOptionsDraft = checkOptionsDraft.copy(timeoutSeconds = it) },
        onCheckSearchChange = { enabled ->
            checkOptionsDraft = checkOptionsDraft.copy(
                checkSearch = enabled,
                checkDiscovery = checkOptionsDraft.checkDiscovery || !enabled,
            )
        },
        onCheckDiscoveryChange = { enabled ->
            checkOptionsDraft = checkOptionsDraft.copy(
                checkDiscovery = enabled,
                checkSearch = checkOptionsDraft.checkSearch || !enabled,
            )
        },
        onCheckInfoChange = { enabled ->
            checkOptionsDraft = checkOptionsDraft.copy(
                checkInfo = enabled,
                checkCategory = checkOptionsDraft.checkCategory && enabled,
                checkContent = checkOptionsDraft.checkContent && enabled,
            )
        },
        onCheckCategoryChange = { enabled ->
            checkOptionsDraft = checkOptionsDraft.copy(
                checkCategory = enabled,
                checkContent = checkOptionsDraft.checkContent && enabled,
            )
        },
        onCheckContentChange = { checkOptionsDraft = checkOptionsDraft.copy(checkContent = it) },
        onConfirm = {
            onIntent(BookSourceIntent.UpdateCheckOptions(checkOptionsDraft))
            checkSheet = CheckSheet.Run
        },
        onDismiss = { checkSheet = CheckSheet.Run },
    )

    BookSourceGroupFilterSheet(
        show = showGroupFilterSheet,
        state = state,
        onDismissRequest = { showGroupFilterSheet = false },
        onSelect = { value ->
            showGroupFilterSheet = false
            onIntent(BookSourceIntent.SetFilter(value))
        },
    )

    TextListInputDialog(
        show = addGroup,
        title = stringResource(R.string.add_group),
        hint = stringResource(R.string.group_name),
        suggestions = state.groups,
        onDismissRequest = { addGroup = false },
        onConfirm = { onIntent(BookSourceIntent.AddToGroup(selectedIds, it)); addGroup = false })
    TextListInputDialog(
        show = removeGroup,
        title = stringResource(R.string.remove_group),
        hint = stringResource(R.string.group_name),
        suggestions = state.groups,
        onDismissRequest = { removeGroup = false },
        onConfirm = {
            onIntent(BookSourceIntent.RemoveFromGroup(selectedIds, it)); removeGroup = false
        })
    GroupManageBottomSheet(
        groupManage, state.groups, { groupManage = false },
        onUpdateGroup = { old, new -> onIntent(BookSourceIntent.UpdateGroup(old, new)) },
        onDeleteGroup = { onIntent(BookSourceIntent.DeleteGroup(it)) }
    )
    AppAlertDialog(
        deleteIds,
        { deleteIds = null },
        stringResource(R.string.delete),
        confirmText = stringResource(R.string.ok),
        onConfirm = { ids -> onIntent(BookSourceIntent.Delete(ids)); deleteIds = null },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { deleteIds = null })

    RuleListScaffold(
        title = stringResource(R.string.book_source),
        subtitle = state.groupFilterName ?: stringResource(R.string.all),
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = { onIntent(BookSourceIntent.SetSearchMode(it)) },
        onSearchQueryChange = { onIntent(BookSourceIntent.SetSearchQuery(it)) },
        searchPlaceholder = stringResource(R.string.search_book_source),
        onClearSelection = { onIntent(BookSourceIntent.SetSelection(emptySet())) },
        onSelectAll = {
            onIntent(BookSourceIntent.SetSelection(displayedRules.map { it.id }.toSet()))
        },
        onSelectInvert = {
            onIntent(BookSourceIntent.SetSelection(displayedRules.map { it.id }
                .toSet() - selectedIds))
        },
        topBarActions = {
            TopBarActionButton(
                onClick = { showGroupFilterSheet = true },
                imageVector = AppIcons.Filter,
                contentDescription = stringResource(R.string.menu_action_group),
            )
        },
        snackbarHostState = snackbarHostState,
        onAddClick = onAddSource,
        selectionSecondaryActions = listOf(
            ActionItem(stringResource(R.string.enable_selection)) {
                onIntent(
                    BookSourceIntent.SetEnabledForSelection(
                        selectedIds,
                        true
                    )
                )
            },
            ActionItem(stringResource(R.string.disable_selection)) {
                onIntent(
                    BookSourceIntent.SetEnabledForSelection(
                        selectedIds,
                        false
                    )
                )
            },
            ActionItem(stringResource(R.string.enable_explore)) {
                onIntent(
                    BookSourceIntent.SetExploreEnabled(
                        selectedIds,
                        true
                    )
                )
            },
            ActionItem(stringResource(R.string.disable_explore)) {
                onIntent(
                    BookSourceIntent.SetExploreEnabled(
                        selectedIds,
                        false
                    )
                )
            },
            ActionItem(stringResource(R.string.add_group)) { addGroup = true },
            ActionItem(stringResource(R.string.remove_group)) { removeGroup = true },
            ActionItem(stringResource(R.string.selection_to_top)) {
                onIntent(
                    BookSourceIntent.MoveToEdge(
                        selectedIds,
                        true
                    )
                )
            },
            ActionItem(stringResource(R.string.selection_to_bottom)) {
                onIntent(
                    BookSourceIntent.MoveToEdge(
                        selectedIds,
                        false
                    )
                )
            },
            ActionItem(stringResource(R.string.check_selected_interval)) {
                onIntent(
                    BookSourceIntent.CheckSelectedInterval(
                        selectedIds
                    )
                )
            },
            ActionItem(stringResource(R.string.check_book_source)) {
                checkSourceIds = selectedIds
                checkOptionsDraft = state.checkOptions
                checkSheet = CheckSheet.Run
                onIntent(BookSourceIntent.SetSelection(emptySet()))
            },
            ActionItem(stringResource(R.string.export)) {
                pendingExportIds = selectedIds
                showExportSheet = true
            },
        ),
        onDeleteSelected = { deleteIds = it as Set<String> },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.group_manage),
                onClick = { dismiss(); groupManage = true })
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_local),
                onClick = {
                    dismiss(); importDocument.launch(
                    arrayOf(
                        "application/json",
                        "text/plain",
                        "text/*"
                    )
                )
                })
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_on_line),
                onClick = { dismiss(); showOnlineImport = true })
            RoundDropdownMenuItem(
                text = stringResource(R.string.group_sources_by_domain),
                isSelected = state.groupByDomain,
                onClick = { dismiss(); onIntent(BookSourceIntent.ToggleGroupByDomain) },
            )
            PillDivider()
            SortMenuItem(R.string.sort_manual, BookSourceSort.Default, state, dismiss, onIntent)
            SortMenuItem(R.string.sort_auto, BookSourceSort.Weight, state, dismiss, onIntent)
            SortMenuItem(R.string.sort_by_name, BookSourceSort.Name, state, dismiss, onIntent)
            SortMenuItem(R.string.sort_by_url, BookSourceSort.Url, state, dismiss, onIntent)
            SortMenuItem(
                R.string.sort_by_lastUpdateTime,
                BookSourceSort.Update,
                state,
                dismiss,
                onIntent
            )
            SortMenuItem(
                R.string.sort_by_respondTime,
                BookSourceSort.Respond,
                state,
                dismiss,
                onIntent
            )
            SortMenuItem(R.string.is_enabled, BookSourceSort.Enable, state, dismiss, onIntent)
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_desc),
                isSelected = !state.sortAscending,
                onClick = { dismiss(); onIntent(BookSourceIntent.ToggleSortDirection) },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = adaptiveContentPadding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayedRules.forEachIndexed { index, item ->
                    if (state.groupByDomain && (index == 0 || displayedRules[index - 1].domain != item.domain)) {
                        item(key = "domain:${item.domain}", contentType = "domain-header") {
                            AppText(
                                text = item.domain,
                                style = LegadoTheme.typography.titleSmall,
                                color = LegadoTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item(key = item.id, contentType = "book-source") {
                        ReorderableSelectionItem(
                            state = reorderState,
                            key = item.id,
                            reorderIndex = index,
                            reorderItemCount = displayedRules.size,
                            onMoveItem = { from, to ->
                                val moved = displayedRules.toMutableList()
                                if (from in moved.indices && to in moved.indices) {
                                    moved.add(to, moved.removeAt(from))
                                    dragOrder = moved
                                    onIntent(
                                        BookSourceIntent.CommitSortOrder(
                                            moved.map { it.id },
                                            state.sortAscending
                                        )
                                    )
                                }
                            },
                            title = item.name,
                            subtitle = listOfNotNull(
                                item.group,
                                item.checkMessage
                            ).joinToString(" · ").ifBlank { null },
                            isEnabled = item.enabled,
                            isSelected = item.id in selectedIds,
                            canReorder = state.sort == BookSourceSort.Default && !state.groupByDomain,
                            inSelectionMode = selectedIds.isNotEmpty(),
                            onToggleSelection = { onIntent(BookSourceIntent.ToggleSelection(item.id)) },
                            onEnabledChange = {
                                onIntent(
                                    BookSourceIntent.SetEnabled(
                                        item.id,
                                        it
                                    )
                                )
                            },
                            onClickEdit = { onEditSource(item.id) },
                            trailingAction = {
                                BookSourceItemMenu(
                                    item = item,
                                    canMoveToEdge = state.sort == BookSourceSort.Default && !state.groupByDomain,
                                    onMoveToEdge = { toTop ->
                                        onIntent(BookSourceIntent.MoveToEdge(setOf(item.id), toTop))
                                    },
                                    onLogin = { onLoginSource(item.id) },
                                    onSearch = { onSearchSource(item.id) },
                                    onDebug = { onDebugSource(item.id) },
                                    onDelete = { deleteIds = setOf(item.id) },
                                    onSetExploreEnabled = { enabled ->
                                        onIntent(
                                            BookSourceIntent.SetExploreEnabled(
                                                setOf(item.id),
                                                enabled
                                            )
                                        )
                                    },
                                )
                            })
                    }
                }
            }
            if (selectedIds.isNotEmpty()) DraggableSelectionHandler(
                listState = listState,
                items = displayedRules,
                selectedIds = selectedIds,
                onSelectionChange = { onIntent(BookSourceIntent.SetSelection(it)) },
                idProvider = { it.id },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun BookSourceImportGroupDialog(
    show: Boolean,
    initialGroup: String,
    initialAdd: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var group by remember(show, initialGroup) { mutableStateOf(initialGroup) }
    var add by remember(show, initialAdd) { mutableStateOf(initialAdd) }
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.diy_source_group),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = stringResource(R.string.group_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                SwitchSettingItem(
                    title = stringResource(R.string.add_group),
                    checked = add,
                    onCheckedChange = { add = it },
                )
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onConfirm(group.trim(), add) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}

@Composable
private fun CheckBookSourceSheet(
    sourceIds: Set<String>?,
    options: BookSourceCheckOptionsUi,
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfirm: (Set<String>, String, BookSourceCheckOptionsUi) -> Unit,
) {
    var keyword by remember(sourceIds) { mutableStateOf("我的") }
    AppModalBottomSheet(
        data = sourceIds,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.check_book_source),
        startAction = {
            MediumTonalButton(
                icon = AppIcons.Settings,
                contentDescription = stringResource(R.string.check_source_config),
                onClick = onOpenSettings,
            )
        },
        endAction = {
            MediumTonalButton(
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.check_book_source),
                onClick = { sourceIds?.let { onConfirm(it, keyword.trim(), options) } },
            )
        },
    ) {
        AppTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = stringResource(R.string.search_book_key),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
        )
    }
}

private enum class CheckSheet { Run, Settings }

@Composable
private fun BookSourceItemMenu(
    item: BookSourceItemUi,
    canMoveToEdge: Boolean,
    onMoveToEdge: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onDebug: () -> Unit,
    onDelete: () -> Unit,
    onSetExploreEnabled: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SmallPlainButton(
            icon = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.menu),
            onClick = { expanded = true },
        )
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            if (canMoveToEdge) {
                RoundDropdownMenuItem(stringResource(R.string.to_top), onClick = {
                    dismiss(); onMoveToEdge(true)
                })
                RoundDropdownMenuItem(stringResource(R.string.to_bottom), onClick = {
                    dismiss(); onMoveToEdge(false)
                })
            }
            if (item.hasLoginUrl) {
                RoundDropdownMenuItem(stringResource(R.string.login), onClick = {
                    dismiss(); onLogin()
                })
            }
            RoundDropdownMenuItem(stringResource(R.string.search), onClick = {
                dismiss(); onSearch()
            })
            RoundDropdownMenuItem(stringResource(R.string.debug), onClick = {
                dismiss(); onDebug()
            })
            RoundDropdownMenuItem(stringResource(R.string.delete), onClick = {
                dismiss(); onDelete()
            })
            if (item.hasExploreUrl) {
                RoundDropdownMenuItem(
                    text = stringResource(
                        if (item.enabledExplore) R.string.disable_explore else R.string.enable_explore
                    ),
                    onClick = {
                        dismiss(); onSetExploreEnabled(!item.enabledExplore)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    textRes: Int,
    sort: BookSourceSort,
    state: BookSourceUiState,
    dismiss: () -> Unit,
    onIntent: (BookSourceIntent) -> Unit,
) = RoundDropdownMenuItem(
    text = stringResource(textRes),
    isSelected = state.sort == sort,
    onClick = { dismiss(); onIntent(BookSourceIntent.SetSort(sort)) },
)

@Composable
private fun BookSourceGroupFilterSheet(
    show: Boolean,
    state: BookSourceUiState,
    onDismissRequest: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember(show) { mutableStateOf("") }
    val defaultOptions = listOf(
        stringResource(R.string.all) to null,
        stringResource(R.string.enabled) to BookSourceViewModel.FILTER_ENABLED,
        stringResource(R.string.disabled) to BookSourceViewModel.FILTER_DISABLED,
        stringResource(R.string.need_login) to BookSourceViewModel.FILTER_LOGIN,
        stringResource(R.string.no_group) to BookSourceViewModel.FILTER_NO_GROUP,
        stringResource(R.string.enabled_explore) to BookSourceViewModel.FILTER_ENABLED_EXPLORE,
        stringResource(R.string.disabled_explore) to BookSourceViewModel.FILTER_DISABLED_EXPLORE,
    )
    val otherOptions = state.groups.map { group ->
        group to "${BookSourceViewModel.PREFIX_GROUP}$group"
    }
    val filteredDefaultOptions = remember(defaultOptions, query) {
        if (query.isBlank()) defaultOptions else defaultOptions.filter { (label, _) ->
            label.contains(query, ignoreCase = true)
        }
    }
    val filteredOtherOptions = remember(otherOptions, query) {
        if (query.isBlank()) otherOptions else otherOptions.filter { (label, _) ->
            label.contains(query, ignoreCase = true)
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.menu_action_group),
    ) {
        Column(Modifier.fillMaxWidth()) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_placeholder),
                autoFocus = false,
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (filteredDefaultOptions.isNotEmpty()) {
                    item(key = "default-groups-header", span = { GridItemSpan(maxLineSpan) }) {
                        GroupFilterSectionTitle(stringResource(R.string.book_source_default_groups))
                    }
                }
                gridItems(
                    filteredDefaultOptions,
                    key = { (_, value) -> value ?: "@all" }) { (label, value) ->
                    GroupFilterItem(label, value, state.activeFilter, onSelect)
                }
                if (filteredOtherOptions.isNotEmpty()) {
                    item(key = "other-groups-header", span = { GridItemSpan(maxLineSpan) }) {
                        GroupFilterSectionTitle(stringResource(R.string.book_source_other_groups))
                    }
                }
                gridItems(filteredOtherOptions, key = { (_, value) -> value }) { (label, value) ->
                    GroupFilterItem(label, value, state.activeFilter, onSelect)
                }
            }
        }
    }
}

@Composable
private fun GroupFilterSectionTitle(text: String) {
    AppText(
        text = text,
        style = LegadoTheme.typography.labelMedium,
        color = LegadoTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun GroupFilterItem(
    label: String,
    value: String?,
    activeFilter: String?,
    onSelect: (String?) -> Unit,
) {
    val selected = activeFilter == value
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(12.dp))
            .background(
                color = if (selected) LegadoTheme.colorScheme.primaryContainer
                else LegadoTheme.colorScheme.onSheetContent
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AppText(
            text = label,
            style = LegadoTheme.typography.labelMediumEmphasized,
            color = if (selected) LegadoTheme.colorScheme.onPrimaryContainer
            else LegadoTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
