package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SmallOutlinedIconButton
import io.legado.app.ui.widget.components.card.TextCard

@Composable
fun ValueStepper(
    value: Float,
    displayValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallOutlinedIconButton(
            onClick = {
                val newValue = (value.toInt() - 1).toFloat().coerceIn(valueRange)
                onValueChange(newValue)
            },
            imageVector = Icons.Default.Remove
        )
        TextCard(
            cornerRadius = 8.dp,
            horizontalPadding = 8.dp,
            verticalPadding = 4.dp,
            text = displayValue.toInt().toString(),
            backgroundColor = LegadoTheme.colorScheme.surfaceContainer,
            contentColor = LegadoTheme.colorScheme.onSurface
        )
        SmallOutlinedIconButton(
            onClick = {
                val newValue = (value.toInt() + 1).toFloat().coerceIn(valueRange)
                onValueChange(newValue)
            },
            imageVector = Icons.Default.Add
        )
    }
}
