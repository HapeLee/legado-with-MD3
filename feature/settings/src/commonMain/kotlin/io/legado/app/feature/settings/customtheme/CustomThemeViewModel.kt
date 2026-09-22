package io.legado.app.feature.settings.customtheme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-3b：从 `:app` 的 `io.legado.app.ui.config.customTheme` 迁来（**只改包名**，
// 逻辑逐字一致）。
//
// 与前面两个子页的 VM 相比，它多了一处值得注意的语义：
// `update()` 用 `runCatching` 包住 `themeSettingsGateway.update`，失败时发
// `SettingsUpdateFailed`（带 `error.message ?: error.javaClass.simpleName`）。
// 这条路径在共享层，但它**不碰平台**：文案由宿主解释成 Toast。
//
// 另外 `CustomThemePicker.DaySeed` 分支要做**两件事**——写设置**并且**发
// `ApplyLegacyPrimarySeed`（让旧的 `ThemeStore` 也跟上种子色）。后者是纯 `:app` 的概念，
// 故以 Effect 形式交给宿主，而不是让共享层知道 `ThemeStore` 的存在。
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
                update { it.copy(enableDeepPersonalization = intent.value) }
            is CustomThemeIntent.PaletteStyleChanged ->
                update { it.copy(paletteStyle = intent.value) }
            is CustomThemeIntent.CustomContrastChanged ->
                update { it.copy(customContrast = intent.value) }
            is CustomThemeIntent.MaterialVersionChanged ->
                update { it.copy(materialVersion = intent.value) }
            is CustomThemeIntent.OpenPicker ->
                _uiState.update { it.copy(activePicker = intent.picker) }
            CustomThemeIntent.DismissPicker ->
                _uiState.update { it.copy(activePicker = null) }
            is CustomThemeIntent.ColorSelected -> selectColor(intent.value)
        }
    }

    private fun selectColor(color: Int) {
        when (val picker = _uiState.value.activePicker) {
            is CustomThemePicker.DeepColor -> update { settings ->
                when (picker.slot) {
                    CustomThemeColorSlot.Primary -> settings.copy(themeColor = color)
                    CustomThemeColorSlot.Secondary -> settings.copy(secondaryThemeColor = color)
                    CustomThemeColorSlot.PrimaryText -> settings.copy(primaryTextColor = color)
                    CustomThemeColorSlot.SecondaryText ->
                        settings.copy(secondaryTextColor = color)
                    CustomThemeColorSlot.Background -> settings.copy(themeBackgroundColor = color)
                    CustomThemeColorSlot.LabelContainer ->
                        settings.copy(labelContainerColor = color)
                    CustomThemeColorSlot.PrimaryNight -> settings.copy(themeColorNight = color)
                    CustomThemeColorSlot.SecondaryNight ->
                        settings.copy(secondaryThemeColorNight = color)
                    CustomThemeColorSlot.PrimaryTextNight ->
                        settings.copy(primaryTextColorNight = color)
                    CustomThemeColorSlot.SecondaryTextNight ->
                        settings.copy(secondaryTextColorNight = color)
                    CustomThemeColorSlot.BackgroundNight ->
                        settings.copy(themeBackgroundColorNight = color)
                    CustomThemeColorSlot.LabelContainerNight ->
                        settings.copy(labelContainerColorNight = color)
                }
            }
            CustomThemePicker.DaySeed -> {
                update { it.copy(customPrimary = color) }
                _effects.tryEmit(CustomThemeEffect.ApplyLegacyPrimarySeed(color))
            }
            CustomThemePicker.NightSeed -> update { it.copy(customNightPrimary = color) }
            null -> return
        }
        _uiState.update { it.copy(activePicker = null) }
    }

    private fun update(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch {
            runCatching { themeSettingsGateway.update(transform) }
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
