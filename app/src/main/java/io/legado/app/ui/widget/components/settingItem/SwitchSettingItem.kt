package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AdaptiveSwitch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch


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
    val composeEngine = LegadoTheme.composeEngine

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        val toggleAction = {
            if (enabled) onCheckedChange(!checked)
        }

        BasicComponent(
            title = title,
            summary = description,
            enabled = enabled,
            onClick = toggleAction,
            endActions = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            onClick = { if (enabled) onCheckedChange(!checked) },
            trailingContent = {
                AdaptiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
    }
}
