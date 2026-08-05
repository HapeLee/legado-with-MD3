package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.Slider as M3Slider
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

/**
 * Vertical slider component that supports both Miuix and M3 engines.
 *
 * @param height Visual height of the vertical slider.
 */
@Composable
fun AppVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    height: Dp = 160.dp,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    val sliderModifier = Modifier
        .requiredWidth(height)
        .requiredHeight(48.dp)
        .graphicsLayer { rotationZ = -90f }
        .sliderAccessibility(
            label = accessibilityLabel,
            value = accessibilityValue,
        )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (ThemeResolver.isMiuixEngine(composeEngine)) {
            MiuixSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = sliderModifier,
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
            )
        } else {
            M3Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = sliderModifier,
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
            )
        }
    }
}
