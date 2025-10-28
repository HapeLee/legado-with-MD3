package io.legado.app.help

import android.content.ComponentName
import android.content.pm.PackageManager
import io.legado.app.R
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * Created by GKF on 2018/2/27.
 * 更换图标
 */
object LauncherIconHelp {
    private val packageManager: PackageManager = appCtx.packageManager
    private val componentNames = arrayListOf(
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcherw"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher0"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher1"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher2"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher3"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher4"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher5"),
        ComponentName(appCtx.packageName, "${appCtx.packageName}.Launcher6")
    )

    // 主Activity的ComponentName
    private val mainActivity = ComponentName(
        appCtx.packageName,
        "${appCtx.packageName}.ui.main.MainActivity"
    )

    fun changeIcon(icon: String?) {
        if (icon.isNullOrEmpty()) return

        try {
            // 首先禁用主Activity的启动器intent
            disableMainActivityLauncher()

            // 然后处理所有别名
            var matched = false
            componentNames.forEach {
                val aliasName = it.className.substringAfterLast(".")
                if (icon.equals(aliasName, ignoreCase = true)) {
                    matched = true
                    packageManager.setComponentEnabledSetting(
                        it,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    packageManager.setComponentEnabledSetting(
                        it,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            if (!matched) {
                enableMainActivityLauncher()
            }

            // 通知用户图标已更改
            appCtx.toastOnUi(R.string.change_icon_success)

        } catch (e: Exception) {
            e.printStackTrace()
            appCtx.toastOnUi(R.string.change_icon_error)
        }
    }

    // 禁用主Activity的启动器功能
    private fun disableMainActivityLauncher() {
        try {
            // 获取当前组件状态
            val state = packageManager.getComponentEnabledSetting(mainActivity)

            // 如果已经禁用，不需要再次操作
            if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                packageManager.setComponentEnabledSetting(
                    mainActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 启用主Activity的启动器功能
    private fun enableMainActivityLauncher() {
        try {
            // 获取当前组件状态
            val state = packageManager.getComponentEnabledSetting(mainActivity)

            // 如果已经启用，不需要再次操作
            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(
                    mainActivity,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
