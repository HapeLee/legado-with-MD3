package io.legado.app.ui.config.readConfig

import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration
import org.koin.core.context.GlobalContext

/**
 * 已废弃的同步只读阅读配置门面。
 *
 * 新代码应注入对应 Gateway；此对象只服务尚未迁移的 View、渲染器与启动路径。
 */
@Deprecated("使用 ReadSettingsGateway / ReadAloudSettingsGateway.currentSettings")
object ReadConfig {
    private val read get() = GlobalContext.get().get<ReadSettingsGateway>().currentSettings
    private val aloud get() = GlobalContext.get().get<ReadAloudSettingsGateway>().currentSettings
    private val backup get() = GlobalContext.get().get<BackupSettingsGateway>().currentSettings
    private val cache get() = GlobalContext.get().get<DownloadCacheSettingsGateway>().currentSettings
    private val other get() = GlobalContext.get().get<OtherSettingsGateway>().currentSettings
    private val shell get() = GlobalContext.get().get<AppShellSettingsGateway>().currentSettings
    private val theme get() = GlobalContext.get().get<ThemeSettingsGateway>().currentSettings
    private val preferences get() = AppConfigStore.preferences

    val isEInkMode get() = theme.appTheme == "4"
    val isNightTheme: Boolean
        get() = when (shell.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }
    val enableReview get() = BuildConfig.DEBUG && (preferences.compatDsBoolean(PreferKey.enableReview) ?: false)
    val useAntiAlias get() = other.antiAlias
    val systemTypefaces get() = read.systemTypefaces
    val doubleHorizontalPage get() = read.doubleHorizontalPage
    val adaptSpecialStyle get() = read.adaptSpecialStyle
    val useUnderline get() = read.useUnderline
    val optimizeRender get() = read.optimizeRender
    val noAnimScrollPage get() = read.noAnimScrollPage
    val paddingDisplayCutouts get() = read.paddingDisplayCutouts
    val pageTouchSlop get() = read.pageTouchSlop
    val selectText get() = read.selectText
    val clickImgWay get() = read.clickImgWay
    val chineseConverterType get() = read.chineseConverterType

    val titleBarMode get() = read.titleBarMode
    val readBarStyleFollowPage get() = read.readBarStyleFollowPage
    val readBarStyle get() = read.readBarStyle
    val progressBarBehavior get() = read.progressBarBehavior
    val showSelectMenuIcon get() = read.showSelectMenuIcon
    val textSelectMenuConfig get() = read.textSelectMenuConfig
    val showReadTitleAddition get() = read.showReadTitleAddition
    val clickActionTL get() = read.clickActionTL
    val clickActionTC get() = read.clickActionTC
    val clickActionTR get() = read.clickActionTR
    val clickActionML get() = read.clickActionML
    val clickActionMC get() = read.clickActionMC
    val clickActionMR get() = read.clickActionMR
    val clickActionBL get() = read.clickActionBL
    val clickActionBC get() = read.clickActionBC
    val clickActionBR get() = read.clickActionBR

    val mouseWheelPage get() = read.mouseWheelPage
    val volumeKeyPage get() = read.volumeKeyPage
    val volumeKeyPageOnPlay get() = read.volumeKeyPageOnPlay
    val keyPageOnLongPress get() = read.keyPageOnLongPress
    val sliderVibrator get() = read.sliderVibrator
    val selectVibrator get() = read.selectVibrator

    val speechRatePlay get() = if (aloud.ttsFollowSys) 5 else aloud.ttsSpeechRate
    val ttsEngine get() = aloud.ttsEngine
    val ttsFollowSys get() = aloud.ttsFollowSys
    val ttsSpeechRate get() = aloud.ttsSpeechRate
    val ttsTimer get() = aloud.ttsTimer
    val ttsParagraphInterval get() = aloud.ttsParagraphInterval
    val ignoreAudioFocus get() = aloud.ignoreAudioFocus
    val pauseReadAloudWhilePhoneCalls get() = aloud.pauseReadAloudWhilePhoneCalls
    val readAloudWakeLock get() = aloud.readAloudWakeLock
    val mediaButtonPerNext get() = aloud.mediaButtonPerNext
    val readAloudByPage get() = aloud.readAloudByPage
    val systemMediaControlCompatibilityChange get() = aloud.systemMediaControlCompatibilityChange
    val streamReadAloudAudio get() = aloud.streamReadAloudAudio
    val contentSelectSpeakMod get() = aloud.contentSelectSpeakMode
    val audioPreDownloadNum get() = aloud.audioPreDownloadNum
    val audioCacheCleanTime get() = aloud.audioCacheCleanTime * 60 * 1000L
    val speechAnalysisMode get() = aloud.speechAnalysisMode
    val useMultiSpeaker get() = aloud.useMultiSpeaker
    val defaultInterface get() = aloud.defaultInterface

    val syncBookProgress get() = backup.syncBookProgress
    val syncBookProgressPlus get() = backup.syncBookProgressPlus
    val autoChangeSource get() = read.autoChangeSource
    val autoSuggestDayNight get() = read.autoSuggestDayNight
    val defaultSourceChangeAll get() = read.defaultSourceChangeAll
    val readUrlInBrowser get() = read.readUrlInBrowser
    val tocUiUseReplace get() = read.tocUiUseReplace
    val tocCountWords get() = read.tocCountWords
    val preDownloadNum get() = read.preDownloadNum
    val imageRetainNum get() = cache.imageRetainNum
    val keepLight get() = read.keepLight
    val screenOrientation get() = read.screenOrientation
}
