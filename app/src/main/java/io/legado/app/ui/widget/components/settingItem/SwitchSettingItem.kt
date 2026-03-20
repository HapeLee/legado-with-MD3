package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.widget.components.IconSwitch

@Composable
fun SwitchSettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    imageVector: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingItem(
        title = title,
        description = description,
        imageVector = imageVector,
        color = color,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailingContent = {
            IconSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}
