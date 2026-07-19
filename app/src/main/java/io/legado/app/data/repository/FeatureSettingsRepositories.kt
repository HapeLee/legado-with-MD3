package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.BuildConfig
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppShellBooleanSetting
import io.legado.app.domain.gateway.AppShellStringSetting
import io.legado.app.domain.gateway.AppShellSettingsUpdate
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsUpdate
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.ThemeBooleanSetting
import io.legado.app.domain.gateway.ThemeColorSlot
import io.legado.app.domain.gateway.ThemeFloatSetting
import io.legado.app.domain.gateway.ThemeIntSetting
import io.legado.app.domain.gateway.ThemeStringSetting
import io.legado.app.domain.gateway.ThemeSettingsUpdate
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.domain.model.settings.TranslationSettings
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsUpdate
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsFloat
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

    override suspend fun update(update: AppShellSettingsUpdate) = updateAll(listOf(update))

    override suspend fun updateAll(updates: List<AppShellSettingsUpdate>) {
        val values = updates.associate { update ->
            val (key, value) = when (update) {
                is AppShellSettingsUpdate.ThemeMode -> PreferKey.themeMode to update.value
                is AppShellSettingsUpdate.FontScale -> PreferKey.fontScale to update.value
                is AppShellSettingsUpdate.ComposeEngine -> PreferKey.composeEngine to update.value
                is AppShellSettingsUpdate.MainNavigationOrder ->
                    PreferKey.mainNavigationOrder to update.value
                is AppShellSettingsUpdate.BooleanValue -> when (update.setting) {
                    AppShellBooleanSetting.ShowHome -> PreferKey.showHome to update.value
                    AppShellBooleanSetting.ShowDiscovery -> PreferKey.showDiscovery to update.value
                    AppShellBooleanSetting.ShowRss -> PreferKey.showRss to update.value
                    AppShellBooleanSetting.ShowStatusBar -> PreferKey.showStatusBar to update.value
                    AppShellBooleanSetting.SwipeAnimation -> PreferKey.swipeAnimation to update.value
                    AppShellBooleanSetting.PredictiveBack ->
                        PreferKey.isPredictiveBackEnabled to update.value
                    AppShellBooleanSetting.ShowBottomView -> PreferKey.showBottomView to update.value
                    AppShellBooleanSetting.UseFloatingBottomBar ->
                        PreferKey.useFloatingBottomBar to update.value
                    AppShellBooleanSetting.UseFloatingBottomBarLiquidGlass ->
                        PreferKey.useFloatingBottomBarLiquidGlass to update.value
                    AppShellBooleanSetting.NavExtended -> PreferKey.navExtended to update.value
                }
                is AppShellSettingsUpdate.StringValue -> when (update.setting) {
                    AppShellStringSetting.TabletInterface ->
                        PreferKey.tabletInterface to update.value
                    AppShellStringSetting.LabelVisibilityMode ->
                        PreferKey.labelVisibilityMode to update.value
                    AppShellStringSetting.DefaultHomePage ->
                        PreferKey.defaultHomePage to update.value
                    AppShellStringSetting.NavIconHome -> PreferKey.navIconHome to update.value
                    AppShellStringSetting.NavIconBookshelf -> PreferKey.navIconBookshelf to update.value
                    AppShellStringSetting.NavIconExplore -> PreferKey.navIconExplore to update.value
                    AppShellStringSetting.NavIconRss -> PreferKey.navIconRss to update.value
                    AppShellStringSetting.NavIconMy -> PreferKey.navIconMy to update.value
                    AppShellStringSetting.LauncherIcon -> PreferKey.launcherIcon to update.value
                }
            }
            key to value
        }
        AppConfigStore.putAll(values)
    }
}

class ThemeSettingsRepository : ThemeSettingsGateway {
    override val currentSettings: ThemeSettings
        get() = AppConfigStore.preferences.toThemeSettings()

