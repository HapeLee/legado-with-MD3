package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val GlassTabShadowPadding = 8.dp

/** Material uses zero insets; COUI uses at least 12dp inside its content; OS4 includes shadow space. */
@Composable
fun AppTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true,
    horizontalPadding: Dp = 0.dp,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    // GlassTabRow keys its children by title; duplicate titles keep the compatibility path.
    val useGlassTabs = isMiuixHyperOs4Enabled() && tabTitles.isNotEmpty() &&
        tabTitles.distinct().size == tabTitles.size
    val composeEngine = LegadoTheme.composeEngine
    val useCouiTabs = composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported()
    val contentInset = when {
        composeEngine == ComposeEngine.Material -> 0.dp
        useCouiTabs -> maxOf(horizontalPadding, 12.dp)
        else -> horizontalPadding
    }
    val tabInset = when {
        useGlassTabs -> GlassTabShadowPadding
        useCouiTabs -> contentInset
        else -> 0.dp
    }
    val startPadding = (contentInset - tabInset).coerceAtLeast(0.dp)
    val endPadding = if (trailingAction == null) startPadding else contentInset
    val couiContentPadding = PaddingValues(
        start = contentInset,
        end = if (trailingAction == null) contentInset else 8.dp,
        top = 8.dp,
        bottom = 8.dp,
    )
    val rowModifier = modifier.padding(start = startPadding, end = endPadding)
    if (trailingAction == null) {
        AppTabRowContent(tabTitles, selectedTabIndex, onTabSelected, rowModifier, isScrollable, useGlassTabs, couiContentPadding)
    } else {
        Row(
            modifier = rowModifier.fillMaxWidth(),
            // Glass and COUI content already reserves 8dp before the action.
            horizontalArrangement = Arrangement.spacedBy(if (useGlassTabs || useCouiTabs) 0.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTabRowContent(tabTitles, selectedTabIndex, onTabSelected, Modifier.weight(1f), isScrollable, useGlassTabs, couiContentPadding)
            // Keep popup content in the same layout node as the trigger, outside the scroll/fade.
            Box { trailingAction() }
        }
    }
}

@Composable
private fun AppTabRowContent(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
    isScrollable: Boolean,
    useGlassTabs: Boolean,
    couiContentPadding: PaddingValues,
) {
    if (tabTitles.isEmpty()) return
    val composeEngine = LegadoTheme.composeEngine
    if (composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported()) {
        AppCouiTabRow(tabTitles, selectedTabIndex, onTabSelected, modifier, isScrollable, couiContentPadding)
        return
    }

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            val selectedIndex = selectedTabIndex.coerceIn(tabTitles.indices)
            if (useGlassTabs) {
                AppGlassTabRow(tabTitles, selectedIndex, onTabSelected, modifier, isScrollable)
            } else {
                TabRowWithContour(
                    tabs = tabTitles,
                    selectedTabIndex = selectedIndex,
                    onTabSelected = onTabSelected,
                    modifier = modifier.padding(vertical = 4.dp),
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = Color.Transparent
                    )
                )
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            if (isScrollable) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 0.dp,
                    divider = { },
                    containerColor = Color.Transparent,
                    minTabWidth = 0.dp,
                    modifier = modifier
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        AppTab(
                            selected = selectedTabIndex == index,
                            onClick = { onTabSelected(index) },
                            title = title
                        )
                    }
                }
            } else {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    divider = { },
                    containerColor = Color.Transparent,
                    modifier = modifier
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        AppTab(
                            selected = selectedTabIndex == index,
                            onClick = { onTabSelected(index) },
                            title = title
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGlassTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier,
    scrollable: Boolean,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val selectedStyle = MiuixTheme.textStyles.subtitle
    val unselectedStyle = MiuixTheme.textStyles.body2
    val labelWidth = remember(titles, textMeasurer, selectedStyle, unselectedStyle, density, layoutDirection) {
        val pixels = titles.maxOf { title ->
            maxOf(
                textMeasurer.measure(title, selectedStyle, maxLines = 1, softWrap = false).size.width,
                textMeasurer.measure(title, unselectedStyle, maxLines = 1, softWrap = false).size.width,
            )
        }
        with(density) { pixels.toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Reserve the floating shadow's 8dp reach inside every viewport edge.
        val shadowPadding = GlassTabShadowPadding
        val availableWidth = (maxWidth - shadowPadding * 2).coerceAtLeast(0.dp)
        // Give the library's weighted tabs a finite content width, even inside horizontalScroll.
        val minTabWidth = maxOf(
            TabRowDefaults.TabRowWithContourMinWidth,
            labelWidth + GlassTabRowDefaults.TabPaddingHorizontal * 2,
        ).coerceAtMost(availableWidth)
        val gap = GlassTabRowDefaults.TabGap
        val contentWidth = if (scrollable) {
            maxOf(availableWidth, minTabWidth * titles.size + gap * (titles.size - 1))
        } else {
            availableWidth
        }
        val hasOverflow = scrollable && contentWidth > availableWidth
        val shadowPaddingPx = with(density) { shadowPadding.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val tabWidthPx = with(density) { contentWidth.roundToPx() - gapPx * (titles.size - 1) } / titles.size.toFloat()
        var lastSelectedIndex by remember(scrollState) { mutableIntStateOf(-1) }
        LaunchedEffect(selectedIndex, tabWidthPx, gapPx, scrollState.maxValue, scrollState.viewportSize, layoutDirection, hasOverflow) {
            if (!hasOverflow) {
                scrollState.scrollTo(0)
                lastSelectedIndex = selectedIndex
                return@LaunchedEffect
            }
            if (scrollState.viewportSize == 0) return@LaunchedEffect
            val target = (shadowPaddingPx + selectedIndex * (tabWidthPx + gapPx) - (scrollState.viewportSize - tabWidthPx) / 2)
                .roundToInt().coerceIn(0, scrollState.maxValue)
            if (lastSelectedIndex < 0 || lastSelectedIndex == selectedIndex) {
                scrollState.scrollTo(target)
            } else {
                scrollState.animateScrollTo(target)
            }
            lastSelectedIndex = selectedIndex
        }
        Box(
            Modifier
                .then(
                    if (hasOverflow) Modifier
                        .fadingEdge(scrollState, gradientWidth = 16.dp)
                        .horizontalScroll(scrollState)
                    else Modifier
                )
                // The 24px floating shadow at the library's 3x source density reaches 8dp.
                // Keep this space inside both the scroll viewport and the offscreen fade layer.
                .padding(shadowPadding),
        ) {
            GlassTabRow(
                tabs = titles,
                selectedIndex = selectedIndex,
                onSelect = onTabSelected,
                backdrop = LocalMiuixBarBackdrop.current,
                modifier = Modifier.width(contentWidth),
                height = GlassTabRowDefaults.NeutralHeight,
                colors = GlassTabRowDefaults.neutralColors(),
            )
        }
    }
}

@Composable
private fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            AppText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.labelLargeEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = if (selected) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
