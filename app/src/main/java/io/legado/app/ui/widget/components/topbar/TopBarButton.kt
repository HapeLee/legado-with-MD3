package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.button.AnimatedActionButtonCore
import io.legado.app.ui.widget.components.button.AnimatedIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@Composable
private fun TopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = GlassTopAppBarDefaults.controlContainerColor(),
            contentColor = LegadoTheme.colorScheme.onSurface
        )
    ) {
        AnimatedIcon(
            modifier = Modifier.size(20.dp),
            imageVector = imageVector,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun TopBarNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = AppIcons.Back,
    contentDescription: String? = stringResource(id = R.string.back)
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        TopBarButton(
            onClick = onClick,
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val enableProgressive = ThemeConfig.enableProgressiveBlur
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        if (enableProgressive) {
            TopBarButton(
                onClick = onClick,
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = modifier.padding(end = 12.dp)
            )
        } else {
            IconButton(
                onClick = onClick,
                modifier = modifier
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarAnimatedActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(150),
            label = "MiuixActionButtonContainer"
        )

        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
            animationSpec = tween(150),
            label = "MiuixActionButtonContainer"
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
                    tint = tint ?: Color.Unspecified,
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = iconModifier
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
            modifier = modifier.height(36.dp),
            iconSize = 20.dp,
            textStyle = LegadoTheme.typography.labelMedium,
            textStartPadding = 8.dp,
            button = { buttonModifier, onToggle, content ->
                ToggleButton(
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
                AnimatedContent(
                    targetState = imageVector,
                    label = "IconAnimation"
                ) { targetIcon ->
                    AnimatedIcon(
                        modifier = iconModifier,
                        imageVector = targetIcon,
                        contentDescription = null
                    )
                }
            },
            text = { label, textModifier, style, color ->
                Text(
                    text = label,
                    style = style,
                    color = color ?: Color.Unspecified,
                    modifier = textModifier,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    }
}
