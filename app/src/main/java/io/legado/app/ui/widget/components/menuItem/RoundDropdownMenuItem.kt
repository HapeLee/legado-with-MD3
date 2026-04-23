package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import io.legado.app.ui.widget.components.icon.AppIcon
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun RoundDropdownMenuItem(
    text: String,
    color: Color = Color.Unspecified,
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
    val hasCustomContentColor = color != Color.Unspecified

    if (isMiuix) {
        val dropdownColors = DropdownDefaults.dropdownColors()
        val (textColor, backgroundColor) = if (isSelected) {
            dropdownColors.selectedContentColor to dropdownColors.selectedContainerColor
        } else {
            dropdownColors.contentColor to dropdownColors.containerColor
        }
        val checkColor = if (isSelected) {
            dropdownColors.selectedContentColor
        } else {
            Color.Transparent
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .drawBehind { drawRect(backgroundColor) }
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(12.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    MiuixText(
                        modifier = Modifier.widthIn(max = 200.dp),
                        text = text,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                    )
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(12.dp))
                    trailingIcon()
                } else {
                    Spacer(Modifier.width(12.dp))
                    AppIcon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = checkColor
                    )
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val selectedContentColor = colorScheme.onPrimaryContainer
        val defaultContentColor = colorScheme.onSurface
        val contentColor = if (enabled) {
            when {
                hasCustomContentColor -> color
                isSelected -> selectedContentColor
                else -> defaultContentColor
            }
        } else {
            when {
                hasCustomContentColor -> color.copy(alpha = 0.38f)
                isSelected -> selectedContentColor.copy(alpha = 0.38f)
                else -> defaultContentColor.copy(alpha = 0.38f)
            }
        }
        val containerColor = colorScheme.surface

        Surface(
            onClick = onClick,
            modifier = modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            contentColor = contentColor,
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
                        style = LegadoTheme.typography.labelLargeEmphasized,
                        color = contentColor
                    )
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                } else {
                    Spacer(Modifier.width(8.dp))
                    AppIcon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) contentColor else Color.Transparent
                    )
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
