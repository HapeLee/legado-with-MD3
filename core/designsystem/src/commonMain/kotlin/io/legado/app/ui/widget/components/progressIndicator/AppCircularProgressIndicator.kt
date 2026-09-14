package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    strokeWidth: Dp = 4.dp,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixCircularProgressIndicator(
            modifier = modifier,
            progress = progress,
            strokeWidth = strokeWidth,
        )
    } else {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = modifier,
                strokeWidth = strokeWidth,
            )
        } else {
            CircularProgressIndicator(
                modifier = modifier,
                strokeWidth = strokeWidth,
            )
        }
    }
}
