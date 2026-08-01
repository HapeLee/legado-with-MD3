package io.legado.app.ui.rss.source.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RssSourceEditRoute(
    sourceUrl: String?,
    viewModel: RssSourceEditViewModel,
    onBack: (savedSourceUrl: String?) -> Unit,
    onLogin: (String) -> Unit,
    onDebug: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(sourceUrl, viewModel) {
        viewModel.onIntent(RssSourceEditIntent.Load(sourceUrl))
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RssSourceEditEffect.Finish -> onBack(effect.url.takeIf { it.isNotEmpty() })
                is RssSourceEditEffect.Debug -> onDebug(effect.url)
                is RssSourceEditEffect.Login -> onLogin(effect.url)
                is RssSourceEditEffect.Copy -> context.sendToClip(effect.text)
                is RssSourceEditEffect.Share -> context.share(effect.text)
                RssSourceEditEffect.ReadClipboard -> {
                    val text = context.getClipText()
                    if (text.isNullOrBlank()) context.toastOnUi("剪贴板为空")
                    else viewModel.onIntent(RssSourceEditIntent.Import(text))
                }

                is RssSourceEditEffect.Variable -> Unit
                is RssSourceEditEffect.Message -> context.toastOnUi(effect.text)
            }
        }
    }
    RssSourceEditScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = { viewModel.onIntent(RssSourceEditIntent.Back) },
    )
    AppAlertDialog(
        show = state.activeDialog == RssSourceEditDialog.ConfirmDiscard,
        onDismissRequest = { viewModel.onIntent(RssSourceEditIntent.DismissDialog) },
        title = stringResource(R.string.exit),
        text = stringResource(R.string.exit_no_save),
        confirmText = stringResource(R.string.yes),
        onConfirm = { viewModel.onIntent(RssSourceEditIntent.DiscardChanges) },
        dismissText = stringResource(R.string.no),
        onDismiss = { viewModel.onIntent(RssSourceEditIntent.DismissDialog) },
    )
}
