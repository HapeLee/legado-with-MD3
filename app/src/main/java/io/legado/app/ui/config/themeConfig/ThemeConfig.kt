package io.legado.app.ui.config.themeConfig

import androidx.appcompat.app.AppCompatDelegate
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import org.koin.core.context.GlobalContext

data class TagColorPair(
    val textColor: Int = 0,
    val bgColor: Int = 0,
)

@Deprecated("使用 ThemeSettingsGateway / AppShellSettingsGateway.currentSettings")
object ThemeConfig {
    const val BOOK_INFO_BACKGROUND_BLUR_OFF = "off"
    const val BOOK_INFO_BACKGROUND_BLUR_ON = "on"
    const val BOOK_INFO_BACKGROUND_COVER_HIDDEN = "off_for_default"

    private val theme get() = GlobalContext.get().get<ThemeSettingsGateway>().currentSettings
    private val shell get() = GlobalContext.get().get<AppShellSettingsGateway>().currentSettings
    private val other get() = GlobalContext.get().get<OtherSettingsGateway>().currentSettings
    private val backup get() = GlobalContext.get().get<BackupSettingsGateway>().currentSettings

    val containerOpacity get() = theme.containerOpacity
    val enableBlur get() = theme.enableBlur
    val paletteStyle get() = theme.paletteStyle
    val materialVersion get() = theme.materialVersion
    val appTheme get() = theme.appTheme
    val themeMode get() = shell.themeMode
    val isPureBlack get() = theme.isPureBlack
    val bgImageLight get() = theme.backgroundImageLight
    val bgImageDark get() = theme.backgroundImageDark
    val bgImageBlurring get() = theme.backgroundImageBlurring
    val bgImageNBlurring get() = theme.backgroundImageDarkBlurring
    val isPredictiveBackEnabled get() = shell.predictiveBackEnabled
    val customMode get() = theme.customMode
    val fontScale get() = shell.fontScale
    val appFontPath get() = theme.appFontPath
    val showHome get() = shell.showHome
    val showDiscovery get() = shell.showDiscovery
    val showRss get() = shell.showRss
    val showStatusBar get() = shell.showStatusBar
    val swipeAnimation get() = shell.swipeAnimation
    val showBottomView get() = shell.showBottomView
    val tabletInterface get() = shell.tabletInterface
    val labelVisibilityMode get() = shell.labelVisibilityMode
    val defaultHomePage get() = shell.defaultHomePage
    val autoRefreshBook get() = other.autoRefresh
    val autoCheckNewBackup get() = backup.autoCheckNewBackup

    fun initNightMode() {
        val mode = when (themeMode) {
            "1" -> AppCompatDelegate.MODE_NIGHT_NO
            "2" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
