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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAnchoredPanel
import io.legado.app.ui.widget.components.text.AppText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent

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
    selectedContainerColor: Color? = null,
    contentDescription: String? = null,
    enableSwitchContentDescription: String? = null,
    editContentDescription: String? = null,
    moreContentDescription: String? = null
) {
    val isMiuix = composeEngine == ComposeEngine.Miuix
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected)
            selectedContainerColor
                ?: if (isMiuix) LegadoTheme.colorScheme.secondaryContainer else LegadoTheme.colorScheme.secondaryContainer
        else
            containerColor
                ?: if (isMiuix) LegadoTheme.colorScheme.surfaceContainer else LegadoTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "CardColor"
    )

    GlassCard(
        onClick = onToggleSelection,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null || inSelectionMode) {
                    Modifier.semantics {
                        contentDescription?.let { this.contentDescription = it }
                        if (inSelectionMode) {
                            selected = isSelected
                        }
                        role = Role.Button
                    }
                } else {
                    Modifier
                }
            ),
        cornerRadius = 12.dp,
        containerColor = animatedContainerColor,
        elevation = elevation
    ) {
        SelectionItemCardContent(
            title = title,
            subtitle = subtitle,
            supportingContent = supportingContent,
            isEnabled = isEnabled,
            isSelected = isSelected,
            inSelectionMode = inSelectionMode,
            leadingContent = leadingContent,
            onEnabledChange = onEnabledChange,
            onClickEdit = onClickEdit,
            trailingAction = trailingAction,
            dropdownContent = dropdownContent,
            enableSwitchContentDescription = enableSwitchContentDescription,
            editContentDescription = editContentDescription,
            moreContentDescription = moreContentDescription
        )
    }
}

@Composable
fun SelectionItemCardContent(
    title: String,
    subtitle: String? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    leadingContent: @Composable (() -> Unit)? = null,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onClickEdit: (() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    dropdownContent: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null,
    enableSwitchContentDescription: String? = null,
    editContentDescription: String? = null,
    moreContentDescription: String? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val defaultEditDescription = stringResource(R.string.edit)
    val defaultMoreDescription = stringResource(R.string.more_menu)

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
                        AppCheckbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            includeStateSemantics = false,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    } else {
                        leadingContent?.invoke()
                    }
                }
            }
        }

        when (composeEngine) {
            ComposeEngine.Miuix -> {
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
            }
            ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
                ListItem(
                    modifier = Modifier.weight(1f),
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
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(end = 8.dp)
        ) {
            onEnabledChange?.let {
                AdaptiveSwitch(
                    modifier = Modifier
                        .scale(0.8f)
                        .then(
                            enableSwitchContentDescription?.let { description ->
                                Modifier.semantics {
                                    contentDescription = description
                                }
                            } ?: Modifier
                        ),
                    checked = isEnabled,
                    onCheckedChange = it
                )
            }

            if (onClickEdit != null) {
                SmallPlainButton(
                    onClick = onClickEdit,
                    icon = AppIcons.Edit,
                    contentDescription = editContentDescription ?: defaultEditDescription
                )
            }

            if (trailingAction != null) {
                trailingAction()
            }

            if (dropdownContent != null) {
                // 弹层分派收编到 AppAnchoredPanel 的兼容路径：⋮ 按钮仍由卡片渲染
                // （外观与无障碍名称不变）；content 与公共插槽同构，6 个调用点零改动，
                // 菜单项仍由各调用点自己决定是否 dismiss()。
                AppAnchoredPanel(
                    expanded = showMenu,
                    onExpandedChange = { showMenu = it },
                    externalTrigger = true,
                    trigger = {
                        SmallPlainButton(
                            onClick = { open() },
                            icon = AppIcons.MoreVert,
                            contentDescription = moreContentDescription ?: defaultMoreDescription
                        )
                    },
                    content = dropdownContent,
                )
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
    selectedContainerColor: Color? = null,
    contentDescription: String? = null,
    enableSwitchContentDescription: String? = null,
    editContentDescription: String? = null,
    moreContentDescription: String? = null,
    reorderIndex: Int? = null,
    reorderItemCount: Int = 0,
    onMoveItem: ((from: Int, to: Int) -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val reorderAccessibilityModifier = if (reorderIndex != null && onMoveItem != null) {
        Modifier.reorderAccessibility(
            index = reorderIndex,
            itemCount = reorderItemCount,
            enabled = canReorder && !inSelectionMode,
            onMove = onMoveItem,
        )
    } else Modifier
    val canDrag = canReorder && !inSelectionMode

    ReorderableItem(state, key = key) { isDragging ->
        // 句柄捕获 ReorderableItem scope，排序状态或条目标识变化时必须随之更新。
        val dragHandleModifier = remember(state, key, canDrag, hapticFeedback) {
            if (canDrag) {
                Modifier.longPressDraggableHandle(
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragStopped = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                )
            } else Modifier
        }
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
            contentDescription = contentDescription,
            enableSwitchContentDescription = enableSwitchContentDescription,
            editContentDescription = editContentDescription,
            moreContentDescription = moreContentDescription,
            modifier = modifier
                .then(reorderAccessibilityModifier)
                .zIndex(if (isDragging) 1f else 0f)
                .then(dragHandleModifier)
        )
    }
}
