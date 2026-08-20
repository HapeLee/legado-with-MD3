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
import kotlin.reflect.full.primaryConstructor

class ReadSettingsMappingTest {

    @Test
    fun `gateway 持久化映射覆盖 ReadSettings 全部 109 个字段`() {
        val actualKeys = ReadSettings().toGatewayPrefMap().keys
        val expectedKeys = ReadSettings().expectedGatewayPrefMap().keys

        assertEquals(109, actualKeys.size)
        assertEquals(expectedKeys, actualKeys)
    }

    /**
     * 上面那条比对的是两张手写表，防串键；这条防的是「新增字段忘了加进映射」——
     * 漏掉的字段走通用 `update {}` 会被静默丢写。字段数由反射得出，不写死。
     */
    @Test
    fun `新增 ReadSettings 字段必须同时加进 gateway 映射`() {
        val fieldCount = ReadSettings::class.primaryConstructor!!.parameters.size
        val mappedCount = ReadSettings().toGatewayPrefMap().size

        assertEquals(
            "ReadSettings 有 $fieldCount 个字段，toGatewayPrefMap 只映射了 $mappedCount 个。" +
                "未映射的字段经 update {} 写入会被静默丢弃——请补齐映射与本文件的 " +
                "expectedGatewayPrefMap。",
            fieldCount,
            mappedCount,
        )
    }

    @Test
    fun `阅读设置 gateway 全部键写读映射逐字段对应`() {
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
        assertEquals(85, settings.readMenuBlurAlpha)
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
        readMenuFloatingIconLiquidGlass = true,
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
        menuAlpha = 1001,
        expandTextMenu = true,
        showSelectMenuIcon = false,
        autoReadSpeed = 1004,
        tocUiUseReplace = true,
        tocCountWords = false,
        useNewTocSheet = false,
        maxLengthWithNoToc = 3000,
        readStyleSelect = 1007,
        comicStyleSelect = 1008,
        shareLayout = true,
        readBarStyleFollowPage = true,
        readBarStyle = 1011,
        clickActionTL = 1012,
        clickActionTC = 1013,
        clickActionTR = 1014,
        clickActionML = 1015,
        clickActionMC = 1016,
        clickActionMR = 1017,
        clickActionBL = 1018,
        clickActionBC = 1019,
        clickActionBR = 1020,
        readMenuBgColor = 1021,
        readMenuAccentColor = 1022,
        readMenuContainerColor = 1023,
        readMenuBgColorNight = 1024,
        readMenuAccentColorNight = 1025,
        readMenuContainerColorNight = 1026,
        readMenuTextColor = 1027,
        readMenuTextColorNight = 1028,
        readMenuColorMode = 1029,
        readMenuIconShowText = true,
        readMenuIconStyle = 1031,
        titleBarIconStyle = 1032,
        readMenuIconItemsPerRow = 1033,
        readMenuIconRowCount = 1034,
        readMenuBottomCornerRadius = 1035,
        readMenuFloatingBottomBar = false,
        readMenuTopBarBlurMode = 1037,
        readMenuBottomBarBlurMode = 1038,
        readMenuTopBarLiquidGlassButtons = true,
        readMenuTopBarMergeButtons = true,
        readMenuTopBarTitleCapsule = true,
        readMenuBottomBarLiquidGlassButtons = true,
        readMenuTopBarBlurStyle = 1042,
        readMenuBottomBarBlurStyle = 1043,
        readMenuBlurRadius = 1044,
        readMenuBlurColor = 1045,
        readMenuBlurColorNight = 1046,
        readMenuPaletteStyle = "readMenuPalett-47",
        readMenuLensRadius = 1048f,
        readMenuBorderWidth = 1049,
        readMenuBorderColor = 1050,
        readMenuBorderColorNight = 1051,
        readMenuCustomIcons = "readMenuCustom-52",
        titleBarCustomIcons = "titleBarCustom-53",
        titleBarIconPosition = 1054,
        showTitleBarIcons = true,
        chineseConverterType = 1056,
    )
    return listOf(
        ReadSettings(),
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
        base.copy(brightnessAuto = true),
        base.copy(useUnderline = true),
        base.copy(mouseWheelPage = false),
        base.copy(volumeKeyPage = false),
        base.copy(volumeKeyPageOnPlay = false),
        base.copy(keyPageOnLongPress = true),
        base.copy(swipeToAddBookmark = true),
        base.copy(bookmarkBadgeImage = "badge.svg"),
        base.copy(bookmarkBadgeSize = 24),
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
        base.copy(moreActionsConfig = "change_source,refresh"),
    )
}

