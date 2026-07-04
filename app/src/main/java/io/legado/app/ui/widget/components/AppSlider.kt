package io.legado.app.ui.widget.components

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    val sliderModifier = modifier.sliderAccessibility(
        label = accessibilityLabel,
        value = accessibilityValue,
    )
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = sliderModifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
    } else {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = sliderModifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

internal fun Modifier.sliderAccessibility(
    label: String?,
    value: String?,
): Modifier {
    if (label == null && value == null) return this
    return semantics {
        label?.let { contentDescription = it }
        value?.let { stateDescription = it }
    }
}
