package io.legado.app.help.storage

import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

internal val alwaysIgnoredPreferenceKeys = setOf(
    PreferKey.defaultCover,
    PreferKey.defaultCoverDark,
    PreferKey.backupPath,
    PreferKey.defaultBookTreeUri,
    PreferKey.webDavDeviceName,
    PreferKey.launcherIcon,
    PreferKey.bitmapCacheSize,
    PreferKey.webServiceWakeLock,
    PreferKey.readAloudWakeLock,
    PreferKey.audioPlayWakeLock,
    LocalPreferencesKeys.PASSWORD.name,
    LocalPreferencesKeys.MIGRATED_TO_SETTINGS.name,
)

/**
 * 备份配置
 */
@Suppress("ConstPropertyName")
object BackupConfig {

    private val ignoreConfigPath = FileUtils.getPath(appCtx.filesDir, "restoreIgnore.json")
    val ignoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(ignoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private val backupIgnoreConfigPath = FileUtils.getPath(appCtx.filesDir, "backupIgnore.json")
    val backupIgnoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(backupIgnoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private val dbIgnoreConfigPath = FileUtils.getPath(appCtx.filesDir, "dbIgnore.json")
    val dbIgnoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(dbIgnoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private val backupDbIgnoreConfigPath = FileUtils.getPath(appCtx.filesDir, "backupDbIgnore.json")
    val backupDbIgnoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(backupDbIgnoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private const val readConfigKey = "readConfig"
    private const val themeConfigKey = "themeConfig"
    private const val coverConfigKey = "coverConfig"
    private const val localBookKey = "localBook"
    private const val mangaKey = "mangaConfig"

    //数据库忽略key
    private const val dbKeyBookmark = "bookmark"
    private const val dbKeyBookGroup = "bookGroup"
    private const val dbKeyBookSource = "bookSource"
    private const val dbKeyRssSource = "rssSource"
    private const val dbKeyRssStar = "rssStar"
    private const val dbKeyReplaceRule = "replaceRule"
    private const val dbKeyReadRecord = "readRecord"
    private const val dbKeySearchHistory = "searchHistory"
    private const val dbKeySourceSub = "sourceSub"
    private const val dbKeyTxtTocRule = "txtTocRule"
    private const val dbKeyHttpTts = "httpTTS"
    private const val dbKeyKeyboardAssists = "keyboardAssists"
    private const val dbKeyDictRule = "dictRule"
    private const val dbKeyHomepageModules = "homepageModules"
    private const val dbKeyHomepageCustomSets = "homepageCustomSets"
    private const val dbKeyHighlightRule = "highlightRule"
    private const val dbKeyHighlightTagRule = "highlightTagRule"
    private const val dbKeyTagGroupRule = "tagGroupRule"
    private const val dbKeyServer = "server"

    val dbIgnoreKeys = arrayOf(
        dbKeyBookmark, dbKeyBookGroup, dbKeyBookSource, dbKeyRssSource,
        dbKeyRssStar, dbKeyReplaceRule, dbKeyReadRecord, dbKeySearchHistory,
        dbKeySourceSub, dbKeyTxtTocRule, dbKeyHttpTts, dbKeyKeyboardAssists,
        dbKeyDictRule, dbKeyHomepageModules, dbKeyHomepageCustomSets,
        dbKeyHighlightRule, dbKeyHighlightTagRule, dbKeyTagGroupRule, dbKeyServer
    )

    val dbIgnoreTitle = arrayOf(
        appCtx.getString(R.string.bookmark),
        appCtx.getString(R.string.book_group),
        appCtx.getString(R.string.book_source),
        appCtx.getString(R.string.rss_source),
        appCtx.getString(R.string.rss_star),
        appCtx.getString(R.string.replace_rule),
        appCtx.getString(R.string.read_record),
        appCtx.getString(R.string.search_history),
        appCtx.getString(R.string.source_sub),
        appCtx.getString(R.string.txt_toc_rule),
        appCtx.getString(R.string.http_tts),
        appCtx.getString(R.string.keyboard_assists),
        appCtx.getString(R.string.dict_rule),
        appCtx.getString(R.string.homepage_modules),
        appCtx.getString(R.string.homepage_custom_sets),
        appCtx.getString(R.string.highlight_rule_config),
        appCtx.getString(R.string.highlight_tag_config),
        appCtx.getString(R.string.tag_group_rules),
        appCtx.getString(R.string.server_config)
    )

    val backupDbIgnoreKeys = dbIgnoreKeys
    val backupDbIgnoreTitle = dbIgnoreTitle

    fun dbIsNotIgnored(key: String, isBackup: Boolean = false): Boolean {
        val config = if (isBackup) backupDbIgnoreConfig else dbIgnoreConfig
        return config[key] != true
    }

    fun saveDbIgnoreConfig() {
        val json = GSON.toJson(dbIgnoreConfig)
        FileUtils.createFileIfNotExist(dbIgnoreConfigPath).writeText(json)
    }

    fun saveBackupDbIgnoreConfig() {
        val json = GSON.toJson(backupDbIgnoreConfig)
        FileUtils.createFileIfNotExist(backupDbIgnoreConfigPath).writeText(json)
    }

    //配置忽略key
    val ignoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey,
        mangaKey
    )

    //配置忽略标题
    val ignoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book),
        appCtx.getString(R.string.manga_config)
    )

    //备份忽略key
    val backupIgnoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey,
        mangaKey
    )

