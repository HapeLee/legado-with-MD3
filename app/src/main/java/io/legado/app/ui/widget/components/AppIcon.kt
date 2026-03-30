package io.legado.app.ui.widget.components


import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.LocalContentColor as MaterialLocalContentColor
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = currentContentColor(),
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    if (isMiuix) {
        MiuixIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    } else {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

@Composable
fun currentContentColor(): Color {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    return if (isMiuix) {
        MiuixLocalContentColor.current
    } else {
        MaterialLocalContentColor.current
    }
}