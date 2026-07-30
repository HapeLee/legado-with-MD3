package io.legado.app.help.config

import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.ImportBookSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration

/**
 * 已废弃的同步只读配置门面。
 *
 * 新代码必须注入具体 Gateway；此对象只保留给尚未迁移的 View、服务和启动逻辑。
 */
@Deprecated("使用具体 SettingsGateway.currentSettings")
object AppConfig {
    private lateinit var shellGateway: AppShellSettingsGateway
    private lateinit var themeGateway: ThemeSettingsGateway
    private lateinit var bookshelfGateway: BookshelfSettingsGateway
    private lateinit var otherGateway: OtherSettingsGateway
    private lateinit var backupGateway: BackupSettingsGateway
    private lateinit var cacheGateway: DownloadCacheSettingsGateway
    private lateinit var coverGateway: CoverSettingsGateway
    private lateinit var readGateway: ReadSettingsGateway
    private lateinit var aloudGateway: ReadAloudSettingsGateway
    private lateinit var importBookGateway: ImportBookSettingsGateway
    private lateinit var exportGateway: BookExportSettingsGateway

    internal fun initialize(
        shellGateway: AppShellSettingsGateway,
        themeGateway: ThemeSettingsGateway,
        bookshelfGateway: BookshelfSettingsGateway,
        otherGateway: OtherSettingsGateway,
        backupGateway: BackupSettingsGateway,
        cacheGateway: DownloadCacheSettingsGateway,
        coverGateway: CoverSettingsGateway,
        readGateway: ReadSettingsGateway,
        aloudGateway: ReadAloudSettingsGateway,
        importBookGateway: ImportBookSettingsGateway,
        exportGateway: BookExportSettingsGateway,
    ) {
        this.shellGateway = shellGateway
        this.themeGateway = themeGateway
        this.bookshelfGateway = bookshelfGateway
        this.otherGateway = otherGateway
        this.backupGateway = backupGateway
        this.cacheGateway = cacheGateway
        this.coverGateway = coverGateway
        this.readGateway = readGateway
        this.aloudGateway = aloudGateway
        this.importBookGateway = importBookGateway
        this.exportGateway = exportGateway
    }

    private val shell get() = shellGateway.currentSettings
    private val theme get() = themeGateway.currentSettings
    private val bookshelf get() = bookshelfGateway.currentSettings
    private val other get() = otherGateway.currentSettings
    private val backup get() = backupGateway.currentSettings
    private val cache get() = cacheGateway.currentSettings
    private val cover get() = coverGateway.currentSettings
    private val read get() = readGateway.currentSettings
    private val aloud get() = aloudGateway.currentSettings
    private val importBook get() = importBookGateway.currentSettings
    private val export get() = exportGateway.currentSettings

    val isCronet get() = cache.cronetEnabled
    val userAgent get() = cache.userAgent
    val isEInkMode get() = theme.appTheme == "4"
    val customMode get() = theme.customMode
    val useDefaultCover get() = cover.useDefaultCover
    val recordLog get() = other.recordLog
    val recordHeapDump get() = other.recordHeapDump
    val webServiceAutoStart get() = other.webServiceAutoStart
    val adaptSpecialStyle get() = read.adaptSpecialStyle
    val isNightTheme: Boolean
        get() = when (shell.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }
    val bookshelfLayoutModePortrait get() = bookshelf.bookshelfLayoutModePortrait
    val bookshelfLayoutModeLandscape get() = bookshelf.bookshelfLayoutModeLandscape
    val bookshelfLayoutGridPortrait get() = bookshelf.bookshelfLayoutGridPortrait
    val bookshelfLayoutGridLandscape get() = bookshelf.bookshelfLayoutGridLandscape
    val bookExportFileName get() = export.bookExportFileName
    val episodeExportFileName get() = export.episodeExportFileName
    val bookImportFileName get() = importBook.bookImportFileName
    val backupPath get() = backup.backupPath
    val showStatusBar get() = shell.showStatusBar
    val autoRefreshBook get() = other.autoRefresh
    val threadCount get() = cache.threadCount
    val importBookPath get() = importBook.importBookPath
    val chineseConverterType get() = read.chineseConverterType
    val enableCustomExport get() = export.enableCustomExport
    val exportType get() = export.exportType
    val importKeepName get() = other.importKeepName
    val importKeepGroup get() = other.importKeepGroup
    val importKeepEnable get() = other.importKeepEnable
    val preDownloadNum get() = cache.preDownloadNum
    val syncBookProgress get() = backup.syncBookProgress
    val mediaButtonOnExit get() = aloud.mediaButtonOnExit
    val readAloudByMediaButton get() = aloud.readAloudByMediaButton
    val replaceEnableDefault get() = other.replaceEnableDefault
    val webDavDir get() = backup.webDavDir
    val webDavDeviceName get() = backup.webDavDeviceName
    val onlyLatestBackup get() = backup.onlyLatestBackup
    val autoCheckNewBackup get() = backup.autoCheckNewBackup
    val updateToVariant get() = other.updateToVariant
    val bookshelfSort get() = bookshelf.bookshelfSort
    val bitmapCacheSize get() = cache.bitmapCacheSize
    val sourceEditMaxLine get() = other.sourceEditMaxLine
    val audioPlayUseWakeLock get() = other.audioPlayUseWakeLock
    val firebaseEnable get() = other.firebaseEnable
    val pureBlack get() = theme.isPureBlack
    val systemMediaControlCompatibilityChange get() = aloud.systemMediaControlCompatibilityChange
    val isPredictiveBackEnabled get() = shell.predictiveBackEnabled
}
