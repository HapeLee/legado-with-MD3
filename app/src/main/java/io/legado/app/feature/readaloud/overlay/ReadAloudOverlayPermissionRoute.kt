package io.legado.app.feature.readaloud.overlay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.utils.toastOnUi

/** 权限与 Activity Result 留在 Android host；没有新增设置或播放状态所有者。 */
@Composable
fun ReadAloudOverlayPermissionRoute() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember(context) { mutableStateOf(Settings.canDrawOverlays(context)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            granted = Settings.canDrawOverlays(context)
        }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = Settings.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    TinyClickableSettingItem(
        title = stringResource(R.string.read_aloud_overlay_permission),
        description = stringResource(if (granted) R.string.read_aloud_overlay_granted else R.string.read_aloud_overlay_required),
        onClick = {
            try {
                launcher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                context.toastOnUi(R.string.read_aloud_overlay_unavailable)
            }
        },
    )
}
