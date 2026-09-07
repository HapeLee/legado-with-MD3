package io.legado.app.data.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppShellSettingsRepository(
    private val preferences: PreferenceStore,
) : AppShellSettingsGateway {
    override val currentSettings: AppShellSettings
        get() = preferences.currentSnapshot().toAppShellSettings()

    override val settings: Flow<AppShellSettings> = preferences.observeSnapshot()
        .map { it.toAppShellSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppShellSettings) -> AppShellSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toAppShellSettings() },
            toPrefMap = AppShellSettings::toPrefMap,
            transform = transform,
        )
    }
}

class ThemeSettingsRepository(
    private val preferences: PreferenceStore,
) : ThemeSettingsGateway {
    override val currentSettings: ThemeSettings
        get() = preferences.currentSnapshot().toThemeSettings()

    override val settings: Flow<ThemeSettings> = preferences.observeSnapshot()
        .map { it.toThemeSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toThemeSettings() },
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = transform,
        )
    }
}

class DownloadCacheSettingsRepository(
    private val preferences: PreferenceStore,
    private val defaultUserAgent: String,
) : DownloadCacheSettingsGateway {
    override val currentSettings: DownloadCacheSettings
        get() = preferences.currentSnapshot().toDownloadCacheSettings(defaultUserAgent)

    override val settings: Flow<DownloadCacheSettings> = preferences.observeSnapshot()
        .map { it.toDownloadCacheSettings(defaultUserAgent) }
        .distinctUntilChanged()

    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toDownloadCacheSettings(defaultUserAgent) },
            toPrefMap = DownloadCacheSettings::toPrefMap,
            transform = transform,
        )
    }
}

class CoverSettingsRepository(
    private val preferences: PreferenceStore,
) : CoverSettingsGateway {
    override val currentSettings: CoverSettings
        get() = preferences.currentSnapshot().toCoverSettings()

    override val settings: Flow<CoverSettings> = preferences.observeSnapshot()
        .map { it.toCoverSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (CoverSettings) -> CoverSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toCoverSettings() },
            toPrefMap = CoverSettings::toPrefMap,
            transform = transform,
        )
    }
}

class LabSettingsRepository(
    private val preferences: PreferenceStore,
) : LabSettingsGateway {
    override val currentSettings: LabSettings
        get() = preferences.currentSnapshot().toLabSettings()

    override val settings: Flow<LabSettings> = preferences.observeSnapshot()
        .map { it.toLabSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (LabSettings) -> LabSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toLabSettings() },
            toPrefMap = LabSettings::toPrefMap,
            transform = transform,
        )
    }
}

class TranslationSettingsRepository(
    private val preferences: PreferenceStore,
) : TranslationSettingsGateway {
    override val currentSettings: TranslationSettings
        get() = preferences.currentSnapshot().toTranslationSettings()

    override val settings: Flow<TranslationSettings> = preferences.observeSnapshot()
        .map { it.toTranslationSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (TranslationSettings) -> TranslationSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toTranslationSettings() },
            toPrefMap = TranslationSettings::toPrefMap,
            transform = transform,
        )
    }
}

