package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.SplicedColumnDivider

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
    // 未注入 ⇒ 走下面那条原本就存在的 Material3 渲染路径；理由见契约 KDoc
    // （`LocalComposeEngine` 默认 Material3，Miuix 引擎只由 Android 侧提供 ⇒ desktop 上不可达）。
    val miuixRenderer = MiuixPreferenceRendererProvider.current
    SplicedColumnDivider()

    if (miuixRenderer != null && ThemeResolver.isMiuixEngine(composeEngine)) {
        miuixRenderer.switchPreference(
            title = title,
            summary = description,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    } else {
        SettingItem(
            title = title,
            description = description,
            imageVector = imageVector,
            color = color,
            enabled = enabled,
            semanticRole = Role.Switch,
            semanticToggleState = checked,
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
