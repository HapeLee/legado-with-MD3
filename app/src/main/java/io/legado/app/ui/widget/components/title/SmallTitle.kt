package io.legado.app.ui.widget.components.title

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun AdaptiveTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val composeEngine = LegadoTheme.composeEngine
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            MiuixText(
                modifier = modifier,
                text = text,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        ComposeEngine.Material, ComposeEngine.Mochi -> {
            Text(
                text = text,
                style = LegadoTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
    }
}