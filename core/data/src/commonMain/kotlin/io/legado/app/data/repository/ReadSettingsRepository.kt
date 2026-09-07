package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

typealias ReadPreferences = ReadSettings

class ReadSettingsRepository(
    private val settingsRepository: PreferenceStore,
) : ReadSettingsGateway {

    override val currentSettings: ReadSettings
        get() = settingsRepository.currentSnapshot().toReadSettings()

    override val settings: Flow<ReadSettings> = settingsRepository
        .observeSnapshot()
        .map { snapshot ->
            snapshot.toReadSettings()
        }

    val preferences: Flow<ReadPreferences> = settings

    override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
        settingsRepository.atomicUpdateSettings(
            read = { it.toReadSettings() },
            toPrefMap = ReadSettings::toGatewayPrefMap,
            transform = transform,
        )
    }

    suspend fun setScreenOrientation(value: String) =
        settingsRepository.setString(PreferKey.screenOrientation, value)

    suspend fun setKeepLight(value: String) =
        settingsRepository.setString(PreferKey.keepLight, value)

    suspend fun setHideStatusBar(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.hideStatusBar, value)

    suspend fun setHideNavigationBar(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.hideNavigationBar, value)

    suspend fun setPaddingDisplayCutouts(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.paddingDisplayCutouts, value)

    suspend fun setTitleBarMode(value: String) =
        settingsRepository.setString(PreferKey.titleBarMode, value)

    suspend fun setMenuAlpha(value: Int) =
        settingsRepository.setInt(PreferKey.menuAlpha, value)

    suspend fun setReadBodyToLh(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readBodyToLh, value)

    suspend fun setDefaultSourceChangeAll(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.defaultSourceChangeAll, value)

    suspend fun setTextFullJustify(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.textFullJustify, value)

    suspend fun setTextBottomJustify(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.textBottomJustify, value)

    suspend fun setAdaptSpecialStyle(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.adaptSpecialStyle, value)

    suspend fun setUseZhLayout(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.useZhLayout, value)

    suspend fun setShowBrightnessView(value: String) =
        settingsRepository.setString(PreferKey.showBrightnessView, value)

    suspend fun setBrightnessVwPos(value: String) =
        settingsRepository.setString(PreferKey.brightnessVwPos, value)

    suspend fun setReadBrightness(value: Int) =
        settingsRepository.setInt(PreferKey.brightness, value)

    suspend fun setBrightnessAuto(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.brightnessAuto, value)

    suspend fun setUseUnderline(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.useUnderline, value)

    suspend fun setReadSliderMode(value: String) =
        settingsRepository.setString(PreferKey.readSliderMode, value)

    suspend fun setDoubleHorizontalPage(value: String) =
        settingsRepository.setString(PreferKey.doublePageHorizontal, value)

    suspend fun setProgressBarBehavior(value: String) =
        settingsRepository.setString(PreferKey.progressBarBehavior, value)

    suspend fun setMouseWheelPage(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.mouseWheelPage, value)

    suspend fun setVolumeKeyPage(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.volumeKeyPage, value)

    suspend fun setVolumeKeyPageOnPlay(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.volumeKeyPageOnPlay, value)

    suspend fun setKeyPageOnLongPress(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.keyPageOnLongPress, value)

    suspend fun setPageTouchSlop(value: Int) =
        settingsRepository.setInt(PreferKey.pageTouchSlop, value)

    suspend fun setSliderVibrator(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.sliderVibrator, value)

    suspend fun setUseNewTocSheet(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.useNewTocSheet, value)

    suspend fun setMaxLengthWithNoToc(value: Int) =
        settingsRepository.setInt(PreferKey.maxLengthWithNoToc, value)

    suspend fun setSelectVibrator(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.selectVibrator, value)

    suspend fun setAutoChangeSource(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.autoChangeSource, value)

    suspend fun setAutoSuggestDayNight(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.autoSuggestDayNight, value)

    suspend fun setReadingAnchorEnabled(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readingAnchorEnabled, value)

    suspend fun setReadAloudDetachReminderEnabled(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readAloudDetachReminderEnabled, value)

    suspend fun setSelectText(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.selectText, value)

    suspend fun setNoAnimScrollPage(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.noAnimScrollPage, value)

    suspend fun setClickImgWay(value: String) =
        settingsRepository.setString(PreferKey.clickImgWay, value)

    suspend fun setOptimizeRender(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.optimizeRender, value)

    suspend fun setDisableReturnKey(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.disableReturnKey, value)

    suspend fun setExpandTextMenu(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.expandTextMenu, value)

    suspend fun setShowSelectMenuIcon(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.showSelectMenuIcon, value)

    suspend fun setShowReadTitleAddition(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.showReadTitleAddition, value)

    suspend fun setAutoReadSpeed(value: Int) =
        settingsRepository.setInt(PreferKey.autoReadSpeed, value)

    suspend fun setSystemTypefaces(value: Int) =
        settingsRepository.setInt(PreferKey.systemTypefaces, value)

    suspend fun setPreDownloadNum(value: Int) =
        settingsRepository.setInt(PreferKey.preDownloadNum, value)

    suspend fun setPageKeys(prevKeys: String, nextKeys: String) {
        settingsRepository.setStrings(
            mapOf(
                PreferKey.prevKeys to prevKeys,
                PreferKey.nextKeys to nextKeys
            )
        )
    }

    suspend fun setTocUiUseReplace(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.tocUiUseReplace, value)

    suspend fun setTocCountWords(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.tocCountWords, value)

    suspend fun setReadStyleSelect(value: Int) =
        settingsRepository.setInt(PreferKey.readStyleSelect, value)

    suspend fun setComicStyleSelect(value: Int) =
        settingsRepository.setInt(PreferKey.comicStyleSelect, value)

    suspend fun setShareLayout(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.shareLayout, value)

    suspend fun setReadBarStyleFollowPage(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readBarStyleFollowPage, value)

    suspend fun setReadBarStyle(value: Int) =
        settingsRepository.setInt(PreferKey.readBarStyle, value.coerceIn(0, 2))

    suspend fun setClickAction(key: String, value: Int) =
        settingsRepository.setInt(key, value)

    suspend fun setFontFolder(value: String) =
        settingsRepository.setString(PreferKey.fontFolder, value)

    suspend fun setReadMenuBgColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBgColor, value)

    suspend fun setReadMenuAccentColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuAccentColor, value)

    suspend fun setReadMenuContainerColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuContainerColor, value)

    suspend fun setReadMenuBgColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBgColorNight, value)

    suspend fun setReadMenuAccentColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuAccentColorNight, value)

    suspend fun setReadMenuContainerColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuContainerColorNight, value)

    suspend fun setReadMenuTextColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuTextColor, value)

    suspend fun setReadMenuTextColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuTextColorNight, value)

    suspend fun setReadMenuColorMode(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuColorMode, value.coerceIn(0, 1))

    suspend fun setReadMenuIconShowText(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuIconShowText, value)

    suspend fun setReadMenuIconStyle(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuIconStyle, value.coerceIn(0, 2))

    suspend fun setTitleBarIconStyle(value: Int) =
        settingsRepository.setInt(PreferKey.titleBarIconStyle, value.coerceIn(0, 2))

    suspend fun setReadMenuIconItemsPerRow(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuIconItemsPerRow, value.coerceIn(2, 8))

    suspend fun setReadMenuIconRowCount(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuIconRowCount, value.coerceIn(1, 2))

    suspend fun setReadMenuBottomCornerRadius(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBottomCornerRadius, value.coerceIn(0, 32))

    suspend fun setReadMenuFloatingBottomBar(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuFloatingBottomBar, value)

    suspend fun setReadMenuTopBarBlurMode(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuTopBarBlurMode, value.coerceIn(0, 2))

    suspend fun setReadMenuBottomBarBlurMode(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBottomBarBlurMode, value.coerceIn(0, 2))

    suspend fun setReadMenuTopBarLiquidGlassButtons(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuTopBarLiquidGlassButtons, value)

    suspend fun setReadMenuTopBarMergeButtons(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuTopBarMergeButtons, value)

    suspend fun setReadMenuTopBarTitleCapsule(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuTopBarTitleCapsule, value)

    suspend fun setReadMenuBottomBarLiquidGlassButtons(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuBottomBarLiquidGlassButtons, value)

    suspend fun setReadMenuFloatingIconLiquidGlass(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.readMenuFloatingIconLiquidGlass, value)

    suspend fun setReadMenuTopBarBlurStyle(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuTopBarBlurStyle, value.coerceIn(0, 1))

    suspend fun setReadMenuBottomBarBlurStyle(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBottomBarBlurStyle, value.coerceIn(0, 1))

    suspend fun setReadMenuBlurRadius(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBlurRadius, value.coerceIn(0, 32))

    suspend fun setReadMenuBlurAlpha(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBlurAlpha, value.coerceIn(0, 100))

    suspend fun setReadMenuBlurColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBlurColor, value)

    suspend fun setReadMenuBlurColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBlurColorNight, value)

    suspend fun setReadMenuPaletteStyle(value: String) =
        settingsRepository.setString(PreferKey.readMenuPaletteStyle, value)

    suspend fun setReadMenuLensRadius(value: Float) =
        settingsRepository.setFloat(PreferKey.readMenuLensRadius, value.coerceIn(0f, 48f))

    suspend fun setReadMenuBorderWidth(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBorderWidth, value.coerceIn(0, 4))

    suspend fun setReadMenuBorderColor(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBorderColor, value)

    suspend fun setReadMenuBorderColorNight(value: Int) =
        settingsRepository.setInt(PreferKey.readMenuBorderColorNight, value)

    suspend fun setReadMenuCustomIcons(value: String) =
        settingsRepository.setString(PreferKey.readMenuCustomIcons, value)

    suspend fun setTitleBarCustomIcons(value: String) =
        settingsRepository.setString(PreferKey.titleBarCustomIcons, value)

    suspend fun setTitleBarIconPosition(value: Int) =
        settingsRepository.setInt(PreferKey.titleBarIconPosition, value.coerceIn(0, 3))

    suspend fun setShowTitleBarIcons(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.showTitleBarIcons, value)

    suspend fun setShowMenuIcon(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.showMenuIcon, value)

    suspend fun setTitleBarCompact(value: Boolean) =
        settingsRepository.setBoolean(PreferKey.titleBarCompact, value)

    suspend fun setChineseConverterType(value: Int) =
        settingsRepository.setInt(PreferKey.chineseConverterType, value)

    suspend fun setStyleSelect(isComic: Boolean, value: Int) {
        if (isComic) {
            setComicStyleSelect(value)
        } else {
            setReadStyleSelect(value)
        }
    }

    internal fun Map<String, PreferenceValue>.toReadSettings(): ReadSettings {
        val readStyleSelect = compatValue(PreferKey.readStyleSelect, 0)
        return ReadSettings(
            screenOrientation = compatValue(PreferKey.screenOrientation, "0"),
            keepLight = compatValue(PreferKey.keepLight, "0"),
            hideStatusBar = compatValue(PreferKey.hideStatusBar, false),
            hideNavigationBar = compatValue(PreferKey.hideNavigationBar, false),
            paddingDisplayCutouts = compatValue(PreferKey.paddingDisplayCutouts, false),
            titleBarMode = compatValue(PreferKey.titleBarMode, "1"),
            menuAlpha = compatValue(PreferKey.menuAlpha, 100),
            readBodyToLh = compatValue(PreferKey.readBodyToLh, true),
            defaultSourceChangeAll = compatValue(PreferKey.defaultSourceChangeAll, true),
            textFullJustify = compatValue(PreferKey.textFullJustify, true),
            textBottomJustify = compatValue(PreferKey.textBottomJustify, true),
            adaptSpecialStyle = compatValue(PreferKey.adaptSpecialStyle, true),
            useZhLayout = compatValue(PreferKey.useZhLayout, false),
            showBrightnessView = compatValue(PreferKey.showBrightnessView, "0"),
            brightnessVwPos = compatValue(PreferKey.brightnessVwPos, "1"),
            readBrightness = compatValue(PreferKey.brightness, 100),
            brightnessAuto = compatValue(PreferKey.brightnessAuto, true),
            useUnderline = compatValue(PreferKey.useUnderline, false),
            readSliderMode = compatValue(PreferKey.readSliderMode, "0"),
            doubleHorizontalPage = compatValue(PreferKey.doublePageHorizontal, "0"),
            progressBarBehavior = compatValue(PreferKey.progressBarBehavior, "page"),
            mouseWheelPage = compatValue(PreferKey.mouseWheelPage, true),
            volumeKeyPage = compatValue(PreferKey.volumeKeyPage, true),
            volumeKeyPageOnPlay = compatValue(PreferKey.volumeKeyPageOnPlay, true),
            keyPageOnLongPress = compatValue(PreferKey.keyPageOnLongPress, false),
            swipeToAddBookmark = compatValue(PreferKey.swipeToAddBookmark, false),
            bookmarkBadgeImage = compatValue(PreferKey.bookmarkBadgeImage, ""),
            bookmarkBadgeSize = compatValue(PreferKey.bookmarkBadgeSize, 10),
            pageTouchSlop = compatValue(PreferKey.pageTouchSlop, 0),
            sliderVibrator = compatValue(PreferKey.sliderVibrator, false),
            useNewTocSheet = compatValue(PreferKey.useNewTocSheet, true),
            maxLengthWithNoToc = compatValue(PreferKey.maxLengthWithNoToc, 3000),
            selectVibrator = compatValue(PreferKey.selectVibrator, false),
            autoChangeSource = compatValue(PreferKey.autoChangeSource, true),
            autoSuggestDayNight = compatValue(PreferKey.autoSuggestDayNight, false),
            readingAnchorEnabled = compatValue(PreferKey.readingAnchorEnabled, true),
            readAloudDetachReminderEnabled = compatValue(PreferKey.readAloudDetachReminderEnabled, false),
            selectText = compatValue(PreferKey.selectText, true),
            noAnimScrollPage = compatValue(PreferKey.noAnimScrollPage, false),
            clickImgWay = compatValue(PreferKey.clickImgWay, "2"),
            optimizeRender = compatValue(PreferKey.optimizeRender, false),
            disableReturnKey = compatValue(PreferKey.disableReturnKey, false),
            expandTextMenu = compatValue(PreferKey.expandTextMenu, false),
            showSelectMenuIcon = compatValue(PreferKey.showSelectMenuIcon, true),
            textSelectMenuConfig = compatValue(PreferKey.textSelectMenuConfig, ""),
            showReadTitleAddition = compatValue(PreferKey.showReadTitleAddition, true),
            autoReadSpeed = compatValue(PreferKey.autoReadSpeed, 10),
            systemTypefaces = compatValue(PreferKey.systemTypefaces, 0),
            preDownloadNum = compatValue(PreferKey.preDownloadNum, 10),
            prevKeys = compatValue(PreferKey.prevKeys, ""),
            nextKeys = compatValue(PreferKey.nextKeys, ""),
            tocUiUseReplace = compatValue(PreferKey.tocUiUseReplace, false),
            tocCountWords = compatValue(PreferKey.tocCountWords, true),
            readUrlInBrowser = compatValue(PreferKey.readUrlOpenInBrowser, false),
            readStyleSelect = readStyleSelect,
            comicStyleSelect = compatValue(PreferKey.comicStyleSelect, readStyleSelect),
            shareLayout = compatValue(PreferKey.shareLayout, false),
            readBarStyleFollowPage = compatValue(PreferKey.readBarStyleFollowPage, false),
            readBarStyle = compatValue(PreferKey.readBarStyle, 1),
            clickActionTL = compatValue(PreferKey.clickActionTL, 2),
            clickActionTC = compatValue(PreferKey.clickActionTC, 2),
            clickActionTR = compatValue(PreferKey.clickActionTR, 1),
            clickActionML = compatValue(PreferKey.clickActionML, 2),
            clickActionMC = compatValue(PreferKey.clickActionMC, 0),
            clickActionMR = compatValue(PreferKey.clickActionMR, 1),
            clickActionBL = compatValue(PreferKey.clickActionBL, 2),
            clickActionBC = compatValue(PreferKey.clickActionBC, 1),
            clickActionBR = compatValue(PreferKey.clickActionBR, 1),
            fontFolder = compatValue(PreferKey.fontFolder, ""),
            readMenuBgColor = compatValue(PreferKey.readMenuBgColor, 0),
            readMenuAccentColor = compatValue(PreferKey.readMenuAccentColor, 0),
            readMenuContainerColor = compatValue(PreferKey.readMenuContainerColor, 0),
            readMenuBgColorNight = compatValue(PreferKey.readMenuBgColorNight, 0),
            readMenuAccentColorNight = compatValue(PreferKey.readMenuAccentColorNight, 0),
            readMenuContainerColorNight = compatValue(PreferKey.readMenuContainerColorNight, 0),
            readMenuTextColor = compatValue(PreferKey.readMenuTextColor, 0),
            readMenuTextColorNight = compatValue(PreferKey.readMenuTextColorNight, 0),
            readMenuColorMode = compatValue(PreferKey.readMenuColorMode, 1),
            readMenuIconShowText = compatValue(PreferKey.readMenuIconShowText, false),
            readMenuIconStyle = compatValue(PreferKey.readMenuIconStyle, 0),
            titleBarIconStyle = compatValue(PreferKey.titleBarIconStyle, 0),
            readMenuIconItemsPerRow = compatValue(PreferKey.readMenuIconItemsPerRow, 5),
            readMenuIconRowCount = compatValue(PreferKey.readMenuIconRowCount, 1),
            readMenuBottomCornerRadius = compatValue(PreferKey.readMenuBottomCornerRadius, 32),
            readMenuFloatingBottomBar = compatValue(PreferKey.readMenuFloatingBottomBar, true),
            readMenuTopBarBlurMode = compatValue(PreferKey.readMenuTopBarBlurMode, ReadMenuBlurMode.None),
            readMenuBottomBarBlurMode = compatValue(PreferKey.readMenuBottomBarBlurMode, ReadMenuBlurMode.None),
            readMenuTopBarLiquidGlassButtons = compatValue(PreferKey.readMenuTopBarLiquidGlassButtons, false),
            readMenuTopBarMergeButtons = compatValue(PreferKey.readMenuTopBarMergeButtons, false),
            readMenuTopBarTitleCapsule = compatValue(PreferKey.readMenuTopBarTitleCapsule, false),
            readMenuBottomBarLiquidGlassButtons = compatValue(PreferKey.readMenuBottomBarLiquidGlassButtons, false),
            readMenuFloatingIconLiquidGlass = compatValue(
                PreferKey.readMenuFloatingIconLiquidGlass,
                false
            ),
            readMenuTopBarBlurStyle = compatValue(
                PreferKey.readMenuTopBarBlurStyle,
                ReadMenuBlurStyle.Solid
            ),
            readMenuBottomBarBlurStyle = compatValue(PreferKey.readMenuBottomBarBlurStyle, ReadMenuBlurStyle.Solid),
            readMenuBlurRadius = compatValue(PreferKey.readMenuBlurRadius, 24),
            readMenuBlurAlpha = compatValue(PreferKey.readMenuBlurAlpha, 85),
            readMenuBlurColor = compatValue(PreferKey.readMenuBlurColor, 0),
            readMenuBlurColorNight = compatValue(PreferKey.readMenuBlurColorNight, 0),
            readMenuPaletteStyle = compatValue(PreferKey.readMenuPaletteStyle, ""),
            readMenuLensRadius = compatValue(PreferKey.readMenuLensRadius, 24f),
            readMenuBorderWidth = compatValue(PreferKey.readMenuBorderWidth, 1),
            readMenuBorderColor = compatValue(PreferKey.readMenuBorderColor, 0),
            readMenuBorderColorNight = compatValue(PreferKey.readMenuBorderColorNight, 0),
            readMenuCustomIcons = compatValue(PreferKey.readMenuCustomIcons, ""),
            titleBarCustomIcons = compatValue(PreferKey.titleBarCustomIcons, ""),
            titleBarIconPosition = compatValue(PreferKey.titleBarIconPosition, 3),
            showTitleBarIcons = compatValue(PreferKey.showTitleBarIcons, false),
            chineseConverterType = compatValue(PreferKey.chineseConverterType, 0),
            showMenuIcon = compatValue(PreferKey.showMenuIcon, false),
            titleBarCompact = compatValue(PreferKey.titleBarCompact, false),
            moreActionsConfig = compatValue(PreferKey.moreActionsConfig, ""),
        )
    }

}

