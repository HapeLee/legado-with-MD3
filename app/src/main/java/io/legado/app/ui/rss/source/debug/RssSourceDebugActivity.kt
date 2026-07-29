package io.legado.app.ui.rss.source.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.legado.app.base.BaseComposeActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

class RssSourceDebugActivity : BaseComposeActivity() {
    @Composable override fun Content() {
        val vm = koinViewModel<RssSourceDebugViewModel>(); val state = vm.uiState.collectAsStateWithLifecycle().value
        LaunchedEffect(Unit) { vm.onIntent(RssSourceDebugIntent.Load(intent.getStringExtra("key"))); vm.effects.collectLatest { if (it is RssSourceDebugEffect.ShowMessage) toastOnUi(it.value) } }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onIntent(RssSourceDebugIntent.Stop) }
        RssSourceDebugScreen(state, vm::onIntent, ::finish)
    }
}
