package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

typealias ReadPreferences = ReadSettings

class ReadSettingsRepository(
    private val settingsRepository: SettingsRepository,
    private val preferencesFlow: StateFlow<Preferences> = AppConfigStore.preferencesFlow,
) : ReadSettingsGateway {

    override val currentSettings: ReadSettings
        get() = preferencesFlow.value.toReadSettings()

    override val settings: Flow<ReadSettings> = preferencesFlow
        .map { preferences ->
            preferences.toReadSettings()
        }

    val preferences: Flow<ReadPreferences> = settings

    override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
        AppConfigStore.atomicUpdate(
            read = { it.toReadSettings() },
            toPrefMap = ReadSettings::toGatewayPrefMap,
            transform = transform,
        )
    }

    suspend fun setScreenOrientation(value: String) =
        settingsRepository.putString(PreferKey.screenOrientation, value)

    suspend fun setKeepLight(value: String) =
        settingsRepository.putString(PreferKey.keepLight, value)

    suspend fun setHideStatusBar(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.hideStatusBar, value)

    suspend fun setHideNavigationBar(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.hideNavigationBar, value)

    suspend fun setPaddingDisplayCutouts(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.paddingDisplayCutouts, value)

    suspend fun setTitleBarMode(value: String) =
        settingsRepository.putString(PreferKey.titleBarMode, value)

    suspend fun setMenuAlpha(value: Int) =
        settingsRepository.putInt(PreferKey.menuAlpha, value)

    suspend fun setReadBodyToLh(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readBodyToLh, value)

    suspend fun setDefaultSourceChangeAll(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.defaultSourceChangeAll, value)

    suspend fun setTextFullJustify(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.textFullJustify, value)

    suspend fun setTextBottomJustify(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.textBottomJustify, value)

    suspend fun setAdaptSpecialStyle(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.adaptSpecialStyle, value)

    suspend fun setUseZhLayout(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.useZhLayout, value)

    suspend fun setShowBrightnessView(value: String) =
        settingsRepository.putString(PreferKey.showBrightnessView, value)

    suspend fun setBrightnessVwPos(value: String) =
        settingsRepository.putString(PreferKey.brightnessVwPos, value)

    suspend fun setReadBrightness(value: Int) =
        settingsRepository.putInt(PreferKey.brightness, value)

    suspend fun setBrightnessAuto(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.brightnessAuto, value)

    suspend fun setUseUnderline(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.useUnderline, value)

    suspend fun setReadSliderMode(value: String) =
        settingsRepository.putString(PreferKey.readSliderMode, value)

    suspend fun setDoubleHorizontalPage(value: String) =
        settingsRepository.putString(PreferKey.doublePageHorizontal, value)

    suspend fun setProgressBarBehavior(value: String) =
        settingsRepository.putString(PreferKey.progressBarBehavior, value)

    suspend fun setMouseWheelPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.mouseWheelPage, value)

    suspend fun setVolumeKeyPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.volumeKeyPage, value)

    suspend fun setVolumeKeyPageOnPlay(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.volumeKeyPageOnPlay, value)

    suspend fun setKeyPageOnLongPress(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.keyPageOnLongPress, value)

    suspend fun setPageTouchSlop(value: Int) =
        settingsRepository.putInt(PreferKey.pageTouchSlop, value)

    suspend fun setSliderVibrator(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.sliderVibrator, value)

    suspend fun setSelectVibrator(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.selectVibrator, value)

    suspend fun setAutoChangeSource(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.autoChangeSource, value)

    suspend fun setAutoSuggestDayNight(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.autoSuggestDayNight, value)

    suspend fun setSelectText(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.selectText, value)

    suspend fun setNoAnimScrollPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.noAnimScrollPage, value)

    suspend fun setClickImgWay(value: String) =
        settingsRepository.putString(PreferKey.clickImgWay, value)

    suspend fun setOptimizeRender(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.optimizeRender, value)

    suspend fun setDisableReturnKey(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.disableReturnKey, value)

    suspend fun setExpandTextMenu(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.expandTextMenu, value)

    suspend fun setShowSelectMenuIcon(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showSelectMenuIcon, value)

    suspend fun setShowReadTitleAddition(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showReadTitleAddition, value)

    suspend fun setAutoReadSpeed(value: Int) =
        settingsRepository.putInt(PreferKey.autoReadSpeed, value)

    suspend fun setSystemTypefaces(value: Int) =
        settingsRepository.putInt(PreferKey.systemTypefaces, value)

    suspend fun setPreDownloadNum(value: Int) =
        settingsRepository.putInt(PreferKey.preDownloadNum, value)

    suspend fun setPageKeys(prevKeys: String, nextKeys: String) {
        settingsRepository.putStrings(
            mapOf(
                PreferKey.prevKeys to prevKeys,
                PreferKey.nextKeys to nextKeys
            )
        )
    }

    suspend fun setTocUiUseReplace(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.tocUiUseReplace, value)

    suspend fun setTocCountWords(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.tocCountWords, value)

    suspend fun setReadStyleSelect(value: Int) =
        settingsRepository.putInt(PreferKey.readStyleSelect, value)

    suspend fun setComicStyleSelect(value: Int) =
        settingsRepository.putInt(PreferKey.comicStyleSelect, value)

    suspend fun setShareLayout(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.shareLayout, value)

    suspend fun setReadBarStyleFollowPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readBarStyleFollowPage, value)

    suspend fun setReadBarStyle(value: Int) =
        settingsRepository.putInt(PreferKey.readBarStyle, value.coerceIn(0, 2))

    suspend fun setClickAction(key: String, value: Int) =
        settingsRepository.putInt(key, value)

    suspend fun setFontFolder(value: String) =
        settingsRepository.putString(PreferKey.fontFolder, value)

    suspend fun setReadMenuBgColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBgColor, value)

    suspend fun setReadMenuAccentColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuAccentColor, value)

    suspend fun setReadMenuContainerColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuContainerColor, value)

    suspend fun setReadMenuBgColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBgColorNight, value)

    suspend fun setReadMenuAccentColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuAccentColorNight, value)

    suspend fun setReadMenuContainerColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuContainerColorNight, value)

    suspend fun setReadMenuTextColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuTextColor, value)

    suspend fun setReadMenuTextColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuTextColorNight, value)

    suspend fun setReadMenuColorMode(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuColorMode, value.coerceIn(0, 1))

    suspend fun setReadMenuIconShowText(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuIconShowText, value)

    suspend fun setReadMenuIconStyle(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuIconStyle, value.coerceIn(0, 2))

    suspend fun setTitleBarIconStyle(value: Int) =
        settingsRepository.putInt(PreferKey.titleBarIconStyle, value.coerceIn(0, 2))

    suspend fun setReadMenuIconItemsPerRow(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuIconItemsPerRow, value.coerceIn(2, 8))

    suspend fun setReadMenuIconRowCount(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuIconRowCount, value.coerceIn(1, 2))

    suspend fun setReadMenuBottomCornerRadius(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBottomCornerRadius, value.coerceIn(0, 32))

    suspend fun setReadMenuFloatingBottomBar(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuFloatingBottomBar, value)

    suspend fun setReadMenuTopBarBlurMode(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuTopBarBlurMode, value.coerceIn(0, 2))

    suspend fun setReadMenuBottomBarBlurMode(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBottomBarBlurMode, value.coerceIn(0, 2))

    suspend fun setReadMenuTopBarLiquidGlassButtons(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuTopBarLiquidGlassButtons, value)

    suspend fun setReadMenuTopBarTitleCapsule(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuTopBarTitleCapsule, value)

    suspend fun setReadMenuBottomBarLiquidGlassButtons(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuBottomBarLiquidGlassButtons, value)

    suspend fun setReadMenuFloatingIconLiquidGlass(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuFloatingIconLiquidGlass, value)

    suspend fun setReadMenuTopBarBlurStyle(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuTopBarBlurStyle, value.coerceIn(0, 1))

    suspend fun setReadMenuBottomBarBlurStyle(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBottomBarBlurStyle, value.coerceIn(0, 1))

    suspend fun setReadMenuBlurRadius(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBlurRadius, value.coerceIn(0, 32))

    suspend fun setReadMenuBlurAlpha(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBlurAlpha, value.coerceIn(0, 100))

    suspend fun setReadMenuBlurColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBlurColor, value)

    suspend fun setReadMenuBlurColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBlurColorNight, value)

    suspend fun setReadMenuPaletteStyle(value: String) =
        settingsRepository.putString(PreferKey.readMenuPaletteStyle, value)

    suspend fun setReadMenuLensRadius(value: Float) =
        settingsRepository.putFloat(PreferKey.readMenuLensRadius, value.coerceIn(0f, 48f))

    suspend fun setReadMenuBorderWidth(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBorderWidth, value.coerceIn(0, 4))

    suspend fun setReadMenuBorderColor(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBorderColor, value)

    suspend fun setReadMenuBorderColorNight(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuBorderColorNight, value)

    suspend fun setReadMenuCustomIcons(value: String) =
        settingsRepository.putString(PreferKey.readMenuCustomIcons, value)

    suspend fun setTitleBarCustomIcons(value: String) =
        settingsRepository.putString(PreferKey.titleBarCustomIcons, value)

    suspend fun setTitleBarIconPosition(value: Int) =
        settingsRepository.putInt(PreferKey.titleBarIconPosition, value.coerceIn(0, 3))

    suspend fun setShowTitleBarIcons(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showTitleBarIcons, value)

    suspend fun setShowMenuIcon(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showMenuIcon, value)

    suspend fun setTitleBarCompact(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.titleBarCompact, value)

    suspend fun setChineseConverterType(value: Int) =
        settingsRepository.putInt(PreferKey.chineseConverterType, value)

    suspend fun setStyleSelect(isComic: Boolean, value: Int) {
        if (isComic) {
            setComicStyleSelect(value)
        } else {
            setReadStyleSelect(value)
        }
    }

    internal fun Preferences.toReadSettings(): ReadSettings {
        val readStyleSelect = compatDsValue(Keys.ReadStyleSelect, 0)
        return ReadSettings(
            screenOrientation = compatDsValue(Keys.ScreenOrientation, "0"),
            keepLight = compatDsValue(Keys.KeepLight, "0"),
            hideStatusBar = compatDsValue(Keys.HideStatusBar, false),
            hideNavigationBar = compatDsValue(Keys.HideNavigationBar, false),
            paddingDisplayCutouts = compatDsValue(Keys.PaddingDisplayCutouts, false),
            titleBarMode = compatDsValue(Keys.TitleBarMode, "1"),
            menuAlpha = compatDsValue(Keys.MenuAlpha, 100),
            readBodyToLh = compatDsValue(Keys.ReadBodyToLh, true),
            defaultSourceChangeAll = compatDsValue(Keys.DefaultSourceChangeAll, true),
            textFullJustify = compatDsValue(Keys.TextFullJustify, true),
            textBottomJustify = compatDsValue(Keys.TextBottomJustify, true),
            adaptSpecialStyle = compatDsValue(Keys.AdaptSpecialStyle, true),
            useZhLayout = compatDsValue(Keys.UseZhLayout, false),
            eyeProtectionEnabled = compatDsValue(Keys.EyeProtectionEnabled, false),
            eyeProtectionIntensity = compatDsValue(Keys.EyeProtectionIntensity, 50).coerceIn(0, 100),
            eyeProtectionAutoNight = compatDsValue(Keys.EyeProtectionAutoNight, false),
            showBrightnessView = compatDsValue(Keys.ShowBrightnessView, "0"),
            brightnessVwPos = compatDsValue(Keys.BrightnessVwPos, "1"),
            readBrightness = compatDsValue(Keys.ReadBrightness, 100),
            brightnessAuto = compatDsValue(Keys.BrightnessAuto, true),
            useUnderline = compatDsValue(Keys.UseUnderline, false),
            readSliderMode = compatDsValue(Keys.ReadSliderMode, "0"),
            doubleHorizontalPage = compatDsValue(Keys.DoubleHorizontalPage, "0"),
            progressBarBehavior = compatDsValue(Keys.ProgressBarBehavior, "page"),
            mouseWheelPage = compatDsValue(Keys.MouseWheelPage, true),
            volumeKeyPage = compatDsValue(Keys.VolumeKeyPage, true),
            volumeKeyPageOnPlay = compatDsValue(Keys.VolumeKeyPageOnPlay, true),
            keyPageOnLongPress = compatDsValue(Keys.KeyPageOnLongPress, false),
            pageTouchSlop = compatDsValue(Keys.PageTouchSlop, 0),
            sliderVibrator = compatDsValue(Keys.SliderVibrator, false),
            selectVibrator = compatDsValue(Keys.SelectVibrator, false),
            autoChangeSource = compatDsValue(Keys.AutoChangeSource, true),
            autoSuggestDayNight = compatDsValue(Keys.AutoSuggestDayNight, false),
            selectText = compatDsValue(Keys.SelectText, true),
            noAnimScrollPage = compatDsValue(Keys.NoAnimScrollPage, false),
            clickImgWay = compatDsValue(Keys.ClickImgWay, "2"),
            optimizeRender = compatDsValue(Keys.OptimizeRender, false),
            disableReturnKey = compatDsValue(Keys.DisableReturnKey, false),
            expandTextMenu = compatDsValue(Keys.ExpandTextMenu, false),
            showSelectMenuIcon = compatDsValue(Keys.ShowSelectMenuIcon, true),
            textSelectMenuConfig = compatDsValue(Keys.TextSelectMenuConfig, ""),
            showReadTitleAddition = compatDsValue(Keys.ShowReadTitleAddition, true),
            autoReadSpeed = compatDsValue(Keys.AutoReadSpeed, 10),
            systemTypefaces = compatDsValue(Keys.SystemTypefaces, 0),
            preDownloadNum = compatDsValue(Keys.PreDownloadNum, 10),
            prevKeys = compatDsValue(Keys.PrevKeys, ""),
            nextKeys = compatDsValue(Keys.NextKeys, ""),
            tocUiUseReplace = compatDsValue(Keys.TocUiUseReplace, false),
            tocCountWords = compatDsValue(Keys.TocCountWords, true),
            readUrlInBrowser = compatDsValue(Keys.ReadUrlInBrowser, false),
            readStyleSelect = readStyleSelect,
            comicStyleSelect = compatDsValue(Keys.ComicStyleSelect, readStyleSelect),
            shareLayout = compatDsValue(Keys.ShareLayout, false),
            readBarStyleFollowPage = compatDsValue(Keys.ReadBarStyleFollowPage, false),
            readBarStyle = compatDsValue(Keys.ReadBarStyle, 1),
            clickActionTL = compatDsValue(Keys.ClickActionTL, 2),
            clickActionTC = compatDsValue(Keys.ClickActionTC, 2),
            clickActionTR = compatDsValue(Keys.ClickActionTR, 1),
            clickActionML = compatDsValue(Keys.ClickActionML, 2),
            clickActionMC = compatDsValue(Keys.ClickActionMC, 0),
            clickActionMR = compatDsValue(Keys.ClickActionMR, 1),
            clickActionBL = compatDsValue(Keys.ClickActionBL, 2),
            clickActionBC = compatDsValue(Keys.ClickActionBC, 1),
            clickActionBR = compatDsValue(Keys.ClickActionBR, 1),
            fontFolder = compatDsValue(Keys.FontFolder, ""),
            readMenuBgColor = compatDsValue(Keys.ReadMenuBgColor, 0),
            readMenuAccentColor = compatDsValue(Keys.ReadMenuAccentColor, 0),
            readMenuContainerColor = compatDsValue(Keys.ReadMenuContainerColor, 0),
            readMenuBgColorNight = compatDsValue(Keys.ReadMenuBgColorNight, 0),
            readMenuAccentColorNight = compatDsValue(Keys.ReadMenuAccentColorNight, 0),
            readMenuContainerColorNight = compatDsValue(Keys.ReadMenuContainerColorNight, 0),
            readMenuTextColor = compatDsValue(Keys.ReadMenuTextColor, 0),
            readMenuTextColorNight = compatDsValue(Keys.ReadMenuTextColorNight, 0),
            readMenuColorMode = compatDsValue(Keys.ReadMenuColorMode, 1),
            readMenuIconShowText = compatDsValue(Keys.ReadMenuIconShowText, false),
            readMenuIconStyle = compatDsValue(Keys.ReadMenuIconStyle, 0),
            titleBarIconStyle = compatDsValue(Keys.TitleBarIconStyle, 0),
            readMenuIconItemsPerRow = compatDsValue(Keys.ReadMenuIconItemsPerRow, 5),
            readMenuIconRowCount = compatDsValue(Keys.ReadMenuIconRowCount, 1),
            readMenuBottomCornerRadius = compatDsValue(Keys.ReadMenuBottomCornerRadius, 32),
            readMenuFloatingBottomBar = compatDsValue(Keys.ReadMenuFloatingBottomBar, true),
            readMenuTopBarBlurMode = compatDsValue(Keys.ReadMenuTopBarBlurMode, ReadMenuBlurMode.None),
            readMenuBottomBarBlurMode = compatDsValue(Keys.ReadMenuBottomBarBlurMode, ReadMenuBlurMode.None),
            readMenuTopBarLiquidGlassButtons = compatDsValue(Keys.ReadMenuTopBarLiquidGlassButtons, false),
            readMenuTopBarTitleCapsule = compatDsValue(Keys.ReadMenuTopBarTitleCapsule, false),
            readMenuBottomBarLiquidGlassButtons = compatDsValue(Keys.ReadMenuBottomBarLiquidGlassButtons, false),
            readMenuFloatingIconLiquidGlass = compatDsValue(
                Keys.ReadMenuFloatingIconLiquidGlass,
                false
            ),
            readMenuTopBarBlurStyle = compatDsValue(
                Keys.ReadMenuTopBarBlurStyle,
                ReadMenuBlurStyle.Solid
            ),
            readMenuBottomBarBlurStyle = compatDsValue(Keys.ReadMenuBottomBarBlurStyle, ReadMenuBlurStyle.Solid),
            readMenuBlurRadius = compatDsValue(Keys.ReadMenuBlurRadius, 24),
            readMenuBlurAlpha = compatDsValue(Keys.ReadMenuBlurAlpha, 100),
            readMenuBlurColor = compatDsValue(Keys.ReadMenuBlurColor, 0),
            readMenuBlurColorNight = compatDsValue(Keys.ReadMenuBlurColorNight, 0),
            readMenuPaletteStyle = compatDsValue(Keys.ReadMenuPaletteStyle, ""),
            readMenuLensRadius = compatDsValue(Keys.ReadMenuLensRadius, 24f),
            readMenuBorderWidth = compatDsValue(Keys.ReadMenuBorderWidth, 1),
            readMenuBorderColor = compatDsValue(Keys.ReadMenuBorderColor, 0),
            readMenuBorderColorNight = compatDsValue(Keys.ReadMenuBorderColorNight, 0),
            readMenuCustomIcons = compatDsValue(Keys.ReadMenuCustomIcons, ""),
            titleBarCustomIcons = compatDsValue(Keys.TitleBarCustomIcons, ""),
            titleBarIconPosition = compatDsValue(Keys.TitleBarIconPosition, 3),
            showTitleBarIcons = compatDsValue(Keys.ShowTitleBarIcons, false),
            chineseConverterType = compatDsValue(Keys.ChineseConverterType, 0),
            showMenuIcon = compatDsValue(Keys.ShowMenuIcon, false),
            titleBarCompact = compatDsValue(Keys.TitleBarCompact, false),
        )
    }

    private object Keys {
        val ScreenOrientation = stringPreferencesKey(PreferKey.screenOrientation)
        val KeepLight = stringPreferencesKey(PreferKey.keepLight)
        val HideStatusBar = booleanPreferencesKey(PreferKey.hideStatusBar)
        val HideNavigationBar = booleanPreferencesKey(PreferKey.hideNavigationBar)
        val PaddingDisplayCutouts = booleanPreferencesKey(PreferKey.paddingDisplayCutouts)
        val TitleBarMode = stringPreferencesKey(PreferKey.titleBarMode)
        val MenuAlpha = intPreferencesKey(PreferKey.menuAlpha)
        val ReadBodyToLh = booleanPreferencesKey(PreferKey.readBodyToLh)
        val DefaultSourceChangeAll = booleanPreferencesKey(PreferKey.defaultSourceChangeAll)
        val TextFullJustify = booleanPreferencesKey(PreferKey.textFullJustify)
        val TextBottomJustify = booleanPreferencesKey(PreferKey.textBottomJustify)
        val AdaptSpecialStyle = booleanPreferencesKey(PreferKey.adaptSpecialStyle)
        val UseZhLayout = booleanPreferencesKey(PreferKey.useZhLayout)
        val EyeProtectionEnabled = booleanPreferencesKey(PreferKey.eyeProtectionEnabled)
        val EyeProtectionIntensity = intPreferencesKey(PreferKey.colorTemperature)
        val EyeProtectionAutoNight = booleanPreferencesKey(PreferKey.eyeProtectionAutoNight)
        val ShowBrightnessView = stringPreferencesKey(PreferKey.showBrightnessView)
        val BrightnessVwPos = stringPreferencesKey(PreferKey.brightnessVwPos)
        val ReadBrightness = intPreferencesKey(PreferKey.brightness)
        val BrightnessAuto = booleanPreferencesKey(PreferKey.brightnessAuto)
        val UseUnderline = booleanPreferencesKey(PreferKey.useUnderline)
        val ReadSliderMode = stringPreferencesKey(PreferKey.readSliderMode)
        val DoubleHorizontalPage = stringPreferencesKey(PreferKey.doublePageHorizontal)
        val ProgressBarBehavior = stringPreferencesKey(PreferKey.progressBarBehavior)
        val MouseWheelPage = booleanPreferencesKey(PreferKey.mouseWheelPage)
        val VolumeKeyPage = booleanPreferencesKey(PreferKey.volumeKeyPage)
        val VolumeKeyPageOnPlay = booleanPreferencesKey(PreferKey.volumeKeyPageOnPlay)
        val KeyPageOnLongPress = booleanPreferencesKey(PreferKey.keyPageOnLongPress)
        val PageTouchSlop = intPreferencesKey(PreferKey.pageTouchSlop)
        val SliderVibrator = booleanPreferencesKey(PreferKey.sliderVibrator)
        val SelectVibrator = booleanPreferencesKey(PreferKey.selectVibrator)
        val AutoChangeSource = booleanPreferencesKey(PreferKey.autoChangeSource)
        val AutoSuggestDayNight = booleanPreferencesKey(PreferKey.autoSuggestDayNight)
        val SelectText = booleanPreferencesKey(PreferKey.selectText)
        val NoAnimScrollPage = booleanPreferencesKey(PreferKey.noAnimScrollPage)
        val ClickImgWay = stringPreferencesKey(PreferKey.clickImgWay)
        val OptimizeRender = booleanPreferencesKey(PreferKey.optimizeRender)
        val DisableReturnKey = booleanPreferencesKey(PreferKey.disableReturnKey)
        val ExpandTextMenu = booleanPreferencesKey(PreferKey.expandTextMenu)
        val ShowSelectMenuIcon = booleanPreferencesKey(PreferKey.showSelectMenuIcon)
        val TextSelectMenuConfig = stringPreferencesKey(PreferKey.textSelectMenuConfig)
        val ShowReadTitleAddition = booleanPreferencesKey(PreferKey.showReadTitleAddition)
        val AutoReadSpeed = intPreferencesKey(PreferKey.autoReadSpeed)
        val SystemTypefaces = intPreferencesKey(PreferKey.systemTypefaces)
        val PreDownloadNum = intPreferencesKey(PreferKey.preDownloadNum)
        val PrevKeys = stringPreferencesKey(PreferKey.prevKeys)
        val NextKeys = stringPreferencesKey(PreferKey.nextKeys)
        val TocUiUseReplace = booleanPreferencesKey(PreferKey.tocUiUseReplace)
        val TocCountWords = booleanPreferencesKey(PreferKey.tocCountWords)
        val ReadUrlInBrowser = booleanPreferencesKey(PreferKey.readUrlOpenInBrowser)
        val ReadStyleSelect = intPreferencesKey(PreferKey.readStyleSelect)
        val ComicStyleSelect = intPreferencesKey(PreferKey.comicStyleSelect)
        val ShareLayout = booleanPreferencesKey(PreferKey.shareLayout)
        val ReadBarStyleFollowPage = booleanPreferencesKey(PreferKey.readBarStyleFollowPage)
        val ReadBarStyle = intPreferencesKey(PreferKey.readBarStyle)
        val ClickActionTL = intPreferencesKey(PreferKey.clickActionTL)
        val ClickActionTC = intPreferencesKey(PreferKey.clickActionTC)
        val ClickActionTR = intPreferencesKey(PreferKey.clickActionTR)
        val ClickActionML = intPreferencesKey(PreferKey.clickActionML)
        val ClickActionMC = intPreferencesKey(PreferKey.clickActionMC)
        val ClickActionMR = intPreferencesKey(PreferKey.clickActionMR)
        val ClickActionBL = intPreferencesKey(PreferKey.clickActionBL)
        val ClickActionBC = intPreferencesKey(PreferKey.clickActionBC)
        val ClickActionBR = intPreferencesKey(PreferKey.clickActionBR)
        val FontFolder = stringPreferencesKey(PreferKey.fontFolder)
        val ReadMenuBgColor = intPreferencesKey(PreferKey.readMenuBgColor)
        val ReadMenuAccentColor = intPreferencesKey(PreferKey.readMenuAccentColor)
        val ReadMenuContainerColor = intPreferencesKey(PreferKey.readMenuContainerColor)
        val ReadMenuBgColorNight = intPreferencesKey(PreferKey.readMenuBgColorNight)
        val ReadMenuAccentColorNight = intPreferencesKey(PreferKey.readMenuAccentColorNight)
        val ReadMenuContainerColorNight = intPreferencesKey(PreferKey.readMenuContainerColorNight)
        val ReadMenuTextColor = intPreferencesKey(PreferKey.readMenuTextColor)
        val ReadMenuTextColorNight = intPreferencesKey(PreferKey.readMenuTextColorNight)
        val ReadMenuColorMode = intPreferencesKey(PreferKey.readMenuColorMode)
        val ReadMenuIconShowText = booleanPreferencesKey(PreferKey.readMenuIconShowText)
        val ReadMenuIconStyle = intPreferencesKey(PreferKey.readMenuIconStyle)
        val TitleBarIconStyle = intPreferencesKey(PreferKey.titleBarIconStyle)
        val ReadMenuIconItemsPerRow = intPreferencesKey(PreferKey.readMenuIconItemsPerRow)
        val ReadMenuIconRowCount = intPreferencesKey(PreferKey.readMenuIconRowCount)
        val ReadMenuBottomCornerRadius = intPreferencesKey(PreferKey.readMenuBottomCornerRadius)
        val ReadMenuFloatingBottomBar = booleanPreferencesKey(PreferKey.readMenuFloatingBottomBar)
        val ReadMenuTopBarBlurMode = intPreferencesKey(PreferKey.readMenuTopBarBlurMode)
        val ReadMenuBottomBarBlurMode = intPreferencesKey(PreferKey.readMenuBottomBarBlurMode)
        val ReadMenuTopBarLiquidGlassButtons =
            booleanPreferencesKey(PreferKey.readMenuTopBarLiquidGlassButtons)
        val ReadMenuTopBarTitleCapsule =
            booleanPreferencesKey(PreferKey.readMenuTopBarTitleCapsule)
        val ReadMenuBottomBarLiquidGlassButtons =
            booleanPreferencesKey(PreferKey.readMenuBottomBarLiquidGlassButtons)
        val ReadMenuFloatingIconLiquidGlass =
            booleanPreferencesKey(PreferKey.readMenuFloatingIconLiquidGlass)
        val ReadMenuTopBarBlurStyle = intPreferencesKey(PreferKey.readMenuTopBarBlurStyle)
        val ReadMenuBottomBarBlurStyle = intPreferencesKey(PreferKey.readMenuBottomBarBlurStyle)
        val ReadMenuBlurRadius = intPreferencesKey(PreferKey.readMenuBlurRadius)
        val ReadMenuBlurAlpha = intPreferencesKey(PreferKey.readMenuBlurAlpha)
        val ReadMenuBlurColor = intPreferencesKey(PreferKey.readMenuBlurColor)
        val ReadMenuBlurColorNight = intPreferencesKey(PreferKey.readMenuBlurColorNight)
        val ReadMenuPaletteStyle = stringPreferencesKey(PreferKey.readMenuPaletteStyle)
        val ReadMenuLensRadius = floatPreferencesKey(PreferKey.readMenuLensRadius)
        val ReadMenuBorderWidth = intPreferencesKey(PreferKey.readMenuBorderWidth)
        val ReadMenuBorderColor = intPreferencesKey(PreferKey.readMenuBorderColor)
        val ReadMenuBorderColorNight = intPreferencesKey(PreferKey.readMenuBorderColorNight)
        val ReadMenuCustomIcons = stringPreferencesKey(PreferKey.readMenuCustomIcons)
        val TitleBarCustomIcons = stringPreferencesKey(PreferKey.titleBarCustomIcons)
        val TitleBarIconPosition = intPreferencesKey(PreferKey.titleBarIconPosition)
        val ShowTitleBarIcons = booleanPreferencesKey(PreferKey.showTitleBarIcons)
        val ChineseConverterType = intPreferencesKey(PreferKey.chineseConverterType)
        val ShowMenuIcon = booleanPreferencesKey(PreferKey.showMenuIcon)
        val TitleBarCompact = booleanPreferencesKey(PreferKey.titleBarCompact)
    }
}

/**
 * ReadSettings 是 101 字段的读取超集；gateway update 当前只承诺持久化以下 48 个键。
 * 扩展此边界时必须同步更新 ReadSettingsMappingTest 的显式键集契约。
 */
internal fun ReadSettings.toGatewayPrefMap(): Map<String, Any?> = mapOf(
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
