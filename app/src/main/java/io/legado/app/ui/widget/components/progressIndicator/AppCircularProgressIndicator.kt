package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    strokeWidth: Dp = 4.dp,
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixCircularProgressIndicator(
                modifier = modifier,
                progress = progress,
                strokeWidth = strokeWidth,
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {
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
}
