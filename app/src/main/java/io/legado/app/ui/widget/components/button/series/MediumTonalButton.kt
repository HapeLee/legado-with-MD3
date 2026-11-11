package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

/**
 * Tonal 按钮。
 *
 * [onClick] 传 `null` 得到纯展示形态：几何与配色不变，但不安装点击节点、涟漪和按钮语义。
 * 只用于「入口自己包唯一可点节点」的场景（`AppChoiceDropdown` / `AppAnchoredPanel` 的
 * `trigger` 槽）：带 `onClick` 的按钮塞进 trigger 会形成内外两层可点节点，TalkBack 当两个
 * 按钮播报，锚点触点也可能被内层节点吞掉。这类调用方必须把入口给的锚点 `modifier`
 * 透传给本组件，否则菜单定位会丢。
 * 与 `SourceEditOptionCard(onClick = null)` / `GlassCard(onClick = null)` 同一约定。
 */
@Composable
fun MediumTonalButton(
    onClick: (() -> Unit)?,
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
        enforceMinimumInteractiveSize = false,
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
