package io.legado.app.data.repository

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.MangaClickArea
import io.legado.app.domain.gateway.MangaSettingsUpdate
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.help.config.setPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaSettingsMappingTest {

    @Test
    fun `漫画设置写读映射往返恒等`() {
        val expected = MangaSettings(
            showMangaUi = false,
            disableMangaScale = false,
            disableMangaScrollAnimation = true,
            disableMangaCrossFade = true,
            scrollMode = 2,
            preDownloadNum = 22,
            autoPageSpeed = 8,
            footerConfig = "footer",
            disableClickScroll = true,
            longClick = false,
            background = 0xFF123456.toInt(),
            colorFilter = "filter",
            hideTitle = true,
            enableEInk = true,
            eInkThreshold = 188,
            enableGray = true,
            webtoonSidePaddingDp = 12,
            volumeKeyPage = true,
            reverseVolumeKeyPage = true,
            clickActionTL = 0,
            clickActionTC = 1,
            clickActionTR = 2,
            clickActionML = 3,
            clickActionMC = 4,
            clickActionMR = 5,
            clickActionBL = 6,
            clickActionBC = 7,
            clickActionBR = 8,
        )
        val preferences = mutablePreferencesOf().apply {
            expected.toPrefMap().forEach { (key, value) -> setPrefValue(key, value) }
        }

        assertEquals(expected, preferences.toMangaSettings())
    }

    @Test
    fun `历史字符串类型按原键恢复漫画设置`() {
        val settings = mutablePreferencesOf(
            stringPreferencesKey(PreferKey.mangaScrollMode) to "5",
            stringPreferencesKey(PreferKey.mangaPreDownloadNum) to "22",
            stringPreferencesKey(PreferKey.enableMangaEInk) to "true",
            stringPreferencesKey(PreferKey.mangaClickActionMC) to "3",
        ).toMangaSettings()

        assertEquals(5, settings.scrollMode)
        assertEquals(22, settings.preDownloadNum)
        assertTrue(settings.enableEInk)
        assertEquals(3, settings.clickActionMC)
    }

    @Test
    fun `墨水屏与点击区域更新映射为同一批键值`() {
        val values = listOf(
            MangaSettingsUpdate.EInk(enabled = true, threshold = 188),
            MangaSettingsUpdate.ClickAction(MangaClickArea.MC, 0),
        ).toMangaPreferenceValues()

        assertEquals(true, values[PreferKey.enableMangaEInk])
        assertEquals(false, values[PreferKey.enableMangaGray])
        assertEquals(188, values[PreferKey.mangaEInkThreshold])
        assertEquals(0, values[PreferKey.mangaClickActionMC])
    }

    @Test
    fun `默认点击区域保留菜单入口`() {
        val settings = mutablePreferencesOf().toMangaSettings()

        assertTrue(settings.hasMenuClickArea())
        assertFalse(settings.enableGray)
    }
}
