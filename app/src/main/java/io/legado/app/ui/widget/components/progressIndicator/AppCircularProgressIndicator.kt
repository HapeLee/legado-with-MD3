package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixCircularProgressIndicator(
            modifier = modifier,
            progress = progress
        )
    } else {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = modifier
            )
        } else {
            CircularProgressIndicator(
                modifier = modifier
            )
        }
    }
}
