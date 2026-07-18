package io.legado.app.base

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.sysConfiguration


@Suppress("unused")
object AppContextWrapper {

    fun applyFont(activity: Activity) {
        val config = activity.resources.configuration
        val fontScale = getFontScale(activity)

        val newConfig = Configuration(config)
        newConfig.fontScale = fontScale

        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(newConfig, activity.resources.displayMetrics)
    }

    fun getFontScale(context: Context): Float {
        var fontScale = ThemeConfig.fontScale / 10f
        if (fontScale !in 0.8f..1.6f) {
            fontScale = sysConfiguration.fontScale
        }
        return fontScale
    }

}
