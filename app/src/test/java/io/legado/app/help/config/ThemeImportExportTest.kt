package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeImportExportTest {

    @Test
    fun `解析历史明文字段主题`() {
        val data = ThemeImportExport.parseLegacyThemeData(
            """{"appTheme":"12","themeMode":"2","mainNavigationOrder":"my,bookshelf"}"""
        )

        assertNotNull(data)
        assertEquals("12", data?.appTheme)
        assertEquals("2", data?.themeMode)
        assertEquals("my,bookshelf", data?.mainNavigationOrder)
    }

    @Test
    fun `解析历史混淆字段主题`() {
        val data = ThemeImportExport.parseLegacyThemeData(
            """{"a":"12","A":true,"y0":{}}"""
        )

        assertNotNull(data)
        assertEquals("12", data?.appTheme)
        assertEquals(true, data?.enableBlur)
    }
}
