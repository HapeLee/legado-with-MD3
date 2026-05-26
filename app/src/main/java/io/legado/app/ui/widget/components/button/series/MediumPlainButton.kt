package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
internal fun MediumButtonContent(
    icon: ImageVector?,
    text: String?,
    contentDescription: String?
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (isMiuix) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription
                )
            }
        }
        if (text != null) {
            if (isMiuix) {
                MiuixText(text = text)
            } else {
                AppText(text = text)
            }
        }
    }
}

@Composable
fun MediumPlainButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String? = null,
    tint: androidx.compose.ui.graphics.Color = LegadoTheme.colorScheme.onSurface,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        if (icon != null && text == null) {
            MiuixIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint
                )
            }
        } else {
            Card(
                onClick = onClick,
                modifier = modifier,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = LegadoTheme.colorScheme.surfaceVariant,
                    contentColor = LegadoTheme.colorScheme.onSurfaceVariant
                )
            ) {
                MediumButtonContent(icon, text, contentDescription)
            }
        }
    } else {
        if (icon != null && text == null) {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint
                )
            }
        } else {
            Card(
                onClick = onClick,
                modifier = modifier,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = LegadoTheme.colorScheme.surfaceVariant,
                    contentColor = LegadoTheme.colorScheme.onSurfaceVariant
                )
            ) {
                MediumButtonContent(icon, text, contentDescription)
            }
        }
    }
}
