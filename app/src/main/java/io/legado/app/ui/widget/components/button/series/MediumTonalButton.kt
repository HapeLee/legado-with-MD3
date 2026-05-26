package io.legado.app.ui.widget.components.button.series

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@Composable
fun MediumTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        if (icon != null && text == null) {
            MiuixIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                backgroundColor = LegadoTheme.colorScheme.surfaceContainer
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
                    color = LegadoTheme.colorScheme.surfaceContainer,
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
                enabled = enabled
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
                enabled = enabled
            ) {
                MediumButtonContent(icon, text, contentDescription)
            }
        }
    }
}
