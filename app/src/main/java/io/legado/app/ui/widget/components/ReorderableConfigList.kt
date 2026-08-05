package io.legado.app.ui.widget.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 可排序的按钮/图标配置列表：卡片行 + 拖拽排序 + 可见性开关，可选自定义图标。
 * 阅读菜单的更多菜单、底栏、悬浮栏图标配置页共用。
 *
 * @param reorderEnabledFirst 切换启用状态时是否把启用的条目排到前面
 * @param onSelectIcon / @param onClearIcon 提供时启用自定义图标编辑（非空才显示）
 */
@Immutable
data class ConfigListEntry(
    val id: String,
    val enabled: Boolean,
    val icon: ImageVector? = null,
    val label: String,
    val subtitle: String? = null,
)

@Composable
fun ReorderableConfigList(
    initialEntries: List<ConfigListEntry>,
    modifier: Modifier = Modifier,
    customIcons: Map<String, String> = emptyMap(),
    onSelectIcon: ((String) -> Unit)? = null,
    onClearIcon: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (List<ConfigListEntry>) -> Unit,
    reorderEnabledFirst: Boolean = false,
) {
    var entries by remember(initialEntries) { mutableStateOf(initialEntries) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        entries = entries.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            itemsIndexed(entries, key = { _, item -> item.id }) { index, entry ->
                ReorderableItem(reorderState, key = entry.id) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 4.dp else 0.dp)
                    NormalCard(
                        elevation = elevation,
                        cornerRadius = 12.dp,
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ConfigListEntryRow(
                            entry = entry,
                            customIcon = customIcons[entry.id],
                            onToggleEnabled = {
                                entries = entries.toggleEnabled(entry.id, reorderEnabledFirst)
                            },
                            onSelectIcon = onSelectIcon?.let { { it(entry.id) } },
                            onClearIcon = onClearIcon?.let { { it(entry.id) } },
                            dragHandleModifier = Modifier
                                .reorderAccessibility(
                                    index = index,
                                    itemCount = entries.size,
                                    description = stringResource(
                                        R.string.a11y_reorder_named,
                                        entry.label,
                                    ),
                                ) { from, to ->
                                    entries = entries.toMutableList().apply {
                                        add(to, removeAt(from))
                                    }
                                }
                                .draggableHandle(),
                        )
                    }
                }
            }
        }

        ConfirmDismissButtonsRow(
            onDismiss = onDismiss,
            onConfirm = { onConfirm(entries) },
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.action_save),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun ConfigListEntryRow(
    entry: ConfigListEntry,
    customIcon: Any? = null,
    onToggleEnabled: (() -> Unit)? = null,
    onSelectIcon: (() -> Unit)? = null,
    onClearIcon: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    dragHandleModifier: Modifier = Modifier,
) {
    val alpha = if (entry.enabled) 1f else 0.38f
    val hasCustomIcon = customIcon?.let { if (it is String) it.isNotBlank() else true } ?: false

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (hasCustomIcon || entry.icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp),
            ) {
                if (hasCustomIcon) {
                    AsyncImage(
                        model = customIcon,
                        contentDescription = entry.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        alpha = alpha,
                    )
                } else {
                    entry.icon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = entry.label,
                            tint = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = entry.label,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!entry.subtitle.isNullOrBlank()) {
                Text(
                    text = entry.subtitle,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            // 自定义图标编辑（仅在宿主提供回调时显示）
            if (entry.enabled && onSelectIcon != null && onClearIcon != null) {
                if (hasCustomIcon) {
                    SmallPlainButton(
                        onClick = onClearIcon,
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete)
                    )
                } else {
                    SmallTonalButton(
                        onClick = onSelectIcon,
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                    )
                }
            }

            onToggleEnabled?.let { toggle ->
                SmallOutlinedButton(
                    onClick = toggle,
                    icon = if (entry.enabled) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = stringResource(
                        if (entry.enabled) R.string.disable_selection else R.string.enable_selection
                    ),
                )
            }
        }

        Box(
            modifier = dragHandleModifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun List<ConfigListEntry>.toggleEnabled(
    id: String,
    reorderEnabledFirst: Boolean,
): List<ConfigListEntry> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this
    val toggled = this[index].copy(enabled = !this[index].enabled)
    if (!reorderEnabledFirst) {
        return toMutableList().apply { set(index, toggled) }
    }
    val remaining = toMutableList().apply { removeAt(index) }
    val insertIndex = if (toggled.enabled) {
        remaining.indexOfLast { it.enabled } + 1
    } else {
        remaining.indexOfFirst { !it.enabled }
            .takeIf { it >= 0 }
            ?: remaining.size
    }
    return remaining.apply { add(insertIndex.coerceIn(0, size), toggled) }
}
