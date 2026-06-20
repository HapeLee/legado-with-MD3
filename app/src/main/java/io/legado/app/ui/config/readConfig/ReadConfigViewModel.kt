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
                    ReadConfig.screenOrientation = intent.value
                    readSettingsRepository.setScreenOrientation(intent.value)
                }

                is ReadConfigIntent.KeepLightChanged -> {
                    ReadConfig.keepLight = intent.value
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
                    ReadConfig.paddingDisplayCutouts = intent.value
                    readSettingsRepository.setPaddingDisplayCutouts(intent.value)
                }

                is ReadConfigIntent.TitleBarModeChanged -> {
                    ReadConfig.titleBarMode = intent.value
                    readSettingsRepository.setTitleBarMode(intent.value)
                }

                is ReadConfigIntent.ReadMenuBlurAlphaChanged -> {
                    ReadBookConfig.readMenuBlurAlpha = intent.value
                    readSettingsRepository.setReadMenuBlurAlpha(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.ReadBodyToLhChanged -> {
                    ReadBookConfig.readBodyToLh = intent.value
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
                    ReadConfig.adaptSpecialStyle = intent.value
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

                is ReadConfigIntent.BrightnessVwPosChanged -> {
                    readSettingsRepository.setBrightnessVwPos(intent.value)
                }

                is ReadConfigIntent.UseUnderlineChanged -> {
                    ReadConfig.useUnderline = intent.value
                    readSettingsRepository.setUseUnderline(intent.value)
                }

                is ReadConfigIntent.ReadSliderModeChanged -> {
                    ReadBookConfig.readSliderMode = intent.value
                    readSettingsRepository.setReadSliderMode(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.DoubleHorizontalPageChanged -> {
                    ReadConfig.doubleHorizontalPage = intent.value
                    readSettingsRepository.setDoubleHorizontalPage(intent.value)
                    upLayout()
                }

                is ReadConfigIntent.ProgressBarBehaviorChanged -> {
                    ReadConfig.progressBarBehavior = intent.value
                    readSettingsRepository.setProgressBarBehavior(intent.value)
                    postEvent(EventBus.UP_SEEK_BAR, true)
                }

                is ReadConfigIntent.MouseWheelPageChanged -> {
                    ReadConfig.mouseWheelPage = intent.value
                    readSettingsRepository.setMouseWheelPage(intent.value)
                }

                is ReadConfigIntent.VolumeKeyPageChanged -> {
                    ReadConfig.volumeKeyPage = intent.value
                    readSettingsRepository.setVolumeKeyPage(intent.value)
                }

                is ReadConfigIntent.VolumeKeyPageOnPlayChanged -> {
                    ReadConfig.volumeKeyPageOnPlay = intent.value
                    readSettingsRepository.setVolumeKeyPageOnPlay(intent.value)
                }

                is ReadConfigIntent.KeyPageOnLongPressChanged -> {
                    ReadConfig.keyPageOnLongPress = intent.value
                    readSettingsRepository.setKeyPageOnLongPress(intent.value)
                }

                is ReadConfigIntent.PageTouchSlopChanged -> {
                    readSettingsRepository.setPageTouchSlop(intent.value)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                }

                is ReadConfigIntent.SliderVibratorChanged -> {
                    ReadConfig.sliderVibrator = intent.value
                    readSettingsRepository.setSliderVibrator(intent.value)
                }

                is ReadConfigIntent.SelectVibratorChanged -> {
                    ReadConfig.selectVibrator = intent.value
                    readSettingsRepository.setSelectVibrator(intent.value)
                }

                is ReadConfigIntent.AutoChangeSourceChanged -> {
                    ReadConfig.autoChangeSource = intent.value
                    readSettingsRepository.setAutoChangeSource(intent.value)
                }

                is ReadConfigIntent.SelectTextChanged -> {
                    ReadConfig.selectText = intent.value
                    readSettingsRepository.setSelectText(intent.value)
                }

                is ReadConfigIntent.NoAnimScrollPageChanged -> {
                    readSettingsRepository.setNoAnimScrollPage(intent.value)
                    ReadBook.callBack?.upPageAnim()
                }

                is ReadConfigIntent.ClickImgWayChanged -> {
                    ReadConfig.clickImgWay = intent.value
                    readSettingsRepository.setClickImgWay(intent.value)
                }

                is ReadConfigIntent.OptimizeRenderChanged -> {
                    ReadConfig.optimizeRender = intent.value
                    readSettingsRepository.setOptimizeRender(intent.value)
                    upStyle()
                }

                is ReadConfigIntent.DisableReturnKeyChanged -> {
                    ReadConfig.disableReturnKey = intent.value
                    readSettingsRepository.setDisableReturnKey(intent.value)
                }

                is ReadConfigIntent.ExpandTextMenuChanged -> {
                    ReadConfig.expandTextMenu = intent.value
                    readSettingsRepository.setExpandTextMenu(intent.value)
                }

                is ReadConfigIntent.ShowSelectMenuIconChanged -> {
                    ReadConfig.showSelectMenuIcon = intent.value
                    readSettingsRepository.setShowSelectMenuIcon(intent.value)
                }

                is ReadConfigIntent.TextSelectMenuFilterChanged -> {
                    ReadConfig.textSelectMenuFilter = intent.value
                    readSettingsRepository.setTextSelectMenuFilter(intent.value)
                }

                is ReadConfigIntent.ShowReadTitleAdditionChanged -> {
                    ReadConfig.showReadTitleAddition = intent.value
                    readSettingsRepository.setShowReadTitleAddition(intent.value)
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                is ReadConfigIntent.ShowMenuIconChanged -> {
                    ReadConfig.showMenuIcon = intent.value
                    readSettingsRepository.setShowMenuIcon(intent.value)
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
            readMenuBlurAlpha = readMenuBlurAlpha,
            readBodyToLh = readBodyToLh,
            defaultSourceChangeAll = defaultSourceChangeAll,
            textFullJustify = textFullJustify,
            textBottomJustify = textBottomJustify,
            adaptSpecialStyle = adaptSpecialStyle,
            useZhLayout = useZhLayout,
            showBrightnessView = showBrightnessView,
            brightnessVwPos = brightnessVwPos,
            brightnessAuto = brightnessAuto,
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
            showSelectMenuIcon = showSelectMenuIcon,
            textSelectMenuFilter = textSelectMenuFilter,
            showReadTitleAddition = showReadTitleAddition,
            autoReadSpeed = autoReadSpeed,
            prevKeys = prevKeys,
            nextKeys = nextKeys,
            showMenuIcon = showMenuIcon
        )
    }
}
