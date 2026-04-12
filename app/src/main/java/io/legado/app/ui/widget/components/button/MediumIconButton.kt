package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import kotlinx.coroutines.delay
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
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier
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
            modifier = modifier
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
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    if (isMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
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
            border = ButtonDefaults.outlinedButtonBorder()
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
fun MediumOutlinedButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    if (isMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            backgroundColor = LegadoTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiuixIcon(
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
                MiuixText(text = text)
            }
        }
    } else {
        OutlinedIconButton(
            onClick = onClick,
            border = ButtonDefaults.outlinedButtonBorder()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
                Text(text = text)
            }
        }
    }
}

@Composable
fun MediumTonalIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    if (isMiuix) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
            backgroundColor = LegadoTheme.colorScheme.surfaceContainer
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
fun MediumOutlinedIconToggleButton(
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
            modifier = modifier,
            backgroundColor = containerColor
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = iconTint
            )
        }
    } else {
        OutlinedIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier
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
            targetValue = if (checked) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh,
            animationSpec = tween(150),
            label = "MiuixActionButtonContainer"
        )

        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
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
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                MiuixIcon(
                    tint = contentColor,
                    imageVector = if (checked) iconChecked else iconUnchecked,
                    contentDescription = null
                )

                AnimatedVisibility(
                    visible = showText
                ) {
                    MiuixText(
                        text = if (lastCheckedState) activeText else inactiveText,
                        color = contentColor,
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    } else {
        TonalToggleButton(
            checked = checked,
            onCheckedChange = {
                lastCheckedState = it
                onCheckedChange(it)
                showText = true
            },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedIcon(
                    imageVector = if (checked) iconChecked else iconUnchecked,
                    contentDescription = null
                )

                AnimatedVisibility(
                    visible = showText
                ) {
                    Text(
                        text = if (lastCheckedState) activeText else inactiveText,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
