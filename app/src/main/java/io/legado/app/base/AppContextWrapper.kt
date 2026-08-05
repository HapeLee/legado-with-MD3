package io.legado.app.base

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.resolveAppFontScale


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

    fun getFontScale(context: Context): Float =
        resolveAppFontScale(ThemeConfig.fontScale)

}
