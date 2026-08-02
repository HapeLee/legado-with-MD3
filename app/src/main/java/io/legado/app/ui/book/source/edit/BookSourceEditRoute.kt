package io.legado.app.ui.book.source.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BookSourceEditRoute(
    sourceUrl: String?,
    viewModel: BookSourceEditViewModel,
    onBack: (savedSourceUrl: String?) -> Unit,
    onLogin: (String) -> Unit,
    onDebug: (String) -> Unit,
    onSearch: (SearchScope) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(sourceUrl, viewModel) {
        viewModel.onIntent(BookSourceEditIntent.Load(sourceUrl))
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookSourceEditEffect.Finish ->
                    onBack(effect.sourceUrl.takeIf { it.isNotEmpty() })

                is BookSourceEditEffect.OpenDebug -> onDebug(effect.sourceUrl)
                is BookSourceEditEffect.OpenLogin -> onLogin(effect.sourceUrl)
                is BookSourceEditEffect.OpenSearch ->
                    GSON.fromJsonObject<BookSource>(effect.sourceJson).getOrNull()?.let { source ->
                        onSearch(SearchScope(source))
                    }

                is BookSourceEditEffect.CopyText -> context.sendToClip(effect.text)
                is BookSourceEditEffect.ShareText -> context.share(effect.text)
                BookSourceEditEffect.ReadClipboard -> {
                    val text = context.getClipText()
                    if (text.isNullOrBlank()) context.toastOnUi("剪贴板为空")
                    else viewModel.onIntent(BookSourceEditIntent.ImportText(text))
                }

                is BookSourceEditEffect.OpenVariable -> Unit
                is BookSourceEditEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }

    BookSourceEditScreen(
        state = state,
        menuExpanded = menuExpanded,
        onMenuExpandedChange = { menuExpanded = it },
        onIntent = viewModel::onIntent,
    )
    AppAlertDialog(
        show = state.activeDialog == BookSourceEditDialog.ConfirmDiscard,
        onDismissRequest = { viewModel.onIntent(BookSourceEditIntent.DismissDialog) },
        title = stringResource(R.string.exit),
        text = stringResource(R.string.exit_no_save),
        confirmText = stringResource(R.string.yes),
        onConfirm = {
            viewModel.onIntent(BookSourceEditIntent.DiscardChanges)
        },
        dismissText = stringResource(R.string.no),
        onDismiss = { viewModel.onIntent(BookSourceEditIntent.DismissDialog) },
    )
}