    override val settings: Flow<ThemeSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toThemeSettings)
        .distinctUntilChanged()

    override suspend fun update(update: ThemeSettingsUpdate) = updateAll(listOf(update))

    override suspend fun updateAll(updates: List<ThemeSettingsUpdate>) {
        val values = updates.associate { update ->
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
            is ThemeSettingsUpdate.BooleanValue -> when (update.setting) {
                ThemeBooleanSetting.UseMiuixMonet -> PreferKey.useMiuixMonet to update.value
                ThemeBooleanSetting.EnableBlur -> PreferKey.enableBlur to update.value
                ThemeBooleanSetting.EnableProgressiveBlur -> PreferKey.enableProgressiveBlur to update.value
                ThemeBooleanSetting.UseFlexibleTopAppBar -> PreferKey.useFlexibleTopAppBar to update.value
                ThemeBooleanSetting.BookInfoFollowCoverColor -> PreferKey.bookInfoFollowCoverColor to update.value
                ThemeBooleanSetting.EnableItemDivider -> PreferKey.enableItemDivider to update.value
                ThemeBooleanSetting.EyeProtectionEnabled -> PreferKey.eyeProtectionEnabled to update.value
                ThemeBooleanSetting.EyeProtectionSchedule -> PreferKey.eyeProtectionSchedule to update.value
                ThemeBooleanSetting.ShowRefactorTip ->
                    io.legado.app.data.local.preferences.LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP.name to update.value
                ThemeBooleanSetting.EnableCustomTagColors -> PreferKey.enableCustomTagColors to update.value
            }
            is ThemeSettingsUpdate.IntValue -> when (update.setting) {
                ThemeIntSetting.ContainerOpacity -> PreferKey.containerOpacity to update.value
                ThemeIntSetting.TopBarOpacity -> PreferKey.topBarOpacity to update.value
                ThemeIntSetting.BottomBarOpacity -> PreferKey.bottomBarOpacity to update.value
                ThemeIntSetting.TopBarBlurRadius -> PreferKey.topBarBlurRadius to update.value
                ThemeIntSetting.BottomBarBlurRadius -> PreferKey.bottomBarBlurRadius to update.value
                ThemeIntSetting.TopBarBlurAlpha -> PreferKey.topBarBlurAlpha to update.value
                ThemeIntSetting.BottomBarBlurAlpha -> PreferKey.bottomBarBlurAlpha to update.value
                ThemeIntSetting.BackgroundImageBlurring -> PreferKey.bgImageBlurring to update.value
                ThemeIntSetting.BackgroundImageDarkBlurring -> PreferKey.bgImageNBlurring to update.value
                ThemeIntSetting.ItemDividerColor -> PreferKey.itemDividerColor to update.value
                ThemeIntSetting.ColorTemperature -> PreferKey.colorTemperature to update.value
            }
            is ThemeSettingsUpdate.FloatValue -> when (update.setting) {
                ThemeFloatSetting.BottomBarLensRadius -> PreferKey.bottomBarLensRadius to update.value
                ThemeFloatSetting.ItemDividerWidth -> PreferKey.itemDividerWidth to update.value
                ThemeFloatSetting.ItemDividerLength -> PreferKey.itemDividerLength to update.value
            }
            is ThemeSettingsUpdate.StringValue -> when (update.setting) {
                ThemeStringSetting.BookInfoNetworkCoverBackground ->
                    PreferKey.bookInfoNetworkCoverBackground to update.value
                ThemeStringSetting.BookInfoDefaultCoverBackground ->
                    PreferKey.bookInfoDefaultCoverBackground to update.value
                ThemeStringSetting.BackgroundImageLight -> PreferKey.bgImage to update.value
                ThemeStringSetting.BackgroundImageDark -> PreferKey.bgImageN to update.value
                ThemeStringSetting.EyeProtectionStartTime -> PreferKey.eyeProtectionStartTime to update.value
                ThemeStringSetting.EyeProtectionEndTime -> PreferKey.eyeProtectionEndTime to update.value
                ThemeStringSetting.CustomTagColorsJson -> PreferKey.customTagColors to update.value
            }
            }
            key to value
        }
        AppConfigStore.putAll(values)
    }
}

