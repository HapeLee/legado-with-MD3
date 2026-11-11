package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

/**
 * 中号 plain 图标按钮。
 *
 * [onClick] 传 `null` 得到纯展示形态：几何、选中态与配色不变，但不安装点击节点、涟漪和按钮语义。
 * 用途同 [SmallPlainButton]：塞进 `AppActionMenu` / `AppChoiceDropdown` 的 `trigger` 槽，
 * 由入口包唯一可点节点，避免内外两层可点节点。
 */
@Composable
fun MediumPlainButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    icon: ImageVector? = null,
    text: String? = null,
    tint: androidx.compose.ui.graphics.Color = LegadoTheme.colorScheme.onSurface,
    contentDescription: String? = null
) {
    SeriesButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        onLongClick = onLongClick,
        size = if (text == null) MediumSeriesIconButtonSize else null,
        contentColor = tint
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
