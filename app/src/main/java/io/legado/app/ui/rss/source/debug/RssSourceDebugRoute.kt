package io.legado.app.ui.rss.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RssSourceDebugRoute(
    sourceUrl: String?,
    viewModel: RssSourceDebugViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(sourceUrl, viewModel) {
        viewModel.onIntent(RssSourceDebugIntent.Load(sourceUrl))
        viewModel.effects.collectLatest { effect ->
            if (effect is RssSourceDebugEffect.ShowMessage) context.toastOnUi(effect.value)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onIntent(RssSourceDebugIntent.Stop)
    }
    RssSourceDebugScreen(state, viewModel::onIntent, onBack)
}
