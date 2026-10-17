package io.legado.app.feature.readaloud.battery

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.legado.app.R
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.utils.BatteryOptimizationUtils

/**
 * 朗读常规设置中的电池优化与后台保活入口：
 * 宿主在 Android 侧处理，提供机型引导弹窗并走多级降级跳转到系统设置。
 */
@Composable
fun ReadAloudBatteryOptimizationRoute() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBatteryIgnored by remember(context) {
        mutableStateOf(BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context))
    }
    var showDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryIgnored = BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TinyClickableSettingItem(
        title = stringResource(R.string.background_battery_optimization_title),
        description = stringResource(
            if (isBatteryIgnored) R.string.battery_optimization_status_ignored
            else R.string.battery_optimization_status_not_ignored
        ),
        onClick = { showDialog = true },
    )

    if (showDialog) {
        AppAlertDialog(
            show = true,
            onDismissRequest = { showDialog = false },
            title = stringResource(R.string.battery_optimization_dialog_title),
            text = BatteryOptimizationUtils.getBatteryOptimizationGuideText(),
            confirmText = stringResource(R.string.dialog_setting),
            onConfirm = {
                showDialog = false
                (context as? Activity)?.let(BatteryOptimizationUtils::openBatteryOptimizationSettings)
                    ?: BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showDialog = false },
        )
    }
}
