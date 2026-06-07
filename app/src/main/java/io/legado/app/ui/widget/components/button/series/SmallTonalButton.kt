package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    icon: ImageVector? = null,
    text: String? = null,
    contentColor: Color = LegadoTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null,
    selectedContainerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
    contentDescription: String? = null
) {
    SmallNoMinTouchTarget {
        SeriesButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            selected = selected,
            onLongClick = onLongClick,
            size = if (text == null) smallContainerSize() else null,
            style = SeriesIconButtonStyle.Tonal,
            contentColor = contentColor,
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor
        ) { contentColor ->
            SeriesButtonContent(
                icon = icon,
                text = text,
                contentDescription = contentDescription,
                iconSize = smallIconSize,
                textStyle = LegadoTheme.typography.labelMedium,
                contentColor = contentColor,
                padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                spacing = 4.dp
            )
        }
    }
}
