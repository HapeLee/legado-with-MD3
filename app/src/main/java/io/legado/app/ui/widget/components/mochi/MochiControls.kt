package io.legado.app.ui.widget.components.mochi

import androidx.compose.foundation.focusable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import io.legado.app.ui.theme.LegadoTheme
import kotlin.math.roundToInt
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.Text

@Composable
internal fun AppMochiButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    text: String,
    style: MochiButtonStyle,
) {
    MochiButton(
        onClick = onClick,
        modifier = modifier
            .keyboardClick(enabled, onClick)
            .semantics(mergeDescendants = true) {},
        enabled = enabled,
        style = style,
    ) {
        Text(text = text, style = LegadoTheme.typography.labelLarge)
    }
}

@Composable
internal fun AppMochiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    includeStateSemantics: Boolean,
) {
    val interactiveModifier = if (includeStateSemantics) {
        modifier.keyboardClick(enabled) { onCheckedChange(!checked) }
    } else {
        modifier
    }
    MochiSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = interactiveModifier
            .minimumInteractiveComponentSize()
            .clearAndSetSemantics {
                if (includeStateSemantics) {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                    if (enabled) {
                        onClick { onCheckedChange(!checked); true }
                    } else {
                        disabled()
                    }
                }
            },
    )
}

@Composable
internal fun AppMochiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
) {
    fun snap(candidate: Float): Float {
        val clamped = candidate.coerceIn(valueRange)
        val span = valueRange.endInclusive - valueRange.start
        if (steps <= 0 || span <= 0f) return clamped
        val intervals = steps + 1
        return (valueRange.start +
            ((clamped - valueRange.start) / span * intervals).roundToInt() * span / intervals)
            .coerceIn(valueRange)
    }
    fun change(candidate: Float): Boolean {
        val next = snap(candidate)
        if (next == value) return false
        onValueChange(next)
        onValueChangeFinished?.invoke()
        return true
    }
    MochiSlider(
        value = value,
        onValueChange = { onValueChange(snap(it)) },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .onKeyEvent { event ->
                val increment = (valueRange.endInclusive - valueRange.start) /
                    if (steps > 0) steps + 1 else 20
                val next = when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> value - increment
                    Key.DirectionRight, Key.DirectionUp -> value + increment
                    Key.MoveHome -> valueRange.start
                    Key.MoveEnd -> valueRange.endInclusive
                    else -> return@onKeyEvent false
                }
                if (!enabled) return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) change(next)
                true
            }
            .focusable(enabled)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (enabled) {
                    setProgress { change(it) }
                } else {
                    disabled()
                }
            },
    )
}

internal fun Modifier.keyboardClick(enabled: Boolean, onClick: () -> Unit): Modifier =
    onKeyEvent { event ->
        if (!enabled || (event.key != Key.Enter && event.key != Key.NumPadEnter &&
                event.key != Key.Spacebar && event.key != Key.DirectionCenter)) {
            false
        } else {
            if (event.type == KeyEventType.KeyUp) onClick()
            true
        }
    }.focusable(enabled)
