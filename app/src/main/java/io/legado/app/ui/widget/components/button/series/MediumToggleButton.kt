package io.legado.app.ui.widget.components.button.series

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ToggleStyle = ToggleStyle.Outlined,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(150),
            label = "MiuixToggleContainerColor"
        )

        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
            animationSpec = tween(150),
            label = "MiuixToggleContentColor"
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
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    MiuixIcon(
                        imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                        contentDescription = null,
                        tint = contentColor
                    )
                    MiuixText(
                        text = text,
                        color = contentColor,
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            MiuixIconButton(
                onClick = { onCheckedChange(!checked) },
                modifier = modifier,
                enabled = enabled,
                backgroundColor = containerColor
            ) {
                MiuixIcon(
                    imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                    contentDescription = contentDescription,
                    tint = contentColor
                )
            }
        }
    } else {
        if (text != null) {
            ToggleButton(
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
                        contentDescription = null
                    )
                    Text(
                        text = text,
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            when (style) {
                ToggleStyle.Outlined -> {
                    FilledTonalButton(
                        onClick = { onCheckedChange(!checked) },
                        modifier = modifier,
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                            contentDescription = contentDescription,
                        )
                    }
                }

                ToggleStyle.Tonal -> {
                    FilledTonalIconButton(
                        onClick = { onCheckedChange(!checked) },
                        modifier = modifier,
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = if (checked) (iconChecked ?: icon)!! else icon!!,
                            contentDescription = contentDescription,
                        )
                    }
                }
            }
        }
    }
}
