package io.legado.app.ui.config.readConfig

import io.legado.app.data.repository.ReadPreferences

/**
 * 阅读配置门面
 *
 * 代理到子配置对象，保持向后兼容。
 * 子配置按职责拆分为：
 * - [ReadRenderConfig]  渲染/布局
 * - [ReadMenuConfig]    菜单/标题栏/进度条/点击区域
 * - [ReadInteractionConfig] 按键/触控交互
 * - [ReadTtsConfig]     TTS/朗读/音频
 * - [ReadDataConfig]    数据同步/缓存/屏幕
 *
 * 注意：textFullJustify、textBottomJustify、useZhLayout、readBodyToLh、
 * hideStatusBar、hideNavigationBar、showBrightnessView、brightnessVwPos、
 * readBrightness、brightnessAuto、readSliderMode、readMenuBlurAlpha
 * 已在 [ReadBookConfig] 中定义，此处不重复。
 */
object ReadConfig {

    // ── 渲染 ──

    val isEInkMode get() = ReadRenderConfig.isEInkMode
    var isNightTheme
        get() = ReadRenderConfig.isNightTheme
        set(value) { ReadRenderConfig.isNightTheme = value }
    val enableReview get() = ReadRenderConfig.enableReview
    var useAntiAlias
        get() = ReadRenderConfig.useAntiAlias
        set(value) { ReadRenderConfig.useAntiAlias = value }
    var systemTypefaces
        get() = ReadRenderConfig.systemTypefaces
        set(value) { ReadRenderConfig.systemTypefaces = value }
    var doubleHorizontalPage
        get() = ReadRenderConfig.doubleHorizontalPage
        set(value) { ReadRenderConfig.doubleHorizontalPage = value }
    var adaptSpecialStyle
        get() = ReadRenderConfig.adaptSpecialStyle
        set(value) { ReadRenderConfig.adaptSpecialStyle = value }
    var useUnderline
        get() = ReadRenderConfig.useUnderline
        set(value) { ReadRenderConfig.useUnderline = value }
    var optimizeRender
        get() = ReadRenderConfig.optimizeRender
        set(value) { ReadRenderConfig.optimizeRender = value }
    var noAnimScrollPage
        get() = ReadRenderConfig.noAnimScrollPage
        set(value) { ReadRenderConfig.noAnimScrollPage = value }
    var paddingDisplayCutouts
        get() = ReadRenderConfig.paddingDisplayCutouts
        set(value) { ReadRenderConfig.paddingDisplayCutouts = value }
    var pageTouchSlop
        get() = ReadRenderConfig.pageTouchSlop
        set(value) { ReadRenderConfig.pageTouchSlop = value }
    var selectText
        get() = ReadRenderConfig.selectText
        set(value) { ReadRenderConfig.selectText = value }
    var clickImgWay
        get() = ReadRenderConfig.clickImgWay
        set(value) { ReadRenderConfig.clickImgWay = value }
    var chineseConverterType
        get() = ReadRenderConfig.chineseConverterType
        set(value) { ReadRenderConfig.chineseConverterType = value }

    // ── 菜单 ──

    var titleBarMode
        get() = ReadMenuConfig.titleBarMode
        set(value) { ReadMenuConfig.titleBarMode = value }
    var menuAlpha
        get() = ReadMenuConfig.menuAlpha
        set(value) { ReadMenuConfig.menuAlpha = value }
    var readBarStyleFollowPage
        get() = ReadMenuConfig.readBarStyleFollowPage
        set(value) { ReadMenuConfig.readBarStyleFollowPage = value }
    var readBarStyle
        get() = ReadMenuConfig.readBarStyle
        set(value) { ReadMenuConfig.readBarStyle = value }
    var progressBarBehavior
        get() = ReadMenuConfig.progressBarBehavior
        set(value) { ReadMenuConfig.progressBarBehavior = value }
    var expandTextMenu
        get() = ReadMenuConfig.expandTextMenu
        set(value) { ReadMenuConfig.expandTextMenu = value }
    var showSelectMenuIcon
        get() = ReadMenuConfig.showSelectMenuIcon
        set(value) { ReadMenuConfig.showSelectMenuIcon = value }
    var textSelectMenuFilter
        get() = ReadMenuConfig.textSelectMenuFilter
        set(value) { ReadMenuConfig.textSelectMenuFilter = value }
    var showReadTitleAddition
        get() = ReadMenuConfig.showReadTitleAddition
        set(value) { ReadMenuConfig.showReadTitleAddition = value }
    var showMenuIcon
        get() = ReadMenuConfig.showMenuIcon
        set(value) { ReadMenuConfig.showMenuIcon = value }
    var clickActionTL
        get() = ReadMenuConfig.clickActionTL
        set(value) { ReadMenuConfig.clickActionTL = value }
    var clickActionTC
        get() = ReadMenuConfig.clickActionTC
        set(value) { ReadMenuConfig.clickActionTC = value }
    var clickActionTR
        get() = ReadMenuConfig.clickActionTR
        set(value) { ReadMenuConfig.clickActionTR = value }
    var clickActionML
        get() = ReadMenuConfig.clickActionML
        set(value) { ReadMenuConfig.clickActionML = value }
    var clickActionMC
        get() = ReadMenuConfig.clickActionMC
        set(value) { ReadMenuConfig.clickActionMC = value }
    var clickActionMR
        get() = ReadMenuConfig.clickActionMR
        set(value) { ReadMenuConfig.clickActionMR = value }
    var clickActionBL
        get() = ReadMenuConfig.clickActionBL
        set(value) { ReadMenuConfig.clickActionBL = value }
    var clickActionBC
        get() = ReadMenuConfig.clickActionBC
        set(value) { ReadMenuConfig.clickActionBC = value }
    var clickActionBR
        get() = ReadMenuConfig.clickActionBR
        set(value) { ReadMenuConfig.clickActionBR = value }
    fun hasMenuClickArea() = ReadMenuConfig.hasMenuClickArea()
    fun detectClickArea() = ReadMenuConfig.detectClickArea()

