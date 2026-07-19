package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.ThemeExportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePackageSettingsMappingTest {

    @Test
    fun `主题包完整映射为同一批配置键值`() {
        val values = ThemeExportData(
            appTheme = "12",
            themeMode = "2",
            customMode = null,
            bookInfoBackgroundBlur = "off",
            bookInfoNetworkCoverBackground = null,
            bookInfoDefaultCoverBackground = "off_for_default",
            overrideBaseCardCornerRadius = true,
            baseCardCornerRadius = 20.5f,
            overrideBaseCardBorder = true,
            baseCardBorderWidth = 1.5f,
            baseCardBorderColor = 0x11223344,
            baseCardBorderColorNight = 0x55667788,
            disableSplicedColumnGroupCornerRadius = true,
            bottomBarLensRadius = 31.5f,
            mainNavigationOrder = "bookshelf,home,my",
            bgImageLight = "/light.jpg",
            coverDefaultImageDark = "/dark-cover.jpg",
            assets = mapOf("bgImageLight" to "base64"),
        ).toPreferenceValues()

        assertEquals("12", values[PreferKey.appTheme])
        assertEquals("2", values[PreferKey.themeMode])
        assertNull(values[PreferKey.customMode])
        assertEquals("off", values[PreferKey.bookInfoNetworkCoverBackground])
        assertEquals("off_for_default", values[PreferKey.bookInfoDefaultCoverBackground])
        assertEquals(true, values[PreferKey.overrideBaseCardCornerRadius])
        assertEquals(20.5f, values[PreferKey.baseCardCornerRadius])
        assertEquals(true, values[PreferKey.overrideBaseCardBorder])
        assertEquals(1.5f, values[PreferKey.baseCardBorderWidth])
        assertEquals(0x11223344, values[PreferKey.baseCardBorderColor])
        assertEquals(0x55667788, values[PreferKey.baseCardBorderColorNight])
        assertEquals(true, values[PreferKey.disableSplicedColumnGroupCornerRadius])
        assertEquals(31.5f, values[PreferKey.bottomBarLensRadius])
        assertEquals("bookshelf,home,my", values[PreferKey.mainNavigationOrder])
        assertEquals("/light.jpg", values[PreferKey.bgImage])
        assertEquals("/dark-cover.jpg", values[PreferKey.defaultCoverDark])
        assertFalse(values.containsKey("assets"))
    }
}
