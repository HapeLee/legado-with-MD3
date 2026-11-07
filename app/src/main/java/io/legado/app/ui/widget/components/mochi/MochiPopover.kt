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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
    maxAnchorWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    val maxAnchorWidthPx = if (maxAnchorWidth.isSpecified) {
        with(LocalDensity.current) { maxAnchorWidth.roundToPx().coerceAtLeast(1) }
    } else {
        Int.MAX_VALUE
    }
    val showPopover = expanded && anchor != null
    MochiOverlayAccessibility(showPopover)
    val backdrop = rememberMochiOverlayBackdrop(showPopover)
    CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
        // Like DropdownMenu, take no space in the caller's Box. Give Mochi the bounds of
        // that Box, including when the zero-sized child is aligned to its bottom/end.
        Layout(
            modifier = Modifier.onGloballyPositioned {
                // A measured empty anchor is valid (e.g. a settings row with no trailing
                // icon). Give Mochi a point-sized rect while keeping zero as the unmeasured state.
                val p = it.positionInParent()
                anchor = it.parentLayoutCoordinates?.size?.let { size ->
                    val width = size.width.coerceIn(1, maxAnchorWidthPx)
                    val left = (size.width - width).coerceAtLeast(0) / 2 - p.x.roundToInt()
                    IntRect(IntOffset(left, -p.y.roundToInt()), IntSize(width, size.height.coerceAtLeast(1)))
                }
            },
            content = {
                MochiPopoverBox(
                    expanded = showPopover,
                    onExpandedChange = { if (!it) onDismissRequest() },
                    width = width,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    popover = {
                        ProvideAppDensity {
                            val popupModifier = if (showPopover) {
                                mochiOverlayContentModifier(onDismissRequest).then(modifier)
                            } else {
                                modifier.clearAndSetSemantics { hideFromAccessibility() }
                            }
                            Column(popupModifier, content = content)
                        }
                    },
                    anchor = {},
                )
            },
        ) { measurables, constraints ->
            val bounds = anchor ?: IntRect.Zero
            val child = measurables.single().measure(Constraints.fixed(bounds.width, bounds.height))
            layout(constraints.minWidth, constraints.minHeight) {
                child.place(bounds.left, bounds.top)
            }
        }
    }
}
