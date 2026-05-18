package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.FloatingToolbar as MiuixFloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SelectionActions(
    val primaryAction: ActionItem,
    val secondaryActions: List<ActionItem> = emptyList(),
    val onClearSelection: (() -> Unit)? = null,
    val onSelectAll: () -> Unit,
    val onSelectInvert: () -> Unit,
)

data class ActionItem(
    val text: String,
    val icon: ImageVector = Icons.Default.ExpandMore,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    primaryAction: ActionItem,
    secondaryActions: List<ActionItem>
) {
    var showMenu by remember { mutableStateOf(false) }
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixFloatingToolbar(
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                MiuixIconButton(onClick = onSelectAll) {
                    MiuixIcon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select All"
                    )
                }
                MiuixIconButton(onClick = onSelectInvert) {
                    MiuixIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Invert Selection"
                    )
                }

                MiuixIconButton(
                    onClick = primaryAction.onClick,
                    backgroundColor = MiuixTheme.colorScheme.primary,
                    minWidth = 64.dp
                ) {
                    MiuixIcon(
                        imageVector = primaryAction.icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }

                if (secondaryActions.isNotEmpty()) {
                    Box {
                        MiuixIconButton(onClick = { showMenu = true }) {
                            MiuixIcon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions"
                            )
                        }
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            secondaryActions.forEach { action ->
                                RoundDropdownMenuItem(
                                    text = action.text,
                                    onClick = {
                                        action.onClick()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        HorizontalFloatingToolbar(
            modifier = modifier,
            expanded = true,
            leadingContent = {
                IconButton(onClick = onSelectAll) {
                    AppIcon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = "Select All"
                    )
                }
                IconButton(onClick = onSelectInvert) {
                    AppIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Invert Selection"
                    )
                }
            },
            trailingContent = {
                if (secondaryActions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            AppIcon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            secondaryActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { AppText(action.text) },
                                    leadingIcon = action.icon?.let { { AppIcon(imageVector = it, contentDescription = null) } },
                                    onClick = {
                                        action.onClick()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            content = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ),
                    tooltip = { PlainTooltip { AppText(primaryAction.text) } },
                    state = rememberTooltipState(),
                ) {
                    FilledIconButton(
                        modifier = Modifier.width(64.dp),
                        onClick = primaryAction.onClick,
                    ) {
                        AppIcon(imageVector = primaryAction.icon, contentDescription = null)
                    }
                }
            }
        )
    }
}
