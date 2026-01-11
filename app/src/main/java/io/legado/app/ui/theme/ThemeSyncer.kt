package io.legado.app.ui.theme

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import splitties.init.appCtx

object ThemeSyncer {

    fun syncAll() {
        syncThemeMode()
        syncPureBlack()
        syncImageBg()
    }

    fun syncThemeMode() {
        ThemeState.updateThemeMode(
            ThemeResolver.resolveThemeMode(AppConfig.AppTheme.toString())
        )
    }

    fun syncPureBlack() {
        ThemeState.updatePureBlack(appCtx.getPrefBoolean(PreferKey.pureBlack, false))
    }

    fun syncImageBg() {
        ThemeState.updateImageBg(AppConfig.hasImageBg)
    }
}
