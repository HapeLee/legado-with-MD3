package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow

interface ThemeSettingsGateway {
    val currentSettings: ThemeSettings
    val settings: Flow<ThemeSettings>
    suspend fun update(update: ThemeSettingsUpdate)
    suspend fun updateAll(updates: List<ThemeSettingsUpdate>)
}

sealed interface ThemeSettingsUpdate {
    data class AppTheme(val value: String) : ThemeSettingsUpdate
    data class PureBlack(val value: Boolean) : ThemeSettingsUpdate
    data class PaletteStyle(val value: String) : ThemeSettingsUpdate
    data class MaterialVersion(val value: String) : ThemeSettingsUpdate
    data class CustomContrast(val value: String) : ThemeSettingsUpdate
    data class DeepPersonalization(val value: Boolean) : ThemeSettingsUpdate
    data class CustomColor(val slot: ThemeColorSlot, val value: Int) : ThemeSettingsUpdate
    data class AppFontPath(val value: String?) : ThemeSettingsUpdate
    data class CustomPrimary(val value: Int) : ThemeSettingsUpdate
    data class CustomNightPrimary(val value: Int) : ThemeSettingsUpdate
    data class BooleanValue(
        val setting: ThemeBooleanSetting,
        val value: Boolean,
    ) : ThemeSettingsUpdate
    data class IntValue(
        val setting: ThemeIntSetting,
        val value: Int,
    ) : ThemeSettingsUpdate
    data class FloatValue(
        val setting: ThemeFloatSetting,
        val value: Float,
    ) : ThemeSettingsUpdate
    data class StringValue(
        val setting: ThemeStringSetting,
        val value: String?,
    ) : ThemeSettingsUpdate
}

enum class ThemeBooleanSetting {
    UseMiuixMonet,
    EnableBlur,
    EnableProgressiveBlur,
    UseFlexibleTopAppBar,
    BookInfoFollowCoverColor,
    EnableItemDivider,
    OverrideBaseCardCornerRadius,
    OverrideBaseCardBorder,
    DisableSplicedColumnGroupCornerRadius,
    EyeProtectionEnabled,
    EyeProtectionSchedule,
    ShowRefactorTip,
    EnableCustomTagColors,
}

enum class ThemeIntSetting {
    ContainerOpacity,
    TopBarOpacity,
    BottomBarOpacity,
    TopBarBlurRadius,
    BottomBarBlurRadius,
    TopBarBlurAlpha,
    BottomBarBlurAlpha,
    BackgroundImageBlurring,
    BackgroundImageDarkBlurring,
    ItemDividerColor,
    BaseCardBorderColor,
    BaseCardBorderColorNight,
    ColorTemperature,
}

enum class ThemeFloatSetting {
    BottomBarLensRadius,
    ItemDividerWidth,
    ItemDividerLength,
    BaseCardCornerRadius,
    BaseCardBorderWidth,
}

enum class ThemeStringSetting {
    BookInfoNetworkCoverBackground,
    BookInfoDefaultCoverBackground,
    BackgroundImageLight,
    BackgroundImageDark,
    EyeProtectionStartTime,
    EyeProtectionEndTime,
    CustomTagColorsJson,
}

enum class ThemeColorSlot {
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
