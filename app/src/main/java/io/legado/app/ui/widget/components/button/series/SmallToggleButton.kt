package io.legado.app.ui.widget.components.button.series

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

enum class ToggleStyle { Outlined, Tonal }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) LegadoTheme.colorScheme.primaryContainer else LegadoTheme.colorScheme.surfaceContainer,
            animationSpec = tween(150),
            label = "MiuixToggleContainerColor"
        )

        val iconTint by animateColorAsState(
            targetValue = if (checked) LegadoTheme.colorScheme.onPrimaryContainer else LegadoTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(150),
            label = "MiuixToggleIconTint"
        )

        if (text != null) {
            MiuixIconButton(
                onClick = { onCheckedChange(!checked) },
                modifier = modifier,
                enabled = enabled,
                backgroundColor = containerColor
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    MiuixIcon(
                        imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTint
                    )
                    MiuixText(
                        text = text,
                        style = LegadoTheme.typography.labelSmall,
                        color = iconTint,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            SeriesIconButton(
                icon = if (checked) (iconChecked ?: icon)!! else icon!!,
                contentDescription = contentDescription,
                onClick = { onCheckedChange(!checked) },
                modifier = modifier,
                enabled = enabled,
                selected = checked,
                onLongClick = onLongClick,
                size = squareSize(SmallMiuixButtonSize),
                iconSize = SmallMiuixIconSize,
                style = when (style) {
                    ToggleStyle.Outlined -> SeriesIconButtonStyle.Outlined
                    ToggleStyle.Tonal -> SeriesIconButtonStyle.Tonal
                },
                selectedContainerColor = containerColor,
                selectedContentColor = iconTint
            )
        }
    } else {
        SmallNoMinTouchTarget {
            if (text != null) {
                TonalToggleButton(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = modifier,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = text,
                            style = LegadoTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                SeriesIconButton(
                    icon = if (checked) (iconChecked ?: icon)!! else icon!!,
                    contentDescription = contentDescription,
                    onClick = { onCheckedChange(!checked) },
                    modifier = modifier,
                    enabled = enabled,
                    selected = checked,
                    onLongClick = onLongClick,
                    size = smallContainerSize(),
                    iconSize = smallIconSize,
                    style = when (style) {
                        ToggleStyle.Outlined -> SeriesIconButtonStyle.Outlined
                        ToggleStyle.Tonal -> SeriesIconButtonStyle.Tonal
                    }
                )
            }
        }
    }
}
