package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsUpdate
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.ui.book.read.EyeProtection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReadConfigViewModel(
    private val settingsGateway: ReadSettingsGateway,
    private val applyReadSetting: ApplyReadSettingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        settingsGateway.currentSettings.toUiState(activeSheet = null)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { settings.toUiState(activeSheet = it.activeSheet) }
            }
        }
    }

    fun onIntent(intent: ReadConfigIntent) {
        when (intent) {
            ReadConfigIntent.OpenPageKeys -> setSheet(ReadConfigSheet.PageKeys)
            ReadConfigIntent.OpenClickActions -> setSheet(ReadConfigSheet.ClickActions)
            ReadConfigIntent.OpenEyeProtection -> setSheet(ReadConfigSheet.EyeProtection)
            ReadConfigIntent.DismissSheet -> setSheet(null)
            else -> updateSetting(intent.toSettingsUpdate())
        }
    }

    private fun setSheet(sheet: ReadConfigSheet?) {
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    private fun updateSetting(update: ReadSettingsUpdate) {
        viewModelScope.launch {
            runCatching {
                settingsGateway.update(update)
                if (update is ReadSettingsUpdate.EyeProtectionAutoNight && update.value) {
                    EyeProtection.syncEnabledForNight(
                        isNight = ReadConfig.isNightTheme,
                        autoNight = true,
                    )?.let { enabled ->
                        settingsGateway.update(ReadSettingsUpdate.EyeProtectionEnabled(enabled))
                    }
                }
            }
                .onSuccess {
                    applyReadSetting(update)
                    if (update is ReadSettingsUpdate.PageKeys) setSheet(null)
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

private fun ReadConfigIntent.toSettingsUpdate(): ReadSettingsUpdate = when (this) {
    is ReadConfigIntent.ScreenOrientationChanged -> ReadSettingsUpdate.ScreenOrientation(value)
    is ReadConfigIntent.KeepLightChanged -> ReadSettingsUpdate.KeepLight(value)
    is ReadConfigIntent.HideStatusBarChanged -> ReadSettingsUpdate.HideStatusBar(value)
    is ReadConfigIntent.HideNavigationBarChanged -> ReadSettingsUpdate.HideNavigationBar(value)
    is ReadConfigIntent.PaddingDisplayCutoutsChanged -> ReadSettingsUpdate.PaddingDisplayCutouts(value)
    is ReadConfigIntent.TitleBarModeChanged -> ReadSettingsUpdate.TitleBarMode(value)
    is ReadConfigIntent.ReadMenuBlurAlphaChanged -> ReadSettingsUpdate.ReadMenuBlurAlpha(value)
    is ReadConfigIntent.ReadBodyToLhChanged -> ReadSettingsUpdate.ReadBodyToLh(value)
    is ReadConfigIntent.DefaultSourceChangeAllChanged -> ReadSettingsUpdate.DefaultSourceChangeAll(value)
    is ReadConfigIntent.TextFullJustifyChanged -> ReadSettingsUpdate.TextFullJustify(value)
    is ReadConfigIntent.TextBottomJustifyChanged -> ReadSettingsUpdate.TextBottomJustify(value)
    is ReadConfigIntent.AdaptSpecialStyleChanged -> ReadSettingsUpdate.AdaptSpecialStyle(value)
    is ReadConfigIntent.UseZhLayoutChanged -> ReadSettingsUpdate.UseZhLayout(value)
    ReadConfigIntent.OpenEyeProtection -> error("Sheet intents do not update settings")
    is ReadConfigIntent.EyeProtectionEnabledChanged -> ReadSettingsUpdate.EyeProtectionEnabled(value)
    is ReadConfigIntent.EyeProtectionIntensityChanged -> ReadSettingsUpdate.EyeProtectionIntensity(value)
    is ReadConfigIntent.EyeProtectionAutoNightChanged -> ReadSettingsUpdate.EyeProtectionAutoNight(value)
    is ReadConfigIntent.ShowBrightnessViewChanged -> ReadSettingsUpdate.ShowBrightnessView(value)
    is ReadConfigIntent.BrightnessVwPosChanged -> ReadSettingsUpdate.BrightnessVwPos(value)
    is ReadConfigIntent.UseUnderlineChanged -> ReadSettingsUpdate.UseUnderline(value)
    is ReadConfigIntent.ReadSliderModeChanged -> ReadSettingsUpdate.ReadSliderMode(value)
    is ReadConfigIntent.DoubleHorizontalPageChanged -> ReadSettingsUpdate.DoubleHorizontalPage(value)
    is ReadConfigIntent.ProgressBarBehaviorChanged -> ReadSettingsUpdate.ProgressBarBehavior(value)
    is ReadConfigIntent.MouseWheelPageChanged -> ReadSettingsUpdate.MouseWheelPage(value)
    is ReadConfigIntent.VolumeKeyPageChanged -> ReadSettingsUpdate.VolumeKeyPage(value)
    is ReadConfigIntent.VolumeKeyPageOnPlayChanged -> ReadSettingsUpdate.VolumeKeyPageOnPlay(value)
    is ReadConfigIntent.KeyPageOnLongPressChanged -> ReadSettingsUpdate.KeyPageOnLongPress(value)
    is ReadConfigIntent.PageTouchSlopChanged -> ReadSettingsUpdate.PageTouchSlop(value)
    is ReadConfigIntent.SliderVibratorChanged -> ReadSettingsUpdate.SliderVibrator(value)
    is ReadConfigIntent.SelectVibratorChanged -> ReadSettingsUpdate.SelectVibrator(value)
    is ReadConfigIntent.AutoChangeSourceChanged -> ReadSettingsUpdate.AutoChangeSource(value)
    is ReadConfigIntent.AutoSuggestDayNightChanged -> ReadSettingsUpdate.AutoSuggestDayNight(value)
    is ReadConfigIntent.SelectTextChanged -> ReadSettingsUpdate.SelectText(value)
    is ReadConfigIntent.NoAnimScrollPageChanged -> ReadSettingsUpdate.NoAnimScrollPage(value)
    is ReadConfigIntent.ClickImgWayChanged -> ReadSettingsUpdate.ClickImgWay(value)
    is ReadConfigIntent.OptimizeRenderChanged -> ReadSettingsUpdate.OptimizeRender(value)
    is ReadConfigIntent.DisableReturnKeyChanged -> ReadSettingsUpdate.DisableReturnKey(value)
    is ReadConfigIntent.ShowReadTitleAdditionChanged -> ReadSettingsUpdate.ShowReadTitleAddition(value)
    is ReadConfigIntent.ShowMenuIconChanged -> ReadSettingsUpdate.ShowMenuIcon(value)
    is ReadConfigIntent.PageKeysChanged -> ReadSettingsUpdate.PageKeys(prevKeys, nextKeys)
    ReadConfigIntent.OpenPageKeys,
    ReadConfigIntent.OpenClickActions,
    ReadConfigIntent.DismissSheet -> error("Sheet intents do not update settings")
}

private fun ReadSettings.toUiState(activeSheet: ReadConfigSheet?): ReadConfigUiState =
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
        eyeProtectionEnabled = eyeProtectionEnabled,
        eyeProtectionIntensity = eyeProtectionIntensity,
        eyeProtectionAutoNight = eyeProtectionAutoNight,
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
