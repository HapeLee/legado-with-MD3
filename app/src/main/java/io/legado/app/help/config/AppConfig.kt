package io.legado.app.help.config

import android.content.SharedPreferences
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {
    val isCronet = appCtx.getPrefBoolean(PreferKey.cronet)
    var useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)
    var userAgent: String = getPrefUserAgent()

    var isEInkMode = appCtx.getPrefString("app_theme", "0") == "4"
    var isTransparent = appCtx.getPrefString("app_theme", "0") == "13"
    var customMode = appCtx.getPrefString(PreferKey.customMode)
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
    var mangaClickActionTL = appCtx.getPrefInt(PreferKey.mangaClickActionTL, -1)
    var mangaClickActionTC = appCtx.getPrefInt(PreferKey.mangaClickActionTC, -1)
    var mangaClickActionTR = appCtx.getPrefInt(PreferKey.mangaClickActionTR, 1)
    var mangaClickActionML = appCtx.getPrefInt(PreferKey.mangaClickActionML, 2)
    var mangaClickActionMC = appCtx.getPrefInt(PreferKey.mangaClickActionMC, 0)
    var mangaClickActionMR = appCtx.getPrefInt(PreferKey.mangaClickActionMR, 1)
    var mangaClickActionBL = appCtx.getPrefInt(PreferKey.mangaClickActionBL, 2)
    var mangaClickActionBC = appCtx.getPrefInt(PreferKey.mangaClickActionBC, 1)
    var mangaClickActionBR = appCtx.getPrefInt(PreferKey.mangaClickActionBR, 1)

    var themeMode = appCtx.getPrefString(PreferKey.themeMode, "0")
    var AppTheme = appCtx.getPrefString(PreferKey.appTheme, "0")
    var useDefaultCover = appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)
    var optimizeRender = CanvasRecorderFactory.isSupport
            && appCtx.getPrefBoolean(PreferKey.optimizeRender, false)
    var recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {

            PreferKey.appTheme -> {
                AppTheme = appCtx.getPrefString(PreferKey.appTheme, "0")
            }

            PreferKey.themeMode -> {
                themeMode = appCtx.getPrefString(PreferKey.themeMode, "0")
            }

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

            PreferKey.mangaClickActionTL -> mangaClickActionTL =
                appCtx.getPrefInt(PreferKey.mangaClickActionTL, -1)

            PreferKey.mangaClickActionTC -> mangaClickActionTC =
                appCtx.getPrefInt(PreferKey.mangaClickActionTC, -1)

            PreferKey.mangaClickActionTR -> mangaClickActionTR =
                appCtx.getPrefInt(PreferKey.mangaClickActionTR, 1)

            PreferKey.mangaClickActionML -> mangaClickActionML =
                appCtx.getPrefInt(PreferKey.mangaClickActionML, 2)

            PreferKey.mangaClickActionMC -> mangaClickActionMC =
                appCtx.getPrefInt(PreferKey.mangaClickActionMC, 0)

            PreferKey.mangaClickActionMR -> mangaClickActionMR =
                appCtx.getPrefInt(PreferKey.mangaClickActionMR, 1)

            PreferKey.mangaClickActionBL -> mangaClickActionBL =
                appCtx.getPrefInt(PreferKey.mangaClickActionBL, 2)

            PreferKey.mangaClickActionBC -> mangaClickActionBC =
                appCtx.getPrefInt(PreferKey.mangaClickActionBC, 1)

            PreferKey.mangaClickActionBR -> mangaClickActionBR =
                appCtx.getPrefInt(PreferKey.mangaClickActionBR, 1)

            PreferKey.readBodyToLh -> ReadBookConfig.readBodyToLh =
                appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)

            PreferKey.useZhLayout -> ReadBookConfig.useZhLayout =
                appCtx.getPrefBoolean(PreferKey.useZhLayout)

            PreferKey.userAgent -> userAgent = getPrefUserAgent()

            PreferKey.antiAlias -> useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)

            PreferKey.useDefaultCover -> useDefaultCover =
                appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)

            PreferKey.optimizeRender -> optimizeRender = CanvasRecorderFactory.isSupport
                    && appCtx.getPrefBoolean(PreferKey.optimizeRender, false)

            PreferKey.recordLog -> recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)

        }
    }

    var isNightTheme: Boolean
        get() = when (themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }
        set(value) {
            val newMode = if (value) "2" else "1"
            if (themeMode != newMode) {
                appCtx.putPrefString(PreferKey.themeMode, newMode)
            }
        }

    var showUnread: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showUnread, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showUnread, value)
        }

    var showTip: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showTip, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showTip, value)
        }

    var showUnreadNew: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showUnreadNew, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showUnreadNew, value)
        }

    var showLastUpdateTime: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showLastUpdateTime, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showLastUpdateTime, value)
        }

    var showWaitUpCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showWaitUpCount, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showWaitUpCount, value)
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

    var permissionChecked: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.permissionChecked, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.permissionChecked, value)
        }

    val textSelectAble: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.textSelectAble, true)

