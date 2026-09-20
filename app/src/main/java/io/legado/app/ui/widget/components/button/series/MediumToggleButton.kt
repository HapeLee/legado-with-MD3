package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
    // 几何尺寸跟随对应的非 toggle 按钮：Tonal 对齐 MediumTonalButton，
    // Outlined 对齐 MediumOutlinedButton（保留 48dp 最小交互尺寸与 12dp 纵向内边距）
    val isTonal = style == ToggleStyle.Tonal

    SeriesButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        selected = checked,
        onLongClick = onLongClick,
        size = if (text == null) MediumSeriesIconButtonSize else null,
        enforceMinimumInteractiveSize = isTonal,
        style = when (style) {
            ToggleStyle.Outlined -> SeriesIconButtonStyle.Outlined
            ToggleStyle.Tonal -> SeriesIconButtonStyle.Tonal
        },
        // 未选中容器色不在这里覆写，统一由 SeriesIconButtonStyle 解析
        // （Tonal -> surfaceContainerLow，Outlined -> 透明），与对应的非 toggle 按钮一致
        contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
        // M3E 实心 toggle 选中态：反色容器，内容用 inverseOnSurface
        selectedContainerColor = LegadoTheme.colorScheme.inverseSurface,
        selectedContentColor = LegadoTheme.colorScheme.inverseOnSurface,
        // 选中态描边只能覆盖已有描边：Outlined 有 1dp 描边，Tonal 无描边，传值也不会生效
        selectedBorderColor = if (style == ToggleStyle.Outlined) {
            LegadoTheme.colorScheme.inverseSurface
        } else {
            null
        }
    ) { resolvedContentColor ->
        SeriesButtonContent(
            icon = if (checked) (iconChecked ?: icon)!! else icon!!,
            text = text,
            contentDescription = contentDescription,
            iconSize = MediumSeriesIconSize,
            textStyle = LegadoTheme.typography.labelMedium,
            contentColor = resolvedContentColor,
            padding = if (isTonal) {
                PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            } else {
                PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            },
            spacing = 8.dp
        )
    }
}
