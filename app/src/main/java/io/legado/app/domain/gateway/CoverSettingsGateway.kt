package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.CoverSettings
import kotlinx.coroutines.flow.Flow

interface CoverSettingsGateway {
    val currentSettings: CoverSettings
    val settings: Flow<CoverSettings>
    suspend fun update(update: CoverSettingsUpdate)
}

sealed interface CoverSettingsUpdate {
    data class LoadOnlyOnWifi(val value: Boolean) : CoverSettingsUpdate
    data class UseDefaultCover(val value: Boolean) : CoverSettingsUpdate
    data class ShowShadow(val value: Boolean) : CoverSettingsUpdate
    data class ShowStroke(val value: Boolean) : CoverSettingsUpdate
    data class UseDefaultColor(val value: Boolean) : CoverSettingsUpdate
    data class TextColor(val value: Int, val dark: Boolean) : CoverSettingsUpdate
    data class ShadowColor(val value: Int, val dark: Boolean) : CoverSettingsUpdate
    data class ShowName(val value: Boolean, val dark: Boolean) : CoverSettingsUpdate
    data class ShowAuthor(val value: Boolean, val dark: Boolean) : CoverSettingsUpdate
    data class InfoOrientation(val value: String) : CoverSettingsUpdate
    data class ExploreFilterState(val value: Int) : CoverSettingsUpdate
}
