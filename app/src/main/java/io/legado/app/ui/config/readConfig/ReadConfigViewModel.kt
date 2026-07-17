package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import io.legado.app.constant.EventBus
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent

class ReadConfigViewModel(
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    fun onIntent(intent: ReadConfigIntent) {
        when (intent) {
            is ReadConfigIntent.ScreenOrientationChanged -> {
                localPreferencesRepository.updateSettings { it.copy(screenOrientation = intent.value) }
            }

            is ReadConfigIntent.KeepLightChanged -> {
                localPreferencesRepository.updateSettings { it.copy(keepLight = intent.value) }
            }

            is ReadConfigIntent.HideStatusBarChanged -> {
                localPreferencesRepository.updateSettings { it.copy(hideStatusBar = intent.value) }
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            is ReadConfigIntent.HideNavigationBarChanged -> {
                localPreferencesRepository.updateSettings { it.copy(hideNavigationBar = intent.value) }
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            is ReadConfigIntent.PaddingDisplayCutoutsChanged -> {
                localPreferencesRepository.updateSettings { it.copy(paddingDisplayCutouts = intent.value) }
            }

            is ReadConfigIntent.TitleBarModeChanged -> {
                localPreferencesRepository.updateSettings { it.copy(titleBarMode = intent.value) }
            }

            is ReadConfigIntent.ReadMenuBlurAlphaChanged -> {
                localPreferencesRepository.updateSettings { it.copy(readMenuBlurAlpha = intent.value) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.ReadBodyToLhChanged -> {
                localPreferencesRepository.updateSettings { it.copy(readBodyToLh = intent.value) }
            }

            is ReadConfigIntent.DefaultSourceChangeAllChanged -> {
                localPreferencesRepository.updateSettings { it.copy(defaultSourceChangeAll = intent.value) }
            }

            is ReadConfigIntent.TextFullJustifyChanged -> {
                localPreferencesRepository.updateSettings { it.copy(textFullJustify = intent.value) }
                upLayout()
            }

            is ReadConfigIntent.TextBottomJustifyChanged -> {
                localPreferencesRepository.updateSettings { it.copy(textBottomJustify = intent.value) }
                upLayout()
            }

            is ReadConfigIntent.AdaptSpecialStyleChanged -> {
                localPreferencesRepository.updateSettings { it.copy(adaptSpecialStyle = intent.value) }
            }

            is ReadConfigIntent.UseZhLayoutChanged -> {
                localPreferencesRepository.updateSettings { it.copy(useZhLayout = intent.value) }
                upLayout()
            }

            is ReadConfigIntent.ShowBrightnessViewChanged -> {
                localPreferencesRepository.updateSettings { it.copy(showBrightnessView = intent.value) }
            }

            is ReadConfigIntent.BrightnessVwPosChanged -> {
                localPreferencesRepository.updateSettings { it.copy(brightnessVwPos = intent.value) }
            }

            is ReadConfigIntent.UseUnderlineChanged -> {
                localPreferencesRepository.updateSettings { it.copy(useUnderline = intent.value) }
            }

            is ReadConfigIntent.ReadSliderModeChanged -> {
                localPreferencesRepository.updateSettings { it.copy(readSliderMode = intent.value) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.DoubleHorizontalPageChanged -> {
                localPreferencesRepository.updateSettings { it.copy(doubleHorizontalPage = intent.value) }
                upLayout()
            }

            is ReadConfigIntent.ProgressBarBehaviorChanged -> {
                localPreferencesRepository.updateSettings { it.copy(progressBarBehavior = intent.value) }
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            is ReadConfigIntent.MouseWheelPageChanged -> {
                localPreferencesRepository.updateSettings { it.copy(mouseWheelPage = intent.value) }
            }

            is ReadConfigIntent.VolumeKeyPageChanged -> {
                localPreferencesRepository.updateSettings { it.copy(volumeKeyPage = intent.value) }
            }

            is ReadConfigIntent.VolumeKeyPageOnPlayChanged -> {
                localPreferencesRepository.updateSettings { it.copy(volumeKeyPageOnPlay = intent.value) }
            }

            is ReadConfigIntent.KeyPageOnLongPressChanged -> {
                localPreferencesRepository.updateSettings { it.copy(keyPageOnLongPress = intent.value) }
            }

            is ReadConfigIntent.PageTouchSlopChanged -> {
                localPreferencesRepository.updateSettings { it.copy(pageTouchSlop = intent.value) }
                postEvent(EventBus.UP_CONFIG, arrayListOf(4))
            }

            is ReadConfigIntent.SliderVibratorChanged -> {
                localPreferencesRepository.updateSettings { it.copy(sliderVibrator = intent.value) }
            }

            is ReadConfigIntent.SelectVibratorChanged -> {
                localPreferencesRepository.updateSettings { it.copy(selectVibrator = intent.value) }
            }

            is ReadConfigIntent.AutoChangeSourceChanged -> {
                localPreferencesRepository.updateSettings { it.copy(autoChangeSource = intent.value) }
            }

            is ReadConfigIntent.AutoSuggestDayNightChanged -> {
                localPreferencesRepository.updateSettings { it.copy(autoSuggestDayNight = intent.value) }
            }

            is ReadConfigIntent.SelectTextChanged -> {
                localPreferencesRepository.updateSettings { it.copy(selectText = intent.value) }
            }

            is ReadConfigIntent.NoAnimScrollPageChanged -> {
                localPreferencesRepository.updateSettings { it.copy(noAnimScrollPage = intent.value) }
                ReadBook.callBack?.upPageAnim()
            }

            is ReadConfigIntent.ClickImgWayChanged -> {
                localPreferencesRepository.updateSettings { it.copy(clickImgWay = intent.value) }
            }

            is ReadConfigIntent.OptimizeRenderChanged -> {
                localPreferencesRepository.updateSettings { it.copy(optimizeRender = intent.value) }
                upStyle()
            }

            is ReadConfigIntent.DisableReturnKeyChanged -> {
                localPreferencesRepository.updateSettings { it.copy(disableReturnKey = intent.value) }
            }

            is ReadConfigIntent.ShowReadTitleAdditionChanged -> {
                localPreferencesRepository.updateSettings { it.copy(showReadTitleAddition = intent.value) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.ShowMenuIconChanged -> {
                localPreferencesRepository.updateSettings { it.copy(showMenuIcon = intent.value) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.PageKeysChanged -> {
                localPreferencesRepository.updateSettings {
                    it.copy(prevKeys = intent.prevKeys, nextKeys = intent.nextKeys)
                }
            }
        }
    }

    private fun upLayout() {
        ChapterProvider.upLayout()
        ReadBook.loadContent(false)
    }

    private fun upStyle() {
        ChapterProvider.upStyle()
        ReadBook.callBack?.upPageAnim(true)
        ReadBook.loadContent(false)
    }
}
