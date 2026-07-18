package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsUpdate
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsUpdate
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.ThemeColorSlot
import io.legado.app.domain.gateway.ThemeSettingsUpdate
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppShellSettingsRepository : AppShellSettingsGateway {
    override val currentSettings: AppShellSettings
        get() = AppConfigStore.preferences.toAppShellSettings()

    override val settings: Flow<AppShellSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toAppShellSettings)
        .distinctUntilChanged()

    override suspend fun update(update: AppShellSettingsUpdate) {
        val (key, value) = when (update) {
            is AppShellSettingsUpdate.ThemeMode -> PreferKey.themeMode to update.value
            is AppShellSettingsUpdate.Language -> PreferKey.language to update.value
            is AppShellSettingsUpdate.FontScale -> PreferKey.fontScale to update.value
            is AppShellSettingsUpdate.ComposeEngine -> PreferKey.composeEngine to update.value
            is AppShellSettingsUpdate.MainNavigationOrder -> PreferKey.mainNavigationOrder to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}

class ThemeSettingsRepository : ThemeSettingsGateway {
    override val currentSettings: ThemeSettings
        get() = AppConfigStore.preferences.toThemeSettings()

    override val settings: Flow<ThemeSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toThemeSettings)
        .distinctUntilChanged()

    override suspend fun update(update: ThemeSettingsUpdate) {
        val (key, value) = when (update) {
            is ThemeSettingsUpdate.AppTheme -> PreferKey.appTheme to update.value
            is ThemeSettingsUpdate.PureBlack -> PreferKey.pureBlack to update.value
            is ThemeSettingsUpdate.PaletteStyle -> PreferKey.paletteStyle to update.value
            is ThemeSettingsUpdate.MaterialVersion -> PreferKey.materialVersion to update.value
            is ThemeSettingsUpdate.CustomContrast -> PreferKey.customContrast to update.value
            is ThemeSettingsUpdate.DeepPersonalization ->
                PreferKey.enableDeepPersonalization to update.value
            is ThemeSettingsUpdate.CustomColor -> when (update.slot) {
                ThemeColorSlot.Primary -> PreferKey.themeColor to update.value
                ThemeColorSlot.Secondary -> PreferKey.secondaryThemeColor to update.value
                ThemeColorSlot.PrimaryText -> PreferKey.primaryTextColor to update.value
                ThemeColorSlot.SecondaryText -> PreferKey.secondaryTextColor to update.value
                ThemeColorSlot.Background -> PreferKey.themeBackgroundColor to update.value
                ThemeColorSlot.LabelContainer -> PreferKey.labelContainerColor to update.value
                ThemeColorSlot.PrimaryNight -> PreferKey.themeColorNight to update.value
                ThemeColorSlot.SecondaryNight -> PreferKey.secondaryThemeColorNight to update.value
                ThemeColorSlot.PrimaryTextNight -> PreferKey.primaryTextColorNight to update.value
                ThemeColorSlot.SecondaryTextNight -> PreferKey.secondaryTextColorNight to update.value
                ThemeColorSlot.BackgroundNight -> PreferKey.themeBackgroundColorNight to update.value
                ThemeColorSlot.LabelContainerNight -> PreferKey.labelContainerColorNight to update.value
            }
            is ThemeSettingsUpdate.AppFontPath -> PreferKey.appFontPath to update.value
            is ThemeSettingsUpdate.CustomPrimary -> PreferKey.cPrimary to update.value
            is ThemeSettingsUpdate.CustomNightPrimary -> PreferKey.cNPrimary to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}

private fun Preferences.toAppShellSettings(): AppShellSettings = AppShellSettings(
    themeMode = compatDsString(PreferKey.themeMode) ?: "0",
    language = compatDsString(PreferKey.language) ?: "auto",
    fontScale = compatDsInt(PreferKey.fontScale) ?: 10,
    composeEngine = compatDsString(PreferKey.composeEngine) ?: "material",
    showHome = compatDsBoolean(PreferKey.showHome) ?: true,
    showDiscovery = compatDsBoolean(PreferKey.showDiscovery) ?: true,
    showRss = compatDsBoolean(PreferKey.showRss) ?: true,
    showStatusBar = compatDsBoolean(PreferKey.showStatusBar) ?: true,
    showBottomView = compatDsBoolean(PreferKey.showBottomView) ?: true,
    useFloatingBottomBar = compatDsBoolean(PreferKey.useFloatingBottomBar) ?: false,
    useFloatingBottomBarLiquidGlass =
        compatDsBoolean(PreferKey.useFloatingBottomBarLiquidGlass) ?: false,
    tabletInterface = compatDsString(PreferKey.tabletInterface) ?: "auto",
    labelVisibilityMode = compatDsString(PreferKey.labelVisibilityMode) ?: "auto",
    defaultHomePage = compatDsString(PreferKey.defaultHomePage) ?: "bookshelf",
    mainNavigationOrder = compatDsString(PreferKey.mainNavigationOrder)
        ?: "home,bookshelf,explore,rss,my",
    navExtended = compatDsBoolean("navExtended") ?: false,
    navIconHome = compatDsString(PreferKey.navIconHome) ?: "",
    navIconBookshelf = compatDsString(PreferKey.navIconBookshelf) ?: "",
    navIconExplore = compatDsString(PreferKey.navIconExplore) ?: "",
    navIconRss = compatDsString(PreferKey.navIconRss) ?: "",
    navIconMy = compatDsString(PreferKey.navIconMy) ?: "",
)

private fun Preferences.toThemeSettings(): ThemeSettings = ThemeSettings(
    appTheme = compatDsString(PreferKey.appTheme) ?: "0",
    isPureBlack = compatDsBoolean(PreferKey.pureBlack) ?: false,
    paletteStyle = compatDsString(PreferKey.paletteStyle) ?: "tonalSpot",
    materialVersion = compatDsString(PreferKey.materialVersion) ?: "material3",
    customContrast = compatDsString(PreferKey.customContrast) ?: "Default",
    appFontPath = compatDsString(PreferKey.appFontPath),
    customPrimary = compatDsInt(PreferKey.cPrimary) ?: 0,
    customNightPrimary = compatDsInt(PreferKey.cNPrimary) ?: 0,
    enableDeepPersonalization = compatDsBoolean(PreferKey.enableDeepPersonalization) ?: false,
    themeColor = compatDsInt(PreferKey.themeColor) ?: 0,
    secondaryThemeColor = compatDsInt(PreferKey.secondaryThemeColor) ?: 0,
    primaryTextColor = compatDsInt(PreferKey.primaryTextColor) ?: 0,
    secondaryTextColor = compatDsInt(PreferKey.secondaryTextColor) ?: 0,
    themeBackgroundColor = compatDsInt(PreferKey.themeBackgroundColor) ?: 0,
    labelContainerColor = compatDsInt(PreferKey.labelContainerColor) ?: 0,
    themeColorNight = compatDsInt(PreferKey.themeColorNight) ?: 0,
    secondaryThemeColorNight = compatDsInt(PreferKey.secondaryThemeColorNight) ?: 0,
    primaryTextColorNight = compatDsInt(PreferKey.primaryTextColorNight) ?: 0,
    secondaryTextColorNight = compatDsInt(PreferKey.secondaryTextColorNight) ?: 0,
    themeBackgroundColorNight = compatDsInt(PreferKey.themeBackgroundColorNight) ?: 0,
    labelContainerColorNight = compatDsInt(PreferKey.labelContainerColorNight) ?: 0,
)

class OtherSettingsRepository : OtherSettingsGateway {
    override val settings: Flow<OtherSettings> = AppConfigStore.preferencesFlow
        .map { preferences ->
            val rawSourceEditMaxLine = preferences.compatDsInt(PreferKey.sourceEditMaxLine) ?: Int.MAX_VALUE
            OtherSettings(
                language = preferences.compatDsString(PreferKey.language) ?: "auto",
                updateToVariant = preferences.compatDsString(PreferKey.updateToVariant) ?: "official_version",
                autoCheckUpdateOnStart =
                    preferences.compatDsBoolean(PreferKey.autoCheckUpdateOnStart) ?: false,
                webServiceAutoStart = preferences.compatDsBoolean(PreferKey.webServiceAutoStart) ?: false,
                autoRefresh = preferences.compatDsBoolean(PreferKey.autoRefresh) ?: false,
                defaultToRead = preferences.compatDsBoolean(PreferKey.defaultToRead) ?: false,
                notificationsPost = preferences.compatDsBoolean(PreferKey.notificationsPost) ?: true,
                ignoreBatteryPermission =
                    preferences.compatDsBoolean(PreferKey.ignoreBatteryPermission) ?: true,
                firebaseEnable = preferences.compatDsBoolean(PreferKey.firebaseEnable) ?: true,
                defaultBookTreeUri = preferences.compatDsString(PreferKey.defaultBookTreeUri),
                antiAlias = preferences.compatDsBoolean(PreferKey.antiAlias) ?: false,
                replaceEnableDefault = preferences.compatDsBoolean(PreferKey.replaceEnableDefault) ?: true,
                autoClearExpired = preferences.compatDsBoolean(PreferKey.autoClearExpired) ?: true,
                showAddToShelfAlert = preferences.compatDsBoolean(PreferKey.showAddToShelfAlert) ?: true,
                showMangaUi = preferences.compatDsBoolean(PreferKey.showMangaUi) ?: true,
                webServiceWakeLock = preferences.compatDsBoolean(PreferKey.webServiceWakeLock) ?: false,
                sourceEditMaxLine = rawSourceEditMaxLine.takeIf { it >= 10 } ?: Int.MAX_VALUE,
                webPort = preferences.compatDsInt(PreferKey.webPort) ?: 1122,
                processText = preferences.compatDsBoolean(PreferKey.processText) ?: true,
                recordLog = preferences.compatDsBoolean(PreferKey.recordLog) ?: false,
                recordHeapDump = preferences.compatDsBoolean(PreferKey.recordHeapDump) ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun update(update: OtherSettingsUpdate) {
        val (key, value) = when (update) {
            is OtherSettingsUpdate.Language -> PreferKey.language to update.value
            is OtherSettingsUpdate.UpdateToVariant -> PreferKey.updateToVariant to update.value
            is OtherSettingsUpdate.AutoCheckUpdateOnStart -> PreferKey.autoCheckUpdateOnStart to update.value
            is OtherSettingsUpdate.WebServiceAutoStart -> PreferKey.webServiceAutoStart to update.value
            is OtherSettingsUpdate.AutoRefresh -> PreferKey.autoRefresh to update.value
            is OtherSettingsUpdate.DefaultToRead -> PreferKey.defaultToRead to update.value
            is OtherSettingsUpdate.FirebaseEnable -> PreferKey.firebaseEnable to update.value
            is OtherSettingsUpdate.DefaultBookTreeUri -> PreferKey.defaultBookTreeUri to update.value
            is OtherSettingsUpdate.AntiAlias -> PreferKey.antiAlias to update.value
            is OtherSettingsUpdate.ReplaceEnableDefault -> PreferKey.replaceEnableDefault to update.value
            is OtherSettingsUpdate.AutoClearExpired -> PreferKey.autoClearExpired to update.value
            is OtherSettingsUpdate.ShowAddToShelfAlert -> PreferKey.showAddToShelfAlert to update.value
            is OtherSettingsUpdate.ShowMangaUi -> PreferKey.showMangaUi to update.value
            is OtherSettingsUpdate.WebServiceWakeLock -> PreferKey.webServiceWakeLock to update.value
            is OtherSettingsUpdate.SourceEditMaxLine -> PreferKey.sourceEditMaxLine to update.value
            is OtherSettingsUpdate.WebPort -> PreferKey.webPort to update.value
            is OtherSettingsUpdate.ProcessText -> PreferKey.processText to update.value
            is OtherSettingsUpdate.RecordLog -> PreferKey.recordLog to update.value
            is OtherSettingsUpdate.RecordHeapDump -> PreferKey.recordHeapDump to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}