class DownloadCacheSettingsRepository : DownloadCacheSettingsGateway {
    override val currentSettings: DownloadCacheSettings
        get() = AppConfigStore.preferences.toDownloadCacheSettings()

    override val settings: Flow<DownloadCacheSettings> = AppConfigStore.preferencesFlow
        .map { it.toDownloadCacheSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        AppConfigStore.putAllAndAwait(
            currentSettings.diffPrefMap(transform, DownloadCacheSettings::toPrefMap)
        )
    }
}

class CoverSettingsRepository : CoverSettingsGateway {
    override val currentSettings: CoverSettings
        get() = AppConfigStore.preferences.toCoverSettings()

    override val settings: Flow<CoverSettings> = AppConfigStore.preferencesFlow
        .map { it.toCoverSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (CoverSettings) -> CoverSettings) {
        AppConfigStore.putAll(currentSettings.diffPrefMap(transform, CoverSettings::toPrefMap))
    }
}

class LabSettingsRepository : LabSettingsGateway {
    override val currentSettings: LabSettings
        get() = AppConfigStore.preferences.toLabSettings()

    override val settings: Flow<LabSettings> = AppConfigStore.preferencesFlow
        .map { it.toLabSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (LabSettings) -> LabSettings) {
        AppConfigStore.putAll(currentSettings.diffPrefMap(transform, LabSettings::toPrefMap))
    }
}

class TranslationSettingsRepository : TranslationSettingsGateway {
    override val currentSettings: TranslationSettings
        get() = AppConfigStore.preferences.toTranslationSettings()

    override val settings: Flow<TranslationSettings> = AppConfigStore.preferencesFlow
        .map { it.toTranslationSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (TranslationSettings) -> TranslationSettings) {
        AppConfigStore.putAll(currentSettings.diffPrefMap(transform, TranslationSettings::toPrefMap))
    }
}

class BackupSettingsRepository : BackupSettingsGateway {
    override val currentSettings: BackupSettings
        get() = AppConfigStore.preferences.toBackupSettings()

    override val settings: Flow<BackupSettings> = AppConfigStore.preferencesFlow
        .map { it.toBackupSettings() }
        .distinctUntilChanged()

    override suspend fun update(update: BackupSettingsUpdate) = updateAll(listOf(update))

    override suspend fun updateAll(updates: List<BackupSettingsUpdate>) {
        val values = buildMap {
            updates.forEach { update ->
                when (update) {
                    is BackupSettingsUpdate.WebDavUrl -> put(PreferKey.webDavUrl, update.value)
                    is BackupSettingsUpdate.WebDavCredentials -> {
                        put(PreferKey.webDavAccount, update.account)
                        put(PreferKey.webDavPassword, update.password)
                    }
                    is BackupSettingsUpdate.WebDavDir -> put(PreferKey.webDavDir, update.value)
                    is BackupSettingsUpdate.WebDavDeviceName ->
                        put(PreferKey.webDavDeviceName, update.value)
                    is BackupSettingsUpdate.SyncBookProgress ->
                        put(PreferKey.syncBookProgress, update.value)
                    is BackupSettingsUpdate.SyncBookProgressPlus ->
                        put(PreferKey.syncBookProgressPlus, update.value)
                    is BackupSettingsUpdate.AutoCheckNewBackup ->
                        put(PreferKey.autoCheckNewBackup, update.value)
                    is BackupSettingsUpdate.OnlyLatestBackup ->
                        put(PreferKey.onlyLatestBackup, update.value)
                    is BackupSettingsUpdate.BackupSyncMode ->
                        put(PreferKey.backupSyncMode, update.value)
                    is BackupSettingsUpdate.BackupPath -> put(PreferKey.backupPath, update.value)
                }
            }
        }
        AppConfigStore.putAll(values)
    }
}

