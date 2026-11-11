package io.legado.app.ui.book.source.manage

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.service.BookSourceCheckService
import io.legado.app.ui.qrcode.QrCodeResult
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
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppMenuAnchor
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn

import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun BookSourceRouteScreen(
    viewModel: BookSourceViewModel = koinViewModel(),
    initialImportUrl: String? = null,
    closeAfterImport: Boolean = false,
    onImportClosed: () -> Unit = {},
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onLoginSource: (String) -> Unit,
    onSearchSource: (String, String) -> Unit,
    onDebugSource: (String) -> Unit,
) {
    LaunchedEffect(initialImportUrl) {
        initialImportUrl?.let { viewModel.onIntent(BookSourceIntent.Import(it)) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboard.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookSourceEffect.StartCheck -> {
                    BookSourceCheckService.start(context, effect.ids, effect.keyword)
                }

                BookSourceEffect.CancelCheck -> BookSourceCheckService.stop(context)

                BookSourceEffect.ImportFinished -> {
                    if (closeAfterImport) onImportClosed()
                }

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
    BookSourceScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        onImportDismissed = {
            if (closeAfterImport) onImportClosed()
        },
        onBackClick = onBackClick,
        onAddSource = onAddSource,
        onEditSource = onEditSource,
        onLoginSource = onLoginSource,
        onSearchSource = onSearchSource,
        onDebugSource = onDebugSource,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookSourceScreen(
    state: BookSourceUiState,
    onIntent: (BookSourceIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onImportDismissed: () -> Unit = {},
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onLoginSource: (String) -> Unit,
    onSearchSource: (String, String) -> Unit,
    onDebugSource: (String) -> Unit,
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
    var checkOptionsDraft by remember(state.checkOptions) { mutableStateOf(state.checkOptions) }
    var pendingExportIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showCustomGroup by remember { mutableStateOf(false) }
    val importSuccess = state.importState as? BaseImportUiState.Success
    // 拖动回调先更新本地顺序，让布局与后续命中计算使用同一顺序。
    // 松手后提交一次，等数据源追上再撤掉副本，避免列表闪回旧顺序。
    var dragOrder by remember { mutableStateOf<List<BookSourceItemUi>?>(null) }
    val displayedRules = dragOrder ?: rules
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val moved = (dragOrder ?: rules).toMutableList()
        if (from.index in moved.indices && to.index in moved.indices) {
            moved.add(to.index, moved.removeAt(from.index))
            dragOrder = moved
        }
    }
    val isDragging = reorderState.isAnyItemDragging
    LaunchedEffect(isDragging) {
        if (isDragging) return@LaunchedEffect
        val pending = dragOrder ?: return@LaunchedEffect
        onIntent(BookSourceIntent.CommitSortOrder(pending.map { it.id }, state.sortAscending))
    }
    LaunchedEffect(rules, dragOrder, isDragging) {
        val pending = dragOrder ?: return@LaunchedEffect
        val pendingIds = pending.map { it.id }
        val ruleIds = rules.map { it.id }
        // 数据源追上顺序，或者条目集合已经变了（删源/过滤）就以数据源为准，避免本地副本卡死
        if (!isDragging && (ruleIds == pendingIds || ruleIds.toSet() != pendingIds.toSet())) {
            dragOrder = null
        }
    }
    val cancelLabel = stringResource(R.string.cancel)
    LaunchedEffect(state.checkProgress) {
        val progress = state.checkProgress ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = progress,
            actionLabel = cancelLabel,
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
    val qrCodeImport =
        rememberLauncherForActivityResult(QrCodeResult()) { result ->
            result?.let { onIntent(BookSourceIntent.Import(it)) }
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
        onDismissRequest = {
            onIntent(BookSourceIntent.CancelImport)
            onImportDismissed()
        },
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
        configContent = {
            SwitchSettingItem(
                title = stringResource(R.string.keep_original_name),
                checked = importSuccess?.keepOriginalName == true,
                onCheckedChange = { onIntent(BookSourceIntent.SetImportKeepName(it)) },
            )
            SwitchSettingItem(
                title = stringResource(R.string.keep_group),
                checked = importSuccess?.keepOriginalGroup == true,
                onCheckedChange = { onIntent(BookSourceIntent.SetImportKeepGroup(it)) },
            )
            SwitchSettingItem(
                title = stringResource(R.string.keep_enable),
                checked = importSuccess?.keepOriginalEnable == true,
                onCheckedChange = { onIntent(BookSourceIntent.SetImportKeepEnable(it)) },
            )
            SettingItem(
                title = stringResource(R.string.diy_source_group),
                option = importSuccess?.customGroup?.takeIf { it.isNotBlank() },
                onClick = { showCustomGroup = true },
            )
            SettingItem(
                title = stringResource(R.string.select_new_source),
                onClick = { onIntent(BookSourceIntent.SelectImportStatus(ImportStatus.New)) },
            )
            SettingItem(
                title = stringResource(R.string.select_update_source),
                onClick = { onIntent(BookSourceIntent.SelectImportStatus(ImportStatus.Update)) },
            )
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
        show = groupManage,
        groups = state.groups,
        onDismissRequest = { groupManage = false },
        onUpdateGroup = { old, new -> onIntent(BookSourceIntent.UpdateGroup(old, new)) },
        onDeleteGroup = { onIntent(BookSourceIntent.DeleteGroup(it)) },
        onDeleteGroups = { onIntent(BookSourceIntent.DeleteGroups(it)) }
    )
    AppAlertDialog(
        deleteIds,
        { deleteIds = null },
        stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
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
        onDeleteSelected = {
            @Suppress("UNCHECKED_CAST")
            onIntent(BookSourceIntent.Delete(it as Set<String>))
        },
        menuGroups = persistentListOf(
            AppActionGroup(
                id = "source_actions",
                actions = persistentListOf(
                    AppAction("group_manage", stringResource(R.string.group_manage)),
                    AppAction("import_local", stringResource(R.string.import_local)),
                    AppAction("import_by_qr_code", stringResource(R.string.import_by_qr_code)),
                    AppAction("import_on_line", stringResource(R.string.import_on_line)),
                    AppAction(
                        "group_sources_by_domain",
                        stringResource(R.string.group_sources_by_domain),
                        selected = state.groupByDomain,
                    ),
                ),
            ),
            AppActionGroup(
                id = "source_sort",
                actions = persistentListOf(
                    AppAction(
                        "sort_manual", stringResource(R.string.sort_manual),
                        selected = state.sort == BookSourceSort.Default,
                    ),
                    AppAction(
                        "sort_auto", stringResource(R.string.sort_auto),
                        selected = state.sort == BookSourceSort.Weight,
                    ),
                    AppAction(
                        "sort_by_name", stringResource(R.string.sort_by_name),
                        selected = state.sort == BookSourceSort.Name,
                    ),
                    AppAction(
                        "sort_by_url", stringResource(R.string.sort_by_url),
                        selected = state.sort == BookSourceSort.Url,
                    ),
                    AppAction(
                        "sort_by_lastUpdateTime", stringResource(R.string.sort_by_lastUpdateTime),
                        selected = state.sort == BookSourceSort.Update,
                    ),
                    AppAction(
                        "sort_by_respondTime", stringResource(R.string.sort_by_respondTime),
                        selected = state.sort == BookSourceSort.Respond,
                    ),
                    AppAction(
                        "is_enabled", stringResource(R.string.is_enabled),
                        selected = state.sort == BookSourceSort.Enable,
                    ),
                    AppAction(
                        "sort_desc", stringResource(R.string.sort_desc),
                        selected = !state.sortAscending,
                    ),
                ),
            ),
        ),
        onMenuAction = { id ->
            val sort = when (id) {
                "sort_manual" -> BookSourceSort.Default
                "sort_auto" -> BookSourceSort.Weight
                "sort_by_name" -> BookSourceSort.Name
                "sort_by_url" -> BookSourceSort.Url
                "sort_by_lastUpdateTime" -> BookSourceSort.Update
                "sort_by_respondTime" -> BookSourceSort.Respond
                "is_enabled" -> BookSourceSort.Enable
                else -> null
            }
            when {
                sort != null -> onIntent(BookSourceIntent.SetSort(sort))
                id == "sort_desc" -> onIntent(BookSourceIntent.ToggleSortDirection)
                id == "group_manage" -> groupManage = true
                id == "import_local" -> importDocument.launch(
                    arrayOf("application/json", "text/plain", "text/*")
                )
                id == "import_by_qr_code" -> qrCodeImport.launch(null)
                id == "import_on_line" -> showOnlineImport = true
                id == "group_sources_by_domain" ->
                    onIntent(BookSourceIntent.ToggleGroupByDomain)
            }
        },
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
                                    .semantics { heading() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item(key = item.id, contentType = "book-source") {
                        val canReorder = state.sort == BookSourceSort.Default && !state.groupByDomain
                        val enabledState = stringResource(
                            if (item.enabled) R.string.enabled else R.string.disabled
                        )
                        val discoveryState = if (item.hasExploreUrl) {
                            stringResource(
                                if (item.enabledExplore) R.string.enabled_explore
                                else R.string.disabled_explore
                            )
                        } else {
                            null
                        }
                        val reorderHint = if (canReorder) {
                            stringResource(R.string.a11y_long_press_reorder)
                        } else {
                            null
                        }
                        val itemDescription = listOfNotNull(
                            item.name,
                            item.domain.takeUnless { it == "#" },
                            item.group,
                            enabledState,
                            discoveryState,
                            item.checkMessage,
                            reorderHint,
                        ).joinToString()
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
                            supportingContent = if (
                                item.group != null || item.checkMessage != null || item.hasExploreUrl
                            ) {
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (item.hasExploreUrl) {
                                            DiscoveryIndicator(item.enabledExplore)
                                        }
                                        Column {
                                            item.group?.let { group ->
                                                AppText(
                                                    text = group,
                                                    style = LegadoTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            item.checkMessage?.let { message ->
                                                AppText(
                                                    text = message,
                                                    style = LegadoTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            isEnabled = item.enabled,
                            isSelected = item.id in selectedIds,
                            canReorder = canReorder,
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
                            contentDescription = itemDescription,
                            trailingAction = {
                                SmallPlainButton(
                                    icon = AppIcons.Edit,
                                    contentDescription = stringResource(R.string.edit),
                                    onClick = { onEditSource(item.id) },
                                )
                                BookSourceItemMenu(
                                    item = item,
                                    canMoveToEdge = canReorder,
                                    onMoveToEdge = { toTop ->
                                        onIntent(BookSourceIntent.MoveToEdge(setOf(item.id), toTop))
                                    },
                                    onLogin = { onLoginSource(item.id) },
                                    onSearch = { onSearchSource(item.name, item.id) },
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
    val defaultKeyword = stringResource(R.string.book_source_check_default_keyword)
    var keyword by remember(sourceIds, defaultKeyword) { mutableStateOf(defaultKeyword) }
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

/**
 * 书源发现状态标志：有发现且已启用为绿点，有发现但未启用为红点，无发现不显示标志。
 * 标志位于条目信息行（分组名 / 校验信息行）开头。状态文案已由条目 contentDescription
 * 描述，这里仅作装饰性渲染。
 */
@Composable
private fun DiscoveryIndicator(enabledExplore: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                colorResource(
                    if (enabledExplore) R.color.md_green_500 else R.color.md_red_400
                )
            )
    )
}

private enum class CheckSheet { Run, Settings }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val moreActions = buildList {
        if (canMoveToEdge) {
            add(AppAction(id = "to_top", label = stringResource(R.string.to_top)))
            add(AppAction(id = "to_bottom", label = stringResource(R.string.to_bottom)))
        }
        if (item.hasLoginUrl) {
            add(AppAction(id = "login", label = stringResource(R.string.login)))
        }
        add(AppAction(id = "search", label = stringResource(R.string.search)))
        add(AppAction(id = "debug", label = stringResource(R.string.debug)))
        add(AppAction(id = "delete", label = stringResource(R.string.delete)))
        if (item.hasExploreUrl) {
            add(
                AppAction(
                    id = "toggle_explore",
                    label = stringResource(
                        if (item.enabledExplore) R.string.disable_explore
                        else R.string.enable_explore
                    ),
                )
            )
        }
    }.toImmutableList()
    AppActionMenu(
        groups = persistentListOf(
            AppActionGroup(id = "book_source_actions", actions = moreActions),
        ),
        onAction = { id ->
            when (id) {
                "to_top" -> onMoveToEdge(true)
                "to_bottom" -> onMoveToEdge(false)
                "login" -> onLogin()
                "search" -> onSearch()
                "debug" -> onDebug()
                "delete" -> onDelete()
                "toggle_explore" -> onSetExploreEnabled(!item.enabledExplore)
            }
        },
        anchor = AppMenuAnchor.Row,
        title = stringResource(R.string.menu),
    ) {
        SmallPlainButton(
            onClick = null,
            icon = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.menu),
        )
    }
}

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
            .semantics { heading() }
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
            .heightIn(min = 48.dp)
            .clip(shape = RoundedCornerShape(12.dp))
            .background(
                color = if (selected) LegadoTheme.colorScheme.primaryContainer
                else LegadoTheme.colorScheme.onSheetContent
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(value) },
            )
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
