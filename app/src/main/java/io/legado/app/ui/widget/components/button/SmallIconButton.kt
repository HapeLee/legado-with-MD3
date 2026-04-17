package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import kotlinx.coroutines.delay
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
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick
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
                modifier = Modifier.size(smallContainerSize()),
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
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    if (isMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier.size(SmallMiuixButtonSize),
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
                modifier = Modifier.size(smallContainerSize()),
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
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    if (isMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier.size(SmallMiuixButtonSize),
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
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    if (isMiuix) {

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
            modifier = modifier
                .size(SmallMiuixButtonSize),
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

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            OutlinedIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier.size(smallContainerSize()),
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
    var showText by remember { mutableStateOf(false) }
    var lastCheckedState by remember { mutableStateOf(checked) }

    LaunchedEffect(showText) {
        if (showText) {
            delay(1000)
            showText = false
        }
    }

    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    if (isMiuix) {
        val containerColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
            animationSpec = tween(150),
            label = "MiuixActionButtonContainer"
        )

        MiuixIconButton(
            onClick = {
                lastCheckedState = !checked
                onCheckedChange(!checked)
                showText = true
            },
            backgroundColor = containerColor
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                MiuixIcon(
                    imageVector = if (checked) iconChecked else iconUnchecked,
                    contentDescription = null
                )

                AnimatedVisibility(
                    visible = showText
                ) {
                    MiuixText(
                        text = if (lastCheckedState) activeText else inactiveText,
                        style = LegadoTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    } else {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            TonalToggleButton(
                checked = checked,
                onCheckedChange = {
                    lastCheckedState = it
                    onCheckedChange(it)
                    showText = true
                },
                modifier = modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedIcon(
                        modifier = Modifier.size(16.dp),
                        imageVector = if (checked) iconChecked else iconUnchecked,
                        contentDescription = null
                    )

                    AnimatedVisibility(
                        visible = showText
                    ) {
                        Text(
                            text = if (lastCheckedState) activeText else inactiveText,
                            style = LegadoTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
