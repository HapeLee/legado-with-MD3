package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.itos.coui.input.CouiHapticFeedback
import com.itos.coui.input.performCouiHapticFeedback
import com.itos.coui.switch.Switch as CouiSwitch
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
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
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    SplicedColumnDivider()

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            SwitchPreference(
                title = title,
                summary = description,
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled,
            )
        }
        ComposeEngine.Oxygen, ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            val couiView = if (composeEngine == ComposeEngine.Coui) LocalView.current else null
            SettingItem(
                modifier = modifier,
                title = title,
                description = description,
                imageVector = imageVector,
                color = color,
                enabled = enabled,
                semanticRole = Role.Switch,
                semanticToggleState = checked,
                onClick = {
                    if (enabled) {
                        couiView?.performCouiHapticFeedback(CouiHapticFeedback.Switch)
                        onCheckedChange(!checked)
                    }
                },
                trailingContent = {
                    if (composeEngine == ComposeEngine.Coui) {
                        // 整行提供点击热区和语义，滑块只处理拖动。
                        CouiSwitch(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = enabled,
                            onDragCheckedChange = onCheckedChange,
                        )
                    } else {
                        AdaptiveSwitch(
                            modifier = Modifier.clearAndSetSemantics { },
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled,
                            includeStateSemantics = false
                        )
                    }
                }
            )
        }
    }
}
