package io.legado.app.ui.book.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BookSourceDebugRoute(
    sourceUrl: String?,
    viewModel: BookSourceDebugViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(sourceUrl, viewModel) {
        viewModel.onIntent(BookSourceDebugIntent.Load(sourceUrl))
        viewModel.effects.collectLatest { effect ->
            if (effect is BookSourceDebugEffect.ShowMessage) context.toastOnUi(effect.message)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onIntent(BookSourceDebugIntent.Stop)
    }
    BookSourceDebugScreen(state, viewModel::onIntent, onBack)
}
