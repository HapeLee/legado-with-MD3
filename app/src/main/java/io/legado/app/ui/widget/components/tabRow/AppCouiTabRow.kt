package io.legado.app.ui.widget.components.tabRow

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.chip.ChipTab
import com.itos.coui.chip.CouiChipTabDefaults
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.coui.LocalAppCouiTopBarHost
import io.legado.app.ui.widget.components.coui.rememberCouiCapsuleRenderer
import io.legado.app.ui.widget.components.text.AppText

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun AppCouiTabRow(
    titles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
    scrollable: Boolean,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    if (titles.isEmpty()) return
    val selectedIndex = selectedTabIndex.coerceIn(titles.indices)
    val backdrop = currentCouiTabBackdrop()
    val rowModifier = modifier.fillMaxWidth().selectableGroup()
    val spacing = Arrangement.spacedBy(8.dp)

    // Keep fixed widths and repeat taps, and reveal an externally selected page even on first layout.
    if (scrollable) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        LaunchedEffect(selectedIndex, titles.size) {
            val layout = listState.layoutInfo
            val selectedVisible = layout.visibleItemsInfo.any { item ->
                item.index == selectedIndex && item.offset >= layout.viewportStartOffset &&
                    item.offset + item.size <= layout.viewportEndOffset
            }
            if (!selectedVisible) listState.animateScrollToItem(selectedIndex)
        }
        LazyRow(
            modifier = rowModifier.fadingEdge(listState, gradientWidth = 16.dp),
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = spacing,
        ) {
            items(titles.size) { index ->
                AppCouiTab(titles[index], selectedIndex == index, { onTabSelected(index) }, backdrop)
            }
        }
    } else {
        Row(rowModifier.padding(contentPadding), horizontalArrangement = spacing) {
            titles.forEachIndexed { index, title ->
                AppCouiTab(title, selectedIndex == index, { onTabSelected(index) }, backdrop, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun currentCouiTabBackdrop(): CouiBackdrop? {
    val view = LocalView.current
    return LocalAppCouiTopBarHost.current
        ?.takeIf { it.view === view && LegadoTheme.colorScheme.background.alpha == 1f }
        ?.backdrop
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun AppCouiTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: CouiBackdrop?,
    modifier: Modifier = Modifier,
) {
    val renderer = if (backdrop != null) rememberCouiCapsuleRenderer() else null
    if (renderer != null && backdrop != null) {
        ChipTab(modifier, selected, onClick, title, backdrop, renderer = renderer)
    } else {
        val colors = CouiTheme.colorScheme
        val containerColor by animateColorAsState(
            if (selected) colors.accent.copy(alpha = 0.16f) else colors.outlineVariant.copy(alpha = 0.1f),
            label = "CouiTabContainer",
        )
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier.widthIn(min = 52.dp, max = 224.dp).height(32.dp)
                .clip(CouiCapsuleShape)
                .background(containerColor)
                .couiButtonFeedback(interactionSource = interactionSource)
                .selectable(selected, interactionSource, indication = null, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = CouiChipTabDefaults.textStyle
            AppText(
                text = title,
                color = colors.onSurface,
                style = style.copy(fontFamily = CouiTheme.typography.chipLabelFontFamily ?: style.fontFamily),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
