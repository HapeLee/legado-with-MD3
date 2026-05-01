package io.legado.app.ui.widget.components.button

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SmallMiuixButtonSize = 32.dp
private val SmallMiuixIconSize = 18.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun smallContainerSize() = IconButtonDefaults.extraSmallContainerSize(
    IconButtonDefaults.IconButtonWidthOption.Uniform
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val smallIconSize: androidx.compose.ui.unit.Dp
    get() = IconButtonDefaults.extraSmallIconSize

@Composable
private fun SmallNoMinTouchTarget(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallIconButton(
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
            enabled = enabled
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(smallIconSize),
            )
        }
    } else {
        SmallNoMinTouchTarget {
            IconButton(
                onClick = onClick,
                modifier = modifier.size(smallContainerSize()),
                enabled = enabled,
                shape = IconButtonDefaults.extraSmallRoundShape,
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(smallIconSize),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallOutlinedIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier.size(SmallMiuixButtonSize),
            enabled = enabled,
            backgroundColor = LegadoTheme.colorScheme.surfaceContainer
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(SmallMiuixIconSize)
            )
        }
    } else {
        SmallNoMinTouchTarget {
            OutlinedIconButton(
                onClick = onClick,
                modifier = modifier.size(smallContainerSize()),
                enabled = enabled,
                shapes = IconButtonDefaults.shapes(),
                border = ButtonDefaults.outlinedButtonBorder()
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(smallIconSize)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallTonalIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier.size(SmallMiuixButtonSize),
            enabled = enabled,
            backgroundColor = LegadoTheme.colorScheme.surfaceContainer
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(SmallMiuixIconSize)
            )
        }
    } else {
        SmallNoMinTouchTarget {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier.size(smallContainerSize()),
                enabled = enabled,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(smallIconSize)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallOutlinedIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) LegadoTheme.colorScheme.primaryContainer else LegadoTheme.colorScheme.surfaceContainer,
            animationSpec = tween(150),
            label = "MiuixToggleContainerColor"
        )

        val iconTint by animateColorAsState(
            targetValue = if (checked) LegadoTheme.colorScheme.onPrimaryContainer else LegadoTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(150),
            label = "MiuixToggleIconTint"
        )

        MiuixIconButton(
            onClick = { onCheckedChange(!checked) },
            modifier = modifier.size(SmallMiuixButtonSize),
            enabled = enabled,
            backgroundColor = containerColor
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(SmallMiuixIconSize)
            )
        }
    } else {
        val defaultShape = IconButtonDefaults.extraSmallRoundShape
        val pressedShape = IconButtonDefaults.extraSmallPressedShape
        val checkedShape = IconButtonDefaults.extraSmallSelectedRoundShape

        val toggleShapes = remember(defaultShape, checkedShape) {
            IconToggleButtonShapes(
                shape = defaultShape,
                pressedShape = pressedShape,
                checkedShape = checkedShape
            )
        }

        SmallNoMinTouchTarget {
            OutlinedIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier.size(smallContainerSize()),
                enabled = enabled,
                shapes = toggleShapes
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(smallIconSize),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallAnimatedActionButton(
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
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
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
            iconSize = 18.dp,
            textStyle = LegadoTheme.typography.labelSmall,
            textStartPadding = 6.dp,
            button = { buttonModifier, onToggle, content ->
                MiuixIconButton(
                    onClick = { onToggle(!checked) },
                    modifier = buttonModifier,
                    backgroundColor = containerColor
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                    style = style,
                    color = color ?: Color.Unspecified,
                    modifier = textModifier,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    } else {
        SmallNoMinTouchTarget {
            AnimatedActionButtonCore(
                checked = checked,
                onCheckedChange = onCheckedChange,
                iconChecked = iconChecked,
                iconUnchecked = iconUnchecked,
                activeText = activeText,
                inactiveText = inactiveText,
                modifier = modifier.height(36.dp),
                iconSize = 16.dp,
                textStyle = LegadoTheme.typography.labelSmall,
                textStartPadding = 6.dp,
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
}
