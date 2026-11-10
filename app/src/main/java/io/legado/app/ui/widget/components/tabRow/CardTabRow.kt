package io.legado.app.ui.widget.components.tabRow

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.effect.elastic.mochiPressDeform

@Composable
fun CardTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabLongClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    tabEndContent: (@Composable (Int) -> Unit)? = null,
    scrollable: Boolean = false,
) {
    if (LegadoTheme.composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported() &&
        onTabLongClick == null && tabEndContent == null
    ) {
        AppCouiTabRow(tabTitles, selectedTabIndex, onTabSelected, modifier, scrollable)
        return
    }

    val simpleTabs = !scrollable && onTabLongClick == null && tabEndContent == null
    if (simpleTabs && LegadoTheme.composeEngine == ComposeEngine.Miuix &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalAppUiConfiguration.current.theme.useHyperOs4Components &&
        tabTitles.distinct().size == tabTitles.size
    ) {
        val backdrop = LocalMiuixBarBackdrop.current
        if (backdrop != null) {
            GlassSegmentedTabRow(
                tabs = tabTitles,
                selectedIndex = selectedTabIndex,
                onSelect = onTabSelected,
                backdrop = backdrop,
                modifier = modifier.fillMaxWidth(),
            )
        } else {
            GlassTabRow(
                tabs = tabTitles,
                selectedIndex = selectedTabIndex,
                onSelect = onTabSelected,
                backdrop = null,
                modifier = modifier.fillMaxWidth(),
                height = GlassTabRowDefaults.NeutralHeight,
                colors = GlassTabRowDefaults.neutralColors(),
            )
        }
        return
    }

    val useMochi = LegadoTheme.composeEngine == ComposeEngine.Mochi
    if (useMochi && simpleTabs) {
        if (tabTitles.isEmpty()) return
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val lastIndex = tabTitles.lastIndex
        val visualIndex = if (rtl) lastIndex - selectedTabIndex else selectedTabIndex
        val titles = remember(tabTitles, rtl) {
            (if (rtl) tabTitles.asReversed() else tabTitles).toImmutableList()
        }
        MochiSegmentedControl(
            selectedIndex = visualIndex,
            onSelect = { onTabSelected(if (rtl) lastIndex - it else it) },
            items = titles,
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup()
                .onKeyEvent { event ->
                    val next = when (event.key) {
                        Key.DirectionLeft -> visualIndex - 1
                        Key.DirectionRight -> visualIndex + 1
                        Key.MoveHome -> if (rtl) lastIndex else 0
                        Key.MoveEnd -> if (rtl) 0 else lastIndex
                        else -> return@onKeyEvent false
                    }.coerceIn(0, lastIndex)
                    if (event.type == KeyEventType.KeyDown && next != visualIndex) {
                        onTabSelected(if (rtl) lastIndex - next else next)
                    }
                    true
                }
                .focusable(),
        )
        return
    }

    val scrollState = rememberScrollState()
    val tabWidths = remember(tabTitles) { mutableStateMapOf<Int, Int>() }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    // 只有全部 tab 完成测量后的偏移才是准确的，否则会先按缺失宽度为 0 居中再回弹。
    val selectedPosition = tabWidths[selectedTabIndex]
        ?.takeIf { tabWidths.size == tabTitles.size }
        ?.let { width ->
            val left =
                (0 until selectedTabIndex).sumOf { tabWidths[it] ?: 0 } + selectedTabIndex * gap
            left to width
        }
    LaunchedEffect(scrollable, selectedPosition, scrollState.maxValue, scrollState.viewportSize) {
        if (scrollable && selectedPosition != null) {
            val (left, width) = selectedPosition
            val offset = left - (scrollState.viewportSize - width) / 2
            scrollState.animateScrollTo(offset.coerceIn(0, scrollState.maxValue))
        }
    }
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val isSelectionChanged = selectedTabIndex != lastSelectedTabIndex

    SideEffect {
        lastSelectedTabIndex = selectedTabIndex
    }

    val animSpec = if (isSelectionChanged) tween<Color>(durationMillis = 200) else snap()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .then(
                if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabTitles.forEachIndexed { index, title ->
            val selected = selectedTabIndex == index
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    LegadoTheme.colorScheme.secondaryContainer
                } else {
                    LegadoTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = animSpec,
                label = "tabColor",
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    LegadoTheme.colorScheme.onSecondaryContainer
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = animSpec,
                label = "tabContentColor",
            )

            NormalCard(
                onClick = { onTabSelected(index) },
                onLongClick = onTabLongClick?.let { { it(index) } },
                modifier = (if (scrollable) {
                    Modifier
                        .widthIn(min = 48.dp, max = 180.dp)
                        .onSizeChanged {
                            tabWidths[index] = it.width
                        }
                } else Modifier.weight(1f))
                    .then(
                        if (useMochi) Modifier.mochiPressDeform(scale = 0.98f, follow = 0.dp)
                        else Modifier
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                containerColor = containerColor,
                contentColor = contentColor,
                cornerRadius = if (useMochi) 50.dp else 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .then(if (scrollable) Modifier else Modifier.fillMaxWidth())
                        .padding(horizontal = if (scrollable) 16.dp else 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.labelMediumEmphasized,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .then(if (scrollable) Modifier else Modifier.fillMaxWidth())
                            .padding(horizontal = if (tabEndContent == null) 0.dp else 20.dp),
                        textAlign = TextAlign.Center,
                    )
                    if (tabEndContent != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            tabEndContent(index)
                        }
                    }
                }
            }
        }
    }
}
