package io.legado.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.legado.app.ui.config.themeConfig.ThemeConfig

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

    if (!enableBlur) return this

    return this.hazeEffect(
        state = state,
        style = HazeMaterials.ultraThin()
    ) {
        if (enableProgressiveBlur) {
            progressive = HazeProgressive.verticalGradient(
                startIntensity = 1f,
                endIntensity = 0f
            )
        }
    }
}