private fun ReadSettings.expectedGatewayPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.screenOrientation to screenOrientation,
    PreferKey.keepLight to keepLight,
    PreferKey.hideStatusBar to hideStatusBar,
    PreferKey.hideNavigationBar to hideNavigationBar,
    PreferKey.paddingDisplayCutouts to paddingDisplayCutouts,
    PreferKey.titleBarMode to titleBarMode,
    PreferKey.menuAlpha to menuAlpha,
    PreferKey.readBodyToLh to readBodyToLh,
    PreferKey.defaultSourceChangeAll to defaultSourceChangeAll,
    PreferKey.textFullJustify to textFullJustify,
    PreferKey.textBottomJustify to textBottomJustify,
    PreferKey.adaptSpecialStyle to adaptSpecialStyle,
    PreferKey.useZhLayout to useZhLayout,
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
    PreferKey.swipeToAddBookmark to swipeToAddBookmark,
    PreferKey.bookmarkBadgeImage to bookmarkBadgeImage,
    PreferKey.bookmarkBadgeSize to bookmarkBadgeSize,
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
    PreferKey.expandTextMenu to expandTextMenu,
    PreferKey.showSelectMenuIcon to showSelectMenuIcon,
    PreferKey.textSelectMenuConfig to textSelectMenuConfig,
    PreferKey.showReadTitleAddition to showReadTitleAddition,
    PreferKey.autoReadSpeed to autoReadSpeed,
    PreferKey.systemTypefaces to systemTypefaces,
    PreferKey.preDownloadNum to preDownloadNum,
    PreferKey.prevKeys to prevKeys,
    PreferKey.nextKeys to nextKeys,
    PreferKey.tocUiUseReplace to tocUiUseReplace,
    PreferKey.tocCountWords to tocCountWords,
    PreferKey.useNewTocSheet to useNewTocSheet,
    PreferKey.maxLengthWithNoToc to maxLengthWithNoToc,
    PreferKey.readUrlOpenInBrowser to readUrlInBrowser,
    PreferKey.readStyleSelect to readStyleSelect,
    PreferKey.comicStyleSelect to comicStyleSelect,
    PreferKey.shareLayout to shareLayout,
    PreferKey.readBarStyleFollowPage to readBarStyleFollowPage,
    PreferKey.readBarStyle to readBarStyle,
    PreferKey.clickActionTL to clickActionTL,
    PreferKey.clickActionTC to clickActionTC,
    PreferKey.clickActionTR to clickActionTR,
    PreferKey.clickActionML to clickActionML,
    PreferKey.clickActionMC to clickActionMC,
    PreferKey.clickActionMR to clickActionMR,
    PreferKey.clickActionBL to clickActionBL,
    PreferKey.clickActionBC to clickActionBC,
    PreferKey.clickActionBR to clickActionBR,
    PreferKey.fontFolder to fontFolder,
    PreferKey.readMenuBgColor to readMenuBgColor,
    PreferKey.readMenuAccentColor to readMenuAccentColor,
    PreferKey.readMenuContainerColor to readMenuContainerColor,
    PreferKey.readMenuBgColorNight to readMenuBgColorNight,
    PreferKey.readMenuAccentColorNight to readMenuAccentColorNight,
    PreferKey.readMenuContainerColorNight to readMenuContainerColorNight,
    PreferKey.readMenuTextColor to readMenuTextColor,
    PreferKey.readMenuTextColorNight to readMenuTextColorNight,
    PreferKey.readMenuColorMode to readMenuColorMode,
    PreferKey.readMenuIconShowText to readMenuIconShowText,
    PreferKey.readMenuIconStyle to readMenuIconStyle,
    PreferKey.titleBarIconStyle to titleBarIconStyle,
    PreferKey.readMenuIconItemsPerRow to readMenuIconItemsPerRow,
    PreferKey.readMenuIconRowCount to readMenuIconRowCount,
    PreferKey.readMenuBottomCornerRadius to readMenuBottomCornerRadius,
    PreferKey.readMenuFloatingBottomBar to readMenuFloatingBottomBar,
    PreferKey.readMenuTopBarBlurMode to readMenuTopBarBlurMode,
    PreferKey.readMenuBottomBarBlurMode to readMenuBottomBarBlurMode,
    PreferKey.readMenuTopBarLiquidGlassButtons to readMenuTopBarLiquidGlassButtons,
    PreferKey.readMenuTopBarMergeButtons to readMenuTopBarMergeButtons,
    PreferKey.readMenuTopBarTitleCapsule to readMenuTopBarTitleCapsule,
    PreferKey.readMenuBottomBarLiquidGlassButtons to readMenuBottomBarLiquidGlassButtons,
    PreferKey.readMenuFloatingIconLiquidGlass to readMenuFloatingIconLiquidGlass,
    PreferKey.readMenuTopBarBlurStyle to readMenuTopBarBlurStyle,
    PreferKey.readMenuBottomBarBlurStyle to readMenuBottomBarBlurStyle,
    PreferKey.readMenuBlurRadius to readMenuBlurRadius,
    PreferKey.readMenuBlurAlpha to readMenuBlurAlpha,
    PreferKey.readMenuBlurColor to readMenuBlurColor,
    PreferKey.readMenuBlurColorNight to readMenuBlurColorNight,
    PreferKey.readMenuPaletteStyle to readMenuPaletteStyle,
    PreferKey.readMenuLensRadius to readMenuLensRadius,
    PreferKey.readMenuBorderWidth to readMenuBorderWidth,
    PreferKey.readMenuBorderColor to readMenuBorderColor,
    PreferKey.readMenuBorderColorNight to readMenuBorderColorNight,
    PreferKey.readMenuCustomIcons to readMenuCustomIcons,
    PreferKey.titleBarCustomIcons to titleBarCustomIcons,
    PreferKey.titleBarIconPosition to titleBarIconPosition,
    PreferKey.showTitleBarIcons to showTitleBarIcons,
    PreferKey.chineseConverterType to chineseConverterType,
    PreferKey.showMenuIcon to showMenuIcon,
    PreferKey.titleBarCompact to titleBarCompact,
    PreferKey.moreActionsConfig to moreActionsConfig,
)
