package io.legado.app.ui.widget.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
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

@Composable
fun MediumIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    tint: Color = LegadoTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@Composable
fun MediumOutlinedIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            backgroundColor = LegadoTheme.colorScheme.surfaceContainerHigh
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            border = ButtonDefaults.outlinedButtonBorder()
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediumAnimatedActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(150),
            label = "MiuixActionButtonContainer"
        )

        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
            animationSpec = tween(150),
            label = "MiuixActionButtonContent"
        )

        AnimatedActionButtonCore(
            checked = checked,
            onCheckedChange = onCheckedChange,
            iconChecked = iconChecked,
            iconUnchecked = iconUnchecked,
            activeText = activeText,
            inactiveText = inactiveText,
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
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = tint ?: Color.Unspecified
                )
            },
            text = { label, textModifier, style, color ->
                MiuixText(
                    text = label,
                    color = color ?: Color.Unspecified,
                    style = style,
                    modifier = textModifier,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    } else {
        AnimatedActionButtonCore(
            checked = checked,
            onCheckedChange = onCheckedChange,
            iconChecked = iconChecked,
            iconUnchecked = iconUnchecked,
            activeText = activeText,
            inactiveText = inactiveText,
            modifier = modifier,
            iconSize = 24.dp,
            textStyle = LegadoTheme.typography.labelMedium,
            textStartPadding = 8.dp,
            button = { buttonModifier, onToggle, content ->
                TonalToggleButton(
                    checked = checked,
                    onCheckedChange = onToggle,
                    modifier = buttonModifier,
                    contentPadding = PaddingValues(horizontal = 8.dp)
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
            text = { label, textModifier, style, color ->
                Text(
                    text = label,
                    modifier = textModifier,
                    style = style,
                    color = color ?: Color.Unspecified,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    }
}
