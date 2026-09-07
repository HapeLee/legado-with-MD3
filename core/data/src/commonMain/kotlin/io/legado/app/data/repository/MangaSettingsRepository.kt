package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MangaSettingsRepository(
    private val preferences: PreferenceStore,
) : MangaSettingsGateway {

    override val currentSettings: MangaSettings
        get() = preferences.currentSnapshot().toMangaSettings()

    override val settings: Flow<MangaSettings> = preferences.observeSnapshot()
        .map { it.toMangaSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toMangaSettings() },
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
    PreferKey.mangaChapterPrefetchCount to chapterPrefetchCount,
    PreferKey.mangaAutoOfflineCache to autoOfflineCache,
    PreferKey.mangaAutoPageSpeed to autoPageSpeed,
    PreferKey.mangaFooterConfig to footerConfig,
    PreferKey.disableClickScroll to disableClickScroll,
    PreferKey.mangaLongClick to longClick,
    PreferKey.mangaBackground to background,
    PreferKey.mangaAutoBackground to autoBackground,
    PreferKey.mangaPageScaleType to pageScaleType,
    PreferKey.mangaZoomStartPosition to zoomStartPosition,
    PreferKey.mangaWidePageMode to widePageMode,
    PreferKey.mangaDoublePageMode to doublePageMode,
    PreferKey.mangaDoublePageCoverSingle to doublePageCoverSingle,
    PreferKey.mangaDoublePageInvert to doublePageInvert,
    PreferKey.mangaDoublePageShift to doublePageShift,
    PreferKey.mangaColorFilter to colorFilter,
    PreferKey.hideMangaTitle to hideTitle,
    PreferKey.enableMangaEInk to enableEInk,
    PreferKey.mangaEInkThreshold to eInkThreshold,
    PreferKey.enableMangaGray to enableGray,
    PreferKey.webtoonSidePaddingDp to webtoonSidePaddingDp,
    PreferKey.mangaVolumeKeyPage to volumeKeyPage,
    PreferKey.reverseVolumeKeyPage to reverseVolumeKeyPage,
    PreferKey.mangaMenuTopBarLiquidGlass to menuTopBarLiquidGlass,
    PreferKey.mangaMenuBottomBarLiquidGlass to menuBottomBarLiquidGlass,
    PreferKey.mangaMenuBottomBarFloating to menuBottomBarFloating,
    PreferKey.mangaMenuBottomBarBlur to menuBottomBarBlur,
    PreferKey.mangaMenuTopBarCompact to menuTopBarCompact,
    PreferKey.mangaMenuColorSource to menuColorSource,
    PreferKey.mangaMenuSeedColor to menuSeedColor,
    PreferKey.mangaMenuPaletteStyle to menuPaletteStyle,
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

internal fun Map<String, PreferenceValue>.toMangaSettings(): MangaSettings = MangaSettings(
    showMangaUi = compatBoolean(PreferKey.showMangaUi) ?: true,
    disableMangaScale = compatBoolean(PreferKey.disableMangaScale) ?: true,
    disableMangaScrollAnimation = compatBoolean(PreferKey.disableMangaScrollAnimation) ?: false,
    disableMangaCrossFade = compatBoolean(PreferKey.disableMangaCrossFade) ?: false,
    scrollMode = compatInt(PreferKey.mangaScrollMode) ?: 4,
    preDownloadNum = compatInt(PreferKey.mangaPreDownloadNum) ?: 10,
    // Chapter prefetch queues the offline cache downloader, so it must be opt-in.
    chapterPrefetchCount = compatInt(PreferKey.mangaChapterPrefetchCount) ?: 0,
    autoOfflineCache = compatBoolean(PreferKey.mangaAutoOfflineCache) ?: false,
    autoPageSpeed = compatInt(PreferKey.mangaAutoPageSpeed) ?: 3,
    footerConfig = compatString(PreferKey.mangaFooterConfig).orEmpty(),
    disableClickScroll = compatBoolean(PreferKey.disableClickScroll) ?: false,
    longClick = compatBoolean(PreferKey.mangaLongClick) ?: true,
    background = compatInt(PreferKey.mangaBackground) ?: 0xFF000000.toInt(),
    autoBackground = compatBoolean(PreferKey.mangaAutoBackground) ?: false,
    pageScaleType = compatInt(PreferKey.mangaPageScaleType) ?: 0,
    zoomStartPosition = compatInt(PreferKey.mangaZoomStartPosition) ?: 0,
    widePageMode = compatInt(PreferKey.mangaWidePageMode) ?: 0,
    doublePageMode = compatInt(PreferKey.mangaDoublePageMode) ?: 0,
    doublePageCoverSingle = compatBoolean(PreferKey.mangaDoublePageCoverSingle) ?: true,
    doublePageInvert = compatBoolean(PreferKey.mangaDoublePageInvert) ?: false,
    doublePageShift = compatBoolean(PreferKey.mangaDoublePageShift) ?: false,
    colorFilter = compatString(PreferKey.mangaColorFilter).orEmpty(),
    hideTitle = compatBoolean(PreferKey.hideMangaTitle) ?: false,
    enableEInk = compatBoolean(PreferKey.enableMangaEInk) ?: false,
    eInkThreshold = compatInt(PreferKey.mangaEInkThreshold) ?: 150,
    enableGray = compatBoolean(PreferKey.enableMangaGray) ?: false,
    webtoonSidePaddingDp = compatInt(PreferKey.webtoonSidePaddingDp) ?: 0,
    volumeKeyPage = compatBoolean(PreferKey.mangaVolumeKeyPage) ?: false,
    reverseVolumeKeyPage = compatBoolean(PreferKey.reverseVolumeKeyPage) ?: false,
    menuTopBarLiquidGlass = compatBoolean(PreferKey.mangaMenuTopBarLiquidGlass) ?: false,
    menuBottomBarLiquidGlass = compatBoolean(PreferKey.mangaMenuBottomBarLiquidGlass) ?: false,
    menuBottomBarFloating = compatBoolean(PreferKey.mangaMenuBottomBarFloating) ?: true,
    menuBottomBarBlur = compatBoolean(PreferKey.mangaMenuBottomBarBlur) ?: false,
    menuTopBarCompact = compatBoolean(PreferKey.mangaMenuTopBarCompact) ?: false,
    menuColorSource = compatInt(PreferKey.mangaMenuColorSource) ?: 0,
    menuSeedColor = compatInt(PreferKey.mangaMenuSeedColor) ?: 0xFF6750A4.toInt(),
    menuPaletteStyle = compatString(PreferKey.mangaMenuPaletteStyle) ?: "tonalSpot",
    clickActionTL = compatInt(PreferKey.mangaClickActionTL) ?: -1,
    clickActionTC = compatInt(PreferKey.mangaClickActionTC) ?: -1,
    clickActionTR = compatInt(PreferKey.mangaClickActionTR) ?: 1,
    clickActionML = compatInt(PreferKey.mangaClickActionML) ?: 2,
    clickActionMC = compatInt(PreferKey.mangaClickActionMC) ?: 0,
    clickActionMR = compatInt(PreferKey.mangaClickActionMR) ?: 1,
    clickActionBL = compatInt(PreferKey.mangaClickActionBL) ?: 2,
    clickActionBC = compatInt(PreferKey.mangaClickActionBC) ?: 1,
    clickActionBR = compatInt(PreferKey.mangaClickActionBR) ?: 1,
)
