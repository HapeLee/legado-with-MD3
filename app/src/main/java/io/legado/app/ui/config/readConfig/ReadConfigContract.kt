package io.legado.app.ui.config.readConfig

import androidx.compose.runtime.Stable

@Stable
data class ReadConfigUiState(
    val screenOrientation: String = "0",
    val keepLight: String = "0",
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val paddingDisplayCutouts: Boolean = false,
    val titleBarMode: String = "1",
    val readMenuBlurAlpha: Int = 100,
    val readBodyToLh: Boolean = true,
    val defaultSourceChangeAll: Boolean = true,
    val textFullJustify: Boolean = true,
    val textBottomJustify: Boolean = true,
    val adaptSpecialStyle: Boolean = true,
    val useZhLayout: Boolean = false,
    val eyeProtectionEnabled: Boolean = false,
    val eyeProtectionIntensity: Int = 50,
    val eyeProtectionAutoNight: Boolean = false,
    val showBrightnessView: String = "0",
    val brightnessVwPos: String = "1",
    val brightnessAuto: Boolean = true,
    val useUnderline: Boolean = false,
    val readSliderMode: String = "0",
    val doubleHorizontalPage: String = "0",
    val progressBarBehavior: String = "page",
    val mouseWheelPage: Boolean = true,
    val volumeKeyPage: Boolean = true,
    val volumeKeyPageOnPlay: Boolean = true,
    val keyPageOnLongPress: Boolean = false,
    val pageTouchSlop: Int = 0,
    val sliderVibrator: Boolean = false,
    val selectVibrator: Boolean = false,
    val autoChangeSource: Boolean = true,
    val autoSuggestDayNight: Boolean = false,
    val selectText: Boolean = true,
    val noAnimScrollPage: Boolean = false,
    val clickImgWay: String = "2",
    val optimizeRender: Boolean = false,
    val disableReturnKey: Boolean = false,
    val expandTextMenu: Boolean = false,
    val showSelectMenuIcon: Boolean = true,
    val showReadTitleAddition: Boolean = true,
    val autoReadSpeed: Int = 10,
    val prevKeys: String = "",
    val nextKeys: String = "",
    val showMenuIcon: Boolean = false,
    val activeSheet: ReadConfigSheet? = null,
)

sealed interface ReadConfigSheet {
    data object PageKeys : ReadConfigSheet
    data object ClickActions : ReadConfigSheet
    data object EyeProtection : ReadConfigSheet
}

sealed interface ReadConfigIntent {
    data object OpenPageKeys : ReadConfigIntent
    data object OpenClickActions : ReadConfigIntent
    data object DismissSheet : ReadConfigIntent
    data class ScreenOrientationChanged(val value: String) : ReadConfigIntent
    data class KeepLightChanged(val value: String) : ReadConfigIntent
    data class HideStatusBarChanged(val value: Boolean) : ReadConfigIntent
    data class HideNavigationBarChanged(val value: Boolean) : ReadConfigIntent
    data class PaddingDisplayCutoutsChanged(val value: Boolean) : ReadConfigIntent
    data class TitleBarModeChanged(val value: String) : ReadConfigIntent
    data class ReadMenuBlurAlphaChanged(val value: Int) : ReadConfigIntent
    data class ReadBodyToLhChanged(val value: Boolean) : ReadConfigIntent
    data class DefaultSourceChangeAllChanged(val value: Boolean) : ReadConfigIntent
    data class TextFullJustifyChanged(val value: Boolean) : ReadConfigIntent
    data class TextBottomJustifyChanged(val value: Boolean) : ReadConfigIntent
    data class AdaptSpecialStyleChanged(val value: Boolean) : ReadConfigIntent
    data class UseZhLayoutChanged(val value: Boolean) : ReadConfigIntent
    data object OpenEyeProtection : ReadConfigIntent
    data class EyeProtectionEnabledChanged(val value: Boolean) : ReadConfigIntent
    data class EyeProtectionIntensityChanged(val value: Int) : ReadConfigIntent
    data class EyeProtectionAutoNightChanged(val value: Boolean) : ReadConfigIntent
    data class ShowBrightnessViewChanged(val value: String) : ReadConfigIntent
    data class BrightnessVwPosChanged(val value: String) : ReadConfigIntent
    data class UseUnderlineChanged(val value: Boolean) : ReadConfigIntent
    data class ReadSliderModeChanged(val value: String) : ReadConfigIntent
    data class DoubleHorizontalPageChanged(val value: String) : ReadConfigIntent
    data class ProgressBarBehaviorChanged(val value: String) : ReadConfigIntent
    data class MouseWheelPageChanged(val value: Boolean) : ReadConfigIntent
    data class VolumeKeyPageChanged(val value: Boolean) : ReadConfigIntent
    data class VolumeKeyPageOnPlayChanged(val value: Boolean) : ReadConfigIntent
    data class KeyPageOnLongPressChanged(val value: Boolean) : ReadConfigIntent
    data class PageTouchSlopChanged(val value: Int) : ReadConfigIntent
    data class SliderVibratorChanged(val value: Boolean) : ReadConfigIntent
    data class SelectVibratorChanged(val value: Boolean) : ReadConfigIntent
    data class AutoChangeSourceChanged(val value: Boolean) : ReadConfigIntent
    data class SelectTextChanged(val value: Boolean) : ReadConfigIntent
    data class NoAnimScrollPageChanged(val value: Boolean) : ReadConfigIntent
    data class ClickImgWayChanged(val value: String) : ReadConfigIntent
    data class OptimizeRenderChanged(val value: Boolean) : ReadConfigIntent
    data class DisableReturnKeyChanged(val value: Boolean) : ReadConfigIntent
    data class ShowReadTitleAdditionChanged(val value: Boolean) : ReadConfigIntent
    data class ShowMenuIconChanged(val value: Boolean) : ReadConfigIntent
    data class AutoSuggestDayNightChanged(val value: Boolean) : ReadConfigIntent
    data class PageKeysChanged(val prevKeys: String, val nextKeys: String) : ReadConfigIntent
}

sealed interface ReadConfigEffect {
    data class SettingsUpdateFailed(val message: String) : ReadConfigEffect
}
