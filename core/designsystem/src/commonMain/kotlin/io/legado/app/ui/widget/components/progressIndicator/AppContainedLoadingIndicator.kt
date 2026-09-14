package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator as MiuixInfiniteProgressIndicator

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppContainedLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixInfiniteProgressIndicator(
            modifier = modifier,
        )
    } else {
        ContainedLoadingIndicator(
            modifier = modifier
        )
    }
}
