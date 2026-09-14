package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.a11y_move_down
import io.legado.app.core.designsystem.res.a11y_move_up
import org.jetbrains.compose.resources.stringResource
@Composable
fun Modifier.reorderAccessibility(
    index: Int,
    itemCount: Int,
    enabled: Boolean = true,
    description: String? = null,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    if (!enabled || itemCount < 2 || index !in 0 until itemCount) return this

    val moveUpLabel = stringResource(Res.string.a11y_move_up)
    val moveDownLabel = stringResource(Res.string.a11y_move_down)
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
