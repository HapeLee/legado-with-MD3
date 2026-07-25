package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.book.read.EyeProtectionUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReadConfigViewModel(
    private val settingsGateway: ReadSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val applyReadSetting: ApplyReadSettingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        settingsGateway.currentSettings.toUiState(
            activeSheet = null,
            eyeProtection = themeSettingsGateway.currentSettings.toEyeProtectionUiState(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update {
                    settings.toUiState(
                        activeSheet = it.activeSheet,
                        eyeProtection = it.eyeProtection,
                    )
                }
            }
        }
        viewModelScope.launch {
            themeSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(eyeProtection = settings.toEyeProtectionUiState()) }
            }
        }
    }

    fun onIntent(intent: ReadConfigIntent) {
        when (intent) {
            ReadConfigIntent.OpenPageKeys -> setSheet(ReadConfigSheet.PageKeys)
            ReadConfigIntent.OpenClickActions -> setSheet(ReadConfigSheet.ClickActions)
            ReadConfigIntent.OpenEyeProtection -> setSheet(ReadConfigSheet.EyeProtection)
            ReadConfigIntent.DismissSheet -> setSheet(null)
            is ReadConfigIntent.EyeProtectionEnabledChanged -> updateEyeProtection {
                it.copy(eyeProtectionEnabled = intent.value)
            }
            is ReadConfigIntent.EyeProtectionIntensityChanged -> updateEyeProtection {
                it.copy(colorTemperature = intent.value.coerceIn(0, 100))
            }
            is ReadConfigIntent.EyeProtectionAutoNightChanged -> updateEyeProtection {
                it.copy(eyeProtectionAutoNight = intent.value)
            }
            is ReadConfigIntent.EyeProtectionScheduleChanged -> updateEyeProtection {
                it.copy(eyeProtectionSchedule = intent.value)
            }
            is ReadConfigIntent.EyeProtectionStartTimeChanged -> updateEyeProtection {
                it.copy(eyeProtectionStartTime = intent.value)
            }
            is ReadConfigIntent.EyeProtectionEndTimeChanged -> updateEyeProtection {
                it.copy(eyeProtectionEndTime = intent.value)
            }
            else -> updateSetting(intent)
        }
    }

    private fun updateEyeProtection(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { themeSettingsGateway.update(transform) }
    }

    private fun setSheet(sheet: ReadConfigSheet?) {
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    private fun updateSetting(intent: ReadConfigIntent) {
        viewModelScope.launch {
            runCatching { settingsGateway.update(intent.toSettingsTransform()) }
                .onSuccess {
                    applyReadSetting(intent)
                    if (intent is ReadConfigIntent.PageKeysChanged) setSheet(null)
                }
                .onFailure { error ->
                    _effects.tryEmit(
                        ReadConfigEffect.SettingsUpdateFailed(
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
        }
    }
}

private fun ReadConfigIntent.toSettingsTransform(): (ReadSettings) -> ReadSettings = when (this) {
    is ReadConfigIntent.ScreenOrientationChanged -> { settings -> settings.copy(screenOrientation = value) }
    is ReadConfigIntent.KeepLightChanged -> { settings -> settings.copy(keepLight = value) }
    is ReadConfigIntent.HideStatusBarChanged -> { settings -> settings.copy(hideStatusBar = value) }
    is ReadConfigIntent.HideNavigationBarChanged -> { settings -> settings.copy(hideNavigationBar = value) }
    is ReadConfigIntent.PaddingDisplayCutoutsChanged -> { settings -> settings.copy(paddingDisplayCutouts = value) }
    is ReadConfigIntent.TitleBarModeChanged -> { settings -> settings.copy(titleBarMode = value) }
    is ReadConfigIntent.ReadMenuBlurAlphaChanged -> { settings -> settings.copy(readMenuBlurAlpha = value) }
    is ReadConfigIntent.ReadBodyToLhChanged -> { settings -> settings.copy(readBodyToLh = value) }
    is ReadConfigIntent.DefaultSourceChangeAllChanged -> { settings -> settings.copy(defaultSourceChangeAll = value) }
    is ReadConfigIntent.TextFullJustifyChanged -> { settings -> settings.copy(textFullJustify = value) }
    is ReadConfigIntent.TextBottomJustifyChanged -> { settings -> settings.copy(textBottomJustify = value) }
    is ReadConfigIntent.AdaptSpecialStyleChanged -> { settings -> settings.copy(adaptSpecialStyle = value) }
    is ReadConfigIntent.UseZhLayoutChanged -> { settings -> settings.copy(useZhLayout = value) }
    is ReadConfigIntent.ShowBrightnessViewChanged -> { settings -> settings.copy(showBrightnessView = value) }
    is ReadConfigIntent.BrightnessVwPosChanged -> { settings -> settings.copy(brightnessVwPos = value) }
    is ReadConfigIntent.UseUnderlineChanged -> { settings -> settings.copy(useUnderline = value) }
    is ReadConfigIntent.ReadSliderModeChanged -> { settings -> settings.copy(readSliderMode = value) }
    is ReadConfigIntent.DoubleHorizontalPageChanged -> { settings -> settings.copy(doubleHorizontalPage = value) }
    is ReadConfigIntent.ProgressBarBehaviorChanged -> { settings -> settings.copy(progressBarBehavior = value) }
    is ReadConfigIntent.MouseWheelPageChanged -> { settings -> settings.copy(mouseWheelPage = value) }
    is ReadConfigIntent.VolumeKeyPageChanged -> { settings -> settings.copy(volumeKeyPage = value) }
    is ReadConfigIntent.VolumeKeyPageOnPlayChanged -> { settings -> settings.copy(volumeKeyPageOnPlay = value) }
    is ReadConfigIntent.KeyPageOnLongPressChanged -> { settings -> settings.copy(keyPageOnLongPress = value) }
    is ReadConfigIntent.PageTouchSlopChanged -> { settings -> settings.copy(pageTouchSlop = value) }
    is ReadConfigIntent.SliderVibratorChanged -> { settings -> settings.copy(sliderVibrator = value) }
    is ReadConfigIntent.SelectVibratorChanged -> { settings -> settings.copy(selectVibrator = value) }
    is ReadConfigIntent.AutoChangeSourceChanged -> { settings -> settings.copy(autoChangeSource = value) }
    is ReadConfigIntent.AutoSuggestDayNightChanged -> { settings -> settings.copy(autoSuggestDayNight = value) }
    is ReadConfigIntent.SelectTextChanged -> { settings -> settings.copy(selectText = value) }
    is ReadConfigIntent.NoAnimScrollPageChanged -> { settings -> settings.copy(noAnimScrollPage = value) }
    is ReadConfigIntent.ClickImgWayChanged -> { settings -> settings.copy(clickImgWay = value) }
    is ReadConfigIntent.OptimizeRenderChanged -> { settings -> settings.copy(optimizeRender = value) }
    is ReadConfigIntent.DisableReturnKeyChanged -> { settings -> settings.copy(disableReturnKey = value) }
    is ReadConfigIntent.ShowReadTitleAdditionChanged -> { settings -> settings.copy(showReadTitleAddition = value) }
    is ReadConfigIntent.ShowMenuIconChanged -> { settings -> settings.copy(showMenuIcon = value) }
    is ReadConfigIntent.PageKeysChanged -> { settings ->
        settings.copy(prevKeys = prevKeys, nextKeys = nextKeys)
    }
    is ReadConfigIntent.EyeProtectionEnabledChanged,
    is ReadConfigIntent.EyeProtectionIntensityChanged,
    is ReadConfigIntent.EyeProtectionAutoNightChanged,
    is ReadConfigIntent.EyeProtectionScheduleChanged,
    is ReadConfigIntent.EyeProtectionStartTimeChanged,
    is ReadConfigIntent.EyeProtectionEndTimeChanged ->
        error("Eye protection intents update ThemeSettings")
    ReadConfigIntent.OpenPageKeys,
    ReadConfigIntent.OpenClickActions,
    ReadConfigIntent.OpenEyeProtection,
    ReadConfigIntent.DismissSheet -> error("Sheet intents do not update settings")
}

private fun ThemeSettings.toEyeProtectionUiState(): EyeProtectionUiState = EyeProtectionUiState(
    enabled = eyeProtectionEnabled,
    intensity = colorTemperature,
    autoNight = eyeProtectionAutoNight,
    schedule = eyeProtectionSchedule,
    startTime = eyeProtectionStartTime,
    endTime = eyeProtectionEndTime,
)

private fun ReadSettings.toUiState(
    activeSheet: ReadConfigSheet?,
    eyeProtection: EyeProtectionUiState,
): ReadConfigUiState =
    ReadConfigUiState(
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
        eyeProtection = eyeProtection,
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
        autoSuggestDayNight = autoSuggestDayNight,
        selectText = selectText,
        noAnimScrollPage = noAnimScrollPage,
        clickImgWay = clickImgWay,
        optimizeRender = optimizeRender,
        disableReturnKey = disableReturnKey,
        expandTextMenu = expandTextMenu,
        showSelectMenuIcon = showSelectMenuIcon,
        showReadTitleAddition = showReadTitleAddition,
        autoReadSpeed = autoReadSpeed,
        prevKeys = prevKeys,
        nextKeys = nextKeys,
        showMenuIcon = showMenuIcon,
        activeSheet = activeSheet,
    )
