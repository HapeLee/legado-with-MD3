package io.legado.app.ui.config.customTheme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsUpdate
import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomThemeViewModel(
    private val themeSettingsGateway: ThemeSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomThemeUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CustomThemeEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            themeSettingsGateway.settings.collect { settings ->
                _uiState.update { settings.toUiState(it.activePicker) }
            }
        }
    }

    fun onIntent(intent: CustomThemeIntent) {
        when (intent) {
            is CustomThemeIntent.DeepPersonalizationChanged ->
                update(ThemeSettingsUpdate.DeepPersonalization(intent.value))
            is CustomThemeIntent.PaletteStyleChanged ->
                update(ThemeSettingsUpdate.PaletteStyle(intent.value))
            is CustomThemeIntent.CustomContrastChanged ->
                update(ThemeSettingsUpdate.CustomContrast(intent.value))
            is CustomThemeIntent.MaterialVersionChanged ->
                update(ThemeSettingsUpdate.MaterialVersion(intent.value))
            is CustomThemeIntent.OpenPicker ->
                _uiState.update { it.copy(activePicker = intent.picker) }
            CustomThemeIntent.DismissPicker ->
                _uiState.update { it.copy(activePicker = null) }
            is CustomThemeIntent.ColorSelected -> selectColor(intent.value)
        }
    }

    private fun selectColor(color: Int) {
        when (val picker = _uiState.value.activePicker) {
            is CustomThemePicker.DeepColor ->
                update(ThemeSettingsUpdate.CustomColor(picker.slot, color))
            CustomThemePicker.DaySeed -> {
                update(ThemeSettingsUpdate.CustomPrimary(color))
                _effects.tryEmit(CustomThemeEffect.ApplyLegacyPrimarySeed(color))
            }
            CustomThemePicker.NightSeed -> update(ThemeSettingsUpdate.CustomNightPrimary(color))
            null -> return
        }
        _uiState.update { it.copy(activePicker = null) }
    }

    private fun update(update: ThemeSettingsUpdate) {
        viewModelScope.launch {
            runCatching { themeSettingsGateway.update(update) }
                .onFailure { error ->
                    _effects.tryEmit(
                        CustomThemeEffect.SettingsUpdateFailed(
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
        }
    }
}

private fun ThemeSettings.toUiState(activePicker: CustomThemePicker?) = CustomThemeUiState(
    enableDeepPersonalization = enableDeepPersonalization,
    themeColor = themeColor,
    secondaryThemeColor = secondaryThemeColor,
    primaryTextColor = primaryTextColor,
    secondaryTextColor = secondaryTextColor,
    themeBackgroundColor = themeBackgroundColor,
    labelContainerColor = labelContainerColor,
    themeColorNight = themeColorNight,
    secondaryThemeColorNight = secondaryThemeColorNight,
    primaryTextColorNight = primaryTextColorNight,
    secondaryTextColorNight = secondaryTextColorNight,
    themeBackgroundColorNight = themeBackgroundColorNight,
    labelContainerColorNight = labelContainerColorNight,
    primarySeedColor = customPrimary,
    nightPrimarySeedColor = customNightPrimary,
    paletteStyle = paletteStyle,
    customContrast = customContrast,
    materialVersion = materialVersion,
    activePicker = activePicker,
)
