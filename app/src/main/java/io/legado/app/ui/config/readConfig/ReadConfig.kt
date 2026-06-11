package io.legado.app.ui.config.readConfig

import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.ui.config.prefDelegate
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration

object ReadConfig {

    val isEInkMode: Boolean
        get() = ThemeConfig.appTheme == "4"

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

    var useAntiAlias by prefDelegate(
        PreferKey.antiAlias,
        false
    )

    val enableReview: Boolean
        get() = BuildConfig.DEBUG && enableReviewPref

    private var enableReviewPref by prefDelegate(
        PreferKey.enableReview,
        false
    )

    var systemTypefaces by prefDelegate(
        PreferKey.systemTypefaces,
        0
    )

    var readUrlInBrowser by prefDelegate(
        PreferKey.readUrlOpenInBrowser,
        false
    )

    var contentSelectSpeakMod by prefDelegate(
        PreferKey.contentSelectSpeakMod,
        0
    )

    var syncBookProgress by prefDelegate(
        PreferKey.syncBookProgress,
        true
    )

    var syncBookProgressPlus by prefDelegate(
        PreferKey.syncBookProgressPlus,
        false
    )

    var preDownloadNum by prefDelegate(
        PreferKey.preDownloadNum,
        10
    )

    var imageRetainNum by prefDelegate(
        PreferKey.imageRetainNum,
        0
    )

    var audioCacheCleanTimeOrgin by prefDelegate(
        PreferKey.audioCacheCleanTime,
        10
    )

    val audioCacheCleanTime: Long
        get() = audioCacheCleanTimeOrgin * 60 * 1000L

    var audioPreDownloadNum by prefDelegate(
        PreferKey.audioPreDownloadNum,
        10
    )

    private const val defaultSpeechRate = 5

    val speechRatePlay: Int
        get() = if (ttsFollowSys) defaultSpeechRate else ttsSpeechRate

    fun syncReadPreferences(preferences: ReadPreferences) {
        optimizeRender = preferences.optimizeRender
        adaptSpecialStyle = preferences.adaptSpecialStyle
        useUnderline = preferences.useUnderline
        brightnessVwPos = preferences.brightnessVwPos
        readBrightness = preferences.readBrightness
        brightnessAuto = preferences.brightnessAuto
        chineseConverterType = preferences.chineseConverterType
        clickActionTL = preferences.clickActionTL
        clickActionTC = preferences.clickActionTC
        clickActionTR = preferences.clickActionTR
        clickActionML = preferences.clickActionML
        clickActionMC = preferences.clickActionMC
        clickActionMR = preferences.clickActionMR
        clickActionBL = preferences.clickActionBL
        clickActionBC = preferences.clickActionBC
        clickActionBR = preferences.clickActionBR
        screenOrientation = preferences.screenOrientation
        noAnimScrollPage = preferences.noAnimScrollPage
        tocUiUseReplace = preferences.tocUiUseReplace
        tocCountWords = preferences.tocCountWords
        autoChangeSource = preferences.autoChangeSource
        clickImgWay = preferences.clickImgWay
        doubleHorizontalPage = preferences.doubleHorizontalPage
        progressBarBehavior = preferences.progressBarBehavior
        keyPageOnLongPress = preferences.keyPageOnLongPress
        volumeKeyPage = preferences.volumeKeyPage
        volumeKeyPageOnPlay = preferences.volumeKeyPageOnPlay
        mouseWheelPage = preferences.mouseWheelPage
        paddingDisplayCutouts = preferences.paddingDisplayCutouts
        pageTouchSlop = preferences.pageTouchSlop
        showReadTitleAddition = preferences.showReadTitleAddition
        titleBarMode = preferences.titleBarMode
        menuAlpha = preferences.menuAlpha
        readSliderMode = preferences.readSliderMode
        readBarStyleFollowPage = preferences.readBarStyleFollowPage
        readBarStyle = preferences.readBarStyle
        defaultSourceChangeAll = preferences.defaultSourceChangeAll
        sliderVibrator = preferences.sliderVibrator
        selectVibrator = preferences.selectVibrator
    }

    var tocUiUseReplace by prefDelegate(
        PreferKey.tocUiUseReplace,
        false
    )

    var tocCountWords by prefDelegate(
        PreferKey.tocCountWords,
        true
    )

    var screenOrientation by prefDelegate(
        PreferKey.screenOrientation,
        "0"
    )

    var keepLight by prefDelegate(
        PreferKey.keepLight,
        "0"
    )

    var hideStatusBar by prefDelegate(
        PreferKey.hideStatusBar,
        false
    )

    var hideNavigationBar by prefDelegate(
        PreferKey.hideNavigationBar,
        false
    )

    var paddingDisplayCutouts by prefDelegate(
        PreferKey.paddingDisplayCutouts,
        false
    )

    var titleBarMode by prefDelegate(
        PreferKey.titleBarMode,
        "1"
    )

    var menuAlpha by prefDelegate(
        PreferKey.menuAlpha,
        100
    )

    var readBodyToLh by prefDelegate(
        PreferKey.readBodyToLh,
        true
    )

    var defaultSourceChangeAll by prefDelegate(
        PreferKey.defaultSourceChangeAll,
        true
    )

    var textFullJustify by prefDelegate(
        PreferKey.textFullJustify,
        true
    )

    var textBottomJustify by prefDelegate(
        PreferKey.textBottomJustify,
        true
    )

    var adaptSpecialStyle by prefDelegate(
        PreferKey.adaptSpecialStyle,
        true
    )

