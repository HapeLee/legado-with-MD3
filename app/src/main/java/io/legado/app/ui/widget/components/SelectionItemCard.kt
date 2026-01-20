package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

@Composable
fun SelectionItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onClickEdit: (() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        onClick = onToggleSelection,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        ListItem(
            modifier = Modifier.animateContentSize(),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                AnimatedVisibility(
                    visible = inSelectionMode,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onEnabledChange?.let {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = it
                        )
                    }

                    if (onClickEdit != null) {
                        IconButton(onClick = onClickEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }

                    if (trailingAction != null) {
                        trailingAction()
                    }

                    if (dropdownContent != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                dropdownContent { showMenu = false }
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.ReorderableSelectionItem(
    state: ReorderableLazyListState,
    key: Any,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    canReorder: Boolean = true,
    onToggleSelection: () -> Unit = {},
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onClickEdit: (() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current

    ReorderableItem(state, key = key) { isDragging ->
        val elevation by animateDpAsState(
            targetValue = if (isDragging) 8.dp else 0.dp,
            label = "DragElevation"
        )

        SelectionItemCard(
            title = title,
            subtitle = subtitle,
            isEnabled = isEnabled,
            isSelected = isSelected,
            inSelectionMode = inSelectionMode,
            onToggleSelection = onToggleSelection,
            onEnabledChange = onEnabledChange,
            onClickEdit = onClickEdit,
            trailingAction = trailingAction,
            dropdownContent = dropdownContent,
            modifier = modifier
                .zIndex(if (isDragging) 1f else 0f)
                .shadow(elevation, MaterialTheme.shapes.medium)
                .then(
                    if (canReorder && !inSelectionMode) {
                        Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        )
                    } else Modifier
                )
                .animateItem()
        )
    }
}