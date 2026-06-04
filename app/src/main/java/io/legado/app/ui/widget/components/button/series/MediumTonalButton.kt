package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun MediumTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    SeriesButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        onLongClick = onLongClick,
        size = if (text == null) MediumSeriesIconButtonSize else null,
        style = SeriesIconButtonStyle.Tonal
    ) { contentColor ->
        SeriesButtonContent(
            icon = icon,
            text = text,
            contentDescription = contentDescription,
            iconSize = MediumSeriesIconSize,
            textStyle = LegadoTheme.typography.labelMedium,
            contentColor = contentColor,
            padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            spacing = 8.dp
        )
    }
}
