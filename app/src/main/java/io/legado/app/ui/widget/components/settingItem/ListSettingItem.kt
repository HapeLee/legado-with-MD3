package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun DropdownListSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    onValueChange: (String) -> Unit
) {
    val currentEntry = displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

    SettingItem(
        title = title,
        description = description,
        option = currentEntry,
        imageVector = imageVector,
        onClick = { },
        dropdownMenu = { onDismiss ->
            displayEntries.forEachIndexed { index, display ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        onValueChange(entryValues[index])
                        onDismiss()
                    },
                    trailingIcon = if (selectedValue == entryValues[index]) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }
    )
}