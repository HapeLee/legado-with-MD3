package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediumTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    if (onLongClick != null) {
        // Long-click variant: wrap in Box with combinedClickable
        Box(
            modifier = modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        ) {
            if (ThemeResolver.isMiuixEngine(composeEngine)) {
                if (icon != null && text == null) {
                    MiuixIcon(
                        imageVector = icon,
                        contentDescription = contentDescription
                    )
                } else {
                    MediumButtonContent(icon, text, contentDescription)
                }
            } else {
                if (icon != null && text == null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MediumButtonContent(icon, text, contentDescription)
                }
            }
        }
    } else {
        // Standard click-only variant
        if (ThemeResolver.isMiuixEngine(composeEngine)) {
            if (icon != null && text == null) {
                MiuixIconButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    backgroundColor = LegadoTheme.colorScheme.surfaceContainerLow
                ) {
                    MiuixIcon(
                        imageVector = icon,
                        contentDescription = contentDescription
                    )
                }
            } else {
                Card(
                    onClick = onClick,
                    modifier = modifier,
                    showIndication = true,
                    colors = CardDefaults.defaultColors(
                        color = LegadoTheme.colorScheme.surfaceContainerLow,
                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    MediumButtonContent(icon, text, contentDescription)
                }
            }
        } else {
            if (icon != null && text == null) {
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    modifier = modifier,
                    enabled = enabled,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                ) {
                    MediumButtonContent(icon, text, contentDescription)
                }
            }
        }
    }
}
