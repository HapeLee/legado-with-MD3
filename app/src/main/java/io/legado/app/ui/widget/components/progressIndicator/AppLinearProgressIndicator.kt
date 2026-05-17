package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator

@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixLinearProgressIndicator(
            modifier = modifier,
            progress = progress
        )
    } else {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = modifier
            )
        } else {
            LinearProgressIndicator(
                modifier = modifier
            )
        }
    }
}
