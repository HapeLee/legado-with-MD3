package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassTopAppBar as NativeGlassTopAppBar

internal val LocalMiuixGlassTopBar = compositionLocalOf { false }
internal val LocalMiuixGlassTopBarActionProgress = compositionLocalOf<State<Float>?> { null }

@Composable
internal fun MiuixGlassTopBarActions(
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun MiuixNativeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    isContentScrolled: Boolean? = null,
    compactActions: Boolean = false,
    defaultBar: @Composable () -> Unit,
) {
    if (!isMiuixHyperOs4Enabled()) {
        defaultBar()
        return
    }

    val backdrop = LocalMiuixBarBackdrop.current
    val floating = isContentScrolled ?: scrollBehavior?.state?.let { it.contentOffset < 0f } ?: true
    val transition = updateTransition(floating, label = "miuixTopBarFloat")
    // Match the native button transition for actions that need custom content or long press.
    val actionProgress = transition.animateFloat(
        transitionSpec = { tween(350, easing = LinearEasing) },
        label = "miuixTopBarActionMaterial",
    ) { if (it) 1f else 0f }
    // HyperOS 4 顶栏恒为渐进模糊材质：能采到 backdrop 就启用，不再读取模糊开关。
    val bandVisibility = if (backdrop != null && isContentScrolled != null) {
        transition.animateFloat(
            transitionSpec = { tween(100, easing = LinearEasing) },
            label = "miuixTopBarBlurVisibility",
        ) { if (it) 1f else 0f }
    } else null

    CompositionLocalProvider(
        LocalMiuixGlassTopBar provides true,
        LocalMiuixGlassTopBarActionProgress provides actionProgress,
    ) {
        Box(modifier) {
            NativeGlassTopAppBar(
                title = title,
                isContentScrolled = floating,
                contentModifier = contentModifier,
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                backdrop = backdrop,
                progressiveBlur = backdrop != null,
                bandBrush = SolidColor(Color.Transparent),
                bandOverhang = 0.dp,
                backgroundAlpha = { if (backdrop != null) bandVisibility?.value ?: 1f else 0f },
                navigationIcon = navigationIcon,
                actions = { MiuixGlassTopBarActions(compactActions, actions) },
                bottomContent = bottomContent,
            )
        }
    }
}
