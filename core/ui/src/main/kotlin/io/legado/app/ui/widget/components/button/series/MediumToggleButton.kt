package io.legado.app.ui.widget.components.button.series

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun MediumToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: ToggleStyle = ToggleStyle.Outlined,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            LegadoTheme.colorScheme.primaryContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(150),
        label = "MediumToggleContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            LegadoTheme.colorScheme.onPrimaryContainer
        } else {
            LegadoTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(150),
        label = "MediumToggleContentColor"
    )
    SeriesButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        selected = checked,
        onLongClick = onLongClick,
        size = if (text == null) MediumSeriesIconButtonSize else null,
        style = when (style) {
            ToggleStyle.Outlined -> SeriesIconButtonStyle.Outlined
            ToggleStyle.Tonal -> SeriesIconButtonStyle.Tonal
        },
        containerColor = containerColor,
        selectedContainerColor = containerColor,
        contentColor = contentColor,
        selectedContentColor = contentColor
    ) { resolvedContentColor ->
        SeriesButtonContent(
            icon = if (checked) (iconChecked ?: icon)!! else icon!!,
            text = text,
            contentDescription = contentDescription,
            iconSize = MediumSeriesIconSize,
            textStyle = LegadoTheme.typography.labelMedium,
            contentColor = resolvedContentColor,
            padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            spacing = 8.dp
        )
    }
}
