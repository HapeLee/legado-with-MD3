package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.LabSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.model.settings.TranslationSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsFloat
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppShellSettingsRepository : AppShellSettingsGateway {
    override val currentSettings: AppShellSettings
        get() = AppConfigStore.preferences.toAppShellSettings()

    override val settings: Flow<AppShellSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toAppShellSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (AppShellSettings) -> AppShellSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toAppShellSettings,
            toPrefMap = AppShellSettings::toPrefMap,
            transform = transform,
        )
    }
}

class ThemeSettingsRepository : ThemeSettingsGateway {
    override val currentSettings: ThemeSettings
        get() = AppConfigStore.preferences.toThemeSettings()

    override val settings: Flow<ThemeSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toThemeSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toThemeSettings,
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = transform,
        )
    }
}

class DownloadCacheSettingsRepository : DownloadCacheSettingsGateway {
    override val currentSettings: DownloadCacheSettings
        get() = AppConfigStore.preferences.toDownloadCacheSettings()

    override val settings: Flow<DownloadCacheSettings> = AppConfigStore.preferencesFlow
        .map { it.toDownloadCacheSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        AppConfigStore.atomicUpdateAndAwait(
            read = Preferences::toDownloadCacheSettings,
            toPrefMap = DownloadCacheSettings::toPrefMap,
            transform = transform,
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
        AppConfigStore.atomicUpdate(
            read = Preferences::toCoverSettings,
            toPrefMap = CoverSettings::toPrefMap,
            transform = transform,
        )
    }
}

class LabSettingsRepository : LabSettingsGateway {
    override val currentSettings: LabSettings
        get() = AppConfigStore.preferences.toLabSettings()

    override val settings: Flow<LabSettings> = AppConfigStore.preferencesFlow
        .map { it.toLabSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (LabSettings) -> LabSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toLabSettings,
            toPrefMap = LabSettings::toPrefMap,
            transform = transform,
        )
    }
}

class TranslationSettingsRepository : TranslationSettingsGateway {
    override val currentSettings: TranslationSettings
        get() = AppConfigStore.preferences.toTranslationSettings()

    override val settings: Flow<TranslationSettings> = AppConfigStore.preferencesFlow
        .map { it.toTranslationSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (TranslationSettings) -> TranslationSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toTranslationSettings,
            toPrefMap = TranslationSettings::toPrefMap,
            transform = transform,
        )
    }
}

class BackupSettingsRepository : BackupSettingsGateway {
    override val currentSettings: BackupSettings
        get() = AppConfigStore.preferences.toBackupSettings()