    var useZhLayout by prefDelegate(
        PreferKey.useZhLayout,
        false
    )

    var showBrightnessView by prefDelegate(
        PreferKey.showBrightnessView,
        "1"
    )

    var brightnessVwPos by prefDelegate(
        PreferKey.brightnessVwPos,
        "1"
    )

    var readBrightness by prefDelegate(
        PreferKey.brightness,
        100
    )

    var brightnessAuto by prefDelegate(
        PreferKey.brightnessAuto,
        false
    )

    var useUnderline by prefDelegate(
        PreferKey.useUnderline,
        false
    )

    var readSliderMode by prefDelegate(
        PreferKey.readSliderMode,
        "0"
    )

    var doubleHorizontalPage by prefDelegate(
        PreferKey.doublePageHorizontal,
        "0"
    )

    var progressBarBehavior by prefDelegate(
        PreferKey.progressBarBehavior,
        "page"
    )

    var mouseWheelPage by prefDelegate(
        PreferKey.mouseWheelPage,
        true
    )

    var volumeKeyPage by prefDelegate(
        PreferKey.volumeKeyPage,
        true
    )

    var volumeKeyPageOnPlay by prefDelegate(
        PreferKey.volumeKeyPageOnPlay,
        true
    )

    var keyPageOnLongPress by prefDelegate(
        PreferKey.keyPageOnLongPress,
        false
    )

    var pageTouchSlop by prefDelegate(
        PreferKey.pageTouchSlop,
        0
    )

    var sliderVibrator by prefDelegate(
        PreferKey.sliderVibrator,
        false
    )

    var selectVibrator by prefDelegate(
        PreferKey.selectVibrator,
        false
    )

    var autoChangeSource by prefDelegate(
        PreferKey.autoChangeSource,
        true
    )

    var selectText by prefDelegate(
        PreferKey.selectText,
        true
    )

    var noAnimScrollPage by prefDelegate(
        PreferKey.noAnimScrollPage,
        false
    )

    var clickImgWay by prefDelegate(
        PreferKey.clickImgWay,
        "2"
    )

    var optimizeRender by prefDelegate(
        PreferKey.optimizeRender,
        false
    )

    var disableReturnKey by prefDelegate(
        PreferKey.disableReturnKey,
        false
    )

    var expandTextMenu by prefDelegate(
        PreferKey.expandTextMenu,
        false
    )

    var showReadTitleAddition by prefDelegate(
        PreferKey.showReadTitleAddition,
        true
    )

    var clickActionTL by prefDelegate(
        PreferKey.clickActionTL,
        2
    )

    var clickActionTC by prefDelegate(
        PreferKey.clickActionTC,
        2
    )

    var clickActionTR by prefDelegate(
        PreferKey.clickActionTR,
        1
    )

    var clickActionML by prefDelegate(
        PreferKey.clickActionML,
        2
    )

    var clickActionMC by prefDelegate(
        PreferKey.clickActionMC,
        0
    )

    var clickActionMR by prefDelegate(
        PreferKey.clickActionMR,
        1
    )

    var clickActionBL by prefDelegate(
        PreferKey.clickActionBL,
        2
    )

    var clickActionBC by prefDelegate(
        PreferKey.clickActionBC,
        1
    )

    var clickActionBR by prefDelegate(
        PreferKey.clickActionBR,
        1
    )

    fun hasMenuClickArea(): Boolean {
        return clickActionTL * clickActionTC * clickActionTR *
                clickActionML * clickActionMC * clickActionMR *
                clickActionBL * clickActionBC * clickActionBR == 0
    }

    fun detectClickArea() {
        if (!hasMenuClickArea()) {
            clickActionMC = 0
        }
    }

    var readBarStyleFollowPage by prefDelegate(
        PreferKey.readBarStyleFollowPage,
        false
    )

    var readBarStyle by prefDelegate(
        PreferKey.readBarStyle,
        0
    )

    var prevKeys by prefDelegate(
        PreferKey.prevKeys,
        ""
    )

    var nextKeys by prefDelegate(
        PreferKey.nextKeys,
        ""
    )

    // --- Read Aloud ---

    var ignoreAudioFocus by prefDelegate(
        PreferKey.ignoreAudioFocus,
        false
    )

    var pauseReadAloudWhilePhoneCalls by prefDelegate(
        PreferKey.pauseReadAloudWhilePhoneCalls,
        false
    )

    var readAloudWakeLock by prefDelegate(
        PreferKey.readAloudWakeLock,
        false
    )

    var mediaButtonPerNext by prefDelegate(
        "mediaButtonPerNext",
        false
    )

    var readAloudByPage by prefDelegate(
        PreferKey.readAloudByPage,
        false
    )

    var systemMediaControlCompatibilityChange by prefDelegate(
        PreferKey.systemMediaControlCompatibilityChange,
        true
    )

    var streamReadAloudAudio by prefDelegate(
        PreferKey.streamReadAloudAudio,
        false
    )

    var ttsTimer by prefDelegate(
        PreferKey.ttsTimer,
        0
    )

    var ttsFollowSys by prefDelegate(
        PreferKey.ttsFollowSys,
        true
    )

    var ttsSpeechRate by prefDelegate(
        PreferKey.ttsSpeechRate,
        5
    )

    var ttsEngine by prefDelegate<String?>(
        PreferKey.ttsEngine,
        null
    )

    var chineseConverterType by prefDelegate(
        PreferKey.chineseConverterType,
        0
    )
}
