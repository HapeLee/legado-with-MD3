package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.MangaSettings
import kotlinx.coroutines.flow.Flow

interface MangaSettingsGateway {
    val currentSettings: MangaSettings
    val settings: Flow<MangaSettings>
    suspend fun update(update: MangaSettingsUpdate)
    suspend fun updateAll(updates: List<MangaSettingsUpdate>)
}

sealed interface MangaSettingsUpdate {
    data class ShowMangaUi(val value: Boolean) : MangaSettingsUpdate
    data class DisableScale(val value: Boolean) : MangaSettingsUpdate
    data class DisableScrollAnimation(val value: Boolean) : MangaSettingsUpdate
    data class DisableCrossFade(val value: Boolean) : MangaSettingsUpdate
    data class ScrollMode(val value: Int) : MangaSettingsUpdate
    data class PreDownloadNum(val value: Int) : MangaSettingsUpdate
    data class AutoPageSpeed(val value: Int) : MangaSettingsUpdate
    data class FooterConfig(val value: String) : MangaSettingsUpdate
    data class DisableClickScroll(val value: Boolean) : MangaSettingsUpdate
    data class LongClick(val value: Boolean) : MangaSettingsUpdate
    data class Background(val value: Int) : MangaSettingsUpdate
    data class ColorFilter(val value: String) : MangaSettingsUpdate
    data class HideTitle(val value: Boolean) : MangaSettingsUpdate
    data class EInk(val enabled: Boolean, val threshold: Int) : MangaSettingsUpdate
    data class Gray(val enabled: Boolean) : MangaSettingsUpdate
    data class WebtoonSidePadding(val value: Int) : MangaSettingsUpdate
    data class VolumeKeyPage(val value: Boolean) : MangaSettingsUpdate
    data class ReverseVolumeKeyPage(val value: Boolean) : MangaSettingsUpdate
    data class ClickAction(val area: MangaClickArea, val value: Int) : MangaSettingsUpdate
}

enum class MangaClickArea { TL, TC, TR, ML, MC, MR, BL, BC, BR }
