package io.legado.app.ui.widget.components.mochi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.rememberMochiOverlayBackdrop
import ui.feice.mochi.basic.MochiPopoverBox
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import kotlin.math.roundToInt

/** Adapts DropdownMenu's separate trigger/content contract to Mochi's anchored popover. */
@Composable
internal fun AppMochiPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MochiOverlayAccessibility(expanded)
    val backdrop = rememberMochiOverlayBackdrop(expanded)
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
        // Like DropdownMenu, take no space in the caller's Box. Give Mochi the bounds of
        // that Box, including when the zero-sized child is aligned to its bottom/end.
        Layout(
            modifier = Modifier.onGloballyPositioned {
                anchorSize = it.parentLayoutCoordinates?.size ?: IntSize.Zero
                val p = it.positionInParent()
                anchorOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            },
            content = {
                MochiPopoverBox(
                    expanded = expanded && anchorSize != IntSize.Zero,
                    onExpandedChange = { if (!it) onDismissRequest() },
                    width = width,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    popover = { ProvideAppDensity {
                        Column(mochiOverlayContentModifier(onDismissRequest).then(modifier), content = content)
                    } },
                    anchor = {},
                )
            },
        ) { measurables, constraints ->
            val child = measurables.single().measure(Constraints.fixed(anchorSize.width, anchorSize.height))
            layout(constraints.minWidth, constraints.minHeight) {
                child.place(-anchorOffset.x, -anchorOffset.y)
            }
        }
    }
}
