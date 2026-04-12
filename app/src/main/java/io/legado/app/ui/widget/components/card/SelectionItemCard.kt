package io.legado.app.ui.widget.components.card

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.text.AppText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SelectionItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    elevation: Dp = 0.dp,
    onToggleSelection: () -> Unit = {},
    leadingContent: @Composable (() -> Unit)? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onClickEdit: (() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null,
    containerColor: Color? = null,
    selectedContainerColor: Color? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val composeEngine = ThemeResolver.isMiuixEngine(composeEngine)
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected)
            selectedContainerColor
                ?: if (composeEngine) LegadoTheme.colorScheme.secondaryContainer else LegadoTheme.colorScheme.secondaryContainer
        else
            containerColor
                ?: if (composeEngine) LegadoTheme.colorScheme.surfaceContainer else LegadoTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        onClick = onToggleSelection,
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = animatedContainerColor,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = inSelectionMode || leadingContent != null
            ) {
                Box(
                    modifier = Modifier.padding(start = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = inSelectionMode,
                        label = "LeadingContent"
                    ) { selectionMode ->
                        if (selectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                        } else {
                            leadingContent?.invoke()
                        }
                    }
                }
            }

            if (composeEngine) {
                BasicComponent(
                    modifier = Modifier.weight(1f)
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    when {
                        supportingContent != null -> supportingContent()
                        !subtitle.isNullOrBlank() -> {
                            AppText(
                                text = subtitle,
                                style = LegadoTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                ListItem(
                    modifier = Modifier.weight(1f),
                    headlineContent = {
                        AppText(
                            text = title,
                            style = LegadoTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = when {
                        supportingContent != null -> supportingContent
                        !subtitle.isNullOrBlank() -> {
                            {
                                AppText(
                                    text = subtitle,
                                    style = LegadoTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        else -> null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
            ) {
                onEnabledChange?.let {
                    AdaptiveSwitch(
                        modifier = Modifier.scale(0.8f),
                        checked = isEnabled,
                        onCheckedChange = it
                    )
                }

                if (onClickEdit != null) {
                    SmallIconButton(
                        onClick = onClickEdit,
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit"
                    )
                }

                if (trailingAction != null) {
                    trailingAction()
                }

                if (dropdownContent != null) {
                    Box {
                        SmallIconButton(
                            onClick = { showMenu = true },
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            dropdownContent { showMenu = false }
                        }
                    }
                }
            }
        }
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
    supportingContent: @Composable (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    canReorder: Boolean = true,
    onToggleSelection: () -> Unit = {},
    leadingContent: @Composable (() -> Unit)? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onClickEdit: (() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null,
    containerColor: Color? = null,
    selectedContainerColor: Color? = null
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
            supportingContent = supportingContent,
            isEnabled = isEnabled,
            isSelected = isSelected,
            inSelectionMode = inSelectionMode,
            elevation = elevation,
            onToggleSelection = onToggleSelection,
            leadingContent = leadingContent,
            onEnabledChange = onEnabledChange,
            onClickEdit = onClickEdit,
            trailingAction = trailingAction,
            dropdownContent = dropdownContent,
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
            modifier = modifier
                .zIndex(if (isDragging) 1f else 0f)
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