//    val isTransparentStatusBar: Boolean
//        get() = appCtx.getPrefBoolean(PreferKey.transparentStatusBar, true)
//
//    val immNavigationBar: Boolean
//        get() = appCtx.getPrefBoolean(PreferKey.immNavigationBar, true)

    val screenOrientation: String?
        get() = appCtx.getPrefString(PreferKey.screenOrientation)

    var bookGroupStyle: Int
        get() = appCtx.getPrefInt(PreferKey.bookGroupStyle, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookGroupStyle, value)
        }

    var bookshelfLayoutModePortrait: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfLayoutModePortrait, 1)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfLayoutModePortrait, value)
        }

    var bookshelfLayoutModeLandscape: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfLayoutModeLandscape, 1)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfLayoutModeLandscape, value)
        }

    var bookshelfLayoutGridPortrait: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfLayoutGridPortrait, 3)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfLayoutGridPortrait, value)
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
        get() = appCtx.getPrefInt(PreferKey.bookshelfLayoutGridLandscape, 7)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfLayoutGridLandscape, value)
        }

    var saveTabPosition: Int
        get() = appCtx.getPrefInt(PreferKey.saveTabPosition, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.saveTabPosition, value)
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
        get() = appCtx.getPrefString(PreferKey.bookImportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookImportFileName, value)
        }

    var backupPath: String?
        get() = appCtx.getPrefString(PreferKey.backupPath)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.backupPath)
            } else {
                appCtx.putPrefString(PreferKey.backupPath, value)
            }
        }

    // 书籍保存位置
    var defaultBookTreeUri: String?
        get() = appCtx.getPrefString(PreferKey.defaultBookTreeUri)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.defaultBookTreeUri)
            } else {
                appCtx.putPrefString(PreferKey.defaultBookTreeUri, value)
            }
        }

    val showDiscovery: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showDiscovery, true)

    val showRSS: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showRss, true)

    val showStatusBar: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showStatusBar, true)

    val showBottomView: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBottomView, true)

    val autoRefreshBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoRefresh)

    var enableReview: Boolean
        get() = BuildConfig.DEBUG && appCtx.getPrefBoolean(PreferKey.enableReview, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = appCtx.getPrefInt(PreferKey.threadCount, 16)
        set(value) {
            appCtx.putPrefInt(PreferKey.threadCount, value)
        }

    var remoteServerId: Long
        get() = appCtx.getPrefLong(PreferKey.remoteServerId)
        set(value) {
            appCtx.putPrefLong(PreferKey.remoteServerId, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = appCtx.getPrefString("importBookPath")
        set(value) {
            if (value == null) {
                appCtx.removePref("importBookPath")
            } else {
                appCtx.putPrefString("importBookPath", value)
            }
        }

    var ttsFlowSys: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.ttsFollowSys, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.ttsFollowSys, value)
        }

    val noAnimScrollPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.noAnimScrollPage, false)

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = appCtx.getPrefInt(PreferKey.ttsSpeechRate, defaultSpeechRate)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsSpeechRate, value)
        }

    var ttsTimer: Int
        get() = appCtx.getPrefInt(PreferKey.ttsTimer, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsTimer, value)
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    var chineseConverterType: Int
        get() = appCtx.getPrefInt(PreferKey.chineseConverterType)
        set(value) {
            appCtx.putPrefInt(PreferKey.chineseConverterType, value)
        }

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

    var changeSourceCheckAuthor: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceCheckAuthor)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceCheckAuthor, value)
        }

    var ttsEngine: String?
        get() = appCtx.getPrefString(PreferKey.ttsEngine)
        set(value) {
            appCtx.putPrefString(PreferKey.ttsEngine, value)
        }

    var webPort: Int
        get() = appCtx.getPrefInt(PreferKey.webPort, 1122)
        set(value) {
            appCtx.putPrefInt(PreferKey.webPort, value)
        }

    var tocUiUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocUiUseReplace)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocUiUseReplace, value)
        }

    var tocCountWords: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocCountWords, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocCountWords, value)
        }

    var enableReadRecord: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableReadRecord, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    val autoChangeSource: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoChangeSource, true)

    var changeSourceLoadInfo: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadInfo)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadInfo, value)
        }

    var changeSourceLoadToc: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadToc)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadToc, value)
        }

    var changeSourceLoadWordCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadWordCount)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadWordCount, value)
        }

    var openBookInfoByClickTitle: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.openBookInfoByClickTitle, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.openBookInfoByClickTitle, value)
        }

    var showBookshelfFastScroller: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showBookshelfFastScroller, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showBookshelfFastScroller, value)
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

    var preDownloadNum
        get() = appCtx.getPrefInt(PreferKey.preDownloadNum, 10)
        set(value) {
            appCtx.putPrefInt(PreferKey.preDownloadNum, value)
        }

    val syncBookProgress get() = appCtx.getPrefBoolean(PreferKey.syncBookProgress, true)

    val syncBookProgressPlus get() = appCtx.getPrefBoolean(PreferKey.syncBookProgressPlus, false)

    val mediaButtonOnExit get() = appCtx.getPrefBoolean("mediaButtonOnExit", true)

    val readAloudByMediaButton
        get() = appCtx.getPrefBoolean(PreferKey.readAloudByMediaButton, false)

    val replaceEnableDefault get() = appCtx.getPrefBoolean(PreferKey.replaceEnableDefault, true)

    val webDavDir get() = appCtx.getPrefString(PreferKey.webDavDir, "legado")

    val webDavDeviceName get() = appCtx.getPrefString(PreferKey.webDavDeviceName, Build.MODEL)

    val recordHeapDump get() = appCtx.getPrefBoolean(PreferKey.recordHeapDump, false)

    val loadCoverOnlyWifi get() = appCtx.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false)

    val showAddToShelfAlert get() = appCtx.getPrefBoolean(PreferKey.showAddToShelfAlert, true)

    val ignoreAudioFocus get() = appCtx.getPrefBoolean(PreferKey.ignoreAudioFocus, false)

    var pauseReadAloudWhilePhoneCalls
        get() = appCtx.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)

    val onlyLatestBackup get() = appCtx.getPrefBoolean(PreferKey.onlyLatestBackup, true)

    val autoCheckNewBackup get() = appCtx.getPrefBoolean(PreferKey.autoCheckNewBackup, true)

    val defaultHomePage get() = appCtx.getPrefString(PreferKey.defaultHomePage, "bookshelf")

    val updateToVariant get() = appCtx.getPrefString(PreferKey.updateToVariant, "official_version")

    val streamReadAloudAudio get() = appCtx.getPrefBoolean(PreferKey.streamReadAloudAudio, false)

    val doublePageHorizontal: String?
        get() = appCtx.getPrefString(PreferKey.doublePageHorizontal)

    val progressBarBehavior: String?
        get() = appCtx.getPrefString(PreferKey.progressBarBehavior, "page")

    val keyPageOnLongPress
        get() = appCtx.getPrefBoolean(PreferKey.keyPageOnLongPress, false)

    val volumeKeyPage
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPage, true)

    val volumeKeyPageOnPlay
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPageOnPlay, true)

    val mouseWheelPage
        get() = appCtx.getPrefBoolean(PreferKey.mouseWheelPage, true)

    val paddingDisplayCutouts
        get() = appCtx.getPrefBoolean(PreferKey.paddingDisplayCutouts, false)

    val delayBookLoadEnable
        get() = appCtx.getPrefBoolean(PreferKey.delayBookLoadEnable, true)

    val sharedElementEnterTransitionEnable
        get() = appCtx.getPrefBoolean(PreferKey.sharedElementEnterTransitionEnable, false)

    var searchScope: String
        get() = appCtx.getPrefString("searchScope") ?: ""
        set(value) {
            appCtx.putPrefString("searchScope", value)
        }

    var searchGroup: String
        get() = appCtx.getPrefString("searchGroup") ?: ""
        set(value) {
            appCtx.putPrefString("searchGroup", value)
        }

    var pageTouchSlop: Int
        get() = appCtx.getPrefInt(PreferKey.pageTouchSlop, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.pageTouchSlop, value)
        }

    var bookshelfSort: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfSort, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfSort, value)
        }

    var bookshelfSortOrder: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfSortOrder, 1)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfSortOrder, value)
        }

    fun getBookSortByGroupId(groupId: Long): Int {
        return appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
            ?: bookshelfSort
    }

    private fun getPrefUserAgent(): String {
        val ua = appCtx.getPrefString(PreferKey.userAgent)
        if (ua.isNullOrBlank()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + BuildConfig.Cronet_Main_Version + " Safari/537.36"
        }
        return ua
    }

    var bitmapCacheSize: Int
        get() = appCtx.getPrefInt(PreferKey.bitmapCacheSize, 50)
        set(value) {
            appCtx.putPrefInt(PreferKey.bitmapCacheSize, value)
        }

    var imageRetainNum: Int
        get() = appCtx.getPrefInt(PreferKey.imageRetainNum, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.imageRetainNum, value)
        }

    var showReadTitleBarAddition: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showReadTitleAddition, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showReadTitleAddition, value)
        }

    var readBarStyleFollowPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var readBarStyle: Int
        get() = appCtx.getPrefInt(PreferKey.readBarStyle, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.readBarStyle, value)
        }

    var sourceEditMaxLine: Int
        get() {
            val maxLine = appCtx.getPrefInt(PreferKey.sourceEditMaxLine, Int.MAX_VALUE)
            if (maxLine < 10) {
                return Int.MAX_VALUE
            }
            return maxLine
        }
        set(value) {
            appCtx.putPrefInt(PreferKey.sourceEditMaxLine, value)
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
        if (mangaClickActionTL * mangaClickActionTC * mangaClickActionTR
            * mangaClickActionML * mangaClickActionMC * mangaClickActionMR
            * mangaClickActionBL * mangaClickActionBC * mangaClickActionBR != 0
        ) {
            appCtx.putPrefInt(PreferKey.mangaClickActionMC, 0)
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    var firebaseEnable: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.firebaseEnable, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.firebaseEnable, value)
        }

    //跳转到漫画界面不使用富文本模式
    val showMangaUi: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showMangaUi, true)

    //禁用漫画缩放
    var disableMangaScale: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaScale, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaScale, value)
        }

    var disableMangaScrollAnimation: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaScrollAnimation, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaScrollAnimation, value)
        }

    var disableMangaCrossFade: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaCrossFade, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaCrossFade, value)
        }

    var titleBarMode
        get() = appCtx.getPrefString(PreferKey.titleBarMode, "1")
        set(value) {
            appCtx.putPrefString(PreferKey.titleBarMode, value)
        }

    //漫画预加载数量
    var mangaPreDownloadNum
        get() = appCtx.getPrefInt(PreferKey.mangaPreDownloadNum, 10)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaPreDownloadNum, value)
        }

    //点击翻页
    var disableClickScroll
        get() = appCtx.getPrefBoolean(PreferKey.disableClickScroll, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableClickScroll, value)
        }

    //漫画滚动速度
    var mangaAutoPageSpeed
        get() = appCtx.getPrefInt(PreferKey.mangaAutoPageSpeed, 3)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaAutoPageSpeed, value)
        }

    //漫画页脚配置
    var mangaFooterConfig
        get() = appCtx.getPrefString(PreferKey.mangaFooterConfig, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaFooterConfig, value)
        }

    //漫画滚动方式
    var mangaScrollMode: Int
        get() = appCtx.getPrefInt(PreferKey.mangaScrollMode, MangaScrollMode.WEBTOON)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaScrollMode, value)
        }

    var mangaLongClick
        get() = appCtx.getPrefBoolean(PreferKey.mangaLongClick, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.mangaLongClick, value)
        }

    var mangaBackground: Int
        get() = appCtx.getPrefInt(PreferKey.mangaBackground, 0xFF000000.toInt())
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaBackground, value)
        }

    //漫画滤镜
    var mangaColorFilter
        get() = appCtx.getPrefString(PreferKey.mangaColorFilter, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaColorFilter, value)
        }

    //禁用漫画内标题
    var hideMangaTitle
        get() = appCtx.getPrefBoolean(PreferKey.hideMangaTitle, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.hideMangaTitle, value)
        }

    //开启墨水屏模式
    var enableMangaEInk
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaEInk, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaEInk, value)
        }

    //墨水屏阈值
    var mangaEInkThreshold
        get() = appCtx.getPrefInt(PreferKey.mangaEInkThreshold, 150)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaEInkThreshold, value)
        }

    //漫画灰度
    var enableMangaGray
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaGray, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaGray, value)
        }

    //条漫侧边距
    var webtoonSidePaddingDp: Int
        get() = appCtx.getPrefInt(PreferKey.webtoonSidePaddingDp, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.webtoonSidePaddingDp, value)
        }

    //漫画音量键翻页
    var MangaVolumeKeyPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.mangaVolumeKeyPage, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.mangaVolumeKeyPage, value)
        }

    var reverseVolumeKeyPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.reverseVolumeKeyPage, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.reverseVolumeKeyPage, value)
        }

    var tabletInterface
        get() = appCtx.getPrefString(PreferKey.tabletInterface, "auto")
        set(value) {
            appCtx.putPrefString(PreferKey.tabletInterface, value)
        }

    var pureBlack
        get() = appCtx.getPrefBoolean(PreferKey.pureBlack, false)
        set(value) {
            appCtx.getPrefBoolean(PreferKey.pureBlack, value)
        }

    val hasLightBg: Boolean
        get() = !appCtx.getPrefString(PreferKey.bgImage).isNullOrEmpty()

    val hasDarkBg: Boolean
        get() = !appCtx.getPrefString(PreferKey.bgImageN).isNullOrEmpty()

    val hasImageBg: Boolean
        get() = hasLightBg && hasDarkBg

    var labelVisibilityMode
        get() = appCtx.getPrefString(PreferKey.labelVisibilityMode, "auto")
        set(value) {
            appCtx.putPrefString(PreferKey.labelVisibilityMode, value)
        }

    var paletteStyle
        get() = appCtx.getPrefString(PreferKey.paletteStyle, "tonalSpot")
        set(value) {
            appCtx.putPrefString(PreferKey.paletteStyle, value)
        }

    var enableBlur
        get() = appCtx.getPrefBoolean(PreferKey.enableBlur, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableBlur, value)
        }
    var menuAlpha: Int
        get() = appCtx.getPrefInt(PreferKey.menuAlpha, 100)
        set(value) {
            appCtx.putPrefInt(PreferKey.menuAlpha, value)
        }

    var readSliderMode
        get() = appCtx.getPrefString(PreferKey.readSliderMode, "0")
        set(value) {
            appCtx.putPrefString(PreferKey.readSliderMode, value)
        }

    var bookshelfRefreshingLimit: Int
        get() = appCtx.getPrefInt(PreferKey.bookshelfRefreshingLimit, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfRefreshingLimit, value)
        }

    var systemMediaControlCompatibilityChange: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.systemMediaControlCompatibilityChange, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.systemMediaControlCompatibilityChange, value)
        }

    var isPredictiveBackEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.isPredictiveBackEnabled, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.isPredictiveBackEnabled, value)
        }

    var shouldShowExpandButton: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.shouldShowExpandButton, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.shouldShowExpandButton, value)
        }

    var exploreFilterState: Int
        get() = appCtx.getPrefInt(PreferKey.exploreFilterState, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreFilterState, value)
        }

    var exploreLayoutState: Int
        get() = appCtx.getPrefInt(PreferKey.exploreLayoutState, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreLayoutState, value)
        }

    var defaultSourceChangeAll: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.defaultSourceChangeAll, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.defaultSourceChangeAll, value)
        }

    var sliderVibrator: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.sliderVibrator, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.sliderVibrator, value)
        }


}

