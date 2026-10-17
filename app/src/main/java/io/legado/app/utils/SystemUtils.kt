package io.legado.app.utils

import android.app.Activity
import android.view.Display
import splitties.init.appCtx
import splitties.systemservices.displayManager


@Suppress("unused")
object SystemUtils {

    fun ignoreBatteryOptimization(activity: Activity) {
        BatteryOptimizationUtils.openBatteryOptimizationSettings(activity)
    }

    fun isScreenOn(): Boolean {
        return displayManager.displays.filterNotNull().any {
            it.state != Display.STATE_OFF
        }
    }

    /**
     * 屏幕像素宽度
     */
    val screenWidthPx by lazy {
        appCtx.resources.displayMetrics.widthPixels
    }

    /**
     * 屏幕像素高度
     */
    val screenHeightPx by lazy {
        appCtx.resources.displayMetrics.heightPixels
    }
}
