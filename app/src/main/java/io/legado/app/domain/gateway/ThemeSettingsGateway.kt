package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow

interface ThemeSettingsGateway {
    val currentSettings: ThemeSettings
    val settings: Flow<ThemeSettings>
    suspend fun update(update: ThemeSettingsUpdate)
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
