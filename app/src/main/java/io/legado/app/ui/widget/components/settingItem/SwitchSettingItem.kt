package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.preference.SwitchPreference


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
    val switchStateDescription = stringResource(
        if (checked) R.string.a11y_on else R.string.a11y_off
    )
    SplicedColumnDivider()

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        SwitchPreference(
            title = title,
            summary = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = switchStateDescription
                if (!enabled) disabled()
            },
            enabled = enabled,
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            enabled = enabled,
            semanticRole = Role.Switch,
            semanticStateDescription = switchStateDescription,
            onClick = { if (enabled) onCheckedChange(!checked) },
            trailingContent = {
                AdaptiveSwitch(
                    modifier = Modifier.clearAndSetSemantics { },
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    includeStateSemantics = false
                )
            }
        )
    }
}