    //备份忽略标题
    val backupIgnoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book),
        appCtx.getString(R.string.manga_config)
    )

    //阅读配置
    private val readPrefKeys = arrayOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.autoReadSpeed,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR,
        // 阅读进度条
        PreferKey.readBarStyle,
        PreferKey.readBarStyleFollowPage,
        // 阅读菜单
        PreferKey.readMenuBgColor,
        PreferKey.readMenuAccentColor,
        PreferKey.readMenuContainerColor,
        PreferKey.readMenuBgColorNight,
        PreferKey.readMenuAccentColorNight,
        PreferKey.readMenuContainerColorNight,
        PreferKey.readMenuTextColor,
        PreferKey.readMenuTextColorNight,
        PreferKey.readMenuColorMode,
        PreferKey.readMenuIconShowText,
        PreferKey.readMenuIconStyle,
        PreferKey.readMenuIconItemsPerRow,
        PreferKey.readMenuIconRowCount,
        PreferKey.readMenuBottomCornerRadius,
        PreferKey.readMenuFloatingBottomBar,
        PreferKey.readMenuTopBarBlurMode,
        PreferKey.readMenuBottomBarBlurMode,
        PreferKey.readMenuTopBarLiquidGlassButtons,
        PreferKey.readMenuTopBarTitleCapsule,
        PreferKey.readMenuBottomBarLiquidGlassButtons,
        PreferKey.readMenuFloatingIconLiquidGlass,
        PreferKey.readMenuTopBarBlurStyle,
        PreferKey.readMenuBottomBarBlurStyle,
        PreferKey.readMenuBlurRadius,
        PreferKey.readMenuBlurAlpha,
        PreferKey.readMenuBlurColor,
        PreferKey.readMenuBlurColorNight,
        PreferKey.readMenuPaletteStyle,
        PreferKey.readMenuLensRadius,
        PreferKey.readMenuBorderWidth,
        PreferKey.readMenuBorderColor,
        PreferKey.readMenuBorderColorNight,
        PreferKey.readMenuCustomIcons,
        // 标题栏
        PreferKey.titleBarIconStyle,
        PreferKey.titleBarCustomIcons,
        PreferKey.titleBarIconPosition,
        PreferKey.showTitleBarIcons,
        PreferKey.showMenuIcon,
        PreferKey.titleBarMode,
        PreferKey.shouldShowExpandButton,
        PreferKey.eyeProtectionEnabled,
        PreferKey.colorTemperature,
        PreferKey.eyeProtectionAutoNight,
    )

    private val themePrefKeys = arrayOf(
        PreferKey.cPrimary,
        PreferKey.cNPrimary,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.themeColor,
        PreferKey.secondaryThemeColor,
        PreferKey.themeColorNight,
        PreferKey.secondaryThemeColorNight,
        PreferKey.paletteStyle,
        PreferKey.materialVersion,
        PreferKey.composeEngine,
        PreferKey.customContrast,
        PreferKey.customMode,
        PreferKey.useMiuixMonet,
        PreferKey.containerOpacity,
        PreferKey.topBarOpacity,
        PreferKey.bottomBarOpacity,
        PreferKey.enableBlur,
        PreferKey.enableProgressiveBlur,
        PreferKey.topBarBlurRadius,
        PreferKey.bottomBarBlurRadius,
        PreferKey.topBarBlurAlpha,
        PreferKey.bottomBarBlurAlpha,
        PreferKey.bottomBarLensRadius,
        PreferKey.useFlexibleTopAppBar,
        PreferKey.bookInfoFollowCoverColor,
        PreferKey.bookInfoBackgroundBlur,
        PreferKey.bookInfoNetworkCoverBackground,
        PreferKey.bookInfoDefaultCoverBackground,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.enableDeepPersonalization,
        PreferKey.primaryTextColor,
        PreferKey.secondaryTextColor,
        PreferKey.themeBackgroundColor,
        PreferKey.labelContainerColor,
        PreferKey.primaryTextColorNight,
        PreferKey.secondaryTextColorNight,
        PreferKey.themeBackgroundColorNight,
        PreferKey.labelContainerColorNight
    )

    private val bookshelfPrefKeys = arrayOf(
        PreferKey.bookshelfLayout,
        PreferKey.bookshelfLayoutModePortrait,
        PreferKey.bookshelfLayoutModeLandscape,
        PreferKey.bookshelfLayoutCompact,
        PreferKey.bookshelfShowDivider,
        PreferKey.bookshelfGridLayout,
        PreferKey.bookshelfSort,
        PreferKey.bookshelfSortOrder,
        PreferKey.bookshelfLayoutGridLandscape,
        PreferKey.bookshelfLayoutGridPortrait,
        PreferKey.bookshelfLayoutListLandscape,
        PreferKey.bookshelfLayoutListPortrait,
        PreferKey.bookshelfFolderLayoutModePortrait,
        PreferKey.bookshelfFolderLayoutModeLandscape,
        PreferKey.bookshelfFolderLayoutGridPortrait,
        PreferKey.bookshelfFolderLayoutGridLandscape,
        PreferKey.bookshelfFolderLayoutListPortrait,
        PreferKey.bookshelfFolderLayoutListLandscape,
        PreferKey.bookshelfTitleSmallFont,
        PreferKey.bookshelfTitleCenter,
        PreferKey.bookshelfTitleMaxLines,
        PreferKey.bookshelfCoverShadow,
        PreferKey.bookshelfSearchActionDirectToSearch,
        PreferKey.bookshelfCardColor,
        PreferKey.bookshelfCardColorDark,
        PreferKey.bookshelfGroupListStyle,
        PreferKey.bookshelfGroupCoverCount,
        PreferKey.bookshelfListCoverWidth,
        PreferKey.bookshelfGridCoverWidth,
        PreferKey.bookshelfRefreshingLimit,
        PreferKey.bookshelfShowIntro,
        PreferKey.bookshelfShowTag,
        PreferKey.bookshelfShowLatestChapter,
        PreferKey.bookshelfIntroMaxLines
    )

    private val mangaPrefKeys = arrayOf(
        PreferKey.showMangaUi,
        PreferKey.mangaScrollMode,
        PreferKey.webtoonSidePaddingDp,
        PreferKey.mangaPreDownloadNum,
        PreferKey.mangaAutoPageSpeed,
        PreferKey.mangaFooterConfig,
        PreferKey.disableClickScroll,
        PreferKey.hideMangaTitle,
        PreferKey.mangaColorFilter,
        PreferKey.enableMangaEInk,
        PreferKey.mangaEInkThreshold,
        PreferKey.enableMangaGray,
        PreferKey.doublePageHorizontal,
        PreferKey.mouseWheelPage,
        PreferKey.disableMangaScale,
        PreferKey.disableMangaScrollAnimation,
        PreferKey.disableMangaCrossFade,
        PreferKey.mangaVolumeKeyPage,
        PreferKey.reverseVolumeKeyPage,
        PreferKey.mangaLongClick,
        PreferKey.mangaBackground,
        PreferKey.mangaClickActionTL,
        PreferKey.mangaClickActionTC,
        PreferKey.mangaClickActionTR,
        PreferKey.mangaClickActionML,
        PreferKey.mangaClickActionMC,
        PreferKey.mangaClickActionMR,
        PreferKey.mangaClickActionBL,
        PreferKey.mangaClickActionBC,
        PreferKey.mangaClickActionBR
    )

    private val coverPrefKeys = arrayOf(
        PreferKey.useDefaultCover,
        PreferKey.loadCoverOnlyWifi,
        PreferKey.coverShowName,
        PreferKey.coverShowAuthor,
        PreferKey.coverShowNameN,
        PreferKey.coverShowAuthorN,
        PreferKey.coverShowShadow,
        PreferKey.coverShowStroke,
        PreferKey.coverTextColor,
        PreferKey.coverTextColorN,
        PreferKey.coverShadowColor,
        PreferKey.coverShadowColorN,
        PreferKey.coverDefaultColor,
        PreferKey.coverInfoOrientation
    )

    fun keyIsNotIgnore(key: String, isBackup: Boolean = false): Boolean {
        if (key in alwaysIgnoredPreferenceKeys) return false
        if (isBackup) {
            return when {
                backupIgnoreReadConfig && readPrefKeys.contains(key) -> false
                backupIgnoreThemeConfig && themePrefKeys.contains(key) -> false
                backupIgnoreCoverConfig && coverPrefKeys.contains(key) -> false
                backupIgnoreBookshelfLayout && bookshelfPrefKeys.contains(key) -> false
                backupIgnoreManga && mangaPrefKeys.contains(key) -> false
                PreferKey.themeMode == key && backupIgnoreThemeMode -> false
                PreferKey.showRss == key && backupIgnoreShowRss -> false
                PreferKey.threadCount == key && backupIgnoreThreadCount -> false
                else -> true
            }
        }
        return when {
            ignoreReadConfig && readPrefKeys.contains(key) -> false
            ignoreThemeConfig && themePrefKeys.contains(key) -> false
            ignoreCoverConfig && coverPrefKeys.contains(key) -> false
            ignoreBookshelfLayout && bookshelfPrefKeys.contains(key) -> false
            ignoreManga && mangaPrefKeys.contains(key) -> false
            PreferKey.themeMode == key && ignoreThemeMode -> false
            PreferKey.showRss == key && ignoreShowRss -> false
            PreferKey.threadCount == key && ignoreThreadCount -> false
            else -> true
        }
    }

    val ignoreReadConfig: Boolean
        get() = ignoreConfig[readConfigKey] == true
    val ignoreThemeMode: Boolean
        get() = ignoreConfig[PreferKey.themeMode] == true
    val ignoreThemeConfig: Boolean
        get() = ignoreConfig[themeConfigKey] == true
    val ignoreCoverConfig: Boolean
        get() = ignoreConfig[coverConfigKey] == true
    val ignoreBookshelfLayout: Boolean
        get() = ignoreConfig[PreferKey.bookshelfLayout] == true
    val ignoreShowRss: Boolean
        get() = ignoreConfig[PreferKey.showRss] == true
    val ignoreThreadCount: Boolean
        get() = ignoreConfig[PreferKey.threadCount] == true
    val ignoreLocalBook: Boolean
        get() = ignoreConfig[localBookKey] == true
    val ignoreManga: Boolean
        get() = ignoreConfig[mangaKey] == true

    val backupIgnoreReadConfig: Boolean
        get() = backupIgnoreConfig[readConfigKey] == true
    val backupIgnoreThemeMode: Boolean
        get() = backupIgnoreConfig[PreferKey.themeMode] == true
    val backupIgnoreThemeConfig: Boolean
        get() = backupIgnoreConfig[themeConfigKey] == true
    val backupIgnoreCoverConfig: Boolean
        get() = backupIgnoreConfig[coverConfigKey] == true
    val backupIgnoreBookshelfLayout: Boolean
        get() = backupIgnoreConfig[PreferKey.bookshelfLayout] == true
    val backupIgnoreShowRss: Boolean
        get() = backupIgnoreConfig[PreferKey.showRss] == true
    val backupIgnoreThreadCount: Boolean
        get() = backupIgnoreConfig[PreferKey.threadCount] == true
    val backupIgnoreLocalBook: Boolean
        get() = backupIgnoreConfig[localBookKey] == true
    val backupIgnoreManga: Boolean
        get() = backupIgnoreConfig[mangaKey] == true

    fun saveIgnoreConfig() {
        val json = GSON.toJson(ignoreConfig)
        FileUtils.createFileIfNotExist(ignoreConfigPath).writeText(json)
    }

    fun saveBackupIgnoreConfig() {
        val json = GSON.toJson(backupIgnoreConfig)
        FileUtils.createFileIfNotExist(backupIgnoreConfigPath).writeText(json)
    }

}
