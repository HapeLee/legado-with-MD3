package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ActionItem(
    val text: String,
    val icon: @Composable (() -> Unit)? = null,
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

    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        leadingContent = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select All"
                )
            }
            IconButton(onClick = onSelectInvert) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Invert Selection"
                )
            }
        },
        trailingContent = {
            if (secondaryActions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
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
                                text = { Text(action.text) },
                                leadingIcon = action.icon,
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
                tooltip = { PlainTooltip { Text(primaryAction.text) } },
                state = rememberTooltipState(),
            ) {
                FilledIconButton(
                    modifier = Modifier.width(64.dp),
                    onClick = primaryAction.onClick,
                ) {
                    primaryAction.icon?.invoke()
                }
            }
        }
    )
}