internal fun Preferences.toDownloadCacheSettings(): DownloadCacheSettings =
    DownloadCacheSettings(
        bitmapCacheSize = compatDsInt(PreferKey.bitmapCacheSize) ?: 50,
        imageRetainNum = compatDsInt(PreferKey.imageRetainNum) ?: 0,
        preDownloadNum = compatDsInt(PreferKey.preDownloadNum) ?: 10,
        threadCount = compatDsInt(PreferKey.threadCount) ?: 16,
        cacheBookThreadCount = compatDsInt(PreferKey.cacheBookThreadCount) ?: 16,
        userAgent = compatDsString(PreferKey.userAgent).orEmpty().ifBlank {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36"
        },
        cronetEnabled = compatDsBoolean(PreferKey.cronet) ?: false,
    )

internal fun DownloadCacheSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.bitmapCacheSize to bitmapCacheSize,
    PreferKey.imageRetainNum to imageRetainNum,
    PreferKey.preDownloadNum to preDownloadNum,
    PreferKey.threadCount to threadCount,
    PreferKey.cacheBookThreadCount to cacheBookThreadCount,
    PreferKey.userAgent to userAgent,
    PreferKey.cronet to cronetEnabled,
)

internal fun Preferences.toCoverSettings(): CoverSettings = CoverSettings(
    loadOnlyOnWifi = compatDsBoolean(PreferKey.loadCoverOnlyWifi) ?: false,
    useDefaultCover = compatDsBoolean(PreferKey.useDefaultCover) ?: false,
    showShadow = compatDsBoolean(PreferKey.coverShowShadow) ?: false,
    showStroke = compatDsBoolean(PreferKey.coverShowStroke) ?: true,
    useDefaultColor = compatDsBoolean(PreferKey.coverDefaultColor) ?: true,
    textColor = compatDsInt(PreferKey.coverTextColor) ?: -16777216,
    shadowColor = compatDsInt(PreferKey.coverShadowColor) ?: -16777216,
    showName = compatDsBoolean(PreferKey.coverShowName) ?: true,
    showAuthor = compatDsBoolean(PreferKey.coverShowAuthor) ?: true,
    textColorDark = compatDsInt(PreferKey.coverTextColorN) ?: -1,
    shadowColorDark = compatDsInt(PreferKey.coverShadowColorN) ?: -1,
    showNameDark = compatDsBoolean(PreferKey.coverShowNameN) ?: true,
    showAuthorDark = compatDsBoolean(PreferKey.coverShowAuthorN) ?: true,
    infoOrientation = compatDsString(PreferKey.coverInfoOrientation) ?: "0",
    exploreFilterState = compatDsInt(PreferKey.exploreFilterState) ?: 0,
    defaultCover = compatDsString(PreferKey.defaultCover).orEmpty(),
    defaultCoverDark = compatDsString(PreferKey.defaultCoverDark).orEmpty(),
)

internal fun CoverSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.loadCoverOnlyWifi to loadOnlyOnWifi,
    PreferKey.useDefaultCover to useDefaultCover,
    PreferKey.coverShowShadow to showShadow,
    PreferKey.coverShowStroke to showStroke,
    PreferKey.coverDefaultColor to useDefaultColor,
    PreferKey.coverTextColor to textColor,
    PreferKey.coverShadowColor to shadowColor,
    PreferKey.coverShowName to showName,
    PreferKey.coverShowAuthor to showAuthor,
    PreferKey.coverTextColorN to textColorDark,
    PreferKey.coverShadowColorN to shadowColorDark,
    PreferKey.coverShowNameN to showNameDark,
    PreferKey.coverShowAuthorN to showAuthorDark,
    PreferKey.coverInfoOrientation to infoOrientation,
    PreferKey.exploreFilterState to exploreFilterState,
    PreferKey.defaultCover to defaultCover,
    PreferKey.defaultCoverDark to defaultCoverDark,
)

internal fun Preferences.toLabSettings(): LabSettings = LabSettings(
    enabled = compatDsBoolean(PreferKey.labEnabled) ?: false,
    eInkDisplay = compatDsBoolean(PreferKey.labEInkDisplay) ?: false,
    eyeProtection = compatDsBoolean(PreferKey.labEyeProtection) ?: false,
)

internal fun LabSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.labEnabled to enabled,
    PreferKey.labEInkDisplay to eInkDisplay,
    PreferKey.labEyeProtection to eyeProtection,
)

