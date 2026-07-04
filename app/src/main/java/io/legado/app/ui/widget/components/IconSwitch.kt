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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
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
    showIcon: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine
    val switchStateDescription = stringResource(
        if (checked) R.string.a11y_on else R.string.a11y_off
    )

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (includeStateSemantics) {
                modifier.semantics {
                    stateDescription = switchStateDescription
                }
            } else {
                modifier
            },
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
            showIcon = showIcon,
            includeStateSemantics = includeStateSemantics
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
    showIcon: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    val composeEngine = LegadoTheme.composeEngine
    val switchStateDescription = stringResource(
        if (checked) R.string.a11y_on else R.string.a11y_off
    )

    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (includeStateSemantics) {
                modifier
                    .scale(0.9f)
                    .semantics {
                        stateDescription = switchStateDescription
                    }
            } else {
                modifier.scale(0.9f)
            },
            enabled = enabled
        )
    } else {
        IconSwitch(
            modifier = modifier.scale(0.8f),
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            checkedIcon = checkedIcon,
            uncheckedIcon = uncheckedIcon,
            showIcon = showIcon,
            includeStateSemantics = includeStateSemantics
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
    colors: SwitchColors = SwitchDefaults.colors(),
    includeStateSemantics: Boolean = true
) {
    val switchStateDescription = stringResource(
        if (checked) R.string.a11y_on else R.string.a11y_off
    )

    Switch(
        modifier = if (includeStateSemantics) {
            modifier.semantics {
                stateDescription = switchStateDescription
            }
        } else {
            modifier
        },
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
