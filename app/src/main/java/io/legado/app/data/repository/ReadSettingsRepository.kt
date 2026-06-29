package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class ReadPreferences(
    val screenOrientation: String = "0",
    val keepLight: String = "0",
    val hideStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val paddingDisplayCutouts: Boolean = false,
    val titleBarMode: String = "1",
    val menuAlpha: Int = 100,
    val readBodyToLh: Boolean = true,
    val defaultSourceChangeAll: Boolean = true,
    val textFullJustify: Boolean = true,
    val textBottomJustify: Boolean = true,
    val adaptSpecialStyle: Boolean = true,
    val useZhLayout: Boolean = false,
    val showBrightnessView: String = "1",
    val brightnessVwPos: String = "1",
    val readBrightness: Int = 100,
    val brightnessAuto: Boolean = false,
    val useUnderline: Boolean = false,
    val readSliderMode: String = "0",
    val doubleHorizontalPage: String = "0",
    val progressBarBehavior: String = "page",
    val mouseWheelPage: Boolean = true,
    val volumeKeyPage: Boolean = true,
    val volumeKeyPageOnPlay: Boolean = true,
    val keyPageOnLongPress: Boolean = false,
    val pageTouchSlop: Int = 0,
    val sliderVibrator: Boolean = false,
    val selectVibrator: Boolean = false,
    val autoChangeSource: Boolean = true,
    val autoSuggestDayNight: Boolean = false,
    val selectText: Boolean = true,
    val noAnimScrollPage: Boolean = false,
    val clickImgWay: String = "2",
    val optimizeRender: Boolean = false,
    val disableReturnKey: Boolean = false,
    val expandTextMenu: Boolean = false,
    val showSelectMenuIcon: Boolean = true,
    val textSelectMenuFilter: String = "",
    val showReadTitleAddition: Boolean = true,
    val autoReadSpeed: Int = 10,
    val prevKeys: String = "",
    val nextKeys: String = "",
    val tocUiUseReplace: Boolean = false,
    val tocCountWords: Boolean = true,
    val readStyleSelect: Int = 0,
    val comicStyleSelect: Int = 0,
    val shareLayout: Boolean = false,
    val readBarStyleFollowPage: Boolean = false,
    val readBarStyle: Int = 0,
    val clickActionTL: Int = 2,
    val clickActionTC: Int = 2,
    val clickActionTR: Int = 1,
    val clickActionML: Int = 2,
    val clickActionMC: Int = 0,
    val clickActionMR: Int = 1,
    val clickActionBL: Int = 2,
    val clickActionBC: Int = 1,
    val clickActionBR: Int = 1,
    val fontFolder: String = "",
    val readMenuBgColor: Int = 0,
    val readMenuAccentColor: Int = 0,
    val readMenuContainerColor: Int = 0,
    val readMenuBgColorNight: Int = 0,
    val readMenuAccentColorNight: Int = 0,
    val readMenuContainerColorNight: Int = 0,
    val readMenuColorMode: Int = 1,
    val readMenuIconShowText: Boolean = true,
    val readMenuIconStyle: Int = 0,
    val readMenuIconItemsPerRow: Int = 5,
    val readMenuIconRowCount: Int = 1,
    val readMenuBottomCornerRadius: Int = 0,
    val readMenuFloatingBottomBar: Boolean = false,
    val readMenuTopBarBlurMode: Int = ReadMenuBlurMode.None,
    val readMenuBottomBarBlurMode: Int = ReadMenuBlurMode.None,
    val readMenuTopBarLiquidGlassButtons: Boolean = false,
    val readMenuTopBarTitleCapsule: Boolean = false,
    val readMenuBottomBarLiquidGlassButtons: Boolean = false,
    val readMenuTopBarBlurStyle: Int = ReadMenuBlurStyle.Progressive,
    val readMenuBottomBarBlurStyle: Int = ReadMenuBlurStyle.Solid,
    val readMenuBlurRadius: Int = 24,
    val readMenuBlurAlpha: Int = 60,
    val readMenuBlurColor: Int = 0,
    val readMenuPaletteStyle: String = "",
    val readMenuLensRadius: Float = 24f,
    val readMenuBorderWidth: Int = 0,
    val readMenuBorderColor: Int = 0,
    val readMenuBorderColorNight: Int = 0,
    val readMenuCustomIcons: String = "",
    val titleBarCustomIcons: String = "",
    val titleBarIconPosition: Int = 0,
    val showTitleBarIcons: Boolean = true,
    val chineseConverterType: Int = 0,
    val showMenuIcon: Boolean = true,
)

class ReadSettingsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    val preferences: Flow<ReadPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences.toReadPreferences()
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

    suspend fun setTextSelectMenuFilter(value: String) =
        settingsRepository.putString(PreferKey.textSelectMenuFilter, value)

    suspend fun setShowReadTitleAddition(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showReadTitleAddition, value)

    suspend fun setAutoReadSpeed(value: Int) =
        settingsRepository.putInt(PreferKey.autoReadSpeed, value)

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

    suspend fun setReadMenuColorMode(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuColorMode, value.coerceIn(0, 1))

    suspend fun setReadMenuIconShowText(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.readMenuIconShowText, value)

    suspend fun setReadMenuIconStyle(value: Int) =
        settingsRepository.putInt(PreferKey.readMenuIconStyle, value.coerceIn(0, 2))

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

    suspend fun setChineseConverterType(value: Int) =
        settingsRepository.putInt(PreferKey.chineseConverterType, value)

    suspend fun setStyleSelect(isComic: Boolean, value: Int) {
        if (isComic) {
            setComicStyleSelect(value)
        } else {
            setReadStyleSelect(value)
        }
    }

    private fun Preferences.toReadPreferences(): ReadPreferences {
        val readStyleSelect = this[Keys.ReadStyleSelect] ?: 0
        return ReadPreferences(
            screenOrientation = this[Keys.ScreenOrientation] ?: "0",
            keepLight = this[Keys.KeepLight] ?: "0",
            hideStatusBar = this[Keys.HideStatusBar] ?: false,
            hideNavigationBar = this[Keys.HideNavigationBar] ?: false,
            paddingDisplayCutouts = this[Keys.PaddingDisplayCutouts] ?: false,
            titleBarMode = this[Keys.TitleBarMode] ?: "1",
            menuAlpha = this[Keys.MenuAlpha] ?: 100,
            readBodyToLh = this[Keys.ReadBodyToLh] ?: true,
            defaultSourceChangeAll = this[Keys.DefaultSourceChangeAll] ?: true,
            textFullJustify = this[Keys.TextFullJustify] ?: true,
            textBottomJustify = this[Keys.TextBottomJustify] ?: true,
            adaptSpecialStyle = this[Keys.AdaptSpecialStyle] ?: true,
            useZhLayout = this[Keys.UseZhLayout] ?: false,
            showBrightnessView = this[Keys.ShowBrightnessView] ?: "1",
            brightnessVwPos = this[Keys.BrightnessVwPos] ?: "1",
            readBrightness = this[Keys.ReadBrightness] ?: 100,
            brightnessAuto = this[Keys.BrightnessAuto] ?: false,
            useUnderline = this[Keys.UseUnderline] ?: false,
            readSliderMode = this[Keys.ReadSliderMode] ?: "0",
            doubleHorizontalPage = this[Keys.DoubleHorizontalPage] ?: "0",
            progressBarBehavior = this[Keys.ProgressBarBehavior] ?: "page",
            mouseWheelPage = this[Keys.MouseWheelPage] ?: true,
            volumeKeyPage = this[Keys.VolumeKeyPage] ?: true,
            volumeKeyPageOnPlay = this[Keys.VolumeKeyPageOnPlay] ?: true,
            keyPageOnLongPress = this[Keys.KeyPageOnLongPress] ?: false,
            pageTouchSlop = this[Keys.PageTouchSlop] ?: 0,
            sliderVibrator = this[Keys.SliderVibrator] ?: false,
            selectVibrator = this[Keys.SelectVibrator] ?: false,
            autoChangeSource = this[Keys.AutoChangeSource] ?: true,
            autoSuggestDayNight = this[Keys.AutoSuggestDayNight] ?: false,
            selectText = this[Keys.SelectText] ?: true,
            noAnimScrollPage = this[Keys.NoAnimScrollPage] ?: false,
            clickImgWay = this[Keys.ClickImgWay] ?: "2",
            optimizeRender = this[Keys.OptimizeRender] ?: false,
            disableReturnKey = this[Keys.DisableReturnKey] ?: false,
            expandTextMenu = this[Keys.ExpandTextMenu] ?: false,
            showSelectMenuIcon = this[Keys.ShowSelectMenuIcon] ?: true,
            textSelectMenuFilter = this[Keys.TextSelectMenuFilter] ?: "",
            showReadTitleAddition = this[Keys.ShowReadTitleAddition] ?: true,
            autoReadSpeed = this[Keys.AutoReadSpeed] ?: 10,
            prevKeys = this[Keys.PrevKeys] ?: "",
            nextKeys = this[Keys.NextKeys] ?: "",
            tocUiUseReplace = this[Keys.TocUiUseReplace] ?: false,
            tocCountWords = this[Keys.TocCountWords] ?: true,
            readStyleSelect = readStyleSelect,
            comicStyleSelect = this[Keys.ComicStyleSelect] ?: readStyleSelect,
            shareLayout = this[Keys.ShareLayout] ?: false,
            readBarStyleFollowPage = this[Keys.ReadBarStyleFollowPage] ?: false,
            readBarStyle = this[Keys.ReadBarStyle] ?: 0,
            clickActionTL = this[Keys.ClickActionTL] ?: 2,
            clickActionTC = this[Keys.ClickActionTC] ?: 2,
            clickActionTR = this[Keys.ClickActionTR] ?: 1,
            clickActionML = this[Keys.ClickActionML] ?: 2,
            clickActionMC = this[Keys.ClickActionMC] ?: 0,
            clickActionMR = this[Keys.ClickActionMR] ?: 1,
            clickActionBL = this[Keys.ClickActionBL] ?: 2,
            clickActionBC = this[Keys.ClickActionBC] ?: 1,
            clickActionBR = this[Keys.ClickActionBR] ?: 1,
            fontFolder = this[Keys.FontFolder] ?: "",
            readMenuBgColor = this[Keys.ReadMenuBgColor] ?: 0,
            readMenuAccentColor = this[Keys.ReadMenuAccentColor] ?: 0,
            readMenuContainerColor = this[Keys.ReadMenuContainerColor] ?: 0,
            readMenuBgColorNight = this[Keys.ReadMenuBgColorNight] ?: 0,
            readMenuAccentColorNight = this[Keys.ReadMenuAccentColorNight] ?: 0,
            readMenuContainerColorNight = this[Keys.ReadMenuContainerColorNight] ?: 0,
            readMenuColorMode = this[Keys.ReadMenuColorMode] ?: 1,
            readMenuIconShowText = this[Keys.ReadMenuIconShowText] ?: true,
            readMenuIconStyle = this[Keys.ReadMenuIconStyle] ?: 0,
            readMenuIconItemsPerRow = this[Keys.ReadMenuIconItemsPerRow] ?: 5,
            readMenuIconRowCount = this[Keys.ReadMenuIconRowCount] ?: 1,
            readMenuBottomCornerRadius = this[Keys.ReadMenuBottomCornerRadius] ?: 0,
            readMenuFloatingBottomBar = this[Keys.ReadMenuFloatingBottomBar] ?: false,
            readMenuTopBarBlurMode = this[Keys.ReadMenuTopBarBlurMode] ?: ReadMenuBlurMode.None,
            readMenuBottomBarBlurMode = this[Keys.ReadMenuBottomBarBlurMode]
                ?: ReadMenuBlurMode.None,
            readMenuTopBarLiquidGlassButtons = this[Keys.ReadMenuTopBarLiquidGlassButtons] ?: false,
            readMenuTopBarTitleCapsule = this[Keys.ReadMenuTopBarTitleCapsule] ?: false,
            readMenuBottomBarLiquidGlassButtons = this[Keys.ReadMenuBottomBarLiquidGlassButtons]
                ?: false,
            readMenuTopBarBlurStyle = this[Keys.ReadMenuTopBarBlurStyle]
                ?: ReadMenuBlurStyle.Progressive,
            readMenuBottomBarBlurStyle = this[Keys.ReadMenuBottomBarBlurStyle]
                ?: ReadMenuBlurStyle.Solid,
            readMenuBlurRadius = this[Keys.ReadMenuBlurRadius] ?: 24,
            readMenuBlurAlpha = this[Keys.ReadMenuBlurAlpha] ?: 60,
            readMenuBlurColor = this[Keys.ReadMenuBlurColor] ?: 0,
            readMenuPaletteStyle = this[Keys.ReadMenuPaletteStyle] ?: "",
            readMenuLensRadius = this[Keys.ReadMenuLensRadius] ?: 24f,
            readMenuBorderWidth = this[Keys.ReadMenuBorderWidth] ?: 0,
            readMenuBorderColor = this[Keys.ReadMenuBorderColor] ?: 0,
            readMenuBorderColorNight = this[Keys.ReadMenuBorderColorNight] ?: 0,
            readMenuCustomIcons = this[Keys.ReadMenuCustomIcons] ?: "",
            titleBarCustomIcons = this[Keys.TitleBarCustomIcons] ?: "",
            titleBarIconPosition = this[Keys.TitleBarIconPosition] ?: 0,
            showTitleBarIcons = this[Keys.ShowTitleBarIcons] ?: false,
            chineseConverterType = this[Keys.ChineseConverterType] ?: 0,
            showMenuIcon = this[Keys.ShowMenuIcon] ?: true,
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
        val TextSelectMenuFilter = stringPreferencesKey(PreferKey.textSelectMenuFilter)
        val ShowReadTitleAddition = booleanPreferencesKey(PreferKey.showReadTitleAddition)
        val AutoReadSpeed = intPreferencesKey(PreferKey.autoReadSpeed)
        val PrevKeys = stringPreferencesKey(PreferKey.prevKeys)
        val NextKeys = stringPreferencesKey(PreferKey.nextKeys)
        val TocUiUseReplace = booleanPreferencesKey(PreferKey.tocUiUseReplace)
        val TocCountWords = booleanPreferencesKey(PreferKey.tocCountWords)
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
        val ReadMenuColorMode = intPreferencesKey(PreferKey.readMenuColorMode)
        val ReadMenuIconShowText = booleanPreferencesKey(PreferKey.readMenuIconShowText)
        val ReadMenuIconStyle = intPreferencesKey(PreferKey.readMenuIconStyle)
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
        val ReadMenuTopBarBlurStyle = intPreferencesKey(PreferKey.readMenuTopBarBlurStyle)
        val ReadMenuBottomBarBlurStyle = intPreferencesKey(PreferKey.readMenuBottomBarBlurStyle)
        val ReadMenuBlurRadius = intPreferencesKey(PreferKey.readMenuBlurRadius)
        val ReadMenuBlurAlpha = intPreferencesKey(PreferKey.readMenuBlurAlpha)
        val ReadMenuBlurColor = intPreferencesKey(PreferKey.readMenuBlurColor)
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
    }
}
