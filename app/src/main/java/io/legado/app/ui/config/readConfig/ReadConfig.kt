package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object ReadConfig {

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
        true
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

    var prevKeys by prefDelegate(
        PreferKey.prevKeys,
        ""
    )

    var nextKeys by prefDelegate(
        PreferKey.nextKeys,
        ""
    )
}
