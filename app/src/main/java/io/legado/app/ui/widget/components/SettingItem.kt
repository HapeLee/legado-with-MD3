package io.legado.app.ui.widget.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
fun PreviewSettingItemList() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {

            SettingItem(
                title = "Only Title",
                description = null,
                option = null,
                onClick = {}
            )

            SettingItem(
                title = "Title + Description",
                description = "This is a description",
                option = null,
                onClick = {}
            )

            SettingItem(
                title = "Title + Option",
                description = null,
                option = "Dynamic option text",
                onClick = {}
            )

            SettingItem(
                title = "Title + Desc + Option",
                description = "Description content here",
                option = "Animated changing string",
                onClick = {}
            )

            SettingItem(
                imageVector = Icons.Default.Info,
                title = "With Icon",
                description = "Description",
                option = "Option",
                onClick = {}
            )

            SettingItem(
                title = "With Trailing",
                description = "Trailing icon on the right",
                option = null,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                onClick = {}
            )

        }
    }
}

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    title: String,
    description: String? = null,
    option: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp)
    ) {
        ListItem(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (dropdownMenu != null) {
                            showMenu = true
                            onLongClick?.invoke()
                        }
                    }
                ),
            leadingContent = {
                when {
                    painter != null -> Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    imageVector != null -> Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    option?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )

        if (dropdownMenu != null) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                dropdownMenu {
                    showMenu = false
                }
            }
        }
    }
}