    // ── 交互 ──

    var mouseWheelPage
        get() = ReadInteractionConfig.mouseWheelPage
        set(value) { ReadInteractionConfig.mouseWheelPage = value }
    var volumeKeyPage
        get() = ReadInteractionConfig.volumeKeyPage
        set(value) { ReadInteractionConfig.volumeKeyPage = value }
    var volumeKeyPageOnPlay
        get() = ReadInteractionConfig.volumeKeyPageOnPlay
        set(value) { ReadInteractionConfig.volumeKeyPageOnPlay = value }
    var keyPageOnLongPress
        get() = ReadInteractionConfig.keyPageOnLongPress
        set(value) { ReadInteractionConfig.keyPageOnLongPress = value }
    var sliderVibrator
        get() = ReadInteractionConfig.sliderVibrator
        set(value) { ReadInteractionConfig.sliderVibrator = value }
    var selectVibrator
        get() = ReadInteractionConfig.selectVibrator
        set(value) { ReadInteractionConfig.selectVibrator = value }
    var disableReturnKey
        get() = ReadInteractionConfig.disableReturnKey
        set(value) { ReadInteractionConfig.disableReturnKey = value }
    var prevKeys
        get() = ReadInteractionConfig.prevKeys
        set(value) { ReadInteractionConfig.prevKeys = value }
    var nextKeys
        get() = ReadInteractionConfig.nextKeys
        set(value) { ReadInteractionConfig.nextKeys = value }

    // ── TTS ──

    val speechRatePlay get() = ReadTtsConfig.speechRatePlay
    var ttsEngine
        get() = ReadTtsConfig.ttsEngine
        set(value) { ReadTtsConfig.ttsEngine = value }
    var ttsFollowSys
        get() = ReadTtsConfig.ttsFollowSys
        set(value) { ReadTtsConfig.ttsFollowSys = value }
    var ttsSpeechRate
        get() = ReadTtsConfig.ttsSpeechRate
        set(value) { ReadTtsConfig.ttsSpeechRate = value }
    var ttsTimer
        get() = ReadTtsConfig.ttsTimer
        set(value) { ReadTtsConfig.ttsTimer = value }
    var ignoreAudioFocus
        get() = ReadTtsConfig.ignoreAudioFocus
        set(value) { ReadTtsConfig.ignoreAudioFocus = value }
    var pauseReadAloudWhilePhoneCalls
        get() = ReadTtsConfig.pauseReadAloudWhilePhoneCalls
        set(value) { ReadTtsConfig.pauseReadAloudWhilePhoneCalls = value }
    var readAloudWakeLock
        get() = ReadTtsConfig.readAloudWakeLock
        set(value) { ReadTtsConfig.readAloudWakeLock = value }
    var mediaButtonPerNext
        get() = ReadTtsConfig.mediaButtonPerNext
        set(value) { ReadTtsConfig.mediaButtonPerNext = value }
    var readAloudByPage
        get() = ReadTtsConfig.readAloudByPage
        set(value) { ReadTtsConfig.readAloudByPage = value }
    var systemMediaControlCompatibilityChange
        get() = ReadTtsConfig.systemMediaControlCompatibilityChange
        set(value) { ReadTtsConfig.systemMediaControlCompatibilityChange = value }
    var streamReadAloudAudio
        get() = ReadTtsConfig.streamReadAloudAudio
        set(value) { ReadTtsConfig.streamReadAloudAudio = value }
    var contentSelectSpeakMod
        get() = ReadTtsConfig.contentSelectSpeakMod
        set(value) { ReadTtsConfig.contentSelectSpeakMod = value }
    var audioPreDownloadNum
        get() = ReadTtsConfig.audioPreDownloadNum
        set(value) { ReadTtsConfig.audioPreDownloadNum = value }
    var audioCacheCleanTimeOrgin
        get() = ReadTtsConfig.audioCacheCleanTimeOrgin
        set(value) { ReadTtsConfig.audioCacheCleanTimeOrgin = value }
    val audioCacheCleanTime get() = ReadTtsConfig.audioCacheCleanTime

