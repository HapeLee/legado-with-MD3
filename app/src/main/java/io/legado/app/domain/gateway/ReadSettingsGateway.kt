package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.Flow

interface ReadSettingsGateway {
    val currentSettings: ReadSettings
    val settings: Flow<ReadSettings>

    suspend fun update(update: ReadSettingsUpdate)
}

sealed interface ReadSettingsUpdate {
    data class ScreenOrientation(val value: String) : ReadSettingsUpdate
    data class KeepLight(val value: String) : ReadSettingsUpdate
    data class HideStatusBar(val value: Boolean) : ReadSettingsUpdate
    data class HideNavigationBar(val value: Boolean) : ReadSettingsUpdate
    data class PaddingDisplayCutouts(val value: Boolean) : ReadSettingsUpdate
    data class TitleBarMode(val value: String) : ReadSettingsUpdate
    data class ReadMenuBlurAlpha(val value: Int) : ReadSettingsUpdate
    data class ReadBodyToLh(val value: Boolean) : ReadSettingsUpdate
    data class DefaultSourceChangeAll(val value: Boolean) : ReadSettingsUpdate
    data class TextFullJustify(val value: Boolean) : ReadSettingsUpdate
    data class TextBottomJustify(val value: Boolean) : ReadSettingsUpdate
    data class AdaptSpecialStyle(val value: Boolean) : ReadSettingsUpdate
    data class UseZhLayout(val value: Boolean) : ReadSettingsUpdate
    data class ShowBrightnessView(val value: String) : ReadSettingsUpdate
    data class BrightnessVwPos(val value: String) : ReadSettingsUpdate
    data class UseUnderline(val value: Boolean) : ReadSettingsUpdate
    data class ReadSliderMode(val value: String) : ReadSettingsUpdate
    data class DoubleHorizontalPage(val value: String) : ReadSettingsUpdate
    data class ProgressBarBehavior(val value: String) : ReadSettingsUpdate
    data class MouseWheelPage(val value: Boolean) : ReadSettingsUpdate
    data class VolumeKeyPage(val value: Boolean) : ReadSettingsUpdate
    data class VolumeKeyPageOnPlay(val value: Boolean) : ReadSettingsUpdate
    data class KeyPageOnLongPress(val value: Boolean) : ReadSettingsUpdate
    data class PageTouchSlop(val value: Int) : ReadSettingsUpdate
    data class SliderVibrator(val value: Boolean) : ReadSettingsUpdate
    data class SelectVibrator(val value: Boolean) : ReadSettingsUpdate
    data class AutoChangeSource(val value: Boolean) : ReadSettingsUpdate
    data class AutoSuggestDayNight(val value: Boolean) : ReadSettingsUpdate
    data class SelectText(val value: Boolean) : ReadSettingsUpdate
    data class NoAnimScrollPage(val value: Boolean) : ReadSettingsUpdate
    data class ClickImgWay(val value: String) : ReadSettingsUpdate
    data class OptimizeRender(val value: Boolean) : ReadSettingsUpdate
    data class DisableReturnKey(val value: Boolean) : ReadSettingsUpdate
    data class ShowReadTitleAddition(val value: Boolean) : ReadSettingsUpdate
    data class ShowMenuIcon(val value: Boolean) : ReadSettingsUpdate
    data class PageKeys(val previous: String, val next: String) : ReadSettingsUpdate
    data class FontFolder(val value: String) : ReadSettingsUpdate
}
