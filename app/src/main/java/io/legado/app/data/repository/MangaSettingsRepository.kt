package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MangaSettingsRepository : MangaSettingsGateway {

    override val currentSettings: MangaSettings
        get() = AppConfigStore.preferences.toMangaSettings()

    override val settings: Flow<MangaSettings> = AppConfigStore.preferencesFlow
        .map { it.toMangaSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toMangaSettings,
            toPrefMap = MangaSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun MangaSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.showMangaUi to showMangaUi,
    PreferKey.disableMangaScale to disableMangaScale,
    PreferKey.disableMangaScrollAnimation to disableMangaScrollAnimation,
    PreferKey.disableMangaCrossFade to disableMangaCrossFade,
    PreferKey.mangaScrollMode to scrollMode,
    PreferKey.mangaPreDownloadNum to preDownloadNum,
    PreferKey.mangaAutoPageSpeed to autoPageSpeed,
    PreferKey.mangaFooterConfig to footerConfig,
    PreferKey.disableClickScroll to disableClickScroll,
    PreferKey.mangaLongClick to longClick,
    PreferKey.mangaBackground to background,
    PreferKey.mangaColorFilter to colorFilter,
    PreferKey.hideMangaTitle to hideTitle,
    PreferKey.enableMangaEInk to enableEInk,
    PreferKey.mangaEInkThreshold to eInkThreshold,
    PreferKey.enableMangaGray to enableGray,
    PreferKey.webtoonSidePaddingDp to webtoonSidePaddingDp,
    PreferKey.mangaVolumeKeyPage to volumeKeyPage,
    PreferKey.reverseVolumeKeyPage to reverseVolumeKeyPage,
    PreferKey.mangaClickActionTL to clickActionTL,
    PreferKey.mangaClickActionTC to clickActionTC,
    PreferKey.mangaClickActionTR to clickActionTR,
    PreferKey.mangaClickActionML to clickActionML,
    PreferKey.mangaClickActionMC to clickActionMC,
    PreferKey.mangaClickActionMR to clickActionMR,
    PreferKey.mangaClickActionBL to clickActionBL,
    PreferKey.mangaClickActionBC to clickActionBC,
    PreferKey.mangaClickActionBR to clickActionBR,
)

internal fun Preferences.toMangaSettings(): MangaSettings = MangaSettings(
    showMangaUi = compatDsBoolean(PreferKey.showMangaUi) ?: true,
    disableMangaScale = compatDsBoolean(PreferKey.disableMangaScale) ?: true,
    disableMangaScrollAnimation = compatDsBoolean(PreferKey.disableMangaScrollAnimation) ?: false,
    disableMangaCrossFade = compatDsBoolean(PreferKey.disableMangaCrossFade) ?: false,
    scrollMode = compatDsInt(PreferKey.mangaScrollMode) ?: 4,
    preDownloadNum = compatDsInt(PreferKey.mangaPreDownloadNum) ?: 10,
    autoPageSpeed = compatDsInt(PreferKey.mangaAutoPageSpeed) ?: 3,
    footerConfig = compatDsString(PreferKey.mangaFooterConfig).orEmpty(),
    disableClickScroll = compatDsBoolean(PreferKey.disableClickScroll) ?: false,
    longClick = compatDsBoolean(PreferKey.mangaLongClick) ?: true,
    background = compatDsInt(PreferKey.mangaBackground) ?: 0xFF000000.toInt(),
    colorFilter = compatDsString(PreferKey.mangaColorFilter).orEmpty(),
    hideTitle = compatDsBoolean(PreferKey.hideMangaTitle) ?: false,
    enableEInk = compatDsBoolean(PreferKey.enableMangaEInk) ?: false,
    eInkThreshold = compatDsInt(PreferKey.mangaEInkThreshold) ?: 150,
    enableGray = compatDsBoolean(PreferKey.enableMangaGray) ?: false,
    webtoonSidePaddingDp = compatDsInt(PreferKey.webtoonSidePaddingDp) ?: 0,
    volumeKeyPage = compatDsBoolean(PreferKey.mangaVolumeKeyPage) ?: false,
    reverseVolumeKeyPage = compatDsBoolean(PreferKey.reverseVolumeKeyPage) ?: false,
    clickActionTL = compatDsInt(PreferKey.mangaClickActionTL) ?: -1,
    clickActionTC = compatDsInt(PreferKey.mangaClickActionTC) ?: -1,
    clickActionTR = compatDsInt(PreferKey.mangaClickActionTR) ?: 1,
    clickActionML = compatDsInt(PreferKey.mangaClickActionML) ?: 2,
    clickActionMC = compatDsInt(PreferKey.mangaClickActionMC) ?: 0,
    clickActionMR = compatDsInt(PreferKey.mangaClickActionMR) ?: 1,
    clickActionBL = compatDsInt(PreferKey.mangaClickActionBL) ?: 2,
    clickActionBC = compatDsInt(PreferKey.mangaClickActionBC) ?: 1,
    clickActionBR = compatDsInt(PreferKey.mangaClickActionBR) ?: 1,
)
