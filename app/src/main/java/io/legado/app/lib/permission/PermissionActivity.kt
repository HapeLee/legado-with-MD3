package io.legado.app.lib.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.utils.toastOnUi

class PermissionActivity : AppCompatActivity() {

    private var rationaleDialog: AlertDialog? = null
    private var requestType: Int = Request.TYPE_REQUEST_PERMISSION

    private val settingActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onRequestPermissionFinish()
        }
    private val requestPermissionsResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                onRequestPermissionFinish()
            } else {
                openFollowupSettingsForRequestType()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestType = intent.getIntExtra(KEY_INPUT_REQUEST_TYPE, Request.TYPE_REQUEST_PERMISSION)
        val rationale = intent.getStringExtra(KEY_RATIONALE)
        val permissions = intent.getStringArrayExtra(KEY_INPUT_PERMISSIONS) ?: emptyArray()

        showSettingDialog(permissions, rationale) {
            try {
                when (requestType) {
                    Request.TYPE_REQUEST_SETTING -> openSettingsActivity()
                    Request.TYPE_MANAGE_ALL_FILES_ACCESS -> openManageAllFilesAccessSettings()
                    Request.TYPE_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> openIgnoreBatterySettings()
                    Request.TYPE_REQUEST_NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionsResult.launch(permissions)
                        } else {
                            openNotificationSettings()
                        }
                    }

                    else -> requestPermissionsResult.launch(permissions)
                }
            } catch (e: Exception) {
                AppLog.put("请求权限出错\n$e", e, true)
                RequestPlugins.sRequestCallback?.onError(e)
                finish()
            }
        }
    }

    private fun onRequestPermissionFinish() {
        RequestPlugins.sRequestCallback?.onSettingActivityResult()
        finish()
    }

    private fun openSettingsActivity() {
        try {
            val settingIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            settingIntent.data = Uri.fromParts("package", packageName, null)
            settingActivityResult.launch(settingIntent)
        } catch (e: Exception) {
            toastOnUi(R.string.tip_cannot_jump_setting_page)
            RequestPlugins.sRequestCallback?.onError(e)
            finish()
        }
    }

    private fun openFollowupSettingsForRequestType() {
        when (requestType) {
            Request.TYPE_MANAGE_ALL_FILES_ACCESS -> openManageAllFilesAccessSettings()
            Request.TYPE_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> openIgnoreBatterySettings()
            Request.TYPE_REQUEST_NOTIFICATIONS -> openNotificationSettings()
            else -> openSettingsActivity()
        }
    }

    private fun openManageAllFilesAccessSettings() {
        try {
            val settingIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            settingIntent.data = Uri.fromParts("package", packageName, null)
            settingActivityResult.launch(settingIntent)
        } catch (e: Exception) {
            AppLog.put("Failed to open manage all files settings", e, true)
            openSettingsActivity()
        }
    }

    private fun openIgnoreBatterySettings() {
        val intentsToTry = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )

        for (intent in intentsToTry) {
            try {
                settingActivityResult.launch(intent)
                return
            } catch (e: Exception) {
                AppLog.put("Failed to open battery settings intent: $intent", e, true)
            }
        }

        openSettingsActivity()
    }

    private fun openNotificationSettings() {
        try {
            val settingIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            settingActivityResult.launch(settingIntent)
        } catch (e: Exception) {
            AppLog.put("Failed to open notification settings", e, true)
            openSettingsActivity()
        }
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showSettingDialog(
        permissions: Array<String>,
        rationale: CharSequence?,
        onOk: () -> Unit
    ) {
        rationaleDialog?.dismiss()
        if (rationale.isNullOrEmpty()) {
            onOk.invoke()
            return
        }

        rationaleDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(rationale)
            .setPositiveButton(R.string.dialog_setting) { _, _ -> onOk() }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                RequestPlugins.sRequestCallback?.onRequestPermissionsResult(
                    permissions, IntArray(0)
                )
                finish()
            }
            .setOnCancelListener {
                RequestPlugins.sRequestCallback?.onRequestPermissionsResult(
                    permissions, IntArray(0)
                )
                finish()
            }
            .show()
    }

    companion object {

        const val KEY_RATIONALE = "KEY_RATIONALE"
        const val KEY_INPUT_REQUEST_TYPE = "KEY_INPUT_REQUEST_TYPE"
        const val KEY_INPUT_PERMISSIONS_CODE = "KEY_INPUT_PERMISSIONS_CODE"
        const val KEY_INPUT_PERMISSIONS = "KEY_INPUT_PERMISSIONS"
    }
}
