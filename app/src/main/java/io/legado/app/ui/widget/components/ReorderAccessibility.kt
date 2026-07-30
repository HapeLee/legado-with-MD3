package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.legado.app.R

@Composable
fun Modifier.reorderAccessibility(
    index: Int,
    itemCount: Int,
    enabled: Boolean = true,
    description: String? = null,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    if (!enabled || itemCount < 2 || index !in 0 until itemCount) return this

    val moveUpLabel = stringResource(R.string.a11y_move_up)
    val moveDownLabel = stringResource(R.string.a11y_move_down)
    return semantics {
        description?.let { contentDescription = it }
        customActions = buildList {
            if (index > 0) {
                add(CustomAccessibilityAction(moveUpLabel) {
                    onMove(index, index - 1)
                    true
                })
            }
            if (index < itemCount - 1) {
                add(CustomAccessibilityAction(moveDownLabel) {
                    onMove(index, index + 1)
                    true
                })
            }
        }
    }
}
