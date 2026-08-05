package io.legado.app.ui.config.customTheme

import androidx.compose.runtime.Stable

@Stable
data class CustomThemeUiState(
    val enableDeepPersonalization: Boolean = false,
    val themeColor: Int = 0,
    val secondaryThemeColor: Int = 0,
    val primaryTextColor: Int = 0,
    val secondaryTextColor: Int = 0,
    val themeBackgroundColor: Int = 0,
    val labelContainerColor: Int = 0,
    val themeColorNight: Int = 0,
    val secondaryThemeColorNight: Int = 0,
    val primaryTextColorNight: Int = 0,
    val secondaryTextColorNight: Int = 0,
    val themeBackgroundColorNight: Int = 0,
    val labelContainerColorNight: Int = 0,
    val primarySeedColor: Int = 0,
    val nightPrimarySeedColor: Int = 0,
    val paletteStyle: String = "tonalSpot",
    val customContrast: String = "Default",
    val materialVersion: String = "material3",
    val activePicker: CustomThemePicker? = null,
)

sealed interface CustomThemePicker {
    data class DeepColor(val slot: CustomThemeColorSlot) : CustomThemePicker
    data object DaySeed : CustomThemePicker
    data object NightSeed : CustomThemePicker
}

enum class CustomThemeColorSlot {
    Primary,
    Secondary,
    PrimaryText,
    SecondaryText,
    Background,
    LabelContainer,
    PrimaryNight,
    SecondaryNight,
    PrimaryTextNight,
    SecondaryTextNight,
    BackgroundNight,
    LabelContainerNight,
}

sealed interface CustomThemeIntent {
    data class DeepPersonalizationChanged(val value: Boolean) : CustomThemeIntent
    data class PaletteStyleChanged(val value: String) : CustomThemeIntent
    data class CustomContrastChanged(val value: String) : CustomThemeIntent
    data class MaterialVersionChanged(val value: String) : CustomThemeIntent
    data class OpenPicker(val picker: CustomThemePicker) : CustomThemeIntent
    data object DismissPicker : CustomThemeIntent
    data class ColorSelected(val value: Int) : CustomThemeIntent
}

sealed interface CustomThemeEffect {
    data class ApplyLegacyPrimarySeed(val color: Int) : CustomThemeEffect
    data class SettingsUpdateFailed(val message: String) : CustomThemeEffect
}