/**
 * [ReadSettings] 每个字段到 DataStore 键的完整映射。
 *
 * 必须覆盖全部字段：通用 `update {}` 只持久化本表里的键，漏一个就是静默丢写——
 * 调用方以为存了，重启后值回退。此前只覆盖 48/102，其余靠专用 setter 兜底，
 * 但没有任何机制阻止新代码走 `update {}` 改到未覆盖的字段。
 * [ReadSettingsGatewayMapCoverageTest] 断言这张表与 [ReadSettings] 字段一一对应。
 *
 * 全量写不是问题：`AppConfigStore.atomicUpdate` 只把与旧值不同的键入队
 * （见 `atomicDiffLocked`），未被 transform 改动的字段不会产生写入。
 */
internal fun ReadSettings.toGatewayPrefMap(): Map<String, Any?> = mapOf(
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
    PreferKey.useNewTocSheet to useNewTocSheet,
    PreferKey.maxLengthWithNoToc to maxLengthWithNoToc,
    PreferKey.selectVibrator to selectVibrator,
    PreferKey.autoChangeSource to autoChangeSource,
    PreferKey.autoSuggestDayNight to autoSuggestDayNight,
    PreferKey.readingAnchorEnabled to readingAnchorEnabled,
    PreferKey.readAloudDetachReminderEnabled to readAloudDetachReminderEnabled,
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
