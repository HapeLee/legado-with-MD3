package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.postEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadConfigViewModel(
    private val readSettingsRepository: ReadSettingsRepository
) : ViewModel() {

    val uiState = readSettingsRepository.preferences.map { it.toUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReadConfigUiState()
    )

    fun onIntent(intent: ReadConfigIntent) {
        viewModelScope.launch {
            when (intent) {
                is ReadConfigIntent.ScreenOrientationChanged -> {
                    readSettingsRepository.setScreenOrientation(intent.value)
                }

                is ReadConfigIntent.KeepLightChanged -> {
                    readSettingsRepository.setKeepLight(intent.value)
                }

                is ReadConfigIntent.HideStatusBarChanged -> {
                    readSettingsRepository.setHideStatusBar(intent.value)
                    ReadBookConfig.hideStatusBar = intent.value
                    postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                }

                is ReadConfigIntent.HideNavigationBarChanged -> {
                    readSettingsRepository.setHideNavigationBar(intent.value)
                    ReadBookConfig.hideNavigationBar = intent.value
                    postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                }

                is ReadConfigIntent.PaddingDisplayCutoutsChanged -> {
                    readSettingsRepository.setPaddingDisplayCutouts(intent.value)
                }

                is ReadConfigIntent.TitleBarModeChanged -> {
                    readSettingsRepository.setTitleBarMode(intent.value)
                }

                is ReadConfigIntent.MenuAlphaChanged -> {
                    readSettingsRepository.setMenuAlpha(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.ReadBodyToLhChanged -> {
                    readSettingsRepository.setReadBodyToLh(intent.value)
                }

                is ReadConfigIntent.DefaultSourceChangeAllChanged -> {
                    readSettingsRepository.setDefaultSourceChangeAll(intent.value)
                }

                is ReadConfigIntent.TextFullJustifyChanged -> {
                    readSettingsRepository.setTextFullJustify(intent.value)
                    upLayout()
                }

                is ReadConfigIntent.TextBottomJustifyChanged -> {
                    readSettingsRepository.setTextBottomJustify(intent.value)
                    upLayout()
                }

                is ReadConfigIntent.AdaptSpecialStyleChanged -> {
                    readSettingsRepository.setAdaptSpecialStyle(intent.value)
                }

                is ReadConfigIntent.UseZhLayoutChanged -> {
                    readSettingsRepository.setUseZhLayout(intent.value)
                    ReadBookConfig.useZhLayout = intent.value
                    upLayout()
                }

                is ReadConfigIntent.ShowBrightnessViewChanged -> {
                    readSettingsRepository.setShowBrightnessView(intent.value)
                }

                is ReadConfigIntent.UseUnderlineChanged -> {
                    readSettingsRepository.setUseUnderline(intent.value)
                }

                is ReadConfigIntent.ReadSliderModeChanged -> {
                    readSettingsRepository.setReadSliderMode(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.DoubleHorizontalPageChanged -> {
                    readSettingsRepository.setDoubleHorizontalPage(intent.value)
                    upLayout()
                }

                is ReadConfigIntent.ProgressBarBehaviorChanged -> {
                    readSettingsRepository.setProgressBarBehavior(intent.value)
                    postEvent(EventBus.UP_SEEK_BAR, true)
                }

                is ReadConfigIntent.MouseWheelPageChanged -> {
                    readSettingsRepository.setMouseWheelPage(intent.value)
                }

                is ReadConfigIntent.VolumeKeyPageChanged -> {
                    readSettingsRepository.setVolumeKeyPage(intent.value)
                }

                is ReadConfigIntent.VolumeKeyPageOnPlayChanged -> {
                    readSettingsRepository.setVolumeKeyPageOnPlay(intent.value)
                }

                is ReadConfigIntent.KeyPageOnLongPressChanged -> {
                    readSettingsRepository.setKeyPageOnLongPress(intent.value)
                }

                is ReadConfigIntent.PageTouchSlopChanged -> {
                    readSettingsRepository.setPageTouchSlop(intent.value)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                }

                is ReadConfigIntent.SliderVibratorChanged -> {
                    readSettingsRepository.setSliderVibrator(intent.value)
                }

                is ReadConfigIntent.SelectVibratorChanged -> {
                    readSettingsRepository.setSelectVibrator(intent.value)
                }

                is ReadConfigIntent.AutoChangeSourceChanged -> {
                    readSettingsRepository.setAutoChangeSource(intent.value)
                }

                is ReadConfigIntent.SelectTextChanged -> {
                    readSettingsRepository.setSelectText(intent.value)
                }

                is ReadConfigIntent.NoAnimScrollPageChanged -> {
                    readSettingsRepository.setNoAnimScrollPage(intent.value)
                    ReadBook.callBack?.upPageAnim()
                }

                is ReadConfigIntent.ClickImgWayChanged -> {
                    readSettingsRepository.setClickImgWay(intent.value)
                }

                is ReadConfigIntent.OptimizeRenderChanged -> {
                    readSettingsRepository.setOptimizeRender(intent.value)
                    upStyle()
                }

                is ReadConfigIntent.DisableReturnKeyChanged -> {
                    readSettingsRepository.setDisableReturnKey(intent.value)
                }

                is ReadConfigIntent.ExpandTextMenuChanged -> {
                    readSettingsRepository.setExpandTextMenu(intent.value)
                }

                is ReadConfigIntent.ShowReadTitleAdditionChanged -> {
                    readSettingsRepository.setShowReadTitleAddition(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.PageKeysChanged -> {
                    readSettingsRepository.setPageKeys(intent.prevKeys, intent.nextKeys)
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

    private fun ReadPreferences.toUiState(): ReadConfigUiState {
        return ReadConfigUiState(
            screenOrientation = screenOrientation,
            keepLight = keepLight,
            hideStatusBar = hideStatusBar,
            hideNavigationBar = hideNavigationBar,
            paddingDisplayCutouts = paddingDisplayCutouts,
            titleBarMode = titleBarMode,
            menuAlpha = menuAlpha,
            readBodyToLh = readBodyToLh,
            defaultSourceChangeAll = defaultSourceChangeAll,
            textFullJustify = textFullJustify,
            textBottomJustify = textBottomJustify,
            adaptSpecialStyle = adaptSpecialStyle,
            useZhLayout = useZhLayout,
            showBrightnessView = showBrightnessView,
            useUnderline = useUnderline,
            readSliderMode = readSliderMode,
            doubleHorizontalPage = doubleHorizontalPage,
            progressBarBehavior = progressBarBehavior,
            mouseWheelPage = mouseWheelPage,
            volumeKeyPage = volumeKeyPage,
            volumeKeyPageOnPlay = volumeKeyPageOnPlay,
            keyPageOnLongPress = keyPageOnLongPress,
            pageTouchSlop = pageTouchSlop,
            sliderVibrator = sliderVibrator,
            selectVibrator = selectVibrator,
            autoChangeSource = autoChangeSource,
            selectText = selectText,
            noAnimScrollPage = noAnimScrollPage,
            clickImgWay = clickImgWay,
            optimizeRender = optimizeRender,
            disableReturnKey = disableReturnKey,
            expandTextMenu = expandTextMenu,
            showReadTitleAddition = showReadTitleAddition,
            autoReadSpeed = autoReadSpeed,
            prevKeys = prevKeys,
            nextKeys = nextKeys
        )
    }
}
