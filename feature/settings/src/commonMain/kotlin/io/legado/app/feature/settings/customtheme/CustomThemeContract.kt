package io.legado.app.feature.settings.customtheme

import androidx.compose.runtime.Stable

// M5-3b：从 `:app` 的 `io.legado.app.ui.config.customTheme` 迁来（**只改包名**，
// 结构逐字一致）。
//
// 数据全是 `Int` 颜色值与字符串枚举值——没有平台类型，所以本页的 Contract 能整份进
// `commonMain`。两个 `Effect` 分支（`ApplyLegacyPrimarySeed` / `SettingsUpdateFailed`）
// 由 `:app` 的 entry 解释成 `ThemeStore` 与 Toast。

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
