package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSettingsMappingTest {

    @Test
    fun `空快照使用稳定默认值`() {
        val preferences = emptyMap<String, PreferenceValue>()

        assertEquals(AppShellSettings(), preferences.toAppShellSettings())
        assertEquals(ThemeSettings(), preferences.toThemeSettings())
    }

    @Test
    fun `应用壳设置沿用现有 key 并兼容历史字符串类型`() {
        val preferences = mapOf(
            PreferKey.themeMode to PreferenceValue.StringValue("2"),
            PreferKey.fontScale to PreferenceValue.StringValue("13"),
            PreferKey.showStatusBar to PreferenceValue.StringValue("false"),
            PreferKey.useFloatingBottomBar to PreferenceValue.StringValue("true"),
            PreferKey.tabletInterface to PreferenceValue.StringValue("landscape"),
        )

        val settings = preferences.toAppShellSettings()

        assertEquals("2", settings.themeMode)
        assertEquals(13, settings.fontScale)
        assertEquals(false, settings.showStatusBar)
        assertEquals(true, settings.useFloatingBottomBar)
        assertEquals("landscape", settings.tabletInterface)
    }

    @Test
    fun `主题设置沿用现有 key 并映射公共渲染配置`() {
        val preferences = mapOf(
            PreferKey.enableBlur to PreferenceValue.BooleanValue(true),
            PreferKey.topBarBlurRadius to PreferenceValue.IntValue(18),
            PreferKey.topBarOpacity to PreferenceValue.IntValue(72),
            PreferKey.bgImage to PreferenceValue.StringValue("content://theme/light"),
            PreferKey.bookInfoInputColor to PreferenceValue.IntValue(0x102030),
        )

        val settings = preferences.toThemeSettings()

        assertEquals(true, settings.enableBlur)
        assertEquals(18, settings.topBarBlurRadius)
        assertEquals(72, settings.topBarOpacity)
        assertEquals("content://theme/light", settings.backgroundImageLight)
        assertEquals(0x102030, settings.bookInfoInputColor)
    }
}
