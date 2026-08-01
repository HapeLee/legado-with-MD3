package io.legado.app.ui.login

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.constant.BookType
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SourceLoginRoute(
    request: SourceLoginIntent.Initialize,
    viewModel: SourceLoginViewModel,
    host: AppCompatActivity,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(request) {
        viewModel.onIntent(request)
    }
    LaunchedEffect(state.loading, viewModel.source) {
        val source = viewModel.source ?: return@LaunchedEffect
        if (!state.loading) {
            viewModel.attachJsExtensions(
                SourceLoginJsExtensions(
                    activity = host,
                    source = source,
                    bookType = request.type.toBookType(),
                    callback = object : SourceLoginJsExtensions.Callback {
                        override fun upUiData(data: Map<String, Any?>?) {
                            viewModel.updateFromJs(data)
                        }

                        override fun reUiView(deltaUp: Boolean) {
                            viewModel.rebuildFromJs()
                        }
                    },
                )
            )
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                SourceLoginEffect.Finish -> onBack()
                is SourceLoginEffect.ShowMessage -> host.toastOnUi(effect.message)
                is SourceLoginEffect.OpenExternalUrl -> host.openUrl(effect.url)
                is SourceLoginEffect.CopyText -> host.sendToClip(effect.text)
            }
        }
    }
    SourceLoginSheetHost(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenExternalUrl = host::openUrl,
    )
}

private fun SourceLoginType.toBookType(): Int = when (this) {
    SourceLoginType.ReadingBook -> BookType.text
    SourceLoginType.AudioBook -> BookType.audio
    else -> 0
}
