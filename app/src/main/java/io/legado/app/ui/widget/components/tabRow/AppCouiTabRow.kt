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
import androidx.compose.foundation.lazy.items
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

private val CouiTabSpacing = 8.dp
private val CouiTabFadeWidth = 16.dp

/**
 * COUI 胶囊的相对层级投影画在主体边界之外，宿主必须为它保留绘制空间，不能沿主体边界裁剪
 * （见 modules/coui/docs/effects/shadow.md）。
 *
 * foundation 的 `clipScrollableContainer` 已用 `HorizontalScrollableClipShape` 把 `LazyRow`
 * 的纵向 outline 上下各扩 `MaxSupportedElevation = 30.dp`，专门给滚动子项的投影留空间；
 * 真正把阴影切平的是直接挂在 `LazyRow` 上、尺寸等于节点边界的 `fadingEdge` 离屏层。
 * 因此渐隐改挂在父级，并用 [CouiTabShadowBleed] 外扩离屏层；`LazyRow` 的惰性组合、
 * 惯性滚动、选中露出和 RTL 语义全部保留。
 */
private val CouiTabShadowBleed = 30.dp

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
    if (scrollable) {
        AppCouiScrollableTabRow(
            titles, selectedIndex, onTabSelected, backdrop, rowModifier, contentPadding
        )
    } else {
        Row(rowModifier.padding(contentPadding), horizontalArrangement = Arrangement.spacedBy(CouiTabSpacing)) {
            titles.forEachIndexed { index, title ->
                AppCouiTab(title, selectedIndex == index, { onTabSelected(index) }, backdrop, Modifier.weight(1f))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun AppCouiScrollableTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: CouiBackdrop?,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    // 选中项已完整可见时不滚动，避免点击可见标签时把它顶到视口边缘。
    LaunchedEffect(selectedIndex, titles.size) {
        val layout = listState.layoutInfo
        val selectedVisible = layout.visibleItemsInfo.any { item ->
            item.index == selectedIndex && item.offset >= layout.viewportStartOffset &&
                item.offset + item.size <= layout.viewportEndOffset
        }
        if (!selectedVisible) listState.animateScrollToItem(selectedIndex)
    }
    // 外层渐隐离屏层按 30dp 外扩：未渐隐的一侧不收边，纵向始终给投影留满 30dp。
    Box(modifier.fadingEdge(listState, CouiTabFadeWidth, bleed = CouiTabShadowBleed)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(CouiTabSpacing),
        ) {
            items(titles.size) { index ->
                AppCouiTab(titles[index], selectedIndex == index, { onTabSelected(index) }, backdrop)
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
