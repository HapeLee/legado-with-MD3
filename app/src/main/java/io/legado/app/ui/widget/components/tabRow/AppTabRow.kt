package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults

@Composable
fun AppTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            if (tabTitles.isEmpty()) return
            val selectedIndex = selectedTabIndex.coerceIn(tabTitles.indices)
            val tabModifier = modifier.padding(vertical = 4.dp)
            val scrollableTabs: @Composable (Modifier) -> Unit = { rowModifier ->
                TabRowWithContour(
                    tabs = tabTitles,
                    selectedTabIndex = selectedIndex,
                    onTabSelected = onTabSelected,
                    modifier = rowModifier,
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = Color.Transparent
                    )
                )
            }
            if (isMiuixHyperOs4Enabled() && tabTitles.distinct().size == tabTitles.size) {
                BoxWithConstraints(tabModifier) {
                    val requiredWidth = TabRowDefaults.TabRowWithContourMinWidth * tabTitles.size +
                        GlassTabRowDefaults.TabGap * (tabTitles.size - 1)
                    if (isScrollable && maxWidth < requiredWidth) {
                        // The native glass row divides a fixed width across all tabs.
                        scrollableTabs(Modifier)
                    } else {
                        GlassTabRow(
                            tabs = tabTitles,
                            selectedIndex = selectedIndex,
                            onSelect = onTabSelected,
                            backdrop = LocalMiuixBarBackdrop.current,
                        )
                    }
                }
            } else {
                scrollableTabs(tabModifier)
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi -> {
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
