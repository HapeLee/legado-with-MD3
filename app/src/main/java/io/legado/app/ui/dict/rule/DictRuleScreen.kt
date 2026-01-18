package io.legado.app.ui.dict.rule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.ReorderableSelectionItem
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleScreen(
    viewModel: DictRuleViewModel = viewModel(),
    onBackClick: () -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var isSearch by remember { mutableStateOf(false) }
    val selectedIds by viewModel.selectedIds.collectAsState()
    val inSelectionMode = selectedIds.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hapticFeedback = LocalHapticFeedback.current
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DictRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<DictRule?>(null) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            viewModel.saveSortOrder()
        }
    }

    if (showDeleteRuleDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteRuleDialog = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.sure_del) + showDeleteRuleDialog!!.name) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.delete(showDeleteRuleDialog!!)
                        showDeleteRuleDialog = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRuleDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.del_msg)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.delSelectionByIds(selectedIds)
                        viewModel.setSelection(emptySet())
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEditSheet) {
        DictRuleEditSheet(
            rule = editingRule,
            onDismissRequest = { showEditSheet = false },
            onSave = {
                if (editingRule == null) {
                    viewModel.insert(it)
                } else {
                    viewModel.update(it)
                }
                showEditSheet = false
            },
            onCopy = { viewModel.copyRule(it) },
            onPaste = { viewModel.pasteRule() }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                MediumFlexibleTopAppBar(
                    title = {
                        val titleText = remember(inSelectionMode, selectedIds, uiState.items) {
                            when {
                                inSelectionMode -> "已选择 ${selectedIds.size}/${uiState.items.size}"
                                else -> "字典规则"
                            }
                        }
                        AnimatedText(
                            text = titleText
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (inSelectionMode) {
                                    viewModel.setSelection(emptySet())
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (inSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (inSelectionMode) "Cancel" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (!inSelectionMode) {
                            IconButton(onClick = { isSearch = !isSearch }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(visible = isSearch && !inSelectionMode) {
                    SearchBarSection(
                        query = uiState.searchKey ?: "",
                        onQueryChange = {
                            viewModel.setSearchKey(it)
                        },
                        placeholder = stringResource(id = R.string.search)
                    )
                }
            }
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Localized description") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = !inSelectionMode,
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = {
                        editingRule = null
                        showEditSheet = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.name }) { item ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.name,
                        title = item.name,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.name),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = {
                            viewModel.toggleSelection(item.name)
                        },
                        onEnabledChange = { enabled ->
                            viewModel.update(item.rule.copy(enabled = enabled))
                        },
                        onClickEdit = {
                            editingRule = item.rule
                            showEditSheet = true
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        trailingAction = {
                            IconButton(
                                onClick = {
                                    showDeleteRuleDialog = item.rule
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    )
                }
            }
            if (inSelectionMode) {
                DraggableSelectionHandler(
                    listState = listState,
                    items = uiState.items,
                    selectedIds = selectedIds,
                    onSelectionChange = viewModel::setSelection,
                    idProvider = { it.name },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .align(Alignment.TopStart)
                )
            }
            AnimatedVisibility(
                visible = inSelectionMode,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                        .zIndex(1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SelectionBottomBar(
                    onSelectAll = {
                        viewModel.setSelection(uiState.items.map { it.name }.toSet())
                    },
                    onSelectInvert = {
                        val allIds = uiState.items.map { it.name }.toSet()
                        viewModel.setSelection(allIds - selectedIds)
                    },
                    primaryAction = ActionItem(
                        text = stringResource(R.string.delete),
                        icon = { Icon(Icons.Default.Delete, null) },
                        onClick = { showDeleteSelectedDialog = true }
                    ),
                    secondaryActions = listOf(
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
                        )
                    )
                )
            }

        }
    }
}