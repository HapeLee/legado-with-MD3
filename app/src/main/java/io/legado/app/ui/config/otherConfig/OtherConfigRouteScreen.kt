package io.legado.app.ui.config.otherConfig

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.service.WebService
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.utils.restart
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun OtherConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: OtherConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    val selectDocTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.onIntent(OtherConfigIntent.DefaultBookTreeUriChanged(it.toString()))
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                OtherConfigEffect.OpenSystemDirectory -> selectDocTree.launch(null)
                OtherConfigEffect.RestartWebService -> {
                    if (WebService.isRun) {
                        WebService.stop(context)
                        WebService.start(context)
                    }
                }
                OtherConfigEffect.RestartApp -> {
                    delay(RESTART_DELAY_MILLIS)
                    context.restart()
                }
            }
        }
    }

    val pendingMessage = state.pendingMessages.firstOrNull()
    LaunchedEffect(pendingMessage?.id, context) {
        val message = pendingMessage ?: return@LaunchedEffect
        if (message.resId != null) {
            Toast.makeText(context, message.resId, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, message.text.orEmpty(), Toast.LENGTH_SHORT).show()
        }
        viewModel.onIntent(OtherConfigIntent.MessageShown(message.id))
    }

    OtherConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )

    FilePickerSheet(
        show = state.activeOverlay == OtherConfigOverlay.FilePicker,
        onDismissRequest = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
        onSelectSysDir = {
            viewModel.onIntent(OtherConfigIntent.DismissOverlay)
            viewModel.onIntent(OtherConfigIntent.RequestSystemDirectory)
        },
    )

    DirectLinkUploadBottomSheet(
        show = state.activeOverlay == OtherConfigOverlay.DirectLinkUpload,
        state = state,
        onIntent = viewModel::onIntent,
        onDismiss = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
    )

    AppAlertDialog(
        show = state.activeOverlay == OtherConfigOverlay.ClearWebViewConfirmation,
        onDismissRequest = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
        title = stringResource(R.string.clear_webview_data),
        text = stringResource(R.string.sure_del),
        onConfirm = { viewModel.onIntent(OtherConfigIntent.ConfirmClearWebViewData) },
        onDismiss = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
    )

}

private const val RESTART_DELAY_MILLIS = 3_000L
