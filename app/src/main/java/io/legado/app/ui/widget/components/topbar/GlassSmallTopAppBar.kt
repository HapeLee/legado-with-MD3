package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar

/**
 * 小型玻璃顶栏。
 * M3 引擎走 [GlassTopAppBar]，Miuix 引擎走 MiuixSmallTopAppBar（与 RSS 阅读页一致）。
 * 网页类页面统一用它，保证两引擎下顶栏一致。
 */
@Composable
fun GlassSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (!ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        GlassTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }

    val hazeState = LocalHazeState.current
    val containerColor = GlassTopAppBarDefaults.getMiuixAppBarColor()
    val finalModifier = if (hazeState != null) {
        modifier
            .background(containerColor)
            .responsiveHazeEffect(hazeState)
    } else {
        modifier.background(containerColor)
    }

    Column(modifier = finalModifier) {
        MiuixSmallTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            color = Color.Transparent
        )
    }
}
