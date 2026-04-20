package io.legado.app.ui.rss.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.data.entities.RssStar
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SourceIcon
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.dialog.TextListInputDialog
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFavoritesScreen(
    onBackClick: () -> Unit,
    onOpenRead: (title: String?, origin: String, link: String?, openUrl: String?) -> Unit,
    viewModel: RssFavoritesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddToGroupDialog by remember { mutableStateOf(false) }
    var showRemoveFromGroupDialog by remember { mutableStateOf(false) }
    var showSetGroupDialog by remember { mutableStateOf<RssStar?>(null) }

    TextListInputDialog(
        show = showAddToGroupDialog,
        title = stringResource(R.string.add_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showAddToGroupDialog = false },
        onConfirm = { text ->
            viewModel.selectionAddToGroups(state.selectedIds, text)
            showAddToGroupDialog = false
            viewModel.clearSelection()
        }
    )

    TextListInputDialog(
        show = showRemoveFromGroupDialog,
        title = stringResource(R.string.remove_group),
        hint = stringResource(R.string.group_name),
        suggestions = groups,
        onDismissRequest = { showRemoveFromGroupDialog = false },
        onConfirm = { text ->
            viewModel.selectionRemoveFromGroups(state.selectedIds, text)
            showRemoveFromGroupDialog = false
            viewModel.clearSelection()
        }
    )

    TextListInputDialog(
        data = showSetGroupDialog,
        title = stringResource(R.string.change_group),
        hint = stringResource(R.string.group_name),
        initialValue = { rssStar -> rssStar.group },
        suggestions = groups,
        onDismissRequest = { showSetGroupDialog = null },
        onConfirm = { rssStar, text ->
            viewModel.updateGroup(rssStar, text)
            showSetGroupDialog = null
        }
    )

    RuleListScaffold(
        title = stringResource(R.string.favorite),
        subtitle = state.currentGroup.ifEmpty { stringResource(R.string.all) },
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = viewModel::onSearchToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onSelectInvert = viewModel::selectInvert,
        onDeleteSelected = { viewModel.deleteSelected() },
        snackbarHostState = snackbarHostState,
        selectionSecondaryActions = listOf(
            ActionItem(
                text = stringResource(R.string.add_group),
                onClick = { showAddToGroupDialog = true }
            ),
            ActionItem(
                text = stringResource(R.string.remove_group),
                onClick = { showRemoveFromGroupDialog = true }
            )
        ),
        topBarActions = {},
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.all),
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, modifier = Modifier.size(18.dp))
                    }
                },
                onClick = {
                    viewModel.onGroupChange("")
                    dismiss()
                }
            )
            groups.forEach { group ->
                RoundDropdownMenuItem(
                    text = group,
                    leadingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Label,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        viewModel.onGroupChange(group)
                        dismiss()
                    }
                )
            }
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.delete_select_group),
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    }
                },
                onClick = {
                    viewModel.deleteGroup(state.currentGroup)
                    dismiss()
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.all),
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                    }
                },
                onClick = {
                    viewModel.deleteAll()
                    dismiss()
                }
            )
        }
    ) { paddingValues ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyMessage(
                    message = "还没有收藏订阅！",
                    isLoading = state.isLoading
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { "${it.origin}|${it.link}" }) { rssStar ->
                    val id = "${rssStar.origin}|${rssStar.link}"
                    val isSelected = state.selectedIds.contains(id)
                    SelectionItemCard(
                        title = rssStar.title,
                        subtitle = if (rssStar.group.isNotBlank()) {
                            "${rssStar.group} �?${rssStar.pubDate ?: ""}"
                        } else {
                            rssStar.pubDate
                        },
                        isSelected = isSelected,
                        inSelectionMode = state.selectedIds.isNotEmpty(),
                        onToggleSelection = {
                            viewModel.toggleSelection(rssStar)
                        },
                        leadingContent = if (!rssStar.image.isNullOrBlank()) {
                            {
                                SourceIcon(
                                    path = rssStar.image,
                                    sourceOrigin = rssStar.origin,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        } else null,
                        trailingAction = {
                            val openAction = {
                                onOpenRead(rssStar.title, rssStar.origin, rssStar.link, null)
                            }
                            SmallIconButton(
                                onClick = openAction,
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open"
                            )
                        },
                        dropdownContent = { dismiss ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.change_group),
                                onClick = {
                                    showSetGroupDialog = rssStar
                                    dismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.delete),
                                onClick = {
                                    viewModel.deleteStar(rssStar)
                                    dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