class BackupSettingsRepository(
    private val preferences: PreferenceStore,
) : BackupSettingsGateway {
    override val currentSettings: BackupSettings
        get() = preferences.currentSnapshot().toBackupSettings()

    override val settings: Flow<BackupSettings> = preferences.observeSnapshot()
        .map { it.toBackupSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BackupSettings) -> BackupSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toBackupSettings() },
            toPrefMap = BackupSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Map<String, PreferenceValue>.toDownloadCacheSettings(
    defaultUserAgent: String,
): DownloadCacheSettings =
    DownloadCacheSettings(
        bitmapCacheSize = compatInt(PreferKey.bitmapCacheSize) ?: 50,
        imageRetainNum = compatInt(PreferKey.imageRetainNum) ?: 0,
        preDownloadNum = compatInt(PreferKey.preDownloadNum) ?: 10,
        threadCount = compatInt(PreferKey.threadCount) ?: 16,
        cacheBookThreadCount = compatInt(PreferKey.cacheBookThreadCount) ?: 16,
        userAgent = compatString(PreferKey.userAgent).orEmpty().ifBlank { defaultUserAgent },
        cronetEnabled = compatBoolean(PreferKey.cronet) ?: false,
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

internal fun Map<String, PreferenceValue>.toCoverSettings(): CoverSettings = CoverSettings(
    loadOnlyOnWifi = compatBoolean(PreferKey.loadCoverOnlyWifi) ?: false,
    useDefaultCover = compatBoolean(PreferKey.useDefaultCover) ?: false,
    showShadow = compatBoolean(PreferKey.coverShowShadow) ?: false,
    showStroke = compatBoolean(PreferKey.coverShowStroke) ?: true,
    useDefaultColor = compatBoolean(PreferKey.coverDefaultColor) ?: true,
    textColor = compatInt(PreferKey.coverTextColor) ?: -16777216,
    shadowColor = compatInt(PreferKey.coverShadowColor) ?: -16777216,
    showName = compatBoolean(PreferKey.coverShowName) ?: true,
    showAuthor = compatBoolean(PreferKey.coverShowAuthor) ?: true,
    textColorDark = compatInt(PreferKey.coverTextColorN) ?: -1,
    shadowColorDark = compatInt(PreferKey.coverShadowColorN) ?: -1,
    showNameDark = compatBoolean(PreferKey.coverShowNameN) ?: true,
    showAuthorDark = compatBoolean(PreferKey.coverShowAuthorN) ?: true,
    infoOrientation = compatString(PreferKey.coverInfoOrientation) ?: "0",
    exploreFilterState = compatInt(PreferKey.exploreFilterState) ?: 0,
    defaultCover = compatString(PreferKey.defaultCover).orEmpty(),
    defaultCoverDark = compatString(PreferKey.defaultCoverDark).orEmpty(),
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

internal fun Map<String, PreferenceValue>.toLabSettings(): LabSettings = LabSettings(
    enabled = compatBoolean(PreferKey.labEnabled) ?: false,
    eInkDisplay = compatBoolean(PreferKey.labEInkDisplay) ?: false,
    eyeProtection = compatBoolean(PreferKey.labEyeProtection) ?: false,
)

internal fun LabSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.labEnabled to enabled,
    PreferKey.labEInkDisplay to eInkDisplay,
    PreferKey.labEyeProtection to eyeProtection,
)

internal fun Map<String, PreferenceValue>.toTranslationSettings(): TranslationSettings {
    val storedProvider = compatString(PreferKey.llmProvider)
        ?: TranslationConstants.PROVIDER_GOOGLE
    return TranslationSettings(
        provider = if (storedProvider == TranslationConstants.PROVIDER_OPENAI) {
            TranslationConstants.PROVIDER_APP_AI
        } else {
            storedProvider
        },
        targetLanguage = compatString(PreferKey.llmTargetLanguage) ?: "zh",
        maxCharsPerChunk = compatInt(PreferKey.llmMaxCharsPerChunk) ?: 10000,
        concurrentChunks = compatInt(PreferKey.llmConcurrentChunks) ?: 1,
        retryCount = compatInt(PreferKey.llmRetryCount) ?: 2,
    )
}

internal fun TranslationSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.llmProvider to provider,
    PreferKey.llmTargetLanguage to targetLanguage,
    PreferKey.llmMaxCharsPerChunk to maxCharsPerChunk,
    PreferKey.llmConcurrentChunks to concurrentChunks,
    PreferKey.llmRetryCount to retryCount,
)

