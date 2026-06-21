package io.legado.app.help.config

import android.content.SharedPreferences
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.ui.book.read.ReadAiBookHistory
import io.legado.app.ui.config.backupConfig.BackupConfig
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.config.coverConfig.CoverConfig
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfig
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.config.readMangaConfig.ReadMangaConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.main.ai.AI_API_MODE_CHAT_COMPLETIONS
import io.legado.app.ui.main.ai.AI_API_MODE_RESPONSES
import io.legado.app.ui.main.ai.AiChatSession
import io.legado.app.ui.main.ai.AiContextSummary
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.ui.main.ai.AiMcpServerConfig
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.main.ai.AiPersonaConfig
import io.legado.app.ui.main.ai.AiProviderConfig
import io.legado.app.ui.main.ai.AiSkillConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getPrefStringSet
import io.legado.app.utils.isNightMode
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.putPrefStringSet
import io.legado.app.utils.removePref
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import java.net.URI
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    const val DEFAULT_AI_SYSTEM_PROMPT =
        "你是阅读应用内的 AI 助手。回答直接、准确、简洁。需要真实应用数据时必须优先调用工具，工具返回的数据优先级高于你的记忆，不允许编造工具未返回的结果。用户询问书架、书籍、作者、阅读记录、书籍简介、书源、分组、标签、分类方案时，必须先调用 query_bookshelf、get_bookshelf_book_info、manage_bookshelf_group 或 manage_bookshelf_tag，不要只说\"我先看看\"却不调用工具。"

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

    var aiAssistantEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiAssistantEnabled, false) && aiCurrentModelConfig != null
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiAssistantEnabled, value && aiCurrentModelConfig != null)
        }

    var aiProviderList: List<AiProviderConfig>
        get() {
            val providers = readAiProviders()
            syncAiState(providers, readAiModels(providers.map { it.id }.toSet()))
            return providers
        }
        set(value) {
            val providers = normalizeAiProviders(value)
            persistAiProviders(providers)
            val models = normalizeAiModels(
                readAiModelsRaw(),
                providers.map { it.id }.toSet()
            )
            persistAiModels(models)
            syncAiState(providers, models)
        }

    var aiCurrentProviderId: String?
        get() {
            val providers = readAiProviders()
            val models = readAiModels(providers.map { it.id }.toSet())
            syncAiState(providers, models)
            return appCtx.getPrefString(PreferKey.aiCurrentProviderId)
        }
        set(value) {
            val providers = readAiProviders()
            val providerId = providers.firstOrNull { it.id == value }?.id
            if (providerId.isNullOrBlank()) {
                appCtx.removePref(PreferKey.aiCurrentProviderId)
                appCtx.removePref(PreferKey.aiCurrentModelId)
            } else {
                appCtx.putPrefString(PreferKey.aiCurrentProviderId, providerId)
            }
            syncAiState(providers, readAiModels(providers.map { it.id }.toSet()))
        }

    val aiCurrentProvider: AiProviderConfig?
        get() = aiProviderList.firstOrNull { it.id == aiCurrentProviderId }

    var aiModelConfigList: List<AiModelConfig>
        get() {
            val providers = readAiProviders()
            val models = readAiModels(providers.map { it.id }.toSet())
            syncAiState(providers, models)
            return models
        }
        set(value) {
            val providers = readAiProviders()
            val models = normalizeAiModels(value, providers.map { it.id }.toSet())
            persistAiModels(models)
            syncAiState(providers, models)
        }

    var aiCurrentModelId: String?
        get() {
            val providers = readAiProviders()
            val models = readAiModels(providers.map { it.id }.toSet())
            syncAiState(providers, models)
            return appCtx.getPrefString(PreferKey.aiCurrentModelId)
        }
        set(value) {
            val providers = readAiProviders()
            val models = readAiModels(providers.map { it.id }.toSet())
            val model = models.firstOrNull { it.id == value }
            if (model == null) {
                appCtx.removePref(PreferKey.aiCurrentModelId)
            } else {
                appCtx.putPrefString(PreferKey.aiCurrentModelId, model.id)
                appCtx.putPrefString(PreferKey.aiCurrentProviderId, model.providerId)
            }
            syncAiState(providers, models)
        }

    val aiCurrentModelConfig: AiModelConfig?
        get() = aiModelConfigList.firstOrNull { it.id == aiCurrentModelId }

    var aiMcpServerList: List<AiMcpServerConfig>
        get() = readAiMcpServers()
        set(value) {
            persistAiMcpServers(normalizeAiMcpServers(value))
        }

    val aiEnabledMcpServers: List<AiMcpServerConfig>
        get() = aiMcpServerList.filter { it.enabled }

    var aiChatSessionList: List<AiChatSession>
        get() = runCatching {
            GSON.fromJsonArray<AiChatSession>(appCtx.getPrefString(PreferKey.aiChatSessionList))
                .getOrDefault(emptyList())
                .filter { session ->
                    session.id.isNotBlank() &&
                            session.title.isNotBlank() &&
                            session.messages.all { it.content.isNotBlank() }
                }
                .map { session ->
                    session.copy(
                        messages = session.messages.map { message ->
                            message.copy(
                                kind = message.kind ?: io.legado.app.ui.main.ai.AiChatMessage.Kind.TEXT,
                                statusName = message.statusName,
                                statusStage = message.statusStage,
                                statusSuccess = message.statusSuccess
                            )
                        }
                    )
                }
                .sortedByDescending { it.updatedAt }
        }.getOrElse {
            AppLog.put("读取 AI 聊天历史失败, 已清理历史\n${it.localizedMessage}", it)
            appCtx.removePref(PreferKey.aiChatSessionList)
            appCtx.removePref(PreferKey.aiCurrentChatSessionId)
            emptyList()
        }
        set(value) {
            val sessions = value.distinctBy { it.id }
                .mapNotNull { session ->
                    val title = session.title.trim()
                    val normalizedMessages = session.messages
                        .filter { it.content.isNotBlank() }
                        .map { it.copy(pending = false) }
                    if (session.id.isBlank() || title.isBlank() || normalizedMessages.isEmpty()) {
                        null
                    } else {
                        session.copy(
                            id = session.id.trim(),
                            title = title,
                            messages = normalizedMessages
                        )
                    }
                }
                .sortedByDescending { it.updatedAt }
                .take(100)
            if (sessions.isEmpty()) {
                appCtx.removePref(PreferKey.aiChatSessionList)
            } else {
                appCtx.putPrefString(PreferKey.aiChatSessionList, GSON.toJson(sessions))
            }
            val currentId = appCtx.getPrefString(PreferKey.aiCurrentChatSessionId)
            if (currentId != null && sessions.none { it.id == currentId }) {
                appCtx.removePref(PreferKey.aiCurrentChatSessionId)
            }
        }

    var aiReadHistoryList: List<ReadAiBookHistory>
        get() = runCatching {
            readAiReadHistories()
                .filter { it.bookUrl.isNotBlank() && it.sessions.isNotEmpty() }
                .map { history ->
                    val sessions = history.sessions
                        .filter { it.id.isNotBlank() && it.messages.any { message -> message.content.isNotBlank() } }
                        .map { session ->
                            session.copy(
                                messages = session.messages.filter { it.content.isNotBlank() }
                            )
                        }
                        .sortedByDescending { it.updatedAt }
                        .take(20)
                    history.copy(
                        bookUrl = history.bookUrl.trim(),
                        bookName = history.bookName.trim(),
                        currentSessionId = history.currentSessionId.takeIf { id -> sessions.any { it.id == id } }
                            ?: sessions.firstOrNull()?.id.orEmpty(),
                        sessions = sessions
                    )
                }
                .filter { it.sessions.isNotEmpty() }
                .sortedByDescending { it.updatedAt }
                .take(200)
        }.getOrElse {
            AppLog.put("读取阅读问 AI 历史失败, 已清理历史\n${it.localizedMessage}", it)
            appCtx.removePref(PreferKey.aiReadHistoryList)
            emptyList()
        }
        set(value) {
            val histories = value.distinctBy { it.bookUrl }
                .mapNotNull { history ->
                    val bookUrl = history.bookUrl.trim()
                    val sessions = history.sessions
                        .filter { it.id.isNotBlank() && it.messages.any { message -> message.content.isNotBlank() } }
                        .map { session ->
                            session.copy(
                                messages = session.messages.filter { it.content.isNotBlank() }.takeLast(80)
                            )
                        }
                        .sortedByDescending { it.updatedAt }
                        .take(20)
                    if (bookUrl.isBlank() || sessions.isEmpty()) {
                        null
                    } else {
                        history.copy(
                            bookUrl = bookUrl,
                            bookName = history.bookName.trim(),
                            updatedAt = history.updatedAt,
                            currentSessionId = history.currentSessionId.takeIf { id -> sessions.any { it.id == id } }
                                ?: sessions.first().id,
                            sessions = sessions
                        )
                    }
                }
                .sortedByDescending { it.updatedAt }
                .take(200)
            if (histories.isEmpty()) {
                appCtx.removePref(PreferKey.aiReadHistoryList)
            } else {
                appCtx.putPrefString(PreferKey.aiReadHistoryList, GSON.toJson(histories))
            }
        }

    private fun readAiReadHistories(): List<ReadAiBookHistory> {
        val raw = appCtx.getPrefString(PreferKey.aiReadHistoryList).orEmpty()
        val histories = GSON.fromJsonArray<ReadAiBookHistory>(raw).getOrDefault(emptyList())
        if (histories.any { it.sessions.isNotEmpty() }) {
            return histories
        }
        return migrateLegacyReadAiHistories(raw)
    }

    private fun migrateLegacyReadAiHistories(raw: String): List<ReadAiBookHistory> {
        return runCatching {
            org.json.JSONArray(raw).let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val records = item.optJSONArray("records") ?: continue
                        val sessions = buildList {
                            for (recordIndex in 0 until records.length()) {
                                val record = records.optJSONObject(recordIndex) ?: continue
                                val question = record.optString("question")
                                val answer = record.optString("answer")
                                if (question.isBlank() || answer.isBlank()) continue
                                val createdAt = record.optLong("createdAt", System.currentTimeMillis())
                                add(
                                    io.legado.app.ui.book.read.ReadAiSession(
                                        id = record.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                                        title = question.lineSequence().firstOrNull().orEmpty().take(24),
                                        chapterTitle = record.optString("chapterTitle"),
                                        chapterIndex = record.optInt("chapterIndex", -1),
                                        createdAt = createdAt,
                                        updatedAt = createdAt,
                                        messages = listOf(
                                            io.legado.app.ui.book.read.ReadAiMessage(
                                                role = io.legado.app.ui.book.read.ReadAiMessage.Role.USER,
                                                content = question,
                                                createdAt = createdAt
                                            ),
                                            io.legado.app.ui.book.read.ReadAiMessage(
                                                role = io.legado.app.ui.book.read.ReadAiMessage.Role.ASSISTANT,
                                                content = answer,
                                                createdAt = createdAt
                                            )
                                        )
                                    )
                                )
                            }
                        }
                        if (sessions.isNotEmpty()) {
                            add(
                                ReadAiBookHistory(
                                    bookUrl = item.optString("bookUrl"),
                                    bookName = item.optString("bookName"),
                                    updatedAt = item.optLong("updatedAt", sessions.maxOf { it.updatedAt }),
                                    currentSessionId = sessions.first().id,
                                    sessions = sessions
                                )
                            )
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    var aiCurrentChatSessionId: String?
        get() = appCtx.getPrefString(PreferKey.aiCurrentChatSessionId)
        set(value) {
            if (value.isNullOrBlank()) {
                appCtx.removePref(PreferKey.aiCurrentChatSessionId)
            } else {
                appCtx.putPrefString(PreferKey.aiCurrentChatSessionId, value.trim())
            }
        }

    var aiSystemPrompt: String
        get() = appCtx.getPrefString(PreferKey.aiSystemPrompt, DEFAULT_AI_SYSTEM_PROMPT)
            ?: DEFAULT_AI_SYSTEM_PROMPT
        set(value) {
            val prompt = value.trim()
            if (prompt.isBlank() || prompt == DEFAULT_AI_SYSTEM_PROMPT) {
                appCtx.removePref(PreferKey.aiSystemPrompt)
            } else {
                appCtx.putPrefString(PreferKey.aiSystemPrompt, prompt)
            }
        }

    var aiSkillPrompt: String
        get() = appCtx.getPrefString(PreferKey.aiSkillPrompt).orEmpty()
        set(value) {
            val prompt = value.trim()
            if (prompt.isBlank()) {
                appCtx.removePref(PreferKey.aiSkillPrompt)
            } else {
                appCtx.putPrefString(PreferKey.aiSkillPrompt, prompt)
            }
        }

    var aiSkillList: List<AiSkillConfig>
        get() = readAiSkills()
        set(value) {
            persistAiSkills(normalizeAiSkills(value))
        }

    var aiPersonaList: List<AiPersonaConfig>
        get() = normalizeAiPersonas(
            GSON.fromJsonArray<AiPersonaConfig>(appCtx.getPrefString(PreferKey.aiPersonaList))
                .getOrDefault(emptyList())
        )
        set(value) {
            val personas = normalizeAiPersonas(value)
            if (personas.isEmpty()) appCtx.removePref(PreferKey.aiPersonaList)
            else appCtx.putPrefString(PreferKey.aiPersonaList, GSON.toJson(personas))
        }

    var aiCurrentPersonaId: String?
        get() = appCtx.getPrefString(PreferKey.aiCurrentPersonaId)
        set(value) {
            if (value.isNullOrBlank()) appCtx.removePref(PreferKey.aiCurrentPersonaId)
            else appCtx.putPrefString(PreferKey.aiCurrentPersonaId, value)
        }

    val aiCurrentPersona: AiPersonaConfig?
        get() = aiPersonaList.firstOrNull { it.id == aiCurrentPersonaId }

    var aiImageProviderList: List<AiImageProviderConfig>
        get() = normalizeAiImageProviders(
            GSON.fromJsonArray<AiImageProviderConfig>(appCtx.getPrefString(PreferKey.aiImageProviderList))
                .getOrDefault(emptyList())
        )
        set(value) {
            val providers = normalizeAiImageProviders(value)
            if (providers.isEmpty()) appCtx.removePref(PreferKey.aiImageProviderList)
            else appCtx.putPrefString(PreferKey.aiImageProviderList, GSON.toJson(providers))
        }

    val aiEnabledImageProviders: List<AiImageProviderConfig>
        get() = aiImageProviderList.filter { it.enabled }

    var aiCurrentImageProviderId: String?
        get() = appCtx.getPrefString(PreferKey.aiCurrentImageProviderId)
        set(value) {
            if (value.isNullOrBlank()) appCtx.removePref(PreferKey.aiCurrentImageProviderId)
            else appCtx.putPrefString(PreferKey.aiCurrentImageProviderId, value)
        }

    val aiCurrentImageProvider: AiImageProviderConfig?
        get() {
            val providers = aiEnabledImageProviders
            val currentId = aiCurrentImageProviderId
            if (currentId.isNullOrBlank()) {
                return providers.firstOrNull()?.also { aiCurrentImageProviderId = it.id }
            }
            return providers.firstOrNull { it.id == currentId }
        }

    fun findEnabledImageProvider(id: String?): AiImageProviderConfig? {
        val cleanId = id?.trim().orEmpty()
        if (cleanId.isBlank()) return null
        return aiEnabledImageProviders.firstOrNull { it.id == cleanId }
    }

    var aiContextCompressionEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiContextCompressionEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.aiContextCompressionEnabled, value)

    var aiContextWindowTokens: Int
        get() = appCtx.getPrefInt(PreferKey.aiContextWindowTokens, 258_000).coerceIn(8_000, 2_000_000)
        set(value) = appCtx.putPrefInt(PreferKey.aiContextWindowTokens, value.coerceIn(8_000, 2_000_000))

    var aiThinkingContextTokens: Int
        get() = appCtx.getPrefInt(PreferKey.aiThinkingContextTokens, 128_000).coerceIn(0, 1_000_000)
        set(value) = appCtx.putPrefInt(PreferKey.aiThinkingContextTokens, value.coerceIn(0, 1_000_000))

    var aiTavilyEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiTavilyEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.aiTavilyEnabled, value)

    var aiEnterToSend: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiEnterToSend, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.aiEnterToSend, value)

    var aiEnabledToolNames: Set<String>
        get() = appCtx.getPrefStringSet(PreferKey.aiEnabledToolNames, mutableSetOf())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet<String>()
        set(value) = appCtx.putPrefStringSet(
            PreferKey.aiEnabledToolNames,
            value.filter { it.isNotBlank() }.toMutableSet()
        )

    var aiTavilyApiKey: String
        get() = appCtx.getPrefString(PreferKey.aiTavilyApiKey).orEmpty()
        set(value) {
            val key = value.trim()
            if (key.isBlank()) appCtx.removePref(PreferKey.aiTavilyApiKey)
            else appCtx.putPrefString(PreferKey.aiTavilyApiKey, key)
        }

    var aiTavilyBaseUrl: String
        get() = appCtx.getPrefString(PreferKey.aiTavilyBaseUrl, "https://api.tavily.com/search")
            ?: "https://api.tavily.com/search"
        set(value) {
            val url = value.trim().ifBlank { "https://api.tavily.com/search" }
            appCtx.putPrefString(PreferKey.aiTavilyBaseUrl, url)
        }

    var aiTavilySearchDepth: String
        get() = appCtx.getPrefString(PreferKey.aiTavilySearchDepth, "basic") ?: "basic"
        set(value) = appCtx.putPrefString(PreferKey.aiTavilySearchDepth, value.trim().ifBlank { "basic" })

    var aiTavilyTopic: String
        get() = appCtx.getPrefString(PreferKey.aiTavilyTopic, "general") ?: "general"
        set(value) = appCtx.putPrefString(PreferKey.aiTavilyTopic, value.trim().ifBlank { "general" })

    var aiTavilyMaxResults: Int
        get() = appCtx.getPrefInt(PreferKey.aiTavilyMaxResults, 5).coerceIn(1, 10)
        set(value) = appCtx.putPrefInt(PreferKey.aiTavilyMaxResults, value.coerceIn(1, 10))

    val aiEnabledSkills: List<AiSkillConfig>
        get() = aiSkillList.filter { it.enabled }

    private fun readAiProviders(): List<AiProviderConfig> {
        migrateLegacyAiConfigIfNeeded()
        return normalizeAiProviders(
            GSON.fromJsonArray<AiProviderConfig>(appCtx.getPrefString(PreferKey.aiProviderList))
                .getOrDefault(emptyList())
        )
    }

    private fun readAiModels(validProviderIds: Set<String>): List<AiModelConfig> {
        migrateLegacyAiConfigIfNeeded()
        return normalizeAiModels(readAiModelsRaw(), validProviderIds)
    }

    private fun readAiModelsRaw(): List<AiModelConfig> {
        return GSON.fromJsonArray<AiModelConfig>(appCtx.getPrefString(PreferKey.aiModelConfigList))
            .getOrDefault(emptyList())
    }

    private fun normalizeAiProviders(value: List<AiProviderConfig>): List<AiProviderConfig> {
        return value.mapNotNull { provider ->
            val name = safeString { provider.name }.trim()
            val id = safeString { provider.id }.trim()
            if (name.isEmpty() || id.isEmpty()) {
                null
            } else {
                AiProviderConfig(
                    id = id,
                    name = name,
                    baseUrl = safeString { provider.baseUrl }.trim(),
                    apiKey = safeString { provider.apiKey }.trim(),
                    headers = safeString { provider.headers }.trim(),
                    apiMode = normalizeAiApiMode(safeString { provider.apiMode }),
                    promptCache = safeBoolean(false) { provider.promptCache }
                )
            }
        }.distinctBy { it.id }
    }

    private fun normalizeAiModels(
        value: List<AiModelConfig>,
        validProviderIds: Set<String>
    ): List<AiModelConfig> {
        return value.mapNotNull { model ->
            val id = safeString { model.id }.trim()
            val providerId = safeString { model.providerId }.trim()
            val modelId = safeString { model.modelId }.trim()
            if (id.isEmpty() || providerId !in validProviderIds || modelId.isEmpty()) {
                null
            } else {
                AiModelConfig(id = id, providerId = providerId, modelId = modelId)
            }
        }.distinctBy { "${it.providerId}|${it.modelId}" }
    }

    private fun persistAiProviders(providers: List<AiProviderConfig>) {
        if (providers.isEmpty()) {
            appCtx.removePref(PreferKey.aiProviderList)
        } else {
            appCtx.putPrefString(PreferKey.aiProviderList, GSON.toJson(providers))
        }
    }

    private fun persistAiModels(models: List<AiModelConfig>) {
        if (models.isEmpty()) {
            appCtx.removePref(PreferKey.aiModelConfigList)
        } else {
            appCtx.putPrefString(PreferKey.aiModelConfigList, GSON.toJson(models))
        }
    }

    private fun readAiMcpServers(): List<AiMcpServerConfig> {
        return normalizeAiMcpServers(
            GSON.fromJsonArray<AiMcpServerConfig>(appCtx.getPrefString(PreferKey.aiMcpServerList))
                .getOrDefault(emptyList())
        )
    }

    private fun normalizeAiMcpServers(value: List<AiMcpServerConfig>): List<AiMcpServerConfig> {
        return value.mapNotNull { server ->
            val id = safeString { server.id }.trim()
            val name = safeString { server.name }.trim()
            val endpoint = safeString { server.endpoint }.trim()
            if (id.isEmpty() || name.isEmpty() || endpoint.isEmpty()) {
                null
            } else {
                AiMcpServerConfig(
                    id = id,
                    name = name,
                    endpoint = endpoint,
                    apiKey = safeString { server.apiKey }.trim(),
                    enabled = safeBoolean(true) { server.enabled }
                )
            }
        }.distinctBy { it.id }
    }

    private fun persistAiMcpServers(servers: List<AiMcpServerConfig>) {
        if (servers.isEmpty()) {
            appCtx.removePref(PreferKey.aiMcpServerList)
        } else {
            appCtx.putPrefString(PreferKey.aiMcpServerList, GSON.toJson(servers))
        }
    }

    private fun readAiSkills(): List<AiSkillConfig> {
        migrateLegacyAiSkillIfNeeded()
        return normalizeAiSkills(
            GSON.fromJsonArray<AiSkillConfig>(appCtx.getPrefString(PreferKey.aiSkillList))
                .getOrDefault(emptyList())
        )
    }

    private fun normalizeAiSkills(value: List<AiSkillConfig>): List<AiSkillConfig> {
        return value.mapNotNull { skill ->
            val id = safeString { skill.id }.trim()
            val name = safeString { skill.name }.trim()
            val content = safeString { skill.content }.trim()
            if (id.isEmpty() || name.isEmpty() || content.isEmpty()) {
                null
            } else {
                AiSkillConfig(
                    id = id,
                    name = name,
                    description = safeString { skill.description }.trim(),
                    content = content,
                    sourceUrl = safeString { skill.sourceUrl }.trim(),
                    enabled = safeBoolean(true) { skill.enabled }
                )
            }
        }.distinctBy { it.id }
    }

    private fun normalizeAiPersonas(value: List<AiPersonaConfig>): List<AiPersonaConfig> {
        return value.mapNotNull { persona ->
            val id = safeString { persona.id }.trim()
            val name = safeString { persona.name }.trim()
            val prompt = safeString { persona.prompt }.trim()
            if (id.isEmpty() || name.isEmpty() || prompt.isEmpty()) {
                null
            } else {
                AiPersonaConfig(
                    id = id,
                    name = name,
                    prompt = prompt,
                    current = safeBoolean(false) { persona.current }
                )
            }
        }.distinctBy { it.id }
    }

    private fun normalizeAiImageProviders(value: List<AiImageProviderConfig>): List<AiImageProviderConfig> {
        return value.mapNotNull { provider ->
            val id = safeString { provider.id }.trim()
            val name = safeString { provider.name }.trim()
            if (id.isEmpty() || name.isEmpty()) {
                null
            } else {
                val type = safeString { provider.type }.trim()
                    .takeIf { it == AiImageProviderConfig.TYPE_JS || it == AiImageProviderConfig.TYPE_OPENAI }
                    ?: AiImageProviderConfig.TYPE_OPENAI
                AiImageProviderConfig(
                    id = id,
                    name = name,
                    type = type,
                    baseUrl = safeString { provider.baseUrl }.trim(),
                    apiKey = safeString { provider.apiKey }.trim(),
                    headers = safeString { provider.headers }.trim(),
                    model = safeString { provider.model }.trim(),
                    defaultParamsJson = safeString { provider.defaultParamsJson }.trim(),
                    stylePrompt = safeString { provider.stylePrompt }.trim(),
                    jsLib = safeString { provider.jsLib },
                    loginUrl = safeString { provider.loginUrl }.trim(),
                    loginUi = safeString { provider.loginUi },
                    enabledCookieJar = safeBoolean(false) { provider.enabledCookieJar },
                    script = safeString { provider.script },
                    timeoutMillisecond = safeLong(120_000L) { provider.timeoutMillisecond }
                        .takeIf { it > 0L } ?: 120_000L,
                    order = safeInt(0) { provider.order },
                    enabled = safeBoolean(true) { provider.enabled }
                )
            }
        }
            .distinctBy { it.id }
            .sortedBy { it.order }
            .mapIndexed { index, provider -> provider.copy(order = index) }
    }

    private fun normalizeAiApiMode(value: String): String {
        return if (value.trim() == AI_API_MODE_RESPONSES) {
            AI_API_MODE_RESPONSES
        } else {
            AI_API_MODE_CHAT_COMPLETIONS
        }
    }

    private inline fun safeString(block: () -> String?): String {
        return runCatching { block() }.getOrNull().orEmpty()
    }

    private inline fun safeBoolean(default: Boolean, block: () -> Boolean): Boolean {
        return runCatching { block() }.getOrDefault(default)
    }

    private inline fun safeInt(default: Int, block: () -> Int): Int {
        return runCatching { block() }.getOrDefault(default)
    }

    private inline fun safeLong(default: Long, block: () -> Long): Long {
        return runCatching { block() }.getOrDefault(default)
    }

    private fun persistAiSkills(skills: List<AiSkillConfig>) {
        if (skills.isEmpty()) {
            appCtx.removePref(PreferKey.aiSkillList)
        } else {
            appCtx.putPrefString(PreferKey.aiSkillList, GSON.toJson(skills))
        }
    }

    private fun migrateLegacyAiSkillIfNeeded() {
        if (!appCtx.getPrefString(PreferKey.aiSkillList).isNullOrBlank()) {
            return
        }
        val prompt = appCtx.getPrefString(PreferKey.aiSkillPrompt).orEmpty().trim()
        if (prompt.isBlank()) {
            return
        }
        persistAiSkills(
            listOf(
                AiSkillConfig(
                    name = "Legado Skill",
                    description = "从旧版单文本 Skill 配置迁移",
                    content = prompt
                )
            )
        )
        appCtx.removePref(PreferKey.aiSkillPrompt)
    }

    private fun syncAiState(
        providers: List<AiProviderConfig>,
        models: List<AiModelConfig>
    ) {
        val providerId = providers.firstOrNull {
            it.id == appCtx.getPrefString(PreferKey.aiCurrentProviderId)
        }?.id ?: providers.firstOrNull()?.id

        if (providerId.isNullOrBlank()) {
            appCtx.removePref(PreferKey.aiCurrentProviderId)
            appCtx.removePref(PreferKey.aiCurrentModelId)
            appCtx.putPrefBoolean(PreferKey.aiAssistantEnabled, false)
            return
        }

        if (providerId != appCtx.getPrefString(PreferKey.aiCurrentProviderId)) {
            appCtx.putPrefString(PreferKey.aiCurrentProviderId, providerId)
        }

        val providerModels = models.filter { it.providerId == providerId }
        val currentModelId = providerModels.firstOrNull {
            it.id == appCtx.getPrefString(PreferKey.aiCurrentModelId)
        }?.id ?: providerModels.firstOrNull()?.id

        if (currentModelId.isNullOrBlank()) {
            appCtx.removePref(PreferKey.aiCurrentModelId)
            appCtx.putPrefBoolean(PreferKey.aiAssistantEnabled, false)
        } else if (currentModelId != appCtx.getPrefString(PreferKey.aiCurrentModelId)) {
            appCtx.putPrefString(PreferKey.aiCurrentModelId, currentModelId)
        }
    }

    private fun migrateLegacyAiConfigIfNeeded() {
        if (!appCtx.getPrefString(PreferKey.aiProviderList).isNullOrBlank()
            || !appCtx.getPrefString(PreferKey.aiModelConfigList).isNullOrBlank()
        ) {
            return
        }
        val legacyBaseUrl = appCtx.getPrefString(PreferKey.aiBaseUrl, "")?.trim().orEmpty()
        val legacyApiKey = appCtx.getPrefString(PreferKey.aiApiKey, "")?.trim().orEmpty()
        val legacyModels = GSON.fromJsonArray<String>(appCtx.getPrefString(PreferKey.aiModelList))
            .getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (legacyBaseUrl.isBlank() && legacyApiKey.isBlank() && legacyModels.isEmpty()) {
            return
        }
        val provider = AiProviderConfig(
            name = resolveAiProviderName(legacyBaseUrl),
            baseUrl = legacyBaseUrl,
            apiKey = legacyApiKey
        )
        val models = legacyModels.map { modelId ->
            AiModelConfig(providerId = provider.id, modelId = modelId)
        }
        persistAiProviders(listOf(provider))
        if (models.isNotEmpty()) {
            persistAiModels(models)
        }
        appCtx.putPrefString(PreferKey.aiCurrentProviderId, provider.id)
        val legacyCurrentModel = appCtx.getPrefString(PreferKey.aiCurrentModel)?.trim().orEmpty()
        val currentModel = models.firstOrNull { it.modelId == legacyCurrentModel } ?: models.firstOrNull()
        if (currentModel == null) {
            appCtx.removePref(PreferKey.aiCurrentModelId)
            appCtx.putPrefBoolean(PreferKey.aiAssistantEnabled, false)
        } else {
            appCtx.putPrefString(PreferKey.aiCurrentModelId, currentModel.id)
        }
    }

    private fun resolveAiProviderName(baseUrl: String): String {
        if (baseUrl.isBlank()) return "Provider 1"
        return runCatching {
            URI(baseUrl).host?.removePrefix("www.")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Provider 1"
    }
}
