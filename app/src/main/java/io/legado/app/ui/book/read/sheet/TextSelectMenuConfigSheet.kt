package io.legado.app.ui.book.read.sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.book.read.ActionMenuItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSelectMenuConfigSheet(
    show: Boolean,
    items: List<ActionMenuItem>,
    onDismissRequest: () -> Unit,
    onSaved: (List<ActionMenuItem>) -> Unit
) {
    var draftItems by remember(show, items) {
        mutableStateOf(items)
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
        title = stringResource(R.string.edit_select_menu),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                items(draftItems, key = { it.uniqueId }) { item ->
                    ReorderableItem(reorderableState, key = item.uniqueId) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                        NormalCard(
                            elevation = elevation,
                            cornerRadius = 12.dp,
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                        ) {
                            TextSelectMenuConfigItemRow(
                                item = item,
                                onToggleEnabled = {
                                    draftItems = draftItems.map {
                                        if (it.uniqueId == item.uniqueId) {
                                            it.copy(enabled = !it.enabled)
                                        } else it
                                    }
                                },
                                dragHandleModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }

            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = {
                    onSaved(draftItems)
                    onDismissRequest()
                },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.action_save),
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun TextSelectMenuConfigItemRow(
    item: ActionMenuItem,
    onToggleEnabled: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val alpha = if (item.enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = 12.dp),
    ) {
        if (item.iconDrawable != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp),
            ) {
                AsyncImage(
                    model = item.iconDrawable,
                    contentDescription = item.title,
                    alpha = alpha,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AppText(
            text = item.title,
            fontSize = 14.sp,
            color = LegadoTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            maxLines = 1
        )

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
                contentDescription = null,
                tint = if (item.enabled) {
                    LegadoTheme.colorScheme.onSurface
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }

        Box(
            modifier = dragHandleModifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
