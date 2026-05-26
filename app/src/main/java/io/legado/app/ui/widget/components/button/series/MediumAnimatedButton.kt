package io.legado.app.ui.widget.components.button.series

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun MediumAnimatedButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
            animationSpec = tween(150),
            label = "MiuixAnimatedContainerColor"
        )

        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
            animationSpec = tween(150),
            label = "MiuixAnimatedContentColor"
        )

        if (text != null) {
            AnimatedActionButtonCore(
                checked = checked,
                onCheckedChange = onCheckedChange,
                iconChecked = iconChecked ?: icon!!,
                iconUnchecked = icon ?: iconChecked!!,
                activeText = text,
                inactiveText = text,
                modifier = modifier,
                iconSize = 24.dp,
                textStyle = LegadoTheme.typography.labelMedium,
                textStartPadding = 8.dp,
                contentColor = contentColor,
                button = { buttonModifier, onToggle, content ->
                    MiuixIconButton(
                        onClick = { onToggle(!checked) },
                        modifier = buttonModifier,
                        backgroundColor = containerColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            content = content
                        )
                    }
                },
                icon = { imageVector, iconModifier, tint ->
                    MiuixIcon(
                        tint = tint ?: Color.Unspecified,
                        imageVector = imageVector,
                        contentDescription = null,
                        modifier = iconModifier
                    )
                },
                text = { label, textModifier, textStyle, color ->
                    MiuixText(
                        text = label,
                        color = color ?: Color.Unspecified,
                        style = textStyle,
                        modifier = textModifier,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
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
            AnimatedActionButtonCore(
                checked = checked,
                onCheckedChange = onCheckedChange,
                iconChecked = iconChecked ?: icon!!,
                iconUnchecked = icon ?: iconChecked!!,
                activeText = text,
                inactiveText = text,
                modifier = modifier.height(36.dp),
                iconSize = 20.dp,
                textStyle = LegadoTheme.typography.labelMedium,
                textStartPadding = 8.dp,
                button = { buttonModifier, onToggle, content ->
                    TonalToggleButton(
                        modifier = buttonModifier,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        checked = checked,
                        onCheckedChange = onToggle
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            content = content
                        )
                    }
                },
                icon = { imageVector, iconModifier, _ ->
                    AnimatedIcon(
                        imageVector = imageVector,
                        contentDescription = null,
                        modifier = iconModifier
                    )
                },
                text = { label, textModifier, textStyle, color ->
                    Text(
                        text = label,
                        style = textStyle,
                        color = color ?: Color.Unspecified,
                        modifier = textModifier,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        } else {
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