    // ── 数据/屏幕 ──

    var syncBookProgress
        get() = ReadDataConfig.syncBookProgress
        set(value) { ReadDataConfig.syncBookProgress = value }
    var syncBookProgressPlus
        get() = ReadDataConfig.syncBookProgressPlus
        set(value) { ReadDataConfig.syncBookProgressPlus = value }
    var autoChangeSource
        get() = ReadDataConfig.autoChangeSource
        set(value) { ReadDataConfig.autoChangeSource = value }
    var defaultSourceChangeAll
        get() = ReadDataConfig.defaultSourceChangeAll
        set(value) { ReadDataConfig.defaultSourceChangeAll = value }
    var readUrlInBrowser
        get() = ReadDataConfig.readUrlInBrowser
        set(value) { ReadDataConfig.readUrlInBrowser = value }
    var tocUiUseReplace
        get() = ReadDataConfig.tocUiUseReplace
        set(value) { ReadDataConfig.tocUiUseReplace = value }
    var tocCountWords
        get() = ReadDataConfig.tocCountWords
        set(value) { ReadDataConfig.tocCountWords = value }
    var preDownloadNum
        get() = ReadDataConfig.preDownloadNum
        set(value) { ReadDataConfig.preDownloadNum = value }
    var imageRetainNum
        get() = ReadDataConfig.imageRetainNum
        set(value) { ReadDataConfig.imageRetainNum = value }
    var keepLight
        get() = ReadDataConfig.keepLight
        set(value) { ReadDataConfig.keepLight = value }
    var screenOrientation
        get() = ReadDataConfig.screenOrientation
        set(value) { ReadDataConfig.screenOrientation = value }

    // ── 同步 ──

    /**
     * 从 [ReadPreferences] 批量同步到各子配置。
     *
     * 注意：textFullJustify、textBottomJustify、useZhLayout、readBodyToLh、
     * hideStatusBar、hideNavigationBar、showBrightnessView、brightnessVwPos、
     * readBrightness、brightnessAuto、readSliderMode、readMenuBlurAlpha
     * 由 [ReadBookConfig] 的 prefDelegate 自动同步，此处不处理。
     */
    fun syncReadPreferences(preferences: ReadPreferences) {
        // 渲染
        ReadRenderConfig.apply {
            optimizeRender = preferences.optimizeRender
            adaptSpecialStyle = preferences.adaptSpecialStyle
            useUnderline = preferences.useUnderline
            chineseConverterType = preferences.chineseConverterType
            doubleHorizontalPage = preferences.doubleHorizontalPage
            noAnimScrollPage = preferences.noAnimScrollPage
            paddingDisplayCutouts = preferences.paddingDisplayCutouts
            pageTouchSlop = preferences.pageTouchSlop
            selectText = preferences.selectText
            clickImgWay = preferences.clickImgWay
        }
        // 菜单
        ReadMenuConfig.apply {
            titleBarMode = preferences.titleBarMode
            readBarStyleFollowPage = preferences.readBarStyleFollowPage
            readBarStyle = preferences.readBarStyle
            progressBarBehavior = preferences.progressBarBehavior
            expandTextMenu = preferences.expandTextMenu
            showSelectMenuIcon = preferences.showSelectMenuIcon
            textSelectMenuFilter = preferences.textSelectMenuFilter
            showReadTitleAddition = preferences.showReadTitleAddition
            showMenuIcon = preferences.showMenuIcon
            clickActionTL = preferences.clickActionTL
            clickActionTC = preferences.clickActionTC
            clickActionTR = preferences.clickActionTR
            clickActionML = preferences.clickActionML
            clickActionMC = preferences.clickActionMC
            clickActionMR = preferences.clickActionMR
            clickActionBL = preferences.clickActionBL
            clickActionBC = preferences.clickActionBC
            clickActionBR = preferences.clickActionBR
        }
        // 交互
        ReadInteractionConfig.apply {
            mouseWheelPage = preferences.mouseWheelPage
            volumeKeyPage = preferences.volumeKeyPage
            volumeKeyPageOnPlay = preferences.volumeKeyPageOnPlay
            keyPageOnLongPress = preferences.keyPageOnLongPress
            sliderVibrator = preferences.sliderVibrator
            selectVibrator = preferences.selectVibrator
            disableReturnKey = preferences.disableReturnKey
        }
        // 数据/屏幕
        ReadDataConfig.apply {
            keepLight = preferences.keepLight
            screenOrientation = preferences.screenOrientation
            autoChangeSource = preferences.autoChangeSource
            defaultSourceChangeAll = preferences.defaultSourceChangeAll
            tocUiUseReplace = preferences.tocUiUseReplace
            tocCountWords = preferences.tocCountWords
        }
    }
}