    override val settings: Flow<BackupSettings> = AppConfigStore.preferencesFlow
        .map { it.toBackupSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toBackupSettings,
            toPrefMap = BackupSettings::toPrefMap,
            transform = transform,
        )
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

internal fun BackupSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.webDavUrl to webDavUrl,
    PreferKey.webDavAccount to webDavAccount,
    PreferKey.webDavPassword to webDavPassword,
    PreferKey.webDavDir to webDavDir,
    PreferKey.webDavDeviceName to webDavDeviceName,
    PreferKey.syncBookProgress to syncBookProgress,
    PreferKey.syncBookProgressPlus to syncBookProgressPlus,
    PreferKey.autoCheckNewBackup to autoCheckNewBackup,
    PreferKey.onlyLatestBackup to onlyLatestBackup,
    PreferKey.backupSyncMode to backupSyncMode,
    PreferKey.backupPath to backupPath,
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

internal fun AppShellSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.themeMode to themeMode,
    PreferKey.fontScale to fontScale,
    PreferKey.composeEngine to composeEngine,
    PreferKey.showHome to showHome,
    PreferKey.showDiscovery to showDiscovery,
    PreferKey.showRss to showRss,
    PreferKey.showStatusBar to showStatusBar,
    PreferKey.swipeAnimation to swipeAnimation,
    PreferKey.isPredictiveBackEnabled to predictiveBackEnabled,
    PreferKey.showBottomView to showBottomView,
    PreferKey.useFloatingBottomBar to useFloatingBottomBar,
    PreferKey.useFloatingBottomBarLiquidGlass to useFloatingBottomBarLiquidGlass,
    PreferKey.tabletInterface to tabletInterface,
    PreferKey.labelVisibilityMode to labelVisibilityMode,
    PreferKey.defaultHomePage to defaultHomePage,
    PreferKey.mainNavigationOrder to mainNavigationOrder,
    PreferKey.navExtended to navExtended,
    PreferKey.navIconHome to navIconHome,
    PreferKey.navIconBookshelf to navIconBookshelf,
    PreferKey.navIconExplore to navIconExplore,
    PreferKey.navIconRss to navIconRss,
    PreferKey.navIconMy to navIconMy,
    PreferKey.launcherIcon to launcherIcon,
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
    overrideBaseCardCornerRadius =
        compatDsBoolean(PreferKey.overrideBaseCardCornerRadius) ?: false,
    baseCardCornerRadius = compatDsFloat(PreferKey.baseCardCornerRadius) ?: 16f,
    overrideBaseCardBorder = compatDsBoolean(PreferKey.overrideBaseCardBorder) ?: false,
    baseCardBorderWidth = compatDsFloat(PreferKey.baseCardBorderWidth) ?: 1f,
    baseCardBorderColor = compatDsInt(PreferKey.baseCardBorderColor) ?: 0,
    baseCardBorderColorNight = compatDsInt(PreferKey.baseCardBorderColorNight) ?: 0,
    disableSplicedColumnGroupCornerRadius =
        compatDsBoolean(PreferKey.disableSplicedColumnGroupCornerRadius) ?: false,
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

/**
 * Theme gateway 的 59 键写入边界。主题包专用的 [ThemeSettings.customMode] 与
 * [ThemeSettings.bookInfoInputColor] 由 ThemePackageSettingsGateway 的事务路径持久化。
 */
internal fun ThemeSettings.toGatewayPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.appTheme to appTheme,
    PreferKey.useMiuixMonet to useMiuixMonet,
    PreferKey.pureBlack to isPureBlack,
    PreferKey.paletteStyle to paletteStyle,
    PreferKey.materialVersion to materialVersion,
    PreferKey.customContrast to customContrast,
    PreferKey.appFontPath to appFontPath,
    PreferKey.cPrimary to customPrimary,
    PreferKey.cNPrimary to customNightPrimary,
    PreferKey.enableDeepPersonalization to enableDeepPersonalization,
    PreferKey.themeColor to themeColor,
    PreferKey.secondaryThemeColor to secondaryThemeColor,
    PreferKey.primaryTextColor to primaryTextColor,
    PreferKey.secondaryTextColor to secondaryTextColor,
    PreferKey.themeBackgroundColor to themeBackgroundColor,
    PreferKey.labelContainerColor to labelContainerColor,
    PreferKey.themeColorNight to themeColorNight,
    PreferKey.secondaryThemeColorNight to secondaryThemeColorNight,
    PreferKey.primaryTextColorNight to primaryTextColorNight,
    PreferKey.secondaryTextColorNight to secondaryTextColorNight,
    PreferKey.themeBackgroundColorNight to themeBackgroundColorNight,
    PreferKey.labelContainerColorNight to labelContainerColorNight,
    PreferKey.containerOpacity to containerOpacity,
    PreferKey.overrideBaseCardCornerRadius to overrideBaseCardCornerRadius,
    PreferKey.baseCardCornerRadius to baseCardCornerRadius,
    PreferKey.overrideBaseCardBorder to overrideBaseCardBorder,
    PreferKey.baseCardBorderWidth to baseCardBorderWidth,
    PreferKey.baseCardBorderColor to baseCardBorderColor,
    PreferKey.baseCardBorderColorNight to baseCardBorderColorNight,
    PreferKey.disableSplicedColumnGroupCornerRadius to disableSplicedColumnGroupCornerRadius,
    PreferKey.topBarOpacity to topBarOpacity,
    PreferKey.bottomBarOpacity to bottomBarOpacity,
    PreferKey.enableBlur to enableBlur,
    PreferKey.enableProgressiveBlur to enableProgressiveBlur,
    PreferKey.topBarBlurRadius to topBarBlurRadius,
    PreferKey.bottomBarBlurRadius to bottomBarBlurRadius,
    PreferKey.topBarBlurAlpha to topBarBlurAlpha,
    PreferKey.bottomBarBlurAlpha to bottomBarBlurAlpha,
    PreferKey.bottomBarLensRadius to bottomBarLensRadius,
    PreferKey.useFlexibleTopAppBar to useFlexibleTopAppBar,
    PreferKey.bookInfoFollowCoverColor to bookInfoFollowCoverColor,
    PreferKey.bookInfoNetworkCoverBackground to bookInfoNetworkCoverBackground,
    PreferKey.bookInfoDefaultCoverBackground to bookInfoDefaultCoverBackground,
    PreferKey.bgImage to backgroundImageLight,
    PreferKey.bgImageN to backgroundImageDark,
    PreferKey.bgImageBlurring to backgroundImageBlurring,
    PreferKey.bgImageNBlurring to backgroundImageDarkBlurring,
    PreferKey.enableItemDivider to enableItemDivider,
    PreferKey.itemDividerWidth to itemDividerWidth,
    PreferKey.itemDividerLength to itemDividerLength,
    PreferKey.itemDividerColor to itemDividerColor,
    PreferKey.eyeProtectionEnabled to eyeProtectionEnabled,
    PreferKey.colorTemperature to colorTemperature,
    PreferKey.eyeProtectionSchedule to eyeProtectionSchedule,
    PreferKey.eyeProtectionStartTime to eyeProtectionStartTime,
    PreferKey.eyeProtectionEndTime to eyeProtectionEndTime,
    io.legado.app.data.local.preferences.LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP.name to
        showRefactorTip,
    PreferKey.enableCustomTagColors to enableCustomTagColors,
    PreferKey.customTagColors to customTagColorsJson,
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

internal fun OtherSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.updateToVariant to updateToVariant,
    PreferKey.autoCheckUpdateOnStart to autoCheckUpdateOnStart,
    PreferKey.webServiceAutoStart to webServiceAutoStart,
    PreferKey.autoRefresh to autoRefresh,
    PreferKey.defaultToRead to defaultToRead,
    PreferKey.notificationsPost to notificationsPost,
    PreferKey.ignoreBatteryPermission to ignoreBatteryPermission,
    PreferKey.firebaseEnable to firebaseEnable,
    PreferKey.defaultBookTreeUri to defaultBookTreeUri,
    PreferKey.antiAlias to antiAlias,
    PreferKey.replaceEnableDefault to replaceEnableDefault,
    PreferKey.autoClearExpired to autoClearExpired,
    PreferKey.showAddToShelfAlert to showAddToShelfAlert,
    PreferKey.showMangaUi to showMangaUi,
    PreferKey.webServiceWakeLock to webServiceWakeLock,
    PreferKey.sourceEditMaxLine to sourceEditMaxLine,
    PreferKey.webPort to webPort,
    PreferKey.processText to processText,
    PreferKey.recordLog to recordLog,
    PreferKey.recordHeapDump to recordHeapDump,
    PreferKey.audioPlayWakeLock to audioPlayUseWakeLock,
    PreferKey.importKeepName to importKeepName,
    PreferKey.importKeepGroup to importKeepGroup,
    PreferKey.importKeepEnable to importKeepEnable,
    PreferKey.fontSort to fontSort,
)

class OtherSettingsRepository : OtherSettingsGateway {
    override val currentSettings: OtherSettings
        get() = AppConfigStore.preferences.toOtherSettings()

    override val settings: Flow<OtherSettings> = AppConfigStore.preferencesFlow
        .map { it.toOtherSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
        AppConfigStore.atomicUpdateAndAwait(
            read = Preferences::toOtherSettings,
            toPrefMap = OtherSettings::toPrefMap,
            transform = transform,
        )
    }
}
