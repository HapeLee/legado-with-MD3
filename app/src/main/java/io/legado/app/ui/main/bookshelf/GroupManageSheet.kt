package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.book.group.GroupDeleteAction
import io.legado.app.ui.book.group.GroupEditContent
import io.legado.app.ui.book.group.GroupResetCoverAction
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.main.bookshelf.autoGroup.AiAutoGroupSheet
import io.legado.app.ui.tagGroupRule.TagGroupRuleEditSheet
import io.legado.app.ui.tagGroupRule.TagGroupRuleIntent
import io.legado.app.ui.tagGroupRule.TagGroupRuleViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

/** 分组卡片最小宽度，常规手机为两列，更宽的屏幕按可用宽度自动增加列数。 */
private val GroupCardMinWidth = 160.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel(),
    bookshelfViewModel: BookshelfViewModel = koinViewModel(),
    tagGroupRuleViewModel: TagGroupRuleViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val groups by bookshelfViewModel.allGroupsFlow.collectAsState()

    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var coverPath by remember(editingGroup) { mutableStateOf(editingGroup?.cover) }

    var editingTagRule by remember { mutableStateOf<io.legado.app.data.entities.TagGroupRule?>(null) }
    var showTagRuleEdit by remember { mutableStateOf(false) }
    var showAiAutoGroup by remember { mutableStateOf(false) }
    var aiAutoGroupSessionKey by rememberSaveable { mutableStateOf(0L) }

    var editingGroupTagRule by remember { mutableStateOf<io.legado.app.data.entities.TagGroupRule?>(null) }

    LaunchedEffect(editingGroup) {
        editingGroupTagRule = editingGroup?.let { group ->
            viewModel.getTagGroupRule(group.groupName)
        }
    }

    var listData by remember { mutableStateOf(groups) }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
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

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (!isEditing) stringResource(R.string.group_manage) else stringResource(R.string.group_edit),
        startAction = editingGroup?.takeIf {
            isEditing && (it.groupId > 0 || it.groupId == Long.MIN_VALUE)
        }?.let { group ->
            {
                GroupDeleteAction(
                    group = group,
                    onDismissRequest = {
                        editingGroup = null
                        isEditing = false
                    },
                    viewModel = viewModel
                )
            }
        },
        endAction = {
            if (!isEditing) {
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    MediumTonalButton(
                        onClick = { showMenu = true },
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                    )
                    RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.group_add),
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMenu = false
                                editingGroup = null
                                coverPath = null
                                isEditing = true
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.ai_auto_group),
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            onClick = {
                                showMenu = false
                                aiAutoGroupSessionKey += 1L
                                showAiAutoGroup = true
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.tag_group_add_rule),
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMenu = false
                                editingTagRule = null
                                showTagRuleEdit = true
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.tag_group_sync),
                            leadingIcon = { Icon(Icons.Default.Sync, null) },
                            onClick = {
                                showMenu = false
                                tagGroupRuleViewModel.onIntent(TagGroupRuleIntent.SyncGroups)
                            }
                        )
                    }
                }
            } else {
                GroupResetCoverAction(
                    group = editingGroup,
                    onCoverPathChange = { coverPath = it },
                    viewModel = viewModel
                )
            }
        }
    ) {
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
                    onDismissRequest = {
                        editingGroup = null
                        isEditing = false
                    },
                    coverPath = coverPath,
                    onCoverPathChange = { coverPath = it },
                    tagGroupRule = editingGroupTagRule,
                    viewModel = viewModel
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(GroupCardMinWidth),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(listData, key = { _, group -> group.groupId }) { index, group ->
                        val manageNameInfo = remember(group) { group.getManageName(context) }
                        GroupManageGridItem(
                            state = reorderableState,
                            key = group.groupId,
                            reorderIndex = index,
                            reorderItemCount = listData.size,
                            onMoveItem = { from, to ->
                                listData = listData.toMutableList().apply { move(from, to) }
                                val updatedGroups = listData.mapIndexed { i, item ->
                                    item.copy(order = i)
                                }
                                viewModel.upGroup(*updatedGroups.toTypedArray())
                            },
                            title = group.groupName.ifBlank { manageNameInfo.suffix.orEmpty() },
                            subtitle = if (group.groupName.isNotBlank()) manageNameInfo.suffix else null,
                            isEnabled = group.show,
                            onEnabledChange = { isChecked ->
                                viewModel.upGroup(group.copy(show = isChecked))
                            },
                            onClickEdit = {
                                editingGroup = group
                                coverPath = group.cover
                                isEditing = true
                            }
                        )
                    }
                }
            }
        }

        TagGroupRuleEditSheet(
            show = showTagRuleEdit,
            rule = editingTagRule,
            onDismissRequest = {
                showTagRuleEdit = false
                editingTagRule = null
            },
            onSave = { rule ->
                val isNew = editingTagRule == null
                tagGroupRuleViewModel.onIntent(
                    TagGroupRuleIntent.SaveRule(rule, isNew)
                )
                showTagRuleEdit = false
                editingTagRule = null
            },
            onCopy = { rule ->
                tagGroupRuleViewModel.onIntent(TagGroupRuleIntent.CopyRule(rule))
            },
            onPaste = { tagGroupRuleViewModel.pasteRule() }
        )

        AiAutoGroupSheet(
            show = showAiAutoGroup,
            sessionKey = aiAutoGroupSessionKey,
            onDismissRequest = { showAiAutoGroup = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.GroupManageGridItem(
    state: ReorderableLazyGridState,
    key: Any,
    title: String,
    subtitle: String?,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClickEdit: () -> Unit,
    reorderIndex: Int,
    reorderItemCount: Int,
    onMoveItem: (from: Int, to: Int) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    ReorderableItem(state = state, key = key) { isDragging ->
        val elevation by animateDpAsState(
            targetValue = if (isDragging) 8.dp else 0.dp,
            label = "DragElevation"
        )
        GlassCard(
            cornerRadius = 12.dp,
            containerColor = LegadoTheme.colorScheme.onSheetContent,
            elevation = elevation,
            modifier = Modifier
                .reorderAccessibility(
                    index = reorderIndex,
                    itemCount = reorderItemCount,
                    onMove = onMoveItem
                )
                .zIndex(if (isDragging) 1f else 0f)
                .longPressDraggableHandle(
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragStopped = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                )
                .animateItem()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column() {
                    Row(
                        modifier = Modifier.clickable(
                            onClick = onClickEdit
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AppText(
                            text = title,
                            style = LegadoTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        AppIcon(
                            modifier = Modifier
                                .size(12.dp),
                            imageVector = AppIcons.Edit,
                            contentDescription = stringResource(R.string.edit)
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        AppText(
                            text = subtitle,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TinySwitch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

        }
    }
}
