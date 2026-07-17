package io.legado.app.data.local.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfigStore
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

    // 唯一状态真理守护者：桥接自 AppConfigStore 并洗白历史脏类型
    val appSettings: StateFlow<AppSettings> = AppConfigStore.preferencesFlow
        .map { preferences ->
            val themeMode = preferences.compatDsString(PreferKey.themeMode) ?: "0"
            val isDark = when (themeMode) {
                "1" -> false
                "2" -> true
                else -> {
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                }
            }
            AppSettings(
                darkTheme = isDark,
                language = preferences.compatDsString(PreferKey.language) ?: "auto",
                readAloudSpeed = preferences.compatDsInt(PreferKey.ttsSpeechRate) ?: 5,
                textWeight = preferences.compatDsInt("textWeight") ?: 1,
                showBrightnessView = preferences.compatDsString(PreferKey.showBrightnessView) != "0"
            )
        }.stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    /**
     * 优雅的闭包更新变换，物理落盘统一差异化批量投递
     */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = appSettings.value
        val updated = transform(current)

        val changedPairs = mutableMapOf<String, Any?>()
        if (updated.darkTheme != current.darkTheme) {
            changedPairs[PreferKey.themeMode] = if (updated.darkTheme) "2" else "1"
        }
        if (updated.language != current.language) {
            changedPairs[PreferKey.language] = updated.language
        }
        if (updated.readAloudSpeed != current.readAloudSpeed) {
            changedPairs[PreferKey.ttsSpeechRate] = updated.readAloudSpeed
        }
        if (updated.textWeight != current.textWeight) {
            changedPairs["textWeight"] = updated.textWeight
        }
        if (updated.showBrightnessView != current.showBrightnessView) {
            changedPairs[PreferKey.showBrightnessView] = if (updated.showBrightnessView) "1" else "0"
        }

        if (changedPairs.isNotEmpty()) {
            AppConfigStore.putAll(changedPairs)
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
