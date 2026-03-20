package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.book.group.GroupEditContent
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.utils.move
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageSheet(
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel(),
    bookshelfViewModel: BookshelfViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val groups by bookshelfViewModel.allGroupsFlow.collectAsState()
    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    var listData by remember { mutableStateOf(groups) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        listData = listData.toMutableList().apply {
            move(from.index, to.index)
        }
    }

    LaunchedEffect(groups) {
        if (!reorderableState.isAnyItemDragging) {
            listData = groups
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val updatedGroups = listData.mapIndexed { index, group ->
                group.copy(order = index)
            }
            viewModel.upGroup(*updatedGroups.toTypedArray())
        }
    }

    GlassModalBottomSheet(onDismissRequest = onDismissRequest) {
        AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
            },
            label = "GroupManageState"
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
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
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
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listData, key = { it.groupId }) { group ->
                            val manageNameInfo = remember(group) { group.getManageName(context) }
                            ReorderableSelectionItem(
                                state = reorderableState,
                                key = group.groupId,
                                title = group.groupName.ifBlank { manageNameInfo.suffix.orEmpty() },
                                subtitle = if (group.groupName.isNotBlank()) manageNameInfo.suffix else null,
                                isEnabled = group.show,
                                containerColor = MaterialTheme.colorScheme.surface,
                                onEnabledChange = { isChecked ->
                                    viewModel.upGroup(group.copy(show = isChecked))
                                },
                                onClickEdit = {
                                    editingGroup = group
                                    isEditing = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
