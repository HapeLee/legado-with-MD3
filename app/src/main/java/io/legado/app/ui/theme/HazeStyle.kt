package io.legado.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.hazeStyle.HazeLegado
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.GlassDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 自动感知全局配置的 HazeSource
 */
fun Modifier.responsiveHazeSource(state: HazeState): Modifier = this.then(
    if (ThemeConfig.enableBlur) Modifier.hazeSource(state) else Modifier
)

/**
 * 自动感知全局配置的 HazeEffect
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.responsiveHazeEffect(
    state: HazeState
): Modifier {
    val enableBlur = ThemeConfig.enableBlur
    val enableProgressiveBlur = ThemeConfig.enableProgressiveBlur
    val composeEngine = LegadoTheme.composeEngine
    val containerColor = GlassDefaults.secondaryColorOr {
        if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surface
    }

    if (!enableBlur) return this

    val style = HazeLegado.custom(
        containerColor = containerColor,
        blurRadius = ThemeConfig.topBarBlurRadius,
        blurAlpha = ThemeConfig.topBarBlurAlpha
    )

    return this.hazeEffect(
        state = state,
        style = style
    ) {
        progressive = if (enableProgressiveBlur) {
            HazeProgressive.verticalGradient(
                startIntensity = 1f,
                endIntensity = 0f
            )
        } else {
            null
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.responsiveHazeEffectFixedStyle(
    state: HazeState
): Modifier {
    val enableBlur = ThemeConfig.enableBlur
    val composeEngine = LegadoTheme.composeEngine
    val containerColor = GlassDefaults.secondaryColorOr {
        if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surface
    }

    if (!enableBlur) return this

    val style = HazeLegado.ultraThinPlus(containerColor = containerColor)

    return this.hazeEffect(
        state = state,
        style = style
    ) {
        progressive =
            HazeProgressive.verticalGradient(
                startIntensity = 1f,
                endIntensity = 0f
            )
    }
}

/**
 * 仅判断 enableBlur 的简单 HazeEffect
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.regularHazeEffect(state: HazeState): Modifier {
    val enableBlur = ThemeConfig.enableBlur
    val composeEngine = LegadoTheme.composeEngine
    val containerColor = GlassDefaults.secondaryColorOr {
        if (ThemeResolver.isMiuixEngine(composeEngine)) MiuixTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surface
    }

    if (!enableBlur) return this

    return this.hazeEffect(
        state = state,
        style = HazeLegado.ultraThin(containerColor = containerColor)
    )
}
