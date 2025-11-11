package io.legado.app.lib.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    private val settingActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onRequestPermissionFinish()
        }
    private val requestPermissionsResult =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                onRequestPermissionFinish()
            } else {
                openSettingsActivity()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rationale = intent.getStringExtra(KEY_RATIONALE)
        val permissions = intent.getStringArrayExtra(KEY_INPUT_PERMISSIONS)!!

        showSettingDialog(permissions, rationale) {
            try {
                requestPermissionsResult.launch(permissions)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        RequestPlugins.sRequestCallback?.onRequestPermissionsResult(
            permissions,
            grantResults
        )
        finish()
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
