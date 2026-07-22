package io.legado.app.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSettingsMappingTest {

    @Test
    fun `gateway 持久化边界固定为显式声明的 48 键`() {
        val actualKeys = ReadSettings().toGatewayPrefMap().keys
        val expectedKeys = ReadSettings().expectedGatewayPrefMap().keys

        assertEquals(48, actualKeys.size)
        assertEquals(expectedKeys, actualKeys)
    }

    @Test
    fun `阅读设置 gateway 48 键写读映射逐字段对应`() {
        val repository = ReadSettingsRepository(
            settingsRepository = SettingsRepository(),
            preferencesFlow = MutableStateFlow(mutablePreferencesOf()),
        )

        readSettingsMappingSamples().forEach { expected ->
            assertEquals(expected.expectedGatewayPrefMap(), expected.toGatewayPrefMap())
            val actual = with(repository) {
                expected.expectedGatewayPrefMap().toTestPreferences().toReadSettings()
            }
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `PageKeys previous next 通过真实原子路径对称单批写入`() {
        val repository = ReadSettingsRepository(
            settingsRepository = SettingsRepository(),
            preferencesFlow = MutableStateFlow(mutablePreferencesOf()),
        )
        val values = captureAtomicUpdateValues(
            current = ReadSettings(prevKeys = "old-prev", nextKeys = "old-next"),
            read = { with(repository) { it.toReadSettings() } },
            toPrefMap = ReadSettings::toGatewayPrefMap,
            transform = { it.copy(prevKeys = "new-prev", nextKeys = "new-next") },
        )

        assertEquals(
            mapOf(
                PreferKey.prevKeys to "new-prev",
                PreferKey.nextKeys to "new-next",
            ),
            values,
        )
    }

    @Test
    fun `空快照使用精简阅读菜单默认值`() {
        val preferences = mutablePreferencesOf()
        val repository = ReadSettingsRepository(
            settingsRepository = SettingsRepository(),
            preferencesFlow = MutableStateFlow(preferences),
        )

        val settings = with(repository) {
            preferences.toReadSettings()
        }

        assertEquals("0", settings.showBrightnessView)
        assertEquals(1, settings.readBarStyle)
        assertFalse(settings.readMenuIconShowText)
        assertTrue(settings.readMenuFloatingBottomBar)
        assertEquals(ReadMenuBlurStyle.Solid, settings.readMenuTopBarBlurStyle)
        assertEquals(100, settings.readMenuBlurAlpha)
        assertEquals(1, settings.readMenuBorderWidth)
        assertEquals(3, settings.titleBarIconPosition)
        assertFalse(settings.showTitleBarIcons)
        assertFalse(settings.readMenuFloatingIconLiquidGlass)
        assertFalse(settings.showMenuIcon)
        assertFalse(settings.titleBarCompact)
    }
}

private fun readSettingsMappingSamples(): List<ReadSettings> {
    val base = ReadSettings(
        screenOrientation = "orientation",
        keepLight = "keep-light",
        titleBarMode = "title-mode",
        readMenuBlurAlpha = 37,
        eyeProtectionIntensity = 73,
        showBrightnessView = "brightness-view",
        brightnessVwPos = "brightness-pos",
        readBrightness = 73,
        readSliderMode = "slider-mode",
        doubleHorizontalPage = "double-page",
        progressBarBehavior = "chapter",
        pageTouchSlop = 19,
        clickImgWay = "click-way",
        textSelectMenuConfig = "select-menu",
        prevKeys = "previous-keys",
        nextKeys = "next-keys",
        fontFolder = "font-folder",
        systemTypefaces = 23,
        preDownloadNum = 29,
    )
    return listOf(
        base,
        base.copy(hideStatusBar = true),
        base.copy(hideNavigationBar = true),
        base.copy(paddingDisplayCutouts = true),
        base.copy(readBodyToLh = false),
        base.copy(defaultSourceChangeAll = false),
        base.copy(textFullJustify = false),
        base.copy(textBottomJustify = false),
        base.copy(adaptSpecialStyle = false),
        base.copy(useZhLayout = true),
        base.copy(eyeProtectionEnabled = true),
        base.copy(eyeProtectionIntensity = 87),
        base.copy(eyeProtectionAutoNight = true),
        base.copy(brightnessAuto = true),
        base.copy(useUnderline = true),
        base.copy(mouseWheelPage = false),
        base.copy(volumeKeyPage = false),
        base.copy(volumeKeyPageOnPlay = false),
        base.copy(keyPageOnLongPress = true),
        base.copy(sliderVibrator = true),
        base.copy(selectVibrator = true),
        base.copy(autoChangeSource = false),
        base.copy(autoSuggestDayNight = true),
        base.copy(selectText = false),
        base.copy(noAnimScrollPage = true),
        base.copy(optimizeRender = true),
        base.copy(disableReturnKey = true),
        base.copy(showReadTitleAddition = false),
        base.copy(readUrlInBrowser = true),
        base.copy(showMenuIcon = true),
        base.copy(titleBarCompact = true),
    )
}

private fun ReadSettings.expectedGatewayPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.screenOrientation to screenOrientation,
    PreferKey.keepLight to keepLight,
    PreferKey.hideStatusBar to hideStatusBar,
    PreferKey.hideNavigationBar to hideNavigationBar,
    PreferKey.paddingDisplayCutouts to paddingDisplayCutouts,
    PreferKey.titleBarMode to titleBarMode,
    PreferKey.readMenuBlurAlpha to readMenuBlurAlpha,
    PreferKey.readBodyToLh to readBodyToLh,
    PreferKey.defaultSourceChangeAll to defaultSourceChangeAll,
    PreferKey.textFullJustify to textFullJustify,
    PreferKey.textBottomJustify to textBottomJustify,
    PreferKey.adaptSpecialStyle to adaptSpecialStyle,
    PreferKey.useZhLayout to useZhLayout,
    PreferKey.eyeProtectionEnabled to eyeProtectionEnabled,
    PreferKey.colorTemperature to eyeProtectionIntensity.coerceIn(0, 100),
    PreferKey.eyeProtectionAutoNight to eyeProtectionAutoNight,
    PreferKey.showBrightnessView to showBrightnessView,
    PreferKey.brightnessVwPos to brightnessVwPos,
    PreferKey.brightness to readBrightness,
    PreferKey.brightnessAuto to brightnessAuto,
    PreferKey.useUnderline to useUnderline,
    PreferKey.readSliderMode to readSliderMode,
    PreferKey.doublePageHorizontal to doubleHorizontalPage,
    PreferKey.progressBarBehavior to progressBarBehavior,
    PreferKey.mouseWheelPage to mouseWheelPage,
    PreferKey.volumeKeyPage to volumeKeyPage,
    PreferKey.volumeKeyPageOnPlay to volumeKeyPageOnPlay,
    PreferKey.keyPageOnLongPress to keyPageOnLongPress,
    PreferKey.pageTouchSlop to pageTouchSlop,
    PreferKey.sliderVibrator to sliderVibrator,
    PreferKey.selectVibrator to selectVibrator,
    PreferKey.autoChangeSource to autoChangeSource,
    PreferKey.autoSuggestDayNight to autoSuggestDayNight,
    PreferKey.selectText to selectText,
    PreferKey.noAnimScrollPage to noAnimScrollPage,
    PreferKey.clickImgWay to clickImgWay,
    PreferKey.optimizeRender to optimizeRender,
    PreferKey.disableReturnKey to disableReturnKey,
    PreferKey.showReadTitleAddition to showReadTitleAddition,
    PreferKey.textSelectMenuConfig to textSelectMenuConfig,
    PreferKey.readUrlOpenInBrowser to readUrlInBrowser,
    PreferKey.showMenuIcon to showMenuIcon,
    PreferKey.titleBarCompact to titleBarCompact,
    PreferKey.prevKeys to prevKeys,
    PreferKey.nextKeys to nextKeys,
    PreferKey.fontFolder to fontFolder,
    PreferKey.systemTypefaces to systemTypefaces,
    PreferKey.preDownloadNum to preDownloadNum,
)
