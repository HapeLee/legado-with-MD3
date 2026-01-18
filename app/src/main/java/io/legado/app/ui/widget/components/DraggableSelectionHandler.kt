package io.legado.app.ui.widget.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun <T, ID> DraggableSelectionHandler(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedIds: Set<ID>,
    onSelectionChange: (Set<ID>) -> Unit,
    idProvider: (T) -> ID,
    haptic: HapticFeedback = LocalHapticFeedback.current,
) {
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    var isAddingMode by remember { mutableStateOf(true) }
    var lastProcessedIndex by remember { mutableIntStateOf(-1) }

    fun findItemAtOffset(offsetY: Float): Pair<Int, T>? {
        val itemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offsetY >= item.offset && offsetY <= item.offset + item.size
            }

        return itemInfo?.let { info ->
            items.getOrNull(info.index)?.let { item ->
                info.index to item
            }
        }
    }

    fun applySelection(id: ID, add: Boolean) {
        val current = latestSelectedIds
        onSelectionChange(
            if (add) current + id else current - id
        )
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            coroutineScope {
                launch {
                    detectTapGestures(
                        onTap = { offset ->
                            findItemAtOffset(offset.y)?.let { (_, item) ->
                                val id = idProvider(item)
                                applySelection(id, !latestSelectedIds.contains(id))
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }

                launch {
                    detectDragGestures(
                        onDragStart = { offset ->
                            findItemAtOffset(offset.y)?.let { (index, item) ->
                                lastProcessedIndex = index
                                val id = idProvider(item)
                                isAddingMode = !latestSelectedIds.contains(id)
                                applySelection(id, isAddingMode)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, _ ->
                            findItemAtOffset(change.position.y)?.let { (index, item) ->
                                if (index != lastProcessedIndex) {
                                    lastProcessedIndex = index
                                    applySelection(idProvider(item), isAddingMode)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        onDragEnd = { lastProcessedIndex = -1 },
                        onDragCancel = { lastProcessedIndex = -1 }
                    )
                }
            }
        }
    )
}