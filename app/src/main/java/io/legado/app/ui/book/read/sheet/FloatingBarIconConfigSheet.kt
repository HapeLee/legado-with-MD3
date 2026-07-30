package io.legado.app.ui.book.read.sheet

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reorderAccessibility
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun FloatingBarIconConfigSheet(
    show: Boolean,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    ButtonIconConfigSheet(
        show = show,
        title = stringResource(R.string.title_bar_icons),
        items = items,
        customIcons = customIcons,
        onDismissRequest = onDismissRequest,
        onSaved = { onIntent(ReadBookIntent.SaveTitleBarButtonConfig(it)) },
        onSelectIcon = { id -> onIntent(ReadBookIntent.OpenTitleBarCustomIconPicker(id)) },
        onClearIcon = { id ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TitleBarCustomIcon(id, "")))
        },
    )
}

@Composable
internal fun BottomBarIconSheet(
    show: Boolean,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    ButtonIconConfigSheet(
        show = show,
        title = stringResource(R.string.config_btn),
        items = items,
        customIcons = customIcons,
        onDismissRequest = onDismissRequest,
        onSaved = { onIntent(ReadBookIntent.SaveMenuButtonConfig(it)) },
        onSelectIcon = { id -> onIntent(ReadBookIntent.OpenMenuCustomIconPicker(id)) },
        onClearIcon = { id ->
            onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.MenuCustomIcon(id, "")))
        },
    )
}

@Composable
private fun ButtonIconConfigSheet(
    show: Boolean,
    title: String,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onSaved: (List<ReadBookButtonConfigItem>) -> Unit,
    onSelectIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    val context = LocalContext.current
    var draftItems by remember(show, items) {
        mutableStateOf(buildButtonIconEntries(items, context))
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        draftItems = draftItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(draftItems, key = { _, item -> item.id }) { index, item ->
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        NormalCard(
                            elevation = elevation,
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                        ) {
                            ButtonIconConfigItem(
                                item = item,
                                customIcon = customIcons[item.id],
                                onToggleEnabled = {
                                    draftItems = draftItems.toggleButtonEnabled(item.id)
                                },
                                onSelectIcon = { onSelectIcon(item.id) },
                                onClearIcon = { onClearIcon(item.id) },
                                dragHandleModifier = Modifier
                                    .reorderAccessibility(
                                        index = index,
                                        itemCount = draftItems.size,
                                        description = stringResource(
                                            R.string.a11y_reorder_named,
                                            item.label,
                                        ),
                                    ) { from, to ->
                                        draftItems = draftItems.toMutableList().apply {
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
                onDismiss = onDismissRequest,
                onConfirm = {
                    onSaved(draftItems.map { ReadBookButtonConfigItem(it.id, it.enabled) })
                    onDismissRequest()
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.action_save),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ButtonIconConfigItem(
    item: ButtonIconEntry,
    customIcon: String?,
    onToggleEnabled: () -> Unit,
    onSelectIcon: () -> Unit,
    onClearIcon: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    val icon = item.icon
    val name = item.label
    val alpha = if (item.enabled) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp),
        ) {
            if (!customIcon.isNullOrBlank()) {
                AsyncImage(
                    model = customIcon,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    alpha = alpha,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Text(
            text = name,
            style = LegadoTheme.typography.bodyLarge,
            color = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )

        if (item.enabled) {
            // Custom icon button
            if (!customIcon.isNullOrBlank()) {
                IconButton(
                    onClick = onClearIcon,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                SmallTonalButton(
                    onClick = onSelectIcon,
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        // Visibility toggle
        IconButton(
            onClick = onToggleEnabled,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (item.enabled) {
                    Icons.Default.Visibility
                } else {
                    Icons.Default.VisibilityOff
                },
                contentDescription = stringResource(
                    if (item.enabled) R.string.disable_selection else R.string.enable_selection
                ),
                tint = if (item.enabled) {
                    LegadoTheme.colorScheme.onSurface
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }

        // Drag handle
        Box(
            modifier = dragHandleModifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class ButtonIconEntry(
    val id: String,
    val enabled: Boolean,
    val icon: ImageVector,
    val label: String,
)

private fun List<ButtonIconEntry>.toggleButtonEnabled(id: String): List<ButtonIconEntry> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this

    val target = this[index]
    val toggled = target.copy(enabled = !target.enabled)
    val remaining = toMutableList().apply { removeAt(index) }
    val insertIndex = if (toggled.enabled) {
        remaining.indexOfLast { it.enabled } + 1
    } else {
        remaining.indexOfFirst { !it.enabled }
            .takeIf { it >= 0 }
            ?: remaining.size
    }

    return remaining.apply {
        add(insertIndex.coerceIn(0, size), toggled)
    }
}

private fun buildButtonIconEntries(
    items: List<ReadBookButtonConfigItem>,
    context: Context,
): List<ButtonIconEntry> {
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    return items.mapNotNull { item ->
        val id = item.id
        infoMap[id]?.let { info ->
            ButtonIconEntry(id, item.enabled, info.icon, info.label)
        }
    }
}
