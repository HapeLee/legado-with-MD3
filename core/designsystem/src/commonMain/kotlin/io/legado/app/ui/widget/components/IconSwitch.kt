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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LocalComposeEngine
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

// 从 `:core:ui` 搬来（M1-2）。这是 `:core:designsystem` 里第一个**真双引擎组件**：
// Miuix 侧走 `MiuixSwitch`，Material3 侧走本文件的 `IconSwitch`（带 thumb 图标的 M3 Switch）。
// 搬动只改了引擎判定来源（`LegadoTheme.composeEngine` → `LocalComposeEngine`），包名不变，
// 因此 `:core:ui` 的 SelectionItemCard / SwitchSettingItem 与 `:feature:tagrules` 的
// HighlightTagRuleEditSheet 三处调用方 import 零改动。
//
// 为什么它能进 commonMain：Material3 走 `org.jetbrains.compose.material3`、图标走
// `material-icons-extended`、Miuix 走 `miuix-core`，三者都有 desktop 产物。

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
    if (LocalComposeEngine.current == ComposeEngine.Miuix) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (includeStateSemantics) {
                modifier.semantics {
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
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
    if (LocalComposeEngine.current == ComposeEngine.Miuix) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (includeStateSemantics) {
                modifier
                    .scale(0.9f)
                    .semantics {
                        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
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
    Switch(
        modifier = if (includeStateSemantics) {
            modifier.semantics {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
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
