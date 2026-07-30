package io.legado.app.ui.book.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.legado.app.base.BaseComposeActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

class BookSourceDebugActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        val viewModel = koinViewModel<BookSourceDebugViewModel>()
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        LaunchedEffect(Unit) {
            viewModel.onIntent(BookSourceDebugIntent.Load(intent.getStringExtra("key")))
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is BookSourceDebugEffect.ShowMessage -> toastOnUi(effect.message)
                }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            viewModel.onIntent(BookSourceDebugIntent.Stop)
        }
        BookSourceDebugScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = ::finish,
        )
    }
}