internal fun Preferences.toTranslationSettings(): TranslationSettings {
    val storedProvider = compatDsString(PreferKey.llmProvider)
        ?: TranslationConstants.PROVIDER_GOOGLE
    return TranslationSettings(
        provider = if (storedProvider == TranslationConstants.PROVIDER_OPENAI) {
            TranslationConstants.PROVIDER_APP_AI
        } else {
            storedProvider
        },
        targetLanguage = compatDsString(PreferKey.llmTargetLanguage) ?: "zh",
        maxCharsPerChunk = compatDsInt(PreferKey.llmMaxCharsPerChunk) ?: 10000,
        concurrentChunks = compatDsInt(PreferKey.llmConcurrentChunks) ?: 1,
        retryCount = compatDsInt(PreferKey.llmRetryCount) ?: 2,
    )
}

internal fun TranslationSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.llmProvider to provider,
    PreferKey.llmTargetLanguage to targetLanguage,
    PreferKey.llmMaxCharsPerChunk to maxCharsPerChunk,
    PreferKey.llmConcurrentChunks to concurrentChunks,
    PreferKey.llmRetryCount to retryCount,
)

internal fun Preferences.toBackupSettings(): BackupSettings = BackupSettings(
    webDavUrl = compatDsString(PreferKey.webDavUrl).orEmpty(),
    webDavAccount = compatDsString(PreferKey.webDavAccount).orEmpty(),
    webDavPassword = compatDsString(PreferKey.webDavPassword).orEmpty(),
    webDavDir = compatDsString(PreferKey.webDavDir) ?: "legado",
    webDavDeviceName = compatDsString(PreferKey.webDavDeviceName).orEmpty(),
    syncBookProgress = compatDsBoolean(PreferKey.syncBookProgress) ?: true,
    syncBookProgressPlus = compatDsBoolean(PreferKey.syncBookProgressPlus) ?: false,
    autoCheckNewBackup = compatDsBoolean(PreferKey.autoCheckNewBackup) ?: true,
    onlyLatestBackup = compatDsBoolean(PreferKey.onlyLatestBackup) ?: true,
    backupSyncMode = compatDsString(PreferKey.backupSyncMode) ?: "both",
    backupPath = compatDsString(PreferKey.backupPath),
)

internal fun Preferences.toAppShellSettings(): AppShellSettings = AppShellSettings(
    themeMode = compatDsString(PreferKey.themeMode) ?: "0",
    fontScale = compatDsInt(PreferKey.fontScale) ?: 10,
    composeEngine = compatDsString(PreferKey.composeEngine) ?: "material",
    showHome = compatDsBoolean(PreferKey.showHome) ?: true,
    showDiscovery = compatDsBoolean(PreferKey.showDiscovery) ?: true,
    showRss = compatDsBoolean(PreferKey.showRss) ?: true,
    showStatusBar = compatDsBoolean(PreferKey.showStatusBar) ?: true,
    swipeAnimation = compatDsBoolean(PreferKey.swipeAnimation) ?: true,
    predictiveBackEnabled = compatDsBoolean(PreferKey.isPredictiveBackEnabled) ?: true,
    showBottomView = compatDsBoolean(PreferKey.showBottomView) ?: true,
    useFloatingBottomBar = compatDsBoolean(PreferKey.useFloatingBottomBar) ?: false,
    useFloatingBottomBarLiquidGlass =
        compatDsBoolean(PreferKey.useFloatingBottomBarLiquidGlass) ?: false,
    tabletInterface = compatDsString(PreferKey.tabletInterface) ?: "auto",
    labelVisibilityMode = compatDsString(PreferKey.labelVisibilityMode) ?: "auto",
    defaultHomePage = compatDsString(PreferKey.defaultHomePage) ?: "bookshelf",
    mainNavigationOrder = compatDsString(PreferKey.mainNavigationOrder)
        ?: "home,bookshelf,explore,rss,my",
    navExtended = compatDsBoolean(PreferKey.navExtended) ?: false,
    navIconHome = compatDsString(PreferKey.navIconHome) ?: "",
    navIconBookshelf = compatDsString(PreferKey.navIconBookshelf) ?: "",
    navIconExplore = compatDsString(PreferKey.navIconExplore) ?: "",
    navIconRss = compatDsString(PreferKey.navIconRss) ?: "",
    navIconMy = compatDsString(PreferKey.navIconMy) ?: "",
    launcherIcon = compatDsString(PreferKey.launcherIcon) ?: "ic_launcher",
)

