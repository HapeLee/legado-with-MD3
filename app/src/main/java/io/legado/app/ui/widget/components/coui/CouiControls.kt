package io.legado.app.ui.widget.components.coui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.itos.coui.slider.Slider
import com.itos.coui.switch.Switch

@Composable
internal fun AppCouiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    includeStateSemantics: Boolean,
) {
    Switch(
        modifier = modifier
            .then(if (includeStateSemantics) Modifier else Modifier.clearAndSetSemantics { })
            .minimumInteractiveComponentSize(),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

@Composable
internal fun AppCouiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
) {
    Slider(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = { onValueChangeFinished?.invoke() },
    )
}
