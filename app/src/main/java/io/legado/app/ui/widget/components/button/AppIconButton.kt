package io.legado.app.ui.widget.components.button

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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
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
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
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
    } else {
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
