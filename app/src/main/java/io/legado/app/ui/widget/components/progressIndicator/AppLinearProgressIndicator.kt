package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import androidx.compose.material3.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator

@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixLinearProgressIndicator(
                modifier = modifier,
                progress = progress
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
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
}
