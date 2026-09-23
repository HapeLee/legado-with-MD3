package io.legado.app.feature.settings.readconfig

import androidx.compose.runtime.Stable

/**
 * 护眼模式设置，来源是 `ThemeSettings`，与外观设置共用同一份值。
 *
 * M5-11a：从 `:app` 的 `ui/book/read/ReadBookContract.kt` 迁来（**逐字**，含 `configured`
 * 派生字段）。它同时被**阅读器**与**阅读设置页**使用，而两者分属 `:app` 与 `:feature:settings`
 * ⇒ 必须落在共享层。本片放在设置页契约里（与 [ReadConfigUiState] 同文件），阅读器侧改为
 * import 本文件 —— 与 M5-9b 里 `:app` 的 `HomeScreen` 改 import 特征模块的
 * `BackupOptionSheet` 是同一处境。
 *
 * ⚠️ **临时归属**：护眼本质属于「外观」，阅读菜单只是入口之一。将来若有 `:feature:reader`，
 * 这类「读者与设置共用」的 UI 状态应重新划分归属。
 */
@Stable
data class EyeProtectionUiState(
    val enabled: Boolean = false,
    val intensity: Int = 50,
    val autoNight: Boolean = false,
    val schedule: Boolean = false,
    val startTime: String = "22:00",
    val endTime: String = "07:00",
) {
    val configured: Boolean
        get() = enabled || autoNight
}

@Stable
data class ReadConfigUiState(
    val screenOrientation: String = "0",
    val keepLight: String = "0",
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val paddingDisplayCutouts: Boolean = false,
    val titleBarMode: String = "1",
    val readMenuBlurAlpha: Int = 85,
    val readBodyToLh: Boolean = true,
    val defaultSourceChangeAll: Boolean = true,
    val textFullJustify: Boolean = true,
    val textBottomJustify: Boolean = true,
    val adaptSpecialStyle: Boolean = true,
    val useZhLayout: Boolean = false,
    val eyeProtection: EyeProtectionUiState = EyeProtectionUiState(),
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
    val useNewTocSheet: Boolean = true,
    val maxLengthWithNoToc: Int = 3000,
    val selectVibrator: Boolean = false,
    val autoChangeSource: Boolean = true,
    val autoSuggestDayNight: Boolean = false,
    val readingAnchorEnabled: Boolean = true,
    val readAloudDetachReminderEnabled: Boolean = false,
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
    data class EyeProtectionScheduleChanged(val value: Boolean) : ReadConfigIntent
    data class EyeProtectionStartTimeChanged(val value: String) : ReadConfigIntent
    data class EyeProtectionEndTimeChanged(val value: String) : ReadConfigIntent
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
    data class UseNewTocSheetChanged(val value: Boolean) : ReadConfigIntent
    data class MaxLengthWithNoTocChanged(val value: Int) : ReadConfigIntent
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
    data class ReadingAnchorChanged(val value: Boolean) : ReadConfigIntent
    data class ReadAloudDetachReminderChanged(val value: Boolean) : ReadConfigIntent
    data class PageKeysChanged(val prevKeys: String, val nextKeys: String) : ReadConfigIntent
}

sealed interface ReadConfigEffect {
    data class SettingsUpdateFailed(val message: String) : ReadConfigEffect
}
