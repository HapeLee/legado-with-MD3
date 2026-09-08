package io.legado.app.base

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.ui.theme.resolveAppFontScale
import io.legado.app.utils.sysConfiguration
import org.koin.core.context.GlobalContext


@Suppress("unused")
object AppContextWrapper {

    private val shellGateway get() = GlobalContext.get().get<AppShellSettingsGateway>()

    fun applyFont(activity: Activity) {
        val config = activity.resources.configuration
        val fontScale = getFontScale(activity)

        val newConfig = Configuration(config)
        newConfig.fontScale = fontScale

        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(newConfig, activity.resources.displayMetrics)
    }

    fun getFontScale(context: Context): Float =
        resolveAppFontScale(
            fontScaleSetting = shellGateway.currentSettings.fontScale,
            // 系统缩放由 app 侧提供：:core:ui 拿不到 appCtx。
            systemFontScale = sysConfiguration.fontScale,
        )

}
