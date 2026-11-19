package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults as MiuixIconButtonDefaults

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    //M3
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    shape: Shape = IconButtonDefaults.standardShape,
    // MIUIX
    holdDownState: Boolean = false,
    miuixBackgroundColor: Color = Color.Unspecified,
    miuixCornerRadius: Dp? = null,
    miuixMinHeight: Dp? = null,
    miuixMinWidth: Dp? = null,
    content: @Composable () -> Unit
) {

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Oxygen -> {
            io.oxygenx.material3.ProvideMaterial3Content {
                io.oxygenx.ui.basic.IconButton(
                    onClick = onClick,
                    modifier = modifier.clip(shape).background(if (enabled) colors.containerColor else colors.disabledContainerColor),
                    enabled = enabled,
                    contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
                    interactionSource = interactionSource,
                    content = content,
                )
            }
        }
        ComposeEngine.Miuix -> {
            MiuixIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                holdDownState = holdDownState,
                backgroundColor = miuixBackgroundColor,
                cornerRadius = miuixCornerRadius ?: MiuixIconButtonDefaults.CornerRadius,
                minHeight = miuixMinHeight ?: MiuixIconButtonDefaults.MinHeight,
                minWidth = miuixMinWidth ?: MiuixIconButtonDefaults.MinWidth,
                content = content
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                shape = shape,
                content = content
            )
        }
    }
}