internal fun Preferences.toThemeSettings(): ThemeSettings = ThemeSettings(
    appTheme = compatDsString(PreferKey.appTheme) ?: "0",
    useMiuixMonet = compatDsBoolean(PreferKey.useMiuixMonet) ?: false,
    isPureBlack = compatDsBoolean(PreferKey.pureBlack) ?: false,
    paletteStyle = compatDsString(PreferKey.paletteStyle) ?: "tonalSpot",
    materialVersion = compatDsString(PreferKey.materialVersion) ?: "material3",
    customContrast = compatDsString(PreferKey.customContrast) ?: "Default",
    customMode = compatDsString(PreferKey.customMode) ?: "tonalSpot",
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
    containerOpacity = compatDsInt(PreferKey.containerOpacity) ?: 100,
    topBarOpacity = compatDsInt(PreferKey.topBarOpacity) ?: 100,
    bottomBarOpacity = compatDsInt(PreferKey.bottomBarOpacity) ?: 100,
    enableBlur = compatDsBoolean(PreferKey.enableBlur) ?: false,
    enableProgressiveBlur = compatDsBoolean(PreferKey.enableProgressiveBlur) ?: false,
    topBarBlurRadius = compatDsInt(PreferKey.topBarBlurRadius) ?: 24,
    bottomBarBlurRadius = compatDsInt(PreferKey.bottomBarBlurRadius) ?: 8,
    topBarBlurAlpha = compatDsInt(PreferKey.topBarBlurAlpha) ?: 73,
    bottomBarBlurAlpha = compatDsInt(PreferKey.bottomBarBlurAlpha) ?: 40,
    bottomBarLensRadius = compatDsFloat(PreferKey.bottomBarLensRadius) ?: 24f,
    useFlexibleTopAppBar = compatDsBoolean(PreferKey.useFlexibleTopAppBar) ?: true,
    bookInfoFollowCoverColor = compatDsBoolean(PreferKey.bookInfoFollowCoverColor) ?: true,
    bookInfoNetworkCoverBackground =
        compatDsString(PreferKey.bookInfoNetworkCoverBackground) ?: "on",
    bookInfoDefaultCoverBackground =
        compatDsString(PreferKey.bookInfoDefaultCoverBackground) ?: "on",
    bookInfoInputColor = compatDsInt(PreferKey.bookInfoInputColor) ?: 0,
    backgroundImageLight = compatDsString(PreferKey.bgImage),
    backgroundImageDark = compatDsString(PreferKey.bgImageN),
    backgroundImageBlurring = compatDsInt(PreferKey.bgImageBlurring) ?: 0,
    backgroundImageDarkBlurring = compatDsInt(PreferKey.bgImageNBlurring) ?: 0,
    enableItemDivider = compatDsBoolean(PreferKey.enableItemDivider) ?: false,
    itemDividerWidth = compatDsFloat(PreferKey.itemDividerWidth) ?: 1f,
    itemDividerLength = compatDsFloat(PreferKey.itemDividerLength) ?: 80f,
    itemDividerColor = compatDsInt(PreferKey.itemDividerColor) ?: 0,
    eyeProtectionEnabled = compatDsBoolean(PreferKey.eyeProtectionEnabled) ?: false,
    colorTemperature = compatDsInt(PreferKey.colorTemperature) ?: 50,
    eyeProtectionSchedule = compatDsBoolean(PreferKey.eyeProtectionSchedule) ?: false,
    eyeProtectionStartTime = compatDsString(PreferKey.eyeProtectionStartTime) ?: "22:00",
    eyeProtectionEndTime = compatDsString(PreferKey.eyeProtectionEndTime) ?: "07:00",
    showRefactorTip = compatDsBoolean(
        io.legado.app.data.local.preferences.LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP.name
    ) ?: true,
    enableCustomTagColors = compatDsBoolean(PreferKey.enableCustomTagColors) ?: false,
    customTagColorsJson = compatDsString(PreferKey.customTagColors),
)

