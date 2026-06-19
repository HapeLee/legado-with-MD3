package io.legado.app.ui.widget.components.button.series

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val SeriesIconSize: Dp
    get() = IconButtonDefaults.mediumIconSize
internal val MediumSeriesIconButtonSize = DpSize(40.dp, 40.dp)
internal val MediumSeriesIconSize = SeriesIconSize

internal enum class SeriesIconButtonStyle {
    Plain,
    Tonal,
    Outlined
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    size: DpSize? = null,
    style: SeriesIconButtonStyle = SeriesIconButtonStyle.Plain,
    contentColor: Color = LegadoTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null,
    selectedContainerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
    content: @Composable (Color) -> Unit
) {
    var lastEnabled by remember { mutableStateOf(enabled) }
    var lastSelected by remember { mutableStateOf(selected) }
    val isStateChanged = enabled != lastEnabled || selected != lastSelected

    SideEffect {
        lastEnabled = enabled
        lastSelected = selected
    }

    val animSpec = if (isStateChanged) tween<Color>(150) else snap()

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledContainerColor(style)
            selected -> selectedContainerColor
            else -> containerColor ?: containerColor(style)
        },
        animationSpec = animSpec,
        label = "SeriesIconContainerColor"
    )
    val resolvedContentColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledContentColor(contentColor)
            selected -> selectedContentColor
            else -> contentColor
        },
        animationSpec = animSpec,
        label = "SeriesIconContentColor"
    )
    val shape = IconButtonDefaults.extraSmallRoundShape
    val border = borderStroke(style, enabled)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .clip(shape)
            .background(containerColor, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .semantics {
                if (selected) {
                    this.selected = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content(resolvedContentColor)
    }
}

@Composable
internal fun SeriesIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    size: DpSize,
    iconSize: Dp,
    style: SeriesIconButtonStyle = SeriesIconButtonStyle.Plain,
    contentColor: Color = LegadoTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null,
    selectedContainerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
) {
    SeriesButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        onLongClick = onLongClick,
        size = size,
        style = style,
        contentColor = contentColor,
        containerColor = containerColor,
        selectedContainerColor = selectedContainerColor,
        selectedContentColor = selectedContentColor
    ) { resolvedContentColor ->
        SeriesIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = resolvedContentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
internal fun SeriesButtonContent(
    icon: ImageVector?,
    text: String?,
    contentDescription: String?,
    iconSize: Dp,
    textStyle: TextStyle,
    contentColor: Color,
    padding: PaddingValues,
    spacing: Dp
) {
    val hasText = text != null
    Row(
        modifier = Modifier.padding(if (hasText) padding else PaddingValues(0.dp)),
        horizontalArrangement = Arrangement.spacedBy(
            if (hasText) spacing else 0.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            SeriesIcon(
                icon = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
        if (text != null) {
            if (ThemeResolver.isMiuixEngine(composeEngine)) {
                MiuixText(
                    text = text,
                    style = textStyle,
                    color = contentColor
                )
            } else {
                AppText(
                    text = text,
                    style = textStyle,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
internal fun SeriesAnimatedButtonContent(
    icon: ImageVector,
    text: String?,
    contentDescription: String?,
    showText: Boolean,
    iconSize: Dp,
    textStyle: TextStyle,
    contentColor: Color,
    padding: PaddingValues,
    spacing: Dp
) {
    val hasText = text != null
    Row(
        modifier = Modifier.padding(if (hasText) padding else PaddingValues(0.dp)),
        horizontalArrangement = Arrangement.spacedBy(
            if (hasText) spacing else 0.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeriesIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
        AnimatedVisibility(visible = showText && text != null) {
            if (text != null) {
                if (ThemeResolver.isMiuixEngine(composeEngine)) {
                    MiuixText(
                        text = text,
                        style = textStyle,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    AppText(
                        text = text,
                        style = textStyle,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}

internal fun squareSize(size: Dp) = DpSize(size, size)

@Composable
private fun containerColor(style: SeriesIconButtonStyle): Color {
    return when (style) {
        SeriesIconButtonStyle.Plain -> Color.Transparent
        SeriesIconButtonStyle.Tonal,
        SeriesIconButtonStyle.Outlined -> LegadoTheme.colorScheme.surfaceContainerLow
    }
}

@Composable
private fun disabledContainerColor(style: SeriesIconButtonStyle): Color {
    return when (style) {
        SeriesIconButtonStyle.Plain -> Color.Transparent
        SeriesIconButtonStyle.Tonal,
        SeriesIconButtonStyle.Outlined -> LegadoTheme.colorScheme.outlineVariant
    }
}

private fun disabledContentColor(contentColor: Color): Color {
    return contentColor.copy(alpha = 0.38f)
}

@Composable
private fun borderStroke(style: SeriesIconButtonStyle, enabled: Boolean): BorderStroke? {
    return when (style) {
        SeriesIconButtonStyle.Outlined -> if (enabled) {
            BorderStroke(1.dp, LegadoTheme.colorScheme.outlineVariant)
        } else {
            BorderStroke(1.dp, LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }

        SeriesIconButtonStyle.Plain,
        SeriesIconButtonStyle.Tonal -> null
    }
}
