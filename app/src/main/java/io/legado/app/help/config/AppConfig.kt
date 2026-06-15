package io.legado.app.help.config

import android.content.SharedPreferences
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.ui.config.backupConfig.BackupConfig
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfig
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.config.readMangaConfig.ReadMangaConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    val isCronet get() = DownloadCacheConfig.cronetEnable
    val useAntiAlias get() = OtherConfig.antiAlias
    val userAgent: String get() = DownloadCacheConfig.userAgent

    var isEInkMode = appCtx.getPrefString("app_theme", "0") == "4"
    var isTransparent = appCtx.getPrefString("app_theme", "0") == "13"
    val customMode get() = ThemeConfig.customMode
    var clickActionTL = appCtx.getPrefInt(PreferKey.clickActionTL, 2)
    var clickActionTC = appCtx.getPrefInt(PreferKey.clickActionTC, 2)
    var clickActionTR = appCtx.getPrefInt(PreferKey.clickActionTR, 1)
    var clickActionML = appCtx.getPrefInt(PreferKey.clickActionML, 2)
    var clickActionMC = appCtx.getPrefInt(PreferKey.clickActionMC, 0)
    var clickActionMR = appCtx.getPrefInt(PreferKey.clickActionMR, 1)
    var clickActionBL = appCtx.getPrefInt(PreferKey.clickActionBL, 2)
    var clickActionBC = appCtx.getPrefInt(PreferKey.clickActionBC, 1)
    var clickActionBR = appCtx.getPrefInt(PreferKey.clickActionBR, 1)

    //    -1无操作 1下一页 2上一页 0显示菜单
    var mangaClickActionTL
        get() = ReadMangaConfig.mangaClickActionTL
        set(value) { ReadMangaConfig.mangaClickActionTL = value }
    var mangaClickActionTC
        get() = ReadMangaConfig.mangaClickActionTC
        set(value) { ReadMangaConfig.mangaClickActionTC = value }
    var mangaClickActionTR
        get() = ReadMangaConfig.mangaClickActionTR
        set(value) { ReadMangaConfig.mangaClickActionTR = value }
    var mangaClickActionML
        get() = ReadMangaConfig.mangaClickActionML
        set(value) { ReadMangaConfig.mangaClickActionML = value }
    var mangaClickActionMC
        get() = ReadMangaConfig.mangaClickActionMC
        set(value) { ReadMangaConfig.mangaClickActionMC = value }
    var mangaClickActionMR
        get() = ReadMangaConfig.mangaClickActionMR
        set(value) { ReadMangaConfig.mangaClickActionMR = value }
    var mangaClickActionBL
        get() = ReadMangaConfig.mangaClickActionBL
        set(value) { ReadMangaConfig.mangaClickActionBL = value }
    var mangaClickActionBC
        get() = ReadMangaConfig.mangaClickActionBC
        set(value) { ReadMangaConfig.mangaClickActionBC = value }
    var mangaClickActionBR
        get() = ReadMangaConfig.mangaClickActionBR
        set(value) { ReadMangaConfig.mangaClickActionBR = value }

    val AppTheme get() = ThemeConfig.appTheme

    val swipeAnimation get() = ThemeConfig.swipeAnimation

    val useDefaultCover get() = CoverConfig.useDefaultCover
    var optimizeRender
        get() = CanvasRecorderFactory.isSupport && ReadConfig.optimizeRender
        set(value) {
            ReadConfig.optimizeRender = value
        }
    val recordLog get() = OtherConfig.recordLog
    val webServiceAutoStart get() = OtherConfig.webServiceAutoStart

    // -- lyc 版本特性 --
    val adaptSpecialStyle get() = ReadConfig.adaptSpecialStyle
    val useUnderline get() = ReadConfig.useUnderline

    private var readBarStyleFollowPageValue =
        appCtx.getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
    private var readBarStyleValue = appCtx.getPrefInt(PreferKey.readBarStyle, 0)

    fun syncReadPreferences(preferences: ReadPreferences) {
        ReadConfig.optimizeRender = preferences.optimizeRender
        ReadConfig.adaptSpecialStyle = preferences.adaptSpecialStyle
        ReadConfig.useUnderline = preferences.useUnderline
        ReadConfig.chineseConverterType = preferences.chineseConverterType
        clickActionTL = preferences.clickActionTL
        clickActionTC = preferences.clickActionTC
        clickActionTR = preferences.clickActionTR
        clickActionML = preferences.clickActionML
        clickActionMC = preferences.clickActionMC
        clickActionMR = preferences.clickActionMR
        clickActionBL = preferences.clickActionBL
        clickActionBC = preferences.clickActionBC
        clickActionBR = preferences.clickActionBR
        ReadConfig.screenOrientation = preferences.screenOrientation
        ReadConfig.noAnimScrollPage = preferences.noAnimScrollPage
        ReadConfig.tocUiUseReplace = preferences.tocUiUseReplace
        ReadConfig.tocCountWords = preferences.tocCountWords
        ReadConfig.autoChangeSource = preferences.autoChangeSource
        ReadConfig.clickImgWay = preferences.clickImgWay
        ReadConfig.doubleHorizontalPage = preferences.doubleHorizontalPage
        ReadConfig.progressBarBehavior = preferences.progressBarBehavior
        ReadConfig.keyPageOnLongPress = preferences.keyPageOnLongPress
        ReadConfig.volumeKeyPage = preferences.volumeKeyPage
        ReadConfig.volumeKeyPageOnPlay = preferences.volumeKeyPageOnPlay
        ReadConfig.mouseWheelPage = preferences.mouseWheelPage
        ReadConfig.paddingDisplayCutouts = preferences.paddingDisplayCutouts
        ReadConfig.pageTouchSlop = preferences.pageTouchSlop
        ReadConfig.showReadTitleAddition = preferences.showReadTitleAddition
        ReadConfig.titleBarMode = preferences.titleBarMode
        ReadBookConfig.readMenuBlurAlpha = preferences.readMenuBlurAlpha
        ReadBookConfig.readSliderMode = preferences.readSliderMode
        readBarStyleFollowPageValue = preferences.readBarStyleFollowPage
        readBarStyleValue = preferences.readBarStyle
        ReadConfig.defaultSourceChangeAll = preferences.defaultSourceChangeAll
        ReadConfig.sliderVibrator = preferences.sliderVibrator
        ReadConfig.selectVibrator = preferences.selectVibrator
        ReadBookConfig.brightnessVwPos = preferences.brightnessVwPos
        ReadBookConfig.readBrightness = preferences.readBrightness
    }

    fun updateReadBarStyleCache(value: Int) {
        readBarStyleValue = value.coerceIn(0, 2)
    }

    private fun syncReadPreferenceFromSharedPreferences(key: String?) {
        when (key) {
            PreferKey.readBarStyleFollowPage -> readBarStyleFollowPageValue =
                appCtx.getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
            PreferKey.readBarStyle -> readBarStyleValue =
                appCtx.getPrefInt(PreferKey.readBarStyle, 0)
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        syncReadPreferenceFromSharedPreferences(key)
        when (key) {

            PreferKey.clickActionTL -> clickActionTL =
                appCtx.getPrefInt(PreferKey.clickActionTL, 2)

            PreferKey.clickActionTC -> clickActionTC =
                appCtx.getPrefInt(PreferKey.clickActionTC, 2)

            PreferKey.clickActionTR -> clickActionTR =
                appCtx.getPrefInt(PreferKey.clickActionTR, 1)

            PreferKey.clickActionML -> clickActionML =
                appCtx.getPrefInt(PreferKey.clickActionML, 2)

            PreferKey.clickActionMC -> clickActionMC =
                appCtx.getPrefInt(PreferKey.clickActionMC, 0)

            PreferKey.clickActionMR -> clickActionMR =
                appCtx.getPrefInt(PreferKey.clickActionMR, 1)

            PreferKey.clickActionBL -> clickActionBL =
                appCtx.getPrefInt(PreferKey.clickActionBL, 2)

            PreferKey.clickActionBC -> clickActionBC =
                appCtx.getPrefInt(PreferKey.clickActionBC, 1)

            PreferKey.clickActionBR -> clickActionBR =
                appCtx.getPrefInt(PreferKey.clickActionBR, 1)

            PreferKey.readBodyToLh -> ReadBookConfig.readBodyToLh =
                appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)

            PreferKey.useZhLayout -> ReadBookConfig.useZhLayout =
                appCtx.getPrefBoolean(PreferKey.useZhLayout)

        }
    }

    var isNightTheme: Boolean
        get() = when (ThemeConfig.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            val newMode = if (value) "2" else "1"
            if (ThemeConfig.themeMode != newMode) {
                ThemeConfig.themeMode = newMode
            }
        }

    var showUnread: Boolean
        get() = BookshelfConfig.showUnread
        set(value) {
            BookshelfConfig.showUnread = value
        }

    var showTip: Boolean
        get() = BookshelfConfig.showTip
        set(value) {
            BookshelfConfig.showTip = value
        }

    var showUnreadNew: Boolean
        get() = BookshelfConfig.showUnreadNew
        set(value) {
            BookshelfConfig.showUnreadNew = value
        }

    var showLastUpdateTime: Boolean
        get() = BookshelfConfig.showLastUpdateTime
        set(value) {
            BookshelfConfig.showLastUpdateTime = value
        }

    var showWaitUpCount: Boolean
        get() = BookshelfConfig.showWaitUpCount
        set(value) {
            BookshelfConfig.showWaitUpCount = value
        }

    var readBrightness: Int
        get() = if (isNightTheme) {
            appCtx.getPrefInt(PreferKey.nightBrightness, 100)
        } else {
            appCtx.getPrefInt(PreferKey.brightness, 100)
        }
        set(value) {
            if (isNightTheme) {
                appCtx.putPrefInt(PreferKey.nightBrightness, value)
            } else {
                appCtx.putPrefInt(PreferKey.brightness, value)
            }
        }

    val textSelectAble: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.textSelectAble, true)

