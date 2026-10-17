package io.legado.app.ui.widget.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumOutlinedButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageBottomSheet(
    show: Boolean,
    groups: List<String>,
    onDismissRequest: () -> Unit,
    onUpdateGroup: (oldGroup: String, newGroup: String) -> Unit,
    onDeleteGroup: (group: String) -> Unit,
    onDeleteGroups: ((groups: List<String>) -> Unit)? = null,
) {
    var groupToDelete by remember { mutableStateOf<String?>(null) }
    var inSelectionMode by remember { mutableStateOf(false) }
    var selectedGroups by remember { mutableStateOf(emptySet<String>()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (!show) {
            inSelectionMode = false
            selectedGroups = emptySet()
            groupToDelete = null
            showBatchDeleteConfirm = false
        }
    }

    LaunchedEffect(groups) {
        if (groups.isEmpty()) {
            inSelectionMode = false
            selectedGroups = emptySet()
        } else {
            selectedGroups = selectedGroups.intersect(groups.toSet())
        }
    }

    BackHandler(enabled = show && inSelectionMode) {
        inSelectionMode = false
        selectedGroups = emptySet()
    }

    AppAlertDialog(
        show = groupToDelete != null,
        onDismissRequest = { groupToDelete = null },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            groupToDelete?.let(onDeleteGroup)
            groupToDelete = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { groupToDelete = null },
    )

    AppAlertDialog(
        show = showBatchDeleteConfirm,
        onDismissRequest = { showBatchDeleteConfirm = false },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            val toDelete = selectedGroups.toList()
            showBatchDeleteConfirm = false
            selectedGroups = emptySet()
            inSelectionMode = false
            if (onDeleteGroups != null) {
                onDeleteGroups(toDelete)
            } else {
                toDelete.forEach(onDeleteGroup)
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showBatchDeleteConfirm = false },
    )

    val titleText = if (inSelectionMode) {
        stringResource(R.string.selected_count, selectedGroups.size)
    } else {
        stringResource(R.string.group_manage)
    }

    val startAction: @Composable (() -> Unit)? = if (inSelectionMode) {
        {
            MediumTonalButton(
                onClick = {
                    inSelectionMode = false
                    selectedGroups = emptySet()
                },
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel)
            )
        }
    } else null

    val endAction: @Composable (() -> Unit)? = if (inSelectionMode) {
        {
            MediumTonalButton(
                onClick = {
                    selectedGroups = if (selectedGroups.size == groups.size) {
                        emptySet()
                    } else {
                        groups.toSet()
                    }
                },
                icon = Icons.Default.SelectAll,
                contentDescription = if (selectedGroups.size == groups.size && groups.isNotEmpty()) {
                    stringResource(R.string.deselect_all)
                } else {
                    stringResource(R.string.select_all)
                }
            )
        }
    } else if (groups.isNotEmpty()) {
        {
            MediumTonalButton(
                onClick = { inSelectionMode = true },
                icon = Icons.Default.Checklist,
                contentDescription = stringResource(R.string.selection_mode)
            )
        }
    } else null

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            inSelectionMode = false
            selectedGroups = emptySet()
            onDismissRequest()
        },
        title = titleText,
        startAction = startAction,
        endAction = endAction,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups, key = { it }) { group ->
                GroupItem(
                    group = group,
                    inSelectionMode = inSelectionMode,
                    isSelected = selectedGroups.contains(group),
                    onToggleSelect = {
                        selectedGroups = if (selectedGroups.contains(group)) {
                            selectedGroups - group
                        } else {
                            selectedGroups + group
                        }
                    },
                    onLongClick = {
                        if (!inSelectionMode) {
                            inSelectionMode = true
                            selectedGroups = setOf(group)
                        }
                    },
                    onUpdateGroup = onUpdateGroup,
                    onDeleteGroup = { groupToDelete = it }
                )
            }
        }

        AnimatedVisibility(
            visible = inSelectionMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediumOutlinedButton(
                    onClick = {
                        selectedGroups = if (selectedGroups.size == groups.size) {
                            emptySet()
                        } else {
                            groups.toSet()
                        }
                    },
                    text = if (selectedGroups.size == groups.size && groups.isNotEmpty()) {
                        stringResource(R.string.deselect_all)
                    } else {
                        stringResource(R.string.select_all)
                    },
                    icon = Icons.Default.SelectAll,
                    modifier = Modifier.weight(1f)
                )
                MediumTonalButton(
                    onClick = { showBatchDeleteConfirm = true },
                    enabled = selectedGroups.isNotEmpty(),
                    text = if (selectedGroups.isNotEmpty()) {
                        "${stringResource(R.string.delete_selected)} (${selectedGroups.size})"
                    } else {
                        stringResource(R.string.delete_selected)
                    },
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GroupItem(
    group: String,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onUpdateGroup: (oldGroup: String, newGroup: String) -> Unit,
    onDeleteGroup: (group: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = group)

    LaunchedEffect(inSelectionMode) {
        if (inSelectionMode) {
            expanded = false
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, group)
            }
        }
    }

    SettingItem(
        title = group,
        expanded = if (inSelectionMode) false else expanded,
        cornerRadius = 12.dp,
        color = if (inSelectionMode && isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        onClick = if (inSelectionMode) onToggleSelect else null,
        onLongClick = onLongClick,
        onExpandChange = if (inSelectionMode) null else {
            { expanded = it }
        },
        semanticRole = if (inSelectionMode) Role.Checkbox else null,
        semanticToggleState = if (inSelectionMode) isSelected else null,
        trailingContent = {
            if (inSelectionMode) {
                AppCheckbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    includeStateSemantics = false,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            } else {
                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.edit)
                        )
                    }
                    IconButton(onClick = { onDeleteGroup(group) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete)
                        )
                    }
                }
            }
        },
        expandContent = if (inSelectionMode) null else {
            {
                AppTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    label = stringResource(R.string.edit),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = 4.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    onKeyboardAction = {
                        onUpdateGroup(group, state.text.toString())
                        expanded = false
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SmallPlainButton(
                        onClick = {
                            onUpdateGroup(group, state.text.toString())
                            expanded = false
                        },
                        icon = Icons.Default.Check,
                        text = stringResource(id = R.string.ok)
                    )
                }
            }
        }
    )
}
