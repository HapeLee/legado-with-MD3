package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.book.group.GroupEditContent
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SettingItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageSheet(
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel(),
    bookshelfViewModel: BookshelfViewModel = koinViewModel()
) {
    val groups by bookshelfViewModel.groupsFlow.collectAsState(initial = emptyList())
    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    GlassModalBottomSheet(onDismissRequest = onDismissRequest) {
        AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                fadeIn().togetherWith(fadeOut())
            },
            label = "GroupManageTransition"
        ) { editing ->
            if (editing) {
                GroupEditContent(
                    group = editingGroup,
                    onDismissRequest = { isEditing = false },
                    viewModel = viewModel
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.group_manage),
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = {
                            editingGroup = null
                            isEditing = true
                        }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups, key = { it.groupId }) { group ->
                            GroupItem(
                                group = group,
                                onEdit = {
                                    editingGroup = group
                                    isEditing = true
                                },
                                onToggle = { isChecked ->
                                    viewModel.upGroup(group.copy(show = isChecked))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupItem(
    group: BookGroup,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    SettingItem(
        title = group.groupName,
        onClick = onEdit,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                Switch(
                    checked = group.show,
                    onCheckedChange = onToggle
                )
            }
        }
    )
}
