package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipText: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    contentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
    content: (@Composable () -> Unit)? = null
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val fabContent: @Composable () -> Unit = {
        if (icon != null) {
            if (isMiuix){
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = tooltipText,
                    tint = containerColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltipText
                )
            }

        } else {
            content?.invoke()
        }
    }

    if (isMiuix) {
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = fabContent,
            containerColor = LegadoTheme.colorScheme.surfaceContainer
        )
    } else {
        if (tooltipText != null) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { AppText(tooltipText) } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    content = fabContent
                )
            }
        } else {
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                containerColor = containerColor,
                contentColor = contentColor,
                content = fabContent
            )
        }
    }
}

data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppFloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        Column(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEachIndexed { index, (icon, label, action) ->
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(delayMillis = index * 40)) +
                            scaleIn(tween(delayMillis = index * 40), initialScale = 0.8f),
                    exit = fadeOut(tween(80)) + scaleOut(tween(80), targetScale = 0.8f)
                ) {
                    Button(
                        onClick = {
                            action()
                            onExpandedChange(false)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixIcon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MiuixText(
                                text = label,
                                style = MiuixTheme.textStyles.subtitle
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                MiuixFloatingActionButton(
                    onClick = { onExpandedChange(!expanded) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        MiuixIcon(
                            imageVector = if (expanded) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.menu), color = Color.White)
                    }
                }
            }
        }
    } else {
        FloatingActionButtonMenu(
            modifier = modifier,
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    modifier = Modifier
                        .animateFloatingActionButton(
                            visible = visible,
                            alignment = Alignment.BottomEnd,
                        )
                        .focusRequester(focusRequester),
                    checked = expanded,
                    onCheckedChange = { onExpandedChange(!expanded) },
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.Close
                            else Icons.AutoMirrored.Filled.MenuOpen
                        }
                    }
                    Icon(
                        imageVector = imageVector,
                        contentDescription = "Menu",
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        ) {
            items.forEach { (icon, label, action) ->
                FloatingActionButtonMenuItem(
                    onClick = {
                        action()
                        onExpandedChange(false)
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    text = { Text(text = label) }
                )
            }
        }
    }
}
