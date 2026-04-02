package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import io.legado.app.ui.widget.components.icon.AppIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun RoundDropdownMenuItem(
    text: String,
    color: Color = LegadoTheme.colorScheme.surface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val interaction = interactionSource ?: remember { MutableInteractionSource() }

    if (isMiuix) {
        val backgroundColor = if (isSelected) {
            LegadoTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }

        val contentColor = if (isSelected) {
            LegadoTheme.colorScheme.onPrimaryContainer
        } else {
            LegadoTheme.colorScheme.onSurface
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .drawBehind { drawRect(backgroundColor) }
                .clickable(
                    interactionSource = interaction,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MiuixTheme.textStyles.body1
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(12.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    MiuixText(
                        text = text,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(12.dp))
                    trailingIcon()
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }

        Surface(
            onClick = onClick,
            modifier = modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = colorScheme.surface,
            interactionSource = interaction
        ) {
            Row(
                modifier = Modifier
                    .padding(contentPadding)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 120.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(12.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        modifier = Modifier.widthIn(max = 200.dp),
                        text = text,
                        style = LegadoTheme.typography.bodyMediumEmphasized
                    )
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

@Composable
fun MenuItemIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified
) {
    AppIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(18.dp),
        tint = tint
    )
}
