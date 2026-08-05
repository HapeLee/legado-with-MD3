package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

internal fun smallContainerSize() = squareSize(SmallButtonSize)

internal val smallIconSize: Dp = 18.dp
internal val SmallButtonSize = 32.dp

@Composable
fun SmallPlainButton(
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
        size = if (text == null) smallContainerSize() else null,
        enforceMinimumInteractiveSize = false,
        shape = SmallButtonShape,
        contentColor = LegadoTheme.colorScheme.onSurfaceVariant
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
