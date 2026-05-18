package io.legado.app.ui.widget.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.divider.SettingItemDivider
import io.legado.app.ui.widget.components.title.AdaptiveTitle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard

val LocalSplicedColumnGroupState = compositionLocalOf { SplicedColumnGroupState(
    enableItemDivider = false,
    currentIndex = { 0 },
    incrementIndex = { }
) }

data class SplicedColumnGroupState(
    val enableItemDivider: Boolean,
    val currentIndex: () -> Int,
    val incrementIndex: () -> Unit
)

@Composable
fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    items: @Composable ColumnScope.() -> Unit,
) {
    val composeEngine = LegadoTheme.composeEngine
    val enableItemDivider = ThemeConfig.enableItemDivider
    val currentIndex = remember { mutableIntStateOf(0) }

    val groupState = remember {
        SplicedColumnGroupState(
            enableItemDivider = enableItemDivider,
            currentIndex = { currentIndex.intValue },
            incrementIndex = { currentIndex.intValue++ }
        )
    }

    Column(modifier = modifier.padding(top = 8.dp, bottom = 8.dp)) {
        if (title.isNotEmpty()) {
            AdaptiveTitle(
                text = title,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        CompositionLocalProvider(LocalSplicedColumnGroupState provides groupState) {
            if (ThemeResolver.isMiuixEngine(composeEngine)) {
                MiuixCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        currentIndex.intValue = 0
                        items()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    currentIndex.intValue = 0
                    items()
                }
            }
        }
    }
}

@Composable
fun SplicedColumnDivider() {
    val groupState = LocalSplicedColumnGroupState.current
    if (groupState.enableItemDivider && groupState.currentIndex() > 0) {
        SettingItemDivider()
    }
    groupState.incrementIndex()
}

@Composable
fun SettingItemWithDivider(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val groupState = LocalSplicedColumnGroupState.current
    if (groupState.enableItemDivider && groupState.currentIndex() > 0) {
        SettingItemDivider()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        groupState.incrementIndex()
        content()
    }
}