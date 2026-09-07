package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaSettingsMappingTest {

    @Test
    fun `漫画设置 38 键写映射逐字段对应`() {
        mangaMappingSamples().forEach { settings ->
            assertEquals(settings.expectedPrefMap(), settings.toPrefMap())
        }
    }

    @Test
    fun `漫画设置 38 键读映射逐字段对应`() {
        mangaMappingSamples().forEach { expected ->
            assertEquals(expected, expected.expectedPrefMap().toTestSnapshot().toMangaSettings())
        }
    }

    @Test
    fun `历史字符串类型按原键恢复漫画设置`() {
        val settings = mapOf(
            PreferKey.mangaScrollMode to PreferenceValue.StringValue("5"),
            PreferKey.mangaPreDownloadNum to PreferenceValue.StringValue("22"),
            PreferKey.enableMangaEInk to PreferenceValue.StringValue("true"),
            PreferKey.mangaClickActionMC to PreferenceValue.StringValue("3"),
        ).toMangaSettings()

        assertEquals(5, settings.scrollMode)
        assertEquals(22, settings.preDownloadNum)
        assertTrue(settings.enableEInk)
        assertEquals(3, settings.clickActionMC)
    }

    @Test
    fun `墨水屏更新一次产生启用关闭灰度和阈值三项差量`() {
        val diff = captureAtomicUpdateValues(
            current = MangaSettings(enableGray = true),
            read = { it.toMangaSettings() },
            toPrefMap = MangaSettings::toPrefMap,
            transform = {
                it.copy(enableEInk = true, enableGray = false, eInkThreshold = 188)
            },
        )

        assertEquals(
            mapOf(
                PreferKey.enableMangaEInk to PreferenceValue.BooleanValue(true),
                PreferKey.mangaEInkThreshold to PreferenceValue.IntValue(188),
                PreferKey.enableMangaGray to PreferenceValue.BooleanValue(false),
            ),
            diff,
        )
    }

    @Test
    fun `灰度更新一次产生启用灰度和关闭墨水屏两项差量`() {
        val diff = captureAtomicUpdateValues(
            current = MangaSettings(enableEInk = true),
            read = { it.toMangaSettings() },
            toPrefMap = MangaSettings::toPrefMap,
            transform = { it.copy(enableEInk = false, enableGray = true) },
        )

        assertEquals(
            mapOf(
                PreferKey.enableMangaEInk to PreferenceValue.BooleanValue(false),
                PreferKey.enableMangaGray to PreferenceValue.BooleanValue(true),
            ),
            diff,
        )
    }

    @Test
    fun `gateway 通过不可变快照 transform 更新设置`() = runBlocking {
        val gateway = FakeMangaSettingsGateway(MangaSettings(enableGray = true))

        gateway.update {
            it.copy(enableEInk = true, enableGray = false, eInkThreshold = 188)
        }

        assertEquals(
            MangaSettings(enableEInk = true, enableGray = false, eInkThreshold = 188),
            gateway.currentSettings,
        )
    }

    @Test
    fun `默认点击区域保留菜单入口`() {
        val settings = emptyMap<String, PreferenceValue>().toMangaSettings()

        assertTrue(settings.hasMenuClickArea())
        assertFalse(settings.enableGray)
    }

    private class FakeMangaSettingsGateway(initial: MangaSettings) : MangaSettingsGateway {
        private val state = MutableStateFlow(initial)

        override val currentSettings: MangaSettings
            get() = state.value
        override val settings: Flow<MangaSettings> = state.asStateFlow()

        override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
            state.value = transform(state.value)
        }
    }
}

private fun mangaMappingSamples(): List<MangaSettings> {
    val base = MangaSettings(
        scrollMode = 11,
        preDownloadNum = 22,
        chapterPrefetchCount = 2,
        autoPageSpeed = 33,
        footerConfig = "manga-footer",
        background = 0xFF123456.toInt(),
        autoBackground = true,
        pageScaleType = 5,
        zoomStartPosition = 3,
        widePageMode = 2,
        doublePageMode = 2,
        colorFilter = "manga-filter",
        eInkThreshold = 44,
        webtoonSidePaddingDp = 55,
        clickActionTL = 101,
        clickActionTC = 102,
        clickActionTR = 103,
        clickActionML = 104,
        clickActionMC = 105,
        clickActionMR = 106,
        clickActionBL = 107,
        clickActionBC = 108,
        clickActionBR = 109,
    )
    return listOf(
        base,
        base.copy(showMangaUi = false),
        base.copy(disableMangaScale = false),
        base.copy(disableMangaScrollAnimation = true),
        base.copy(disableMangaCrossFade = true),
        base.copy(autoOfflineCache = true),
        base.copy(disableClickScroll = true),
        base.copy(longClick = false),
        base.copy(hideTitle = true),
        base.copy(enableEInk = true),
        base.copy(enableGray = true),
        base.copy(volumeKeyPage = true),
        base.copy(reverseVolumeKeyPage = true),
        base.copy(menuTopBarLiquidGlass = true),
        base.copy(menuBottomBarLiquidGlass = true),
        base.copy(menuBottomBarFloating = false),
        base.copy(menuBottomBarBlur = true),
        base.copy(menuTopBarCompact = true),
        base.copy(doublePageCoverSingle = false),
        base.copy(doublePageInvert = true),
        base.copy(doublePageShift = true),
    )
}

private fun MangaSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
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
