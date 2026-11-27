package io.legado.app.data.repository

import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.NavigationAnimationStyle
import io.legado.app.domain.model.settings.TextAnimationLevel
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.model.settings.diffFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiConfigurationEInkOverrideTest {

    @Test
    fun `开启全局电子书模式时运行时覆写主题、动画和模糊且保留深浅色与字体`() {
        val raw = AppUiConfiguration(
            appShell = AppShellSettings(
                eInkGlobalMode = true,
                themeMode = "2",
                fontScale = 12,
                navigationAnimationStyle = NavigationAnimationStyle.Miuix,
                textAnimationLevel = TextAnimationLevel.Strong,
                predictiveBackEnabled = true,
                readerSharedElementEnabled = true,
                swipeAnimation = true,
                useFloatingBottomBarLiquidGlass = true,
            ),
            theme = ThemeSettings(
                appTheme = "1",
                appFontPath = "/fonts/custom.ttf",
                enableBlur = true,
                enableProgressiveBlur = true,
                mochiTopBarLiquidGlass = true,
                mochiScrollEdgeBlurEnabled = true,
                miuixTopBarLiquidGlass = true,
            ),
        )

        val effective = raw.applyRuntimeOverrides()

        assertEquals("4", effective.theme.appTheme)
        assertEquals(NavigationAnimationStyle.None, effective.appShell.navigationAnimationStyle)
        assertEquals(TextAnimationLevel.Off, effective.appShell.textAnimationLevel)
        assertFalse(effective.appShell.predictiveBackEnabled)
        assertFalse(effective.appShell.readerSharedElementEnabled)
        assertFalse(effective.appShell.swipeAnimation)
        assertFalse(effective.appShell.useFloatingBottomBarLiquidGlass)
        assertFalse(effective.theme.enableBlur)
        assertFalse(effective.theme.enableProgressiveBlur)
        assertFalse(effective.theme.mochiTopBarLiquidGlass)
        assertFalse(effective.theme.mochiScrollEdgeBlurEnabled)
        assertFalse(effective.theme.miuixTopBarLiquidGlass)

        // 保留用户深浅色与字体设置
        assertEquals("2", effective.appShell.themeMode)
        assertEquals(12, effective.appShell.fontScale)
        assertEquals("/fonts/custom.ttf", effective.theme.appFontPath)
    }

    @Test
    fun `关闭全局电子书模式时无损还原原始主题与动画配置`() {
        val prefsOff = (
            AppShellSettings(
                eInkGlobalMode = false,
                navigationAnimationStyle = NavigationAnimationStyle.Scale,
                textAnimationLevel = TextAnimationLevel.Standard,
                predictiveBackEnabled = true,
                readerSharedElementEnabled = true,
                swipeAnimation = true,
            ).toPrefMap() + ThemeSettings(
                appTheme = "2",
                enableBlur = true,
            ).toGatewayPrefMap()
            ).toTestPreferences()

        val prefsOn = (
            AppShellSettings(
                eInkGlobalMode = true,
                navigationAnimationStyle = NavigationAnimationStyle.Scale,
                textAnimationLevel = TextAnimationLevel.Standard,
                predictiveBackEnabled = true,
                readerSharedElementEnabled = true,
                swipeAnimation = true,
            ).toPrefMap() + ThemeSettings(
                appTheme = "2",
                enableBlur = true,
            ).toGatewayPrefMap()
            ).toTestPreferences()

        val configOn = prefsOn.toAppUiConfiguration("auto", isSystemDarkTheme = false)
        val configOff = prefsOff.toAppUiConfiguration("auto", isSystemDarkTheme = false)

        assertEquals("4", configOn.theme.appTheme)
        assertEquals(NavigationAnimationStyle.None, configOn.appShell.navigationAnimationStyle)
        assertEquals("2", configOff.theme.appTheme)
        assertEquals(NavigationAnimationStyle.Scale, configOff.appShell.navigationAnimationStyle)
        assertEquals(TextAnimationLevel.Standard, configOff.appShell.textAnimationLevel)
        assertTrue(configOff.theme.enableBlur)
        assertTrue(configOn.diffFrom(configOff).themeChanged)
    }
}
