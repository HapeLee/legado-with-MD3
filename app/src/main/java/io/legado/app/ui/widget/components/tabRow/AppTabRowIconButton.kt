package io.legado.app.ui.widget.components.tabRow

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.chip.ChipTabButton
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiContentColor
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import io.legado.app.ui.widget.components.button.series.SmallOutlinedButton
import io.legado.app.ui.widget.components.button.series.SmallToggleButton
import io.legado.app.ui.widget.components.coui.rememberCouiCapsuleRenderer
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.mochi.keyboardClick
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.theme.LocalContentColor as MochiContentColor

@Composable
fun AppTabRowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
) {
    val buttonModifier = modifier.semantics {
        if (checked != null) selected = checked
    }
    when (LegadoTheme.composeEngine) {
        ComposeEngine.Coui -> if (ThemeResolver.isCouiSupported()) {
            CouiTabIconButton(icon, contentDescription, onClick, buttonModifier, checked == true)
            return
        }
        ComposeEngine.Miuix -> {
            val colors = MiuixTheme.colorScheme
            val content: @Composable () -> Unit = {
                AppIcon(
                    icon, contentDescription, Modifier.size(20.dp),
                    tint = if (checked == true) colors.primary else colors.onSurface,
                )
            }
            if (isMiuixHyperOs4Enabled()) {
                GlassIconButton(
                    onClick = onClick,
                    modifier = buttonModifier.semantics { role = Role.Button },
                    backdrop = LocalMiuixBarBackdrop.current,
                    size = 36.dp,
                    content = content,
                )
            } else {
                MiuixIconButton(
                    onClick = onClick,
                    modifier = buttonModifier,
                    holdDownState = checked == true,
                    backgroundColor = colors.surfaceContainer,
                    minWidth = 36.dp,
                    minHeight = 36.dp,
                    content = content,
                )
            }
            return
        }
        ComposeEngine.Mochi -> {
            MochiIconButton(
                onClick = onClick,
                size = 36.dp,
                style = if (checked == true) MochiButtonStyle.Filled else MochiButtonStyle.Outlined,
                modifier = buttonModifier.keyboardClick(true, onClick).semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            ) {
                AppIcon(icon, null, Modifier.size(20.dp), MochiContentColor.current)
            }
            return
        }
        ComposeEngine.Material -> Unit
    }
    if (checked == null) {
        SmallOutlinedButton(onClick, buttonModifier, icon = icon, contentDescription = contentDescription)
    } else {
        SmallToggleButton(checked, { onClick() }, buttonModifier, icon = icon, contentDescription = contentDescription)
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun CouiTabIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    checked: Boolean,
) {
    val colors = CouiTheme.colorScheme
    val backdrop = currentCouiTabBackdrop()
    val renderer = if (backdrop != null) rememberCouiCapsuleRenderer() else null
    if (backdrop != null && renderer != null) {
        ChipTabButton(modifier, onClick, contentDescription, backdrop, renderer = renderer) {
            AppIcon(icon, null, Modifier.size(20.dp), if (checked) colors.accent else LocalCouiContentColor.current)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val background = if (checked) colors.accent.copy(alpha = 0.16f) else colors.outlineVariant.copy(alpha = 0.1f)
        Box(
            modifier = modifier.size(32.dp).clip(CouiCapsuleShape).background(background)
                .couiButtonFeedback(interactionSource = interactionSource)
                .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription, Modifier.size(20.dp), if (checked) colors.accent else colors.onSurface)
        }
    }
}