//    val isTransparentStatusBar: Boolean
//        get() = appCtx.getPrefBoolean(PreferKey.transparentStatusBar, true)
//
//    val immNavigationBar: Boolean
//        get() = appCtx.getPrefBoolean(PreferKey.immNavigationBar, true)

    val screenOrientation: String?
        get() = ReadConfig.screenOrientation

    var bookGroupStyle: Int
        get() = BookshelfConfig.bookGroupStyle
        set(value) {
            BookshelfConfig.bookGroupStyle = value
        }

    var bookshelfLayoutModePortrait: Int
        get() = BookshelfConfig.bookshelfLayoutModePortrait
        set(value) {
            BookshelfConfig.bookshelfLayoutModePortrait = value
        }

    var bookshelfLayoutModeLandscape: Int
        get() = BookshelfConfig.bookshelfLayoutModeLandscape
        set(value) {
            BookshelfConfig.bookshelfLayoutModeLandscape = value
        }

    var bookshelfLayoutGridPortrait: Int
        get() = BookshelfConfig.bookshelfLayoutGridPortrait
        set(value) {
            BookshelfConfig.bookshelfLayoutGridPortrait = value
        }

    var exploreLayoutGridLandscape: Int
        get() = appCtx.getPrefInt(PreferKey.exploreLayoutGridLandscape, 7)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreLayoutGridLandscape, value)
        }

    var exploreLayoutGridPortrait: Int
        get() = appCtx.getPrefInt(PreferKey.exploreLayoutGridPortrait, 3)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreLayoutGridPortrait, value)
        }

    var bookshelfLayoutGridLandscape: Int
        get() = BookshelfConfig.bookshelfLayoutGridLandscape
        set(value) {
            BookshelfConfig.bookshelfLayoutGridLandscape = value
        }

    var bookExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.bookExportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookExportFileName, value)
        }

    // 保存 自定义导出章节模式 文件名js表达式
    var episodeExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.episodeExportFileName, "")
        set(value) {
            appCtx.putPrefString(PreferKey.episodeExportFileName, value)
        }

    var bookImportFileName: String?
        get() = ImportBookConfig.bookImportFileName
        set(value) {
            ImportBookConfig.bookImportFileName = value
        }

    var backupPath: String?
        get() = BackupConfig.backupPath
        set(value) {
            BackupConfig.backupPath = value
        }

    val showDiscovery: Boolean
        get() = ThemeConfig.showDiscovery

    val showHome: Boolean
        get() = ThemeConfig.showHome

    val showRSS: Boolean
        get() = ThemeConfig.showRss

    val showStatusBar: Boolean
        get() = ThemeConfig.showStatusBar

    val showBottomView: Boolean
        get() = ThemeConfig.showBottomView

    val autoRefreshBook: Boolean
        get() = ThemeConfig.autoRefreshBook

    var enableReview: Boolean
        get() = BuildConfig.DEBUG && appCtx.getPrefBoolean(PreferKey.enableReview, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = DownloadCacheConfig.threadCount
        set(value) {
            DownloadCacheConfig.threadCount = value
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = ImportBookConfig.importBookPath
        set(value) {
            ImportBookConfig.importBookPath = value
        }

    var ttsFlowSys: Boolean
        get() = ReadConfig.ttsFollowSys
        set(value) {
            ReadConfig.ttsFollowSys = value
        }

    val noAnimScrollPage: Boolean
        get() = ReadConfig.noAnimScrollPage

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = ReadConfig.ttsSpeechRate
        set(value) {
            ReadConfig.ttsSpeechRate = value
        }

    var ttsTimer: Int
        get() = ReadConfig.ttsTimer
        set(value) {
            ReadConfig.ttsTimer = value
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    val chineseConverterType: Int
        get() = ReadConfig.chineseConverterType

    var systemTypefaces: Int
        get() = appCtx.getPrefInt(PreferKey.systemTypefaces)
        set(value) {
            appCtx.putPrefInt(PreferKey.systemTypefaces, value)
        }

//    var elevation: Int
//        get() = if (isEInkMode) 0 else appCtx.getPrefInt(
//            PreferKey.barElevation,
//            AppConst.sysElevation
//        )
//        set(value) {
//            appCtx.putPrefInt(PreferKey.barElevation, value)
//        }

    var readUrlInBrowser: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readUrlOpenInBrowser)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readUrlOpenInBrowser, value)
        }

    var exportCharset: String
        get() {
            val c = appCtx.getPrefString(PreferKey.exportCharset)
            if (c.isNullOrBlank()) {
                return "UTF-8"
            }
            return c
        }
        set(value) {
            appCtx.putPrefString(PreferKey.exportCharset, value)
        }

    var exportUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportUseReplace, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportUseReplace, value)
        }

    var exportToWebDav: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportToWebDav)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportToWebDav, value)
        }
    var exportNoChapterName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportNoChapterName)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportNoChapterName, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableCustomExport, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = appCtx.getPrefInt(PreferKey.exportType)
        set(value) {
            appCtx.putPrefInt(PreferKey.exportType, value)
        }
    var exportPictureFile: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportPictureFile, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportPictureFile, value)
        }

    var parallelExportBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.parallelExportBook, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.parallelExportBook, value)
        }

    var ttsEngine: String?
        get() = io.legado.app.ui.config.readConfig.ReadConfig.ttsEngine
        set(value) {
            io.legado.app.ui.config.readConfig.ReadConfig.ttsEngine = value
        }

    var webPort: Int
        get() = OtherConfig.webPort
        set(value) {
            OtherConfig.webPort = value
        }

    var tocUiUseReplace: Boolean
        get() = ReadConfig.tocUiUseReplace
        set(value) {
            ReadConfig.tocUiUseReplace = value
        }

    var tocCountWords: Boolean
        get() = ReadConfig.tocCountWords
        set(value) {
            ReadConfig.tocCountWords = value
        }

    var enableReadRecord: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableReadRecord, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    val autoChangeSource: Boolean
        get() = ReadConfig.autoChangeSource

    var openBookInfoByClickTitle: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.openBookInfoByClickTitle, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.openBookInfoByClickTitle, value)
        }

    var showBookshelfFastScroller: Boolean
        get() = BookshelfConfig.showBookshelfFastScroller
        set(value) {
            BookshelfConfig.showBookshelfFastScroller = value
        }

    var contentSelectSpeakMod: Int
        get() = appCtx.getPrefInt(PreferKey.contentSelectSpeakMod)
        set(value) {
            appCtx.putPrefInt(PreferKey.contentSelectSpeakMod, value)
        }

    var batchChangeSourceDelay: Int
        get() = appCtx.getPrefInt(PreferKey.batchChangeSourceDelay)
        set(value) {
            appCtx.putPrefInt(PreferKey.batchChangeSourceDelay, value)
        }

    val importKeepName get() = appCtx.getPrefBoolean(PreferKey.importKeepName)
    val importKeepGroup get() = appCtx.getPrefBoolean(PreferKey.importKeepGroup)
    var importKeepEnable: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.importKeepEnable, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.importKeepEnable, value)
        }

    var previewImageByClick: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.previewImageByClick, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.previewImageByClick, value)
        }

    val clickImgWay: String?
        get() = ReadConfig.clickImgWay
    var preDownloadNum
        get() = DownloadCacheConfig.preDownloadNum
        set(value) {
            DownloadCacheConfig.preDownloadNum = value
        }

    val syncBookProgress get() = BackupConfig.syncBookProgress

    val syncBookProgressPlus get() = BackupConfig.syncBookProgressPlus

    val mediaButtonOnExit get() = appCtx.getPrefBoolean(PreferKey.mediaButtonOnExit, true)

    val readAloudByMediaButton
        get() = appCtx.getPrefBoolean(PreferKey.readAloudByMediaButton, false)

    val replaceEnableDefault get() = OtherConfig.replaceEnableDefault

    val webDavDir get() = BackupConfig.webDavDir

    val webDavDeviceName get() = BackupConfig.webDavDeviceName

    val recordHeapDump get() = OtherConfig.recordHeapDump

    val showAddToShelfAlert get() = OtherConfig.showAddToShelfAlert

    var ignoreAudioFocus
        get() = ReadConfig.ignoreAudioFocus
        set(value) {
            ReadConfig.ignoreAudioFocus = value
        }

    var pauseReadAloudWhilePhoneCalls
        get() = ReadConfig.pauseReadAloudWhilePhoneCalls
        set(value) {
            ReadConfig.pauseReadAloudWhilePhoneCalls = value
        }

    val onlyLatestBackup get() = BackupConfig.onlyLatestBackup

    val autoCheckNewBackup get() = ThemeConfig.autoCheckNewBackup

    val defaultHomePage get() = ThemeConfig.defaultHomePage

    val updateToVariant get() = OtherConfig.updateToVariant

    var streamReadAloudAudio
        get() = ReadConfig.streamReadAloudAudio
        set(value) {
            ReadConfig.streamReadAloudAudio = value
        }

    val doublePageHorizontal: String?
        get() = ReadConfig.doubleHorizontalPage

    val progressBarBehavior: String?
        get() = ReadConfig.progressBarBehavior

    val keyPageOnLongPress
        get() = ReadConfig.keyPageOnLongPress

    val volumeKeyPage
        get() = ReadConfig.volumeKeyPage

    val volumeKeyPageOnPlay
        get() = ReadConfig.volumeKeyPageOnPlay

    val mouseWheelPage
        get() = ReadConfig.mouseWheelPage

    val paddingDisplayCutouts
        get() = ReadConfig.paddingDisplayCutouts

    var pageTouchSlop: Int
        get() = ReadConfig.pageTouchSlop
        set(value) {
            ReadConfig.pageTouchSlop = value
        }

    var bookshelfSort: Int
        get() = BookshelfConfig.bookshelfSort
        set(value) {
            BookshelfConfig.bookshelfSort = value
        }

    var bookshelfSortOrder: Int
        get() = BookshelfConfig.bookshelfSortOrder
        set(value) {
            BookshelfConfig.bookshelfSortOrder = value
        }

    fun getBookSortByGroupId(groupId: Long): Int {
        return appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
            ?: bookshelfSort
    }

    var bitmapCacheSize: Int
        get() = DownloadCacheConfig.bitmapCacheSize
        set(value) {
            DownloadCacheConfig.bitmapCacheSize = value
        }

    var imageRetainNum: Int
        get() = DownloadCacheConfig.imageRetainNum
        set(value) {
            DownloadCacheConfig.imageRetainNum = value
        }

    var showReadTitleBarAddition: Boolean
        get() = ReadConfig.showReadTitleAddition
        set(value) {
            ReadConfig.showReadTitleAddition = value
        }

    var readBarStyleFollowPage: Boolean
        get() = readBarStyleFollowPageValue
        set(value) {
            readBarStyleFollowPageValue = value
            appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var readBarStyle: Int
        get() = readBarStyleValue
        set(value) {
            readBarStyleValue = value.coerceIn(0, 2)
            appCtx.putPrefInt(PreferKey.readBarStyle, readBarStyleValue)
        }

    var sourceEditMaxLine: Int
        get() = OtherConfig.sourceEditMaxLine
        set(value) {
            OtherConfig.sourceEditMaxLine = value
        }

    var audioPlayUseWakeLock: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.audioPlayWakeLock)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.audioPlayWakeLock, value)
        }

    var brightnessVwPos: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.brightnessVwPos)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.brightnessVwPos, value)
        }

    fun detectClickArea() {
        if (clickActionTL * clickActionTC * clickActionTR
            * clickActionML * clickActionMC * clickActionMR
            * clickActionBL * clickActionBC * clickActionBR != 0
        ) {
            appCtx.putPrefInt(PreferKey.clickActionMC, 0)
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    fun detectMangaClickArea() {
        ReadMangaConfig.detectMangaClickArea()
    }

    var firebaseEnable: Boolean
        get() = OtherConfig.firebaseEnable
        set(value) {
            OtherConfig.firebaseEnable = value
        }

    //跳转到漫画界面不使用富文本模式
    val showMangaUi: Boolean
        get() = ReadMangaConfig.showMangaUi

    //禁用漫画缩放
    var disableMangaScale: Boolean
        get() = ReadMangaConfig.disableMangaScale
        set(value) { ReadMangaConfig.disableMangaScale = value }

    var disableMangaScrollAnimation: Boolean
        get() = ReadMangaConfig.disableMangaScrollAnimation
        set(value) { ReadMangaConfig.disableMangaScrollAnimation = value }

    var disableMangaCrossFade: Boolean
        get() = ReadMangaConfig.disableMangaCrossFade
        set(value) { ReadMangaConfig.disableMangaCrossFade = value }

    var titleBarMode
        get() = ReadConfig.titleBarMode
        set(value) {
            ReadConfig.titleBarMode = value
        }

    //漫画预加载数量
    var mangaPreDownloadNum
        get() = ReadMangaConfig.mangaPreDownloadNum
        set(value) { ReadMangaConfig.mangaPreDownloadNum = value }

    //点击翻页
    var disableClickScroll
        get() = ReadMangaConfig.disableClickScroll
        set(value) { ReadMangaConfig.disableClickScroll = value }

    //漫画滚动速度
    var mangaAutoPageSpeed
        get() = ReadMangaConfig.mangaAutoPageSpeed
        set(value) { ReadMangaConfig.mangaAutoPageSpeed = value }

    //漫画页脚配置
    var mangaFooterConfig
        get() = ReadMangaConfig.mangaFooterConfig
        set(value) { ReadMangaConfig.mangaFooterConfig = value }

    //漫画滚动方式
    var mangaScrollMode: Int
        get() = ReadMangaConfig.mangaScrollMode
        set(value) { ReadMangaConfig.mangaScrollMode = value }

    var mangaLongClick
        get() = ReadMangaConfig.mangaLongClick
        set(value) { ReadMangaConfig.mangaLongClick = value }

    var mangaBackground: Int
        get() = ReadMangaConfig.mangaBackground
        set(value) { ReadMangaConfig.mangaBackground = value }

    //漫画滤镜
    var mangaColorFilter
        get() = ReadMangaConfig.mangaColorFilter
        set(value) { ReadMangaConfig.mangaColorFilter = value }

    //禁用漫画内标题
    var hideMangaTitle
        get() = ReadMangaConfig.hideMangaTitle
        set(value) { ReadMangaConfig.hideMangaTitle = value }

    //开启墨水屏模式
    var enableMangaEInk
        get() = ReadMangaConfig.enableMangaEInk
        set(value) { ReadMangaConfig.enableMangaEInk = value }

    //墨水屏阈值
    var mangaEInkThreshold
        get() = ReadMangaConfig.mangaEInkThreshold
        set(value) { ReadMangaConfig.mangaEInkThreshold = value }

    //漫画灰度
    var enableMangaGray
        get() = ReadMangaConfig.enableMangaGray
        set(value) { ReadMangaConfig.enableMangaGray = value }

    //条漫侧边距
    var webtoonSidePaddingDp: Int
        get() = ReadMangaConfig.webtoonSidePaddingDp
        set(value) { ReadMangaConfig.webtoonSidePaddingDp = value }

    //漫画音量键翻页
    var MangaVolumeKeyPage: Boolean
        get() = ReadMangaConfig.mangaVolumeKeyPage
        set(value) { ReadMangaConfig.mangaVolumeKeyPage = value }

    var reverseVolumeKeyPage: Boolean
        get() = ReadMangaConfig.reverseVolumeKeyPage
        set(value) { ReadMangaConfig.reverseVolumeKeyPage = value }

    var tabletInterface
        get() = ThemeConfig.tabletInterface
        set(value) {
            ThemeConfig.tabletInterface = value
        }

    var pureBlack
        get() = ThemeConfig.isPureBlack
        set(value) {
            ThemeConfig.isPureBlack = value
        }

    val hasLightBg: Boolean
        get() = !ThemeConfig.bgImageLight.isNullOrEmpty()

    val hasDarkBg: Boolean
        get() = !ThemeConfig.bgImageDark.isNullOrEmpty()

    val hasImageBg: Boolean
        get() = hasLightBg && hasDarkBg

    var labelVisibilityMode
        get() = ThemeConfig.labelVisibilityMode
        set(value) {
            ThemeConfig.labelVisibilityMode = value
        }

    var paletteStyle
        get() = ThemeConfig.paletteStyle
        set(value) {
            ThemeConfig.paletteStyle = value
        }

    var enableBlur
        get() = ThemeConfig.enableBlur
        set(value) {
            ThemeConfig.enableBlur = value
        }

    @Deprecated("Use ReadBookConfig.readMenuBlurAlpha instead", ReplaceWith("ReadBookConfig.readMenuBlurAlpha"))
    var menuAlpha: Int
        get() = ReadBookConfig.readMenuBlurAlpha
        set(value) {
            ReadBookConfig.readMenuBlurAlpha = value
        }

    var readSliderMode
        get() = ReadBookConfig.readSliderMode
        set(value) {
            ReadBookConfig.readSliderMode = value
        }

    var bookshelfRefreshingLimit: Int
        get() = BookshelfConfig.bookshelfRefreshingLimit
        set(value) {
            BookshelfConfig.bookshelfRefreshingLimit = value
        }

    var systemMediaControlCompatibilityChange: Boolean
        get() = ReadConfig.systemMediaControlCompatibilityChange
        set(value) {
            ReadConfig.systemMediaControlCompatibilityChange = value
        }

    var isPredictiveBackEnabled: Boolean
        get() = ThemeConfig.isPredictiveBackEnabled
        set(value) {
            ThemeConfig.isPredictiveBackEnabled = value
        }

    var shouldShowExpandButton: Boolean
        get() = BookshelfConfig.shouldShowExpandButton
        set(value) {
            BookshelfConfig.shouldShowExpandButton = value
        }

    var exploreLayoutState: Int
        get() = appCtx.getPrefInt(PreferKey.exploreLayoutState, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreLayoutState, value)
        }

    var defaultSourceChangeAll: Boolean
        get() = ReadConfig.defaultSourceChangeAll
        set(value) {
            ReadConfig.defaultSourceChangeAll = value
        }

    var sliderVibrator: Boolean
        get() = ReadConfig.sliderVibrator
        set(value) {
            ReadConfig.sliderVibrator = value
        }

    var selectVibrator: Boolean
        get() = ReadConfig.selectVibrator
        set(value) {
            ReadConfig.selectVibrator = value
        }

    val audioPreDownloadNum: Int
        get() = appCtx.getPrefInt(PreferKey.audioPreDownloadNum, 10)

    val audioCacheCleanTimeOrgin: Int
        get() = appCtx.getPrefInt(PreferKey.audioCacheCleanTime, 10)

    val audioCacheCleanTime: Long
        get() {
            val str = appCtx.getPrefInt(PreferKey.audioCacheCleanTime, 10)
            return str * 60 * 1000L
        }

    var containerOpacity: Int
        get() = ThemeConfig.containerOpacity
        set(value) {
            ThemeConfig.containerOpacity = value
        }
}
