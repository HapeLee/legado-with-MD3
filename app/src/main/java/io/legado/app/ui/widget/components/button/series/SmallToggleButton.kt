package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

enum class ToggleStyle { Outlined, Tonal }

@Composable
fun SmallToggleButton(
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
    SeriesButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        selected = checked,
        onLongClick = onLongClick,
        size = if (text == null) smallContainerSize() else null,
        enforceMinimumInteractiveSize = false,
        shape = SmallButtonShape,
        style = when (style) {
            ToggleStyle.Outlined -> SeriesIconButtonStyle.Outlined
            ToggleStyle.Tonal -> SeriesIconButtonStyle.Tonal
        },
        // Outlined 未选中态留空，交给样式解析成透明容器
        containerColor = if (style == ToggleStyle.Tonal) {
            LegadoTheme.colorScheme.surfaceContainer
        } else {
            null
        },
        contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
        // M3E 实心 toggle 选中态：反色容器，背景与描边用 inverseSurface，内容用 inverseOnSurface
        selectedContainerColor = LegadoTheme.colorScheme.inverseSurface,
        selectedContentColor = LegadoTheme.colorScheme.inverseOnSurface,
        selectedBorderColor = LegadoTheme.colorScheme.inverseSurface
    ) { contentColor ->
        SeriesButtonContent(
            icon = if (checked) (iconChecked ?: icon)!! else icon!!,
            text = text,
            contentDescription = contentDescription,
            iconSize = smallIconSize,
            textStyle = LegadoTheme.typography.labelSmall,
            contentColor = contentColor,
            padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            spacing = 6.dp
        )
    }
}
