package io.legado.app.ui.book.source.manage

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GroupManageBottomSheet
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.dialog.TextListInputDialog
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun BookSourceRouteScreen(
    viewModel: BookSourceViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (String) -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookSourceScreen(
        state,
        viewModel::onIntent,
        onBackClick,
        onAddSource,
        onEditSource,
        onImportLocal,
        onImportOnline
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
    onImportLocal: () -> Unit,
    onImportOnline: (String) -> Unit,
) {
    val rules = state.items
    val selectedIds = state.selectedIds
    val listState = rememberLazyListState()
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var addGroup by remember { mutableStateOf(false) }
    var removeGroup by remember { mutableStateOf(false) }
    var groupManage by remember { mutableStateOf(false) }
    var showGroupFilterSheet by remember { mutableStateOf(false) }
    var showOnlineImport by remember { mutableStateOf(false) }
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
    SourceInputDialog(
        show = showOnlineImport,
        title = stringResource(R.string.import_on_line),
        onDismissRequest = { showOnlineImport = false },
        onConfirm = { text -> showOnlineImport = false; onImportOnline(text) })

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
        snackbarHostState = remember { SnackbarHostState() },
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
        ),
        onDeleteSelected = { deleteIds = it as Set<String> },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.group_manage),
                onClick = { dismiss(); groupManage = true })
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_local),
                onClick = { dismiss(); onImportLocal() })
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
                            subtitle = item.group,
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
                                SmallPlainButton(
                                    icon = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    onClick = { deleteIds = setOf(item.id) })
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
