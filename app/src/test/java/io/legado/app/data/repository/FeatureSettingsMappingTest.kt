package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSettingsMappingTest {

    @Test
    fun `空快照使用稳定默认值`() {
        val preferences = mutablePreferencesOf()

        assertEquals(AppShellSettings(), preferences.toAppShellSettings())
        assertEquals(ThemeSettings(), preferences.toThemeSettings())
    }

    @Test
    fun `应用壳设置沿用现有 key 并兼容历史字符串类型`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey(PreferKey.themeMode) to "2",
            stringPreferencesKey(PreferKey.fontScale) to "13",
            stringPreferencesKey(PreferKey.showStatusBar) to "false",
            stringPreferencesKey(PreferKey.useFloatingBottomBar) to "true",
            stringPreferencesKey(PreferKey.tabletInterface) to "landscape",
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
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey(PreferKey.enableBlur) to true,
            intPreferencesKey(PreferKey.topBarBlurRadius) to 18,
            intPreferencesKey(PreferKey.topBarOpacity) to 72,
            stringPreferencesKey(PreferKey.bgImage) to "content://theme/light",
            intPreferencesKey(PreferKey.bookInfoInputColor) to 0x102030,
        )

        val settings = preferences.toThemeSettings()

        assertEquals(true, settings.enableBlur)
        assertEquals(18, settings.topBarBlurRadius)
        assertEquals(72, settings.topBarOpacity)
        assertEquals("content://theme/light", settings.backgroundImageLight)
        assertEquals(0x102030, settings.bookInfoInputColor)
    }
}
