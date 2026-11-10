package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.MiuixBlurredTopBar
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import io.legado.app.ui.widget.components.GlassDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults as MiuixTopAppBarDefaults
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults as MiuixGlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 小型玻璃顶栏。
 * Miuix 保留紧凑栏的高度，HyperOS 4 使用玻璃按钮。
 */
@Composable
fun GlassSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (LegadoTheme.composeEngine != ComposeEngine.Miuix) {
        GlassTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }

    val glass = isMiuixHyperOs4Enabled()
    val containerColor = if (glass && LocalMiuixBarBackdrop.current == null) {
        GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }
    } else {
        GlassTopAppBarDefaults.getMiuixAppBarColor()
    }
    MiuixBlurredTopBar(modifier.background(containerColor)) {
        CompositionLocalProvider(
            LocalMiuixGlassTopBar provides glass,
            LocalMiuixGlassTopBarActionProgress provides null,
        ) {
            MiuixSmallTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = { if (glass) MiuixGlassTopBarActions(content = actions) else actions() },
                color = Color.Transparent,
                navigationIconPadding = if (glass) MiuixGlassTopAppBarDefaults.HorizontalPadding
                    else MiuixTopAppBarDefaults.NavigationIconPadding,
                actionIconPadding = if (glass) MiuixGlassTopAppBarDefaults.HorizontalPadding
                    else MiuixTopAppBarDefaults.ActionIconPadding,
            )
        }
    }
}
