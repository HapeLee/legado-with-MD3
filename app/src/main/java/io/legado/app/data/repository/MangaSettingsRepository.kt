package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.manga.config.MangaScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class MangaPreferences(
    val showMangaUi: Boolean = true,
    val disableMangaScale: Boolean = true,
    val disableMangaScrollAnimation: Boolean = false,
    val disableMangaCrossFade: Boolean = false,
    val disableClickScroll: Boolean = false,
    val mangaPreDownloadNum: Int = 10,
    val mangaAutoPageSpeed: Int = 3,
    val mangaFooterConfig: String = "",
    val mangaScrollMode: Int = MangaScrollMode.WEBTOON,
    val mangaLongClick: Boolean = true,
    val mangaBackground: Int = 0xFF000000.toInt(),
    val mangaColorFilter: String = "",
    val hideMangaTitle: Boolean = false,
    val enableMangaEInk: Boolean = false,
    val mangaEInkThreshold: Int = 150,
    val enableMangaGray: Boolean = false,
    val webtoonSidePaddingDp: Int = 0,
    val mangaVolumeKeyPage: Boolean = false,
    val reverseVolumeKeyPage: Boolean = false,
    val clickActionTL: Int = -1,
    val clickActionTC: Int = -1,
    val clickActionTR: Int = 1,
    val clickActionML: Int = 2,
    val clickActionMC: Int = 0,
    val clickActionMR: Int = 1,
    val clickActionBL: Int = 2,
    val clickActionBC: Int = 1,
    val clickActionBR: Int = 1
)

class MangaSettingsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    val preferences: Flow<MangaPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences.toMangaPreferences()
        }

    suspend fun setShowMangaUi(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.showMangaUi, value)

    suspend fun setMangaPreDownloadNum(value: Int) =
        settingsRepository.putInt(PreferKey.mangaPreDownloadNum, value)

    suspend fun setMangaAutoPageSpeed(value: Int) =
        settingsRepository.putInt(PreferKey.mangaAutoPageSpeed, value)

    suspend fun setDisableClickScroll(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.disableClickScroll, value)

    suspend fun setDisableMangaScrollAnimation(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.disableMangaScrollAnimation, value)

    suspend fun setDisableMangaCrossFade(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.disableMangaCrossFade, value)

    suspend fun setDisableMangaScale(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.disableMangaScale, value)

    suspend fun setEnableMangaEInk(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.enableMangaEInk, value)

    suspend fun setMangaEInkThreshold(value: Int) =
        settingsRepository.putInt(PreferKey.mangaEInkThreshold, value)

    suspend fun setEnableMangaGray(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.enableMangaGray, value)

    suspend fun setMangaAutoColorFilter(value: String) =
        settingsRepository.putString(PreferKey.mangaColorFilter, value)

    suspend fun setMangaBackground(value: Int) =
        settingsRepository.putInt(PreferKey.mangaBackground, value)

    suspend fun setMangaLongClick(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.mangaLongClick, value)

    suspend fun setMangaVolumeKeyPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.mangaVolumeKeyPage, value)

    suspend fun setReverseVolumeKeyPage(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.reverseVolumeKeyPage, value)

    suspend fun setHideMangaTitle(value: Boolean) =
        settingsRepository.putBoolean(PreferKey.hideMangaTitle, value)

    suspend fun setMangaFooterConfig(value: String) =
        settingsRepository.putString(PreferKey.mangaFooterConfig, value)

    suspend fun setMangaClickAction(key: String, value: Int) =
        settingsRepository.putInt(key, value)

    private fun Preferences.toMangaPreferences(): MangaPreferences {
        return MangaPreferences(
            showMangaUi = this[Keys.ShowMangaUi] ?: true,
            disableMangaScale = this[Keys.DisableMangaScale] ?: true,
            disableMangaScrollAnimation = this[Keys.DisableMangaScrollAnimation] ?: false,
            disableMangaCrossFade = this[Keys.DisableMangaCrossFade] ?: false,
            disableClickScroll = this[Keys.DisableClickScroll] ?: false,
            mangaPreDownloadNum = this[Keys.MangaPreDownloadNum] ?: 10,
            mangaAutoPageSpeed = this[Keys.MangaAutoPageSpeed] ?: 3,
            mangaFooterConfig = this[Keys.MangaFooterConfig] ?: "",
            mangaScrollMode = this[Keys.MangaScrollMode] ?: MangaScrollMode.WEBTOON,
            mangaLongClick = this[Keys.MangaLongClick] ?: true,
            mangaBackground = this[Keys.MangaBackground] ?: 0xFF000000.toInt(),
            mangaColorFilter = this[Keys.MangaColorFilter] ?: "",
            hideMangaTitle = this[Keys.HideMangaTitle] ?: false,
            enableMangaEInk = this[Keys.EnableMangaEInk] ?: false,
            mangaEInkThreshold = this[Keys.MangaEInkThreshold] ?: 150,
            enableMangaGray = this[Keys.EnableMangaGray] ?: false,
            webtoonSidePaddingDp = this[Keys.WebtoonSidePaddingDp] ?: 0,
            mangaVolumeKeyPage = this[Keys.MangaVolumeKeyPage] ?: false,
            reverseVolumeKeyPage = this[Keys.ReverseVolumeKeyPage] ?: false,
            clickActionTL = this[Keys.ClickActionTL] ?: -1,
            clickActionTC = this[Keys.ClickActionTC] ?: -1,
            clickActionTR = this[Keys.ClickActionTR] ?: 1,
            clickActionML = this[Keys.ClickActionML] ?: 2,
            clickActionMC = this[Keys.ClickActionMC] ?: 0,
            clickActionMR = this[Keys.ClickActionMR] ?: 1,
            clickActionBL = this[Keys.ClickActionBL] ?: 2,
            clickActionBC = this[Keys.ClickActionBC] ?: 1,
            clickActionBR = this[Keys.ClickActionBR] ?: 1
        )
    }

    private object Keys {
        val ShowMangaUi = booleanPreferencesKey(PreferKey.showMangaUi)
        val DisableMangaScale = booleanPreferencesKey(PreferKey.disableMangaScale)
        val DisableMangaScrollAnimation =
            booleanPreferencesKey(PreferKey.disableMangaScrollAnimation)
        val DisableMangaCrossFade = booleanPreferencesKey(PreferKey.disableMangaCrossFade)
        val DisableClickScroll = booleanPreferencesKey(PreferKey.disableClickScroll)
        val MangaPreDownloadNum = intPreferencesKey(PreferKey.mangaPreDownloadNum)
        val MangaAutoPageSpeed = intPreferencesKey(PreferKey.mangaAutoPageSpeed)
        val MangaFooterConfig = stringPreferencesKey(PreferKey.mangaFooterConfig)
        val MangaScrollMode = intPreferencesKey(PreferKey.mangaScrollMode)
        val MangaLongClick = booleanPreferencesKey(PreferKey.mangaLongClick)
        val MangaBackground = intPreferencesKey(PreferKey.mangaBackground)
        val MangaColorFilter = stringPreferencesKey(PreferKey.mangaColorFilter)
        val HideMangaTitle = booleanPreferencesKey(PreferKey.hideMangaTitle)
        val EnableMangaEInk = booleanPreferencesKey(PreferKey.enableMangaEInk)
        val MangaEInkThreshold = intPreferencesKey(PreferKey.mangaEInkThreshold)
        val EnableMangaGray = booleanPreferencesKey(PreferKey.enableMangaGray)
        val WebtoonSidePaddingDp = intPreferencesKey(PreferKey.webtoonSidePaddingDp)
        val MangaVolumeKeyPage = booleanPreferencesKey(PreferKey.mangaVolumeKeyPage)
        val ReverseVolumeKeyPage = booleanPreferencesKey(PreferKey.reverseVolumeKeyPage)
        val ClickActionTL = intPreferencesKey(PreferKey.mangaClickActionTL)
        val ClickActionTC = intPreferencesKey(PreferKey.mangaClickActionTC)
        val ClickActionTR = intPreferencesKey(PreferKey.mangaClickActionTR)
        val ClickActionML = intPreferencesKey(PreferKey.mangaClickActionML)
        val ClickActionMC = intPreferencesKey(PreferKey.mangaClickActionMC)
        val ClickActionMR = intPreferencesKey(PreferKey.mangaClickActionMR)
        val ClickActionBL = intPreferencesKey(PreferKey.mangaClickActionBL)
        val ClickActionBC = intPreferencesKey(PreferKey.mangaClickActionBC)
        val ClickActionBR = intPreferencesKey(PreferKey.mangaClickActionBR)
    }
}
