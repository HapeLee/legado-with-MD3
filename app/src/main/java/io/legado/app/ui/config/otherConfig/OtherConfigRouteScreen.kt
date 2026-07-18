package io.legado.app.ui.config.otherConfig

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.service.WebService
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.utils.SystemUtils
import io.legado.app.utils.takePersistablePermissionSafely
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun OtherConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: OtherConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var password by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
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
                is OtherConfigEffect.ApplyLanguage -> {
                    AppCompatDelegate.setApplicationLocales(appLocaleListFor(effect.language))
                }
                OtherConfigEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Toast.makeText(context, R.string.permission_not_required, Toast.LENGTH_SHORT).show()
                    }
                }
                OtherConfigEffect.RequestBatteryPermission -> {
                    (context as? Activity)?.let(SystemUtils::ignoreBatteryOptimization)
                }
                OtherConfigEffect.OpenSystemDirectory -> selectDocTree.launch(null)
                OtherConfigEffect.RestartWebService -> {
                    if (WebService.isRun) {
                        WebService.stop(context)
                        WebService.start(context)
                    }
                }
                is OtherConfigEffect.ShowMessage -> {
                    Toast.makeText(context, effect.resId, Toast.LENGTH_SHORT).show()
                }
                is OtherConfigEffect.SettingsUpdateFailed -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    CheckSourceBottomSheet(
        show = state.activeOverlay == OtherConfigOverlay.CheckSource,
        state = state,
        onIntent = viewModel::onIntent,
        onDismiss = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
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

    AppAlertDialog(
        show = state.activeOverlay == OtherConfigOverlay.Password,
        onDismissRequest = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
        title = stringResource(R.string.set_local_password),
        content = {
            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                backgroundColor = LegadoTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            viewModel.setLocalPassword(password)
            viewModel.onIntent(OtherConfigIntent.DismissOverlay)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { viewModel.onIntent(OtherConfigIntent.DismissOverlay) },
    )
}
