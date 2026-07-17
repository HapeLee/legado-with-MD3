package io.legado.app.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class LocalPreferencesRepository(private val context: Context) {

    private val dataStore = context.localDataStore
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appSettings: StateFlow<AppSettings> = AppConfigStore.preferencesFlow
        .map { prefs ->
            AppSettings(
                darkTheme = prefs.compatDsString(PreferKey.themeMode).let {
                    when (it) {
                        "1" -> false
                        "2" -> true
                        else -> false
                    }
                },
                language = prefs.compatDsString(PreferKey.language) ?: "auto",
                readAloudSpeed = prefs.compatDsInt(PreferKey.ttsSpeechRate) ?: 5,
                textWeight = prefs.compatDsInt("textWeight") ?: 1,
                showBrightnessView = prefs.compatDsString(PreferKey.showBrightnessView) ?: "1",
                screenOrientation = prefs.compatDsString(PreferKey.screenOrientation) ?: "0",
                keepLight = prefs.compatDsString(PreferKey.keepLight) ?: "0",
                hideStatusBar = prefs.compatDsBoolean(PreferKey.hideStatusBar) ?: false,
                hideNavigationBar = prefs.compatDsBoolean(PreferKey.hideNavigationBar) ?: false,
                paddingDisplayCutouts = prefs.compatDsBoolean(PreferKey.paddingDisplayCutouts) ?: false,
                titleBarMode = prefs.compatDsString(PreferKey.titleBarMode) ?: "1",
                readMenuBlurAlpha = prefs.compatDsInt(PreferKey.readMenuBlurAlpha) ?: 60,
                readBodyToLh = prefs.compatDsBoolean(PreferKey.readBodyToLh) ?: true,
                defaultSourceChangeAll = prefs.compatDsBoolean(PreferKey.defaultSourceChangeAll) ?: true,
                textFullJustify = prefs.compatDsBoolean(PreferKey.textFullJustify) ?: true,
                textBottomJustify = prefs.compatDsBoolean(PreferKey.textBottomJustify) ?: true,
                adaptSpecialStyle = prefs.compatDsBoolean(PreferKey.adaptSpecialStyle) ?: true,
                useZhLayout = prefs.compatDsBoolean(PreferKey.useZhLayout) ?: false,
                brightnessVwPos = prefs.compatDsString(PreferKey.brightnessVwPos) ?: "1",
                brightnessAuto = prefs.compatDsBoolean(PreferKey.brightnessAuto) ?: false,
                useUnderline = prefs.compatDsBoolean(PreferKey.useUnderline) ?: false,
                readSliderMode = prefs.compatDsString(PreferKey.readSliderMode) ?: "0",
                doubleHorizontalPage = prefs.compatDsString(PreferKey.doublePageHorizontal) ?: "0",
                progressBarBehavior = prefs.compatDsString(PreferKey.progressBarBehavior) ?: "page",
                mouseWheelPage = prefs.compatDsBoolean(PreferKey.mouseWheelPage) ?: true,
                volumeKeyPage = prefs.compatDsBoolean(PreferKey.volumeKeyPage) ?: true,
                volumeKeyPageOnPlay = prefs.compatDsBoolean(PreferKey.volumeKeyPageOnPlay) ?: true,
                keyPageOnLongPress = prefs.compatDsBoolean(PreferKey.keyPageOnLongPress) ?: false,
                pageTouchSlop = prefs.compatDsInt(PreferKey.pageTouchSlop) ?: 0,
                sliderVibrator = prefs.compatDsBoolean(PreferKey.sliderVibrator) ?: false,
                selectVibrator = prefs.compatDsBoolean(PreferKey.selectVibrator) ?: false,
                autoChangeSource = prefs.compatDsBoolean(PreferKey.autoChangeSource) ?: true,
                autoSuggestDayNight = prefs.compatDsBoolean(PreferKey.autoSuggestDayNight) ?: false,
                selectText = prefs.compatDsBoolean(PreferKey.selectText) ?: true,
                noAnimScrollPage = prefs.compatDsBoolean(PreferKey.noAnimScrollPage) ?: false,
                clickImgWay = prefs.compatDsString(PreferKey.clickImgWay) ?: "2",
                optimizeRender = prefs.compatDsBoolean(PreferKey.optimizeRender) ?: false,
                disableReturnKey = prefs.compatDsBoolean(PreferKey.disableReturnKey) ?: false,
                expandTextMenu = prefs.compatDsBoolean(PreferKey.expandTextMenu) ?: false,
                showSelectMenuIcon = prefs.compatDsBoolean(PreferKey.showSelectMenuIcon) ?: true,
                showReadTitleAddition = prefs.compatDsBoolean(PreferKey.showReadTitleAddition) ?: true,
                autoReadSpeed = prefs.compatDsInt(PreferKey.autoReadSpeed) ?: 10,
                prevKeys = prefs.compatDsString(PreferKey.prevKeys) ?: "",
                nextKeys = prefs.compatDsString(PreferKey.nextKeys) ?: "",
                showMenuIcon = prefs.compatDsBoolean(PreferKey.showMenuIcon) ?: true
            )
        }.stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = appSettings.value
        val updated = transform(current)

        val changes = mutableMapOf<String, Any?>()
        if (updated.darkTheme != current.darkTheme) {
            changes[PreferKey.themeMode] = if (updated.darkTheme) "2" else "1"
        }
        if (updated.language != current.language) {
            changes[PreferKey.language] = updated.language
        }
        if (updated.readAloudSpeed != current.readAloudSpeed) {
            changes[PreferKey.ttsSpeechRate] = updated.readAloudSpeed
        }
        if (updated.textWeight != current.textWeight) {
            changes["textWeight"] = updated.textWeight
        }
        if (updated.showBrightnessView != current.showBrightnessView) {
            changes[PreferKey.showBrightnessView] = updated.showBrightnessView
        }
        if (updated.screenOrientation != current.screenOrientation) {
            changes[PreferKey.screenOrientation] = updated.screenOrientation
        }
        if (updated.keepLight != current.keepLight) {
            changes[PreferKey.keepLight] = updated.keepLight
        }
        if (updated.hideStatusBar != current.hideStatusBar) {
            changes[PreferKey.hideStatusBar] = updated.hideStatusBar
        }
        if (updated.hideNavigationBar != current.hideNavigationBar) {
            changes[PreferKey.hideNavigationBar] = updated.hideNavigationBar
        }
        if (updated.paddingDisplayCutouts != current.paddingDisplayCutouts) {
            changes[PreferKey.paddingDisplayCutouts] = updated.paddingDisplayCutouts
        }
        if (updated.titleBarMode != current.titleBarMode) {
            changes[PreferKey.titleBarMode] = updated.titleBarMode
        }
        if (updated.readMenuBlurAlpha != current.readMenuBlurAlpha) {
            changes[PreferKey.readMenuBlurAlpha] = updated.readMenuBlurAlpha
        }
        if (updated.readBodyToLh != current.readBodyToLh) {
            changes[PreferKey.readBodyToLh] = updated.readBodyToLh
        }
        if (updated.defaultSourceChangeAll != current.defaultSourceChangeAll) {
            changes[PreferKey.defaultSourceChangeAll] = updated.defaultSourceChangeAll
        }
        if (updated.textFullJustify != current.textFullJustify) {
            changes[PreferKey.textFullJustify] = updated.textFullJustify
        }
        if (updated.textBottomJustify != current.textBottomJustify) {
            changes[PreferKey.textBottomJustify] = updated.textBottomJustify
        }
        if (updated.adaptSpecialStyle != current.adaptSpecialStyle) {
            changes[PreferKey.adaptSpecialStyle] = updated.adaptSpecialStyle
        }
        if (updated.useZhLayout != current.useZhLayout) {
            changes[PreferKey.useZhLayout] = updated.useZhLayout
        }
        if (updated.brightnessVwPos != current.brightnessVwPos) {
            changes[PreferKey.brightnessVwPos] = updated.brightnessVwPos
        }
        if (updated.brightnessAuto != current.brightnessAuto) {
            changes[PreferKey.brightnessAuto] = updated.brightnessAuto
        }
        if (updated.useUnderline != current.useUnderline) {
            changes[PreferKey.useUnderline] = updated.useUnderline
        }
        if (updated.readSliderMode != current.readSliderMode) {
            changes[PreferKey.readSliderMode] = updated.readSliderMode
        }
        if (updated.doubleHorizontalPage != current.doubleHorizontalPage) {
            changes[PreferKey.doublePageHorizontal] = updated.doubleHorizontalPage
        }
        if (updated.progressBarBehavior != current.progressBarBehavior) {
            changes[PreferKey.progressBarBehavior] = updated.progressBarBehavior
        }
        if (updated.mouseWheelPage != current.mouseWheelPage) {
            changes[PreferKey.mouseWheelPage] = updated.mouseWheelPage
        }
        if (updated.volumeKeyPage != current.volumeKeyPage) {
            changes[PreferKey.volumeKeyPage] = updated.volumeKeyPage
        }
        if (updated.volumeKeyPageOnPlay != current.volumeKeyPageOnPlay) {
            changes[PreferKey.volumeKeyPageOnPlay] = updated.volumeKeyPageOnPlay
        }
        if (updated.keyPageOnLongPress != current.keyPageOnLongPress) {
            changes[PreferKey.keyPageOnLongPress] = updated.keyPageOnLongPress
        }
        if (updated.pageTouchSlop != current.pageTouchSlop) {
            changes[PreferKey.pageTouchSlop] = updated.pageTouchSlop
        }
        if (updated.sliderVibrator != current.sliderVibrator) {
            changes[PreferKey.sliderVibrator] = updated.sliderVibrator
        }
        if (updated.selectVibrator != current.selectVibrator) {
            changes[PreferKey.selectVibrator] = updated.selectVibrator
        }
        if (updated.autoChangeSource != current.autoChangeSource) {
            changes[PreferKey.autoChangeSource] = updated.autoChangeSource
        }
        if (updated.autoSuggestDayNight != current.autoSuggestDayNight) {
            changes[PreferKey.autoSuggestDayNight] = updated.autoSuggestDayNight
        }
        if (updated.selectText != current.selectText) {
            changes[PreferKey.selectText] = updated.selectText
        }
        if (updated.noAnimScrollPage != current.noAnimScrollPage) {
            changes[PreferKey.noAnimScrollPage] = updated.noAnimScrollPage
        }
        if (updated.clickImgWay != current.clickImgWay) {
            changes[PreferKey.clickImgWay] = updated.clickImgWay
        }
        if (updated.optimizeRender != current.optimizeRender) {
            changes[PreferKey.optimizeRender] = updated.optimizeRender
        }
        if (updated.disableReturnKey != current.disableReturnKey) {
            changes[PreferKey.disableReturnKey] = updated.disableReturnKey
        }
        if (updated.expandTextMenu != current.expandTextMenu) {
            changes[PreferKey.expandTextMenu] = updated.expandTextMenu
        }
        if (updated.showSelectMenuIcon != current.showSelectMenuIcon) {
            changes[PreferKey.showSelectMenuIcon] = updated.showSelectMenuIcon
        }
        if (updated.showReadTitleAddition != current.showReadTitleAddition) {
            changes[PreferKey.showReadTitleAddition] = updated.showReadTitleAddition
        }
        if (updated.autoReadSpeed != current.autoReadSpeed) {
            changes[PreferKey.autoReadSpeed] = updated.autoReadSpeed
        }
        if (updated.prevKeys != current.prevKeys) {
            changes[PreferKey.prevKeys] = updated.prevKeys
        }
        if (updated.nextKeys != current.nextKeys) {
            changes[PreferKey.nextKeys] = updated.nextKeys
        }
        if (updated.showMenuIcon != current.showMenuIcon) {
            changes[PreferKey.showMenuIcon] = updated.showMenuIcon
        }

        if (changes.isNotEmpty()) {
            AppConfigStore.putAll(changes)
        }
    }

    fun <T> getPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: defaultValue
            }
    }

    suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
