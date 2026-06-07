package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

@Composable
fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedIcon: ImageVector = Icons.Filled.Check,
    uncheckedIcon: ImageVector? = null,
    showIcon: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        IconSwitch(
            modifier = modifier,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            checkedIcon = checkedIcon,
            uncheckedIcon = uncheckedIcon,
            showIcon = showIcon
        )
    }
}

@Composable
fun TinySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedIcon: ImageVector = Icons.Filled.Check,
    uncheckedIcon: ImageVector? = null,
    showIcon: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.9f),
            enabled = enabled
        )
    } else {
        IconSwitch(
            modifier = Modifier.scale(0.8f),
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            checkedIcon = checkedIcon,
            uncheckedIcon = uncheckedIcon,
            showIcon = showIcon
        )
    }
}

@Composable
fun IconSwitch(
    modifier: Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    checkedIcon: ImageVector = Icons.Filled.Check,
    uncheckedIcon: ImageVector? = null,
    showIcon: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    Switch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        thumbContent = {
            if (!showIcon) return@Switch

            val icon = if (checked) checkedIcon else uncheckedIcon

            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}