package io.legado.app.ui.widget.components.mochi

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabBarDefaults
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun AppMochiNavigationBar(
    items: ImmutableList<MochiTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    alwaysShowLabel: Boolean = true,
    floating: Boolean = false,
    glass: Boolean = true,
) {
    MochiTabBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        onReselect = onSelect,
        modifier = modifier.onKeyEvent { event ->
            val target = when (event.key) {
                Key.DirectionLeft -> (selectedIndex - 1).coerceAtLeast(0)
                Key.DirectionRight -> (selectedIndex + 1).coerceAtMost(items.lastIndex)
                Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter -> selectedIndex
                else -> return@onKeyEvent false
            }
            if (event.type == KeyEventType.KeyUp && target in items.indices) onSelect(target)
            true
        }.focusable(),
        behavior = MochiTabBarDefaults.behavior(
            itemWidth = if (floating) 76.dp else 92.dp,
            showLabels = showLabel,
            selectedLabelOnly = showLabel && !alwaysShowLabel,
        ),
        windowInsets = if (floating) WindowInsets(0) else MochiTabBarDefaults.windowInsets,
        material = if (glass) MochiTheme.materials.bar else null,
    )
}