internal fun Map<String, PreferenceValue>.toBackupSettings(): BackupSettings = BackupSettings(
    webDavUrl = compatString(PreferKey.webDavUrl).orEmpty(),
    webDavAccount = compatString(PreferKey.webDavAccount).orEmpty(),
    webDavPassword = compatString(PreferKey.webDavPassword).orEmpty(),
    webDavDir = compatString(PreferKey.webDavDir) ?: "legado",
    webDavDeviceName = compatString(PreferKey.webDavDeviceName).orEmpty(),
    syncBookProgress = compatBoolean(PreferKey.syncBookProgress) ?: true,
    syncBookProgressPlus = compatBoolean(PreferKey.syncBookProgressPlus) ?: false,
    autoCheckNewBackup = compatBoolean(PreferKey.autoCheckNewBackup) ?: true,
    onlyLatestBackup = compatBoolean(PreferKey.onlyLatestBackup) ?: true,
    backupSyncMode = compatString(PreferKey.backupSyncMode) ?: "both",
    backupPath = compatString(PreferKey.backupPath),
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

internal fun Map<String, PreferenceValue>.toAppShellSettings(): AppShellSettings = AppShellSettings(
    themeMode = compatString(PreferKey.themeMode) ?: "0",
    fontScale = compatInt(PreferKey.fontScale) ?: 10,
    composeEngine = compatString(PreferKey.composeEngine) ?: "material",
    showHome = compatBoolean(PreferKey.showHome) ?: true,
    showDiscovery = compatBoolean(PreferKey.showDiscovery) ?: true,
    showRss = compatBoolean(PreferKey.showRss) ?: true,
    showStatusBar = compatBoolean(PreferKey.showStatusBar) ?: true,
    swipeAnimation = compatBoolean(PreferKey.swipeAnimation) ?: true,
    predictiveBackEnabled = compatBoolean(PreferKey.isPredictiveBackEnabled) ?: true,
    showBottomView = compatBoolean(PreferKey.showBottomView) ?: true,
    useFloatingBottomBar = compatBoolean(PreferKey.useFloatingBottomBar) ?: false,
    useFloatingBottomBarLiquidGlass =
        compatBoolean(PreferKey.useFloatingBottomBarLiquidGlass) ?: false,
    tabletInterface = compatString(PreferKey.tabletInterface) ?: "auto",
    labelVisibilityMode = compatString(PreferKey.labelVisibilityMode) ?: "auto",
    defaultHomePage = compatString(PreferKey.defaultHomePage) ?: "bookshelf",
    mainNavigationOrder = compatString(PreferKey.mainNavigationOrder)
        ?: "home,bookshelf,explore,rss,my",
    navExtended = compatBoolean(PreferKey.navExtended) ?: false,
    navIconHome = compatString(PreferKey.navIconHome) ?: "",
    navIconBookshelf = compatString(PreferKey.navIconBookshelf) ?: "",
    navIconExplore = compatString(PreferKey.navIconExplore) ?: "",
    navIconRss = compatString(PreferKey.navIconRss) ?: "",
    navIconMy = compatString(PreferKey.navIconMy) ?: "",
    navIconHomeSelected = compatString(PreferKey.navIconHomeSelected) ?: "",
    navIconBookshelfSelected = compatString(PreferKey.navIconBookshelfSelected) ?: "",
    navIconExploreSelected = compatString(PreferKey.navIconExploreSelected) ?: "",
    navIconRssSelected = compatString(PreferKey.navIconRssSelected) ?: "",
    navIconMySelected = compatString(PreferKey.navIconMySelected) ?: "",
    launcherIcon = compatString(PreferKey.launcherIcon) ?: "ic_launcher",
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
    PreferKey.navIconHomeSelected to navIconHomeSelected,
    PreferKey.navIconBookshelfSelected to navIconBookshelfSelected,
    PreferKey.navIconExploreSelected to navIconExploreSelected,
    PreferKey.navIconRssSelected to navIconRssSelected,
    PreferKey.navIconMySelected to navIconMySelected,
    PreferKey.launcherIcon to launcherIcon,
)

internal fun Map<String, PreferenceValue>.toThemeSettings(): ThemeSettings = ThemeSettings(
    appTheme = compatString(PreferKey.appTheme) ?: "0",
    useMiuixMonet = compatBoolean(PreferKey.useMiuixMonet) ?: false,
    isPureBlack = compatBoolean(PreferKey.pureBlack) ?: false,
    paletteStyle = compatString(PreferKey.paletteStyle) ?: "tonalSpot",
    materialVersion = compatString(PreferKey.materialVersion) ?: "material3",
    customContrast = compatString(PreferKey.customContrast) ?: "Default",
    customMode = compatString(PreferKey.customMode) ?: "tonalSpot",
    appFontPath = compatString(PreferKey.appFontPath),
    customPrimary = compatInt(PreferKey.cPrimary) ?: 0,
    customNightPrimary = compatInt(PreferKey.cNPrimary) ?: 0,
    enableDeepPersonalization = compatBoolean(PreferKey.enableDeepPersonalization) ?: false,
    themeColor = compatInt(PreferKey.themeColor) ?: 0,
    secondaryThemeColor = compatInt(PreferKey.secondaryThemeColor) ?: 0,
    primaryTextColor = compatInt(PreferKey.primaryTextColor) ?: 0,
    secondaryTextColor = compatInt(PreferKey.secondaryTextColor) ?: 0,
    themeBackgroundColor = compatInt(PreferKey.themeBackgroundColor) ?: 0,
    labelContainerColor = compatInt(PreferKey.labelContainerColor) ?: 0,
    themeColorNight = compatInt(PreferKey.themeColorNight) ?: 0,
    secondaryThemeColorNight = compatInt(PreferKey.secondaryThemeColorNight) ?: 0,
    primaryTextColorNight = compatInt(PreferKey.primaryTextColorNight) ?: 0,
    secondaryTextColorNight = compatInt(PreferKey.secondaryTextColorNight) ?: 0,
    themeBackgroundColorNight = compatInt(PreferKey.themeBackgroundColorNight) ?: 0,
    labelContainerColorNight = compatInt(PreferKey.labelContainerColorNight) ?: 0,
    containerOpacity = compatInt(PreferKey.containerOpacity) ?: 100,
    overrideBaseCardCornerRadius =
        compatBoolean(PreferKey.overrideBaseCardCornerRadius) ?: false,
    baseCardCornerRadius = compatFloat(PreferKey.baseCardCornerRadius) ?: 16f,
    overrideBaseCardBorder = compatBoolean(PreferKey.overrideBaseCardBorder) ?: false,
    baseCardBorderWidth = compatFloat(PreferKey.baseCardBorderWidth) ?: 1f,
    baseCardBorderColor = compatInt(PreferKey.baseCardBorderColor) ?: 0,
    baseCardBorderColorNight = compatInt(PreferKey.baseCardBorderColorNight) ?: 0,
    disableSplicedColumnGroupCornerRadius =
        compatBoolean(PreferKey.disableSplicedColumnGroupCornerRadius) ?: false,
    topBarOpacity = compatInt(PreferKey.topBarOpacity) ?: 100,
    bottomBarOpacity = compatInt(PreferKey.bottomBarOpacity) ?: 100,
    enableBlur = compatBoolean(PreferKey.enableBlur) ?: false,
    enableProgressiveBlur = compatBoolean(PreferKey.enableProgressiveBlur) ?: false,
    topBarBlurRadius = compatInt(PreferKey.topBarBlurRadius) ?: 24,
    bottomBarBlurRadius = compatInt(PreferKey.bottomBarBlurRadius) ?: 8,
    topBarBlurAlpha = compatInt(PreferKey.topBarBlurAlpha) ?: 73,
    bottomBarBlurAlpha = compatInt(PreferKey.bottomBarBlurAlpha) ?: 40,
    bottomBarLensRadius = compatFloat(PreferKey.bottomBarLensRadius) ?: 24f,
    useFlexibleTopAppBar = compatBoolean(PreferKey.useFlexibleTopAppBar) ?: true,
    topBarButtonStyle = compatString(PreferKey.topBarButtonStyle) ?: "tonal",
    mergeTopBarActions = compatBoolean(PreferKey.mergeTopBarActions) ?: false,
    bookInfoFollowCoverColor = compatBoolean(PreferKey.bookInfoFollowCoverColor) ?: true,
    bookInfoNetworkCoverBackground =
        compatString(PreferKey.bookInfoNetworkCoverBackground) ?: "on",
    bookInfoDefaultCoverBackground =
        compatString(PreferKey.bookInfoDefaultCoverBackground) ?: "on",
    bookInfoInputColor = compatInt(PreferKey.bookInfoInputColor) ?: 0,
    backgroundImageLight = compatString(PreferKey.bgImage),
    backgroundImageDark = compatString(PreferKey.bgImageN),
    backgroundImageBlurring = compatInt(PreferKey.bgImageBlurring) ?: 0,
    backgroundImageDarkBlurring = compatInt(PreferKey.bgImageNBlurring) ?: 0,
    largeContainerBackgroundImageLight = compatString(PreferKey.largeContainerBackgroundImageLight),
    largeContainerBackgroundImageDark = compatString(PreferKey.largeContainerBackgroundImageDark),
    itemBackgroundImageLight = compatString(PreferKey.itemBackgroundImageLight),
    itemBackgroundImageDark = compatString(PreferKey.itemBackgroundImageDark),
    enableContainerBackgroundImage = compatBoolean(PreferKey.enableContainerBackgroundImage) ?: false,
    appColumnBackgroundOpacity = compatInt(PreferKey.appColumnBackgroundOpacity) ?: 100,
    glassCardBackgroundOpacity = compatInt(PreferKey.glassCardBackgroundOpacity) ?: 100,
    enableItemDivider = compatBoolean(PreferKey.enableItemDivider) ?: false,
    itemDividerWidth = compatFloat(PreferKey.itemDividerWidth) ?: 1f,
    itemDividerLength = compatFloat(PreferKey.itemDividerLength) ?: 80f,
    itemDividerColor = compatInt(PreferKey.itemDividerColor) ?: 0,
    eyeProtectionEnabled = compatBoolean(PreferKey.eyeProtectionEnabled) ?: false,
    colorTemperature = compatInt(PreferKey.colorTemperature) ?: 50,
    eyeProtectionAutoNight = compatBoolean(PreferKey.eyeProtectionAutoNight) ?: false,
    eyeProtectionSchedule = compatBoolean(PreferKey.eyeProtectionSchedule) ?: false,
    eyeProtectionStartTime = compatString(PreferKey.eyeProtectionStartTime) ?: "22:00",
    eyeProtectionEndTime = compatString(PreferKey.eyeProtectionEndTime) ?: "07:00",
    showRefactorTip = compatBoolean(SHOW_THEME_REFACTOR_TIP) ?: true,
    enableCustomTagColors = compatBoolean(PreferKey.enableCustomTagColors) ?: false,
    customTagColorsJson = compatString(PreferKey.customTagColors),
)

/**
 * Theme gateway 的 60 键写入边界。主题包专用的 [ThemeSettings.customMode] 与
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
    PreferKey.topBarButtonStyle to topBarButtonStyle,
    PreferKey.mergeTopBarActions to mergeTopBarActions,
    PreferKey.bookInfoFollowCoverColor to bookInfoFollowCoverColor,
    PreferKey.bookInfoNetworkCoverBackground to bookInfoNetworkCoverBackground,
    PreferKey.bookInfoDefaultCoverBackground to bookInfoDefaultCoverBackground,
    PreferKey.bgImage to backgroundImageLight,
    PreferKey.bgImageN to backgroundImageDark,
    PreferKey.bgImageBlurring to backgroundImageBlurring,
    PreferKey.bgImageNBlurring to backgroundImageDarkBlurring,
    PreferKey.largeContainerBackgroundImageLight to largeContainerBackgroundImageLight,
    PreferKey.largeContainerBackgroundImageDark to largeContainerBackgroundImageDark,
    PreferKey.itemBackgroundImageLight to itemBackgroundImageLight,
    PreferKey.itemBackgroundImageDark to itemBackgroundImageDark,
    PreferKey.enableContainerBackgroundImage to enableContainerBackgroundImage,
    PreferKey.appColumnBackgroundOpacity to appColumnBackgroundOpacity,
    PreferKey.glassCardBackgroundOpacity to glassCardBackgroundOpacity,
    PreferKey.enableItemDivider to enableItemDivider,
    PreferKey.itemDividerWidth to itemDividerWidth,
    PreferKey.itemDividerLength to itemDividerLength,
    PreferKey.itemDividerColor to itemDividerColor,
    PreferKey.eyeProtectionEnabled to eyeProtectionEnabled,
    PreferKey.colorTemperature to colorTemperature,
    PreferKey.eyeProtectionAutoNight to eyeProtectionAutoNight,
    PreferKey.eyeProtectionSchedule to eyeProtectionSchedule,
    PreferKey.eyeProtectionStartTime to eyeProtectionStartTime,
    PreferKey.eyeProtectionEndTime to eyeProtectionEndTime,
    SHOW_THEME_REFACTOR_TIP to showRefactorTip,
    PreferKey.enableCustomTagColors to enableCustomTagColors,
    PreferKey.customTagColors to customTagColorsJson,
)

internal fun Map<String, PreferenceValue>.toOtherSettings(): OtherSettings {
    val rawSourceEditMaxLine = compatInt(PreferKey.sourceEditMaxLine) ?: Int.MAX_VALUE
    return OtherSettings(
        updateToVariant = compatString(PreferKey.updateToVariant) ?: "official_version",
        autoCheckUpdateOnStart = compatBoolean(PreferKey.autoCheckUpdateOnStart) ?: false,
        webServiceAutoStart = compatBoolean(PreferKey.webServiceAutoStart) ?: false,
        autoRefresh = compatBoolean(PreferKey.autoRefresh) ?: false,
        defaultToRead = compatBoolean(PreferKey.defaultToRead) ?: false,
        notificationsPost = compatBoolean(PreferKey.notificationsPost) ?: true,
        ignoreBatteryPermission = compatBoolean(PreferKey.ignoreBatteryPermission) ?: true,
        firebaseEnable = compatBoolean(PreferKey.firebaseEnable) ?: true,
        defaultBookTreeUri = compatString(PreferKey.defaultBookTreeUri),
        antiAlias = compatBoolean(PreferKey.antiAlias) ?: false,
        replaceEnableDefault = compatBoolean(PreferKey.replaceEnableDefault) ?: true,
        autoClearExpired = compatBoolean(PreferKey.autoClearExpired) ?: true,
        showAddToShelfAlert = compatBoolean(PreferKey.showAddToShelfAlert) ?: true,
        showMangaUi = compatBoolean(PreferKey.showMangaUi) ?: true,
        webServiceWakeLock = compatBoolean(PreferKey.webServiceWakeLock) ?: false,
        sourceEditMaxLine = rawSourceEditMaxLine.takeIf { it >= 10 } ?: Int.MAX_VALUE,
        webPort = compatInt(PreferKey.webPort) ?: 1122,
        processText = compatBoolean(PreferKey.processText) ?: true,
        recordLog = compatBoolean(PreferKey.recordLog) ?: false,
        recordHeapDump = compatBoolean(PreferKey.recordHeapDump) ?: false,
        audioPlayUseWakeLock = compatBoolean(PreferKey.audioPlayWakeLock) ?: false,
        importKeepName = compatBoolean(PreferKey.importKeepName) ?: false,
        importKeepGroup = compatBoolean(PreferKey.importKeepGroup) ?: false,
        importKeepEnable = compatBoolean(PreferKey.importKeepEnable) ?: false,
        fontSort = compatInt(PreferKey.fontSort) ?: 0,
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

class OtherSettingsRepository(
    private val preferences: PreferenceStore,
) : OtherSettingsGateway {
    override val currentSettings: OtherSettings
        get() = preferences.currentSnapshot().toOtherSettings()

    override val settings: Flow<OtherSettings> = preferences.observeSnapshot()
        .map { it.toOtherSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toOtherSettings() },
            toPrefMap = OtherSettings::toPrefMap,
            transform = transform,
        )
    }
}

// 原 LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP 的 key 名
private const val SHOW_THEME_REFACTOR_TIP = "show_theme_refactor_tip"
