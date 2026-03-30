package io.legado.app.ui.widget.components.icon

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
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