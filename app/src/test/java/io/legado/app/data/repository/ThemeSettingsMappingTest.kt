package io.legado.app.data.repository

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.model.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThemeSettingsMappingTest {

    @Test
    fun `Theme gateway 持久化边界固定为 59 键`() {
        val actualKeys = ThemeSettings().toGatewayPrefMap().keys
        val expectedKeys = ThemeSettings().expectedGatewayPrefMap().keys

        assertEquals(59, actualKeys.size)
        assertEquals(expectedKeys, actualKeys)
        assertFalse(PreferKey.customMode in actualKeys)
        assertFalse(PreferKey.bookInfoInputColor in actualKeys)
    }

    @Test
    fun `Theme gateway 59 键写读映射逐字段对应`() {
        themeMappingSamples().forEach { expected ->
            assertEquals(expected.expectedGatewayPrefMap(), expected.toGatewayPrefMap())
            assertEquals(
                expected,
                expected.expectedGatewayPrefMap().toTestPreferences().toThemeSettings(),
            )
        }
    }

    @Test
    fun `gateway 排除字段仍由 Theme 读模型读取`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey(PreferKey.customMode) to "accent",
            intPreferencesKey(PreferKey.bookInfoInputColor) to 0x102030,
        )

        val settings = preferences.toThemeSettings()

        assertEquals("accent", settings.customMode)
        assertEquals(0x102030, settings.bookInfoInputColor)
    }

    @Test
    fun `关闭模糊通过真实原子路径同时关闭渐进模糊`() {
        val values = captureAtomicUpdateValues(
            current = ThemeSettings(enableBlur = true, enableProgressiveBlur = true),
            read = { it.toThemeSettings() },
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = {
                it.copy(enableBlur = false, enableProgressiveBlur = false)
            },
        )

        assertEquals(
            mapOf(
                PreferKey.enableBlur to false,
                PreferKey.enableProgressiveBlur to false,
            ),
            values,
        )
    }

    @Test
    fun `Miuix Monet 根据原子路径当前主题决定是否回退`() {
        fun enableMiuixMonet(current: ThemeSettings) = captureAtomicUpdateValues(
            current = current,
            read = { it.toThemeSettings() },
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = {
                it.copy(
                    useMiuixMonet = true,
                    appTheme = if (it.appTheme != "0" && it.appTheme != "12") {
                        "0"
                    } else {
                        it.appTheme
                    },
                )
            },
        )

        assertEquals(
            mapOf(
                PreferKey.appTheme to "0",
                PreferKey.useMiuixMonet to true,
            ),
            enableMiuixMonet(ThemeSettings(appTheme = "7")),
        )
        assertEquals(
            mapOf(PreferKey.useMiuixMonet to true),
            enableMiuixMonet(ThemeSettings(appTheme = "12")),
        )
    }

    @Test
    fun `透明主题通过真实原子路径同时写主题与容器透明度`() {
        val values = captureAtomicUpdateValues(
            current = ThemeSettings(appTheme = "0", containerOpacity = 80),
            read = { it.toThemeSettings() },
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = { it.copy(appTheme = "13", containerOpacity = 0) },
        )

        assertEquals(
            mapOf(
                PreferKey.appTheme to "13",
                PreferKey.containerOpacity to 0,
            ),
            values,
        )
    }

    @Test
    fun `清空背景路径通过真实原子路径删除对应键`() {
        val values = captureAtomicUpdateValues(
            current = ThemeSettings(backgroundImageLight = "old-background"),
            read = { it.toThemeSettings() },
            toPrefMap = ThemeSettings::toGatewayPrefMap,
            transform = { it.copy(backgroundImageLight = null) },
        )

        assertEquals(mapOf(PreferKey.bgImage to null), values)
    }
}

private fun themeMappingSamples(): List<ThemeSettings> {
    val base = ThemeSettings(
        appTheme = "app-theme",
        useMiuixMonet = false,
        isPureBlack = false,
        paletteStyle = "palette-style",
        materialVersion = "material-version",
        customContrast = "custom-contrast",
        customMode = "tonalSpot",
        appFontPath = "app-font",
        customPrimary = 101,
        customNightPrimary = 102,
        enableDeepPersonalization = false,
        themeColor = 103,
        secondaryThemeColor = 104,
        primaryTextColor = 105,
        secondaryTextColor = 106,
        themeBackgroundColor = 107,
        labelContainerColor = 108,
        themeColorNight = 109,
        secondaryThemeColorNight = 110,
        primaryTextColorNight = 111,
        secondaryTextColorNight = 112,
        themeBackgroundColorNight = 113,
        labelContainerColorNight = 114,
        containerOpacity = 115,
        overrideBaseCardCornerRadius = false,
        baseCardCornerRadius = 1.25f,
        overrideBaseCardBorder = false,
        baseCardBorderWidth = 2.5f,
        baseCardBorderColor = 116,
        baseCardBorderColorNight = 117,
        disableSplicedColumnGroupCornerRadius = false,
        topBarOpacity = 118,
        bottomBarOpacity = 119,
        enableBlur = false,
        enableProgressiveBlur = false,
        topBarBlurRadius = 120,
        bottomBarBlurRadius = 121,
        topBarBlurAlpha = 122,
        bottomBarBlurAlpha = 123,
        bottomBarLensRadius = 3.75f,
        useFlexibleTopAppBar = false,
        bookInfoFollowCoverColor = false,
        bookInfoNetworkCoverBackground = "network-background",
        bookInfoDefaultCoverBackground = "default-background",
        bookInfoInputColor = 0,
        backgroundImageLight = "light-background",
        backgroundImageDark = "dark-background",
        backgroundImageBlurring = 124,
        backgroundImageDarkBlurring = 125,
        enableItemDivider = false,
        itemDividerWidth = 4.5f,
        itemDividerLength = 5.75f,
        itemDividerColor = 126,
        eyeProtectionEnabled = false,
        colorTemperature = 127,
        eyeProtectionSchedule = false,
        eyeProtectionStartTime = "start-time",
        eyeProtectionEndTime = "end-time",
        showRefactorTip = false,
        enableCustomTagColors = false,
        customTagColorsJson = "tag-colors",
    )
    return listOf(
        base,
        base.copy(useMiuixMonet = true),
        base.copy(isPureBlack = true),
        base.copy(enableDeepPersonalization = true),
        base.copy(overrideBaseCardCornerRadius = true),
        base.copy(overrideBaseCardBorder = true),
        base.copy(disableSplicedColumnGroupCornerRadius = true),
        base.copy(enableBlur = true),
        base.copy(enableProgressiveBlur = true),
        base.copy(useFlexibleTopAppBar = true),
        base.copy(bookInfoFollowCoverColor = true),
        base.copy(enableItemDivider = true),
        base.copy(eyeProtectionEnabled = true),
        base.copy(eyeProtectionSchedule = true),
        base.copy(showRefactorTip = true),
        base.copy(enableCustomTagColors = true),
    )
}

private fun ThemeSettings.expectedGatewayPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.appTheme to appTheme,
    PreferKey.useMiuixMonet to useMiuixMonet,
    PreferKey.pureBlack to isPureBlack,
    PreferKey.paletteStyle to paletteStyle,
    PreferKey.materialVersion to materialVersion,
    PreferKey.customContrast to customContrast,
    PreferKey.appFontPath to appFontPath,
    PreferKey.cPrimary to customPrimary,
    PreferKey.cNPrimary to customNightPrimary,
    PreferKey.enableDeepPersonalization to enableDeepPersonalization,
    PreferKey.themeColor to themeColor,
    PreferKey.secondaryThemeColor to secondaryThemeColor,
    PreferKey.primaryTextColor to primaryTextColor,
    PreferKey.secondaryTextColor to secondaryTextColor,
    PreferKey.themeBackgroundColor to themeBackgroundColor,
    PreferKey.labelContainerColor to labelContainerColor,
    PreferKey.themeColorNight to themeColorNight,
    PreferKey.secondaryThemeColorNight to secondaryThemeColorNight,
    PreferKey.primaryTextColorNight to primaryTextColorNight,
    PreferKey.secondaryTextColorNight to secondaryTextColorNight,
    PreferKey.themeBackgroundColorNight to themeBackgroundColorNight,
    PreferKey.labelContainerColorNight to labelContainerColorNight,
    PreferKey.containerOpacity to containerOpacity,
    PreferKey.overrideBaseCardCornerRadius to overrideBaseCardCornerRadius,
    PreferKey.baseCardCornerRadius to baseCardCornerRadius,
    PreferKey.overrideBaseCardBorder to overrideBaseCardBorder,
    PreferKey.baseCardBorderWidth to baseCardBorderWidth,
    PreferKey.baseCardBorderColor to baseCardBorderColor,
    PreferKey.baseCardBorderColorNight to baseCardBorderColorNight,
    PreferKey.disableSplicedColumnGroupCornerRadius to disableSplicedColumnGroupCornerRadius,
    PreferKey.topBarOpacity to topBarOpacity,
    PreferKey.bottomBarOpacity to bottomBarOpacity,
    PreferKey.enableBlur to enableBlur,
    PreferKey.enableProgressiveBlur to enableProgressiveBlur,
    PreferKey.topBarBlurRadius to topBarBlurRadius,
    PreferKey.bottomBarBlurRadius to bottomBarBlurRadius,
    PreferKey.topBarBlurAlpha to topBarBlurAlpha,
    PreferKey.bottomBarBlurAlpha to bottomBarBlurAlpha,
    PreferKey.bottomBarLensRadius to bottomBarLensRadius,
    PreferKey.useFlexibleTopAppBar to useFlexibleTopAppBar,
    PreferKey.bookInfoFollowCoverColor to bookInfoFollowCoverColor,
    PreferKey.bookInfoNetworkCoverBackground to bookInfoNetworkCoverBackground,
    PreferKey.bookInfoDefaultCoverBackground to bookInfoDefaultCoverBackground,
    PreferKey.bgImage to backgroundImageLight,
    PreferKey.bgImageN to backgroundImageDark,
    PreferKey.bgImageBlurring to backgroundImageBlurring,
    PreferKey.bgImageNBlurring to backgroundImageDarkBlurring,
    PreferKey.enableItemDivider to enableItemDivider,
    PreferKey.itemDividerWidth to itemDividerWidth,
    PreferKey.itemDividerLength to itemDividerLength,
    PreferKey.itemDividerColor to itemDividerColor,
    PreferKey.eyeProtectionEnabled to eyeProtectionEnabled,
    PreferKey.colorTemperature to colorTemperature,
    PreferKey.eyeProtectionSchedule to eyeProtectionSchedule,
    PreferKey.eyeProtectionStartTime to eyeProtectionStartTime,
    PreferKey.eyeProtectionEndTime to eyeProtectionEndTime,
    LocalPreferencesKeys.SHOW_THEME_REFACTOR_TIP.name to showRefactorTip,
    PreferKey.enableCustomTagColors to enableCustomTagColors,
    PreferKey.customTagColors to customTagColorsJson,
)
