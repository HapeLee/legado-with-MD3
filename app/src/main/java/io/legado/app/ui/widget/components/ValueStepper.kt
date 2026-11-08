package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallButtonSize
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.smallIconSize
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.mochi.keyboardClick
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.theme.LocalContentColor

@Composable
fun ValueStepper(
    value: Float,
    displayValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stepSize: Float = 1f,
    showDecimal: Boolean = false,
    valueFormat: ((Float) -> String)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ValueStepButton(
            onClick = {
                val newValue = (value - stepSize).coerceIn(valueRange)
                onValueChange(newValue)
            },
            enabled = enabled,
            icon = Icons.Default.Remove,
            contentDescription = stringResource(R.string.a11y_decrease),
        )
        if (content != null) {
            content()
        } else {
            val displayText = valueFormat?.invoke(displayValue) ?: if (showDecimal) {
                displayValue.toString()
            } else {
                displayValue.toInt().toString()
            }
            TextCard(
                cornerRadius = 8.dp,
                horizontalPadding = 8.dp,
                verticalPadding = 4.dp,
                text = displayText,
                backgroundColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                contentColor = LegadoTheme.colorScheme.onSurface
            )
        }
        ValueStepButton(
            onClick = {
                val newValue = (value + stepSize).coerceIn(valueRange)
                onValueChange(newValue)
            },
            enabled = enabled,
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.a11y_increase),
        )
    }
}

@Composable
private fun ValueStepButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
) {
    if (LegadoTheme.composeEngine == ComposeEngine.Mochi) {
        MochiIconButton(
            onClick = onClick,
            enabled = enabled,
            size = SmallButtonSize,
            style = MochiButtonStyle.Outlined,
            modifier = Modifier
                .keyboardClick(enabled, onClick)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                },
        ) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(smallIconSize),
                tint = LocalContentColor.current,
            )
        }
    } else {
        SmallOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            icon = icon,
            contentDescription = contentDescription,
        )
    }
}