internal fun Preferences.toOtherSettings(): OtherSettings {
    val rawSourceEditMaxLine = compatDsInt(PreferKey.sourceEditMaxLine) ?: Int.MAX_VALUE
    return OtherSettings(
        updateToVariant = compatDsString(PreferKey.updateToVariant) ?: "official_version",
        autoCheckUpdateOnStart = compatDsBoolean(PreferKey.autoCheckUpdateOnStart) ?: false,
        webServiceAutoStart = compatDsBoolean(PreferKey.webServiceAutoStart) ?: false,
        autoRefresh = compatDsBoolean(PreferKey.autoRefresh) ?: false,
        defaultToRead = compatDsBoolean(PreferKey.defaultToRead) ?: false,
        notificationsPost = compatDsBoolean(PreferKey.notificationsPost) ?: true,
        ignoreBatteryPermission = compatDsBoolean(PreferKey.ignoreBatteryPermission) ?: true,
        firebaseEnable = compatDsBoolean(PreferKey.firebaseEnable) ?: true,
        defaultBookTreeUri = compatDsString(PreferKey.defaultBookTreeUri),
        antiAlias = compatDsBoolean(PreferKey.antiAlias) ?: false,
        replaceEnableDefault = compatDsBoolean(PreferKey.replaceEnableDefault) ?: true,
        autoClearExpired = compatDsBoolean(PreferKey.autoClearExpired) ?: true,
        showAddToShelfAlert = compatDsBoolean(PreferKey.showAddToShelfAlert) ?: true,
        showMangaUi = compatDsBoolean(PreferKey.showMangaUi) ?: true,
        webServiceWakeLock = compatDsBoolean(PreferKey.webServiceWakeLock) ?: false,
        sourceEditMaxLine = rawSourceEditMaxLine.takeIf { it >= 10 } ?: Int.MAX_VALUE,
        webPort = compatDsInt(PreferKey.webPort) ?: 1122,
        processText = compatDsBoolean(PreferKey.processText) ?: true,
        recordLog = compatDsBoolean(PreferKey.recordLog) ?: false,
        recordHeapDump = compatDsBoolean(PreferKey.recordHeapDump) ?: false,
        audioPlayUseWakeLock = compatDsBoolean(PreferKey.audioPlayWakeLock) ?: false,
        importKeepName = compatDsBoolean(PreferKey.importKeepName) ?: false,
        importKeepGroup = compatDsBoolean(PreferKey.importKeepGroup) ?: false,
        importKeepEnable = compatDsBoolean(PreferKey.importKeepEnable) ?: false,
        fontSort = compatDsInt(PreferKey.fontSort) ?: 0,
    )
}

class OtherSettingsRepository : OtherSettingsGateway {
    override val currentSettings: OtherSettings
        get() = AppConfigStore.preferences.toOtherSettings()

    override val settings: Flow<OtherSettings> = AppConfigStore.preferencesFlow
        .map { it.toOtherSettings() }
        .distinctUntilChanged()

    override suspend fun update(update: OtherSettingsUpdate) {
        val (key, value) = when (update) {
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
            is OtherSettingsUpdate.AudioPlayUseWakeLock -> PreferKey.audioPlayWakeLock to update.value
            is OtherSettingsUpdate.ImportKeepName -> PreferKey.importKeepName to update.value
            is OtherSettingsUpdate.ImportKeepGroup -> PreferKey.importKeepGroup to update.value
            is OtherSettingsUpdate.ImportKeepEnable -> PreferKey.importKeepEnable to update.value
            is OtherSettingsUpdate.FontSort -> PreferKey.fontSort to update.value
        }
        AppConfigStore.putAllAndAwait(mapOf(key to value))
    }
}
