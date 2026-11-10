package io.legado.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import io.legado.app.ui.theme.hazeStyle.HazeLegado
import io.legado.app.ui.widget.components.GlassDefaults

/**
 * 自动感知全局配置的 HazeEffect
 */
@Composable
fun Modifier.responsiveHazeEffect(
    state: HazeState
): Modifier {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val enableBlur = themeSettings.enableBlur
    val enableProgressiveBlur = themeSettings.enableProgressiveBlur
    if (!enableBlur || LegadoTheme.composeEngine != ComposeEngine.Material) return this
    val containerColor = GlassDefaults.secondaryColorOr { MaterialTheme.colorScheme.surface }

    val style = HazeLegado.custom(
        containerColor = containerColor,
        blurRadius = themeSettings.topBarBlurRadius,
        blurAlpha = themeSettings.topBarBlurAlpha
    )

    return this.hazeBlur(
        input = HazeInput.Sources(state),
        style = style.then {
            progressive(
                if (enableProgressiveBlur) {
                    HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f
                    )
                } else {
                    null
                }
            )
        }
    )
}

@Composable
fun Modifier.responsiveHazeEffectFixedStyle(
    state: HazeState
): Modifier {
    val enableBlur = LocalAppUiConfiguration.current.theme.enableBlur
    if (!enableBlur || LegadoTheme.composeEngine != ComposeEngine.Material) return this
    val containerColor = GlassDefaults.secondaryColorOr { MaterialTheme.colorScheme.surface }

    val style = HazeLegado.ultraThinPlus(containerColor = containerColor)

    return this.hazeBlur(
        input = HazeInput.Sources(state),
        style = style.then {
            progressive(
                HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
            )
        }
    )
}

/**
 * 仅判断 enableBlur 的简单 HazeEffect
 */
@Composable
fun Modifier.regularHazeEffect(state: HazeState): Modifier {
    val enableBlur = LocalAppUiConfiguration.current.theme.enableBlur
    if (!enableBlur || LegadoTheme.composeEngine != ComposeEngine.Material) return this
    val containerColor = GlassDefaults.secondaryColorOr { MaterialTheme.colorScheme.surface }

    return this.hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeLegado.ultraThin(containerColor = containerColor)
    )
}
