package io.legado.app.ui.config.privateConfig

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.security.BiometricEnvelopeWriter
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.privacy.findFragmentActivity
import io.legado.app.utils.BatteryOptimizationUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PrivateConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: PrivateConfigViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBatteryGuideDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onIntent(PrivateConfigIntent.RefreshBatteryStatus)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 资源串在 composable 作用域内解析：LocalContext.current.getString 不感知配置变化
    val envelopeTitle = stringResource(R.string.private_biometric_enable_title)
    val envelopeSubtitle = stringResource(R.string.private_biometric_enable_subtitle)
    val cancelLabel = stringResource(R.string.cancel)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is PrivateConfigEffect.ShowMessage -> context.toastOnUi(effect.message)

                PrivateConfigEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Toast.makeText(
                            context,
                            R.string.permission_not_required,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                PrivateConfigEffect.RequestBatteryPermission -> {
                    showBatteryGuideDialog = true
                }

                PrivateConfigEffect.RequestBiometricEnvelope -> {
                    val activity = context.findFragmentActivity()
                    if (activity == null) {
                        Toast.makeText(
                            context,
                            R.string.private_biometric_unavailable,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        BiometricEnvelopeWriter.launch(
                            activity = activity,
                            title = envelopeTitle,
                            subtitle = envelopeSubtitle,
                            negativeButtonText = cancelLabel,
                            onSealed = { ciphertext, iv ->
                                viewModel.onIntent(
                                    PrivateConfigIntent.StoreBiometricEnvelope(ciphertext, iv)
                                )
                            },
                            onError = {
                                Toast.makeText(
                                    context,
                                    R.string.private_biometric_unavailable,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            // 用户主动放弃：开关保持关闭，不需要额外提示
                            onCancel = {},
                        )
                    }
                }
            }
        }
    }

    PrivateConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )

    if (showBatteryGuideDialog) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { showBatteryGuideDialog = false },
            title = stringResource(R.string.battery_optimization_dialog_title),
            text = BatteryOptimizationUtils.getBatteryOptimizationGuideText(),
            confirmText = stringResource(R.string.dialog_setting),
            onConfirm = {
                showBatteryGuideDialog = false
                (context as? Activity)?.let(BatteryOptimizationUtils::openBatteryOptimizationSettings)
                    ?: BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showBatteryGuideDialog = false },
        )
    }
}
