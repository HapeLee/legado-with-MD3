package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.localDataStore
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import io.legado.app.help.config.rawPrefValue
import io.legado.app.help.config.setPrefValue
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                "${context.packageName}_preferences"
            ),
            LocalUiStatusMigration(context),
            ShowBrightnessViewMigration,
        )
    }
)

internal class LocalUiStatusMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val localPreferences = context.localDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        return mergeMissingLocalPreferences(currentData, localPreferences)
    }

    override suspend fun cleanUp() = Unit
}

internal fun mergeMissingLocalPreferences(
    currentData: Preferences,
    localPreferences: Preferences,
): Preferences = currentData.toMutablePreferences().apply {
    localPreferences.asMap().forEach { (key, value) ->
        if (currentData.rawPrefValue(key.name) == null) {
            setPrefValue(key.name, value)
        }
    }
    this[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] = true
}

internal object ShowBrightnessViewMigration : DataMigration<Preferences> {

    private val booleanKey = booleanPreferencesKey(PreferKey.showBrightnessView)
    private val stringKey = stringPreferencesKey(PreferKey.showBrightnessView)

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().entries.any { (key, value) ->
            key.name == PreferKey.showBrightnessView && value is Boolean
        }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val oldValue = currentData.asMap().entries
            .firstOrNull { (key, value) ->
                key.name == PreferKey.showBrightnessView && value is Boolean
            }
            ?.value as? Boolean
            ?: return currentData
        return currentData.toMutablePreferences().apply {
            remove(booleanKey)
            this[stringKey] = if (oldValue) "1" else "0"
        }
    }

    override suspend fun cleanUp() = Unit
}

/**
 * 设置仓储
 * 以 DataStore 为唯一持久化源，通过 [AppConfigStore] 的有效快照统一读写。
 */
class SettingsRepository {

    fun <T : Any> getPreference(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        AppConfigStore.preferencesFlow.map { it.compatDsValue(key, defaultValue) }

    suspend fun <T : Any> updatePreference(key: Preferences.Key<T>, value: T) {
        when (value) {
            is String -> AppConfigStore.putString(key.name, value)
            is Int -> AppConfigStore.putInt(key.name, value)
            is Boolean -> AppConfigStore.putBoolean(key.name, value)
            is Long -> AppConfigStore.putLong(key.name, value)
            is Float -> AppConfigStore.putFloat(key.name, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") {
                AppConfigStore.putStringSet(key.name, value as Set<String>)
            }
        }
    }

    // String 类型的快捷访问
    fun getString(key: String, defaultValue: String = ""): Flow<String> =
        getPreference(stringPreferencesKey(key), defaultValue)

    suspend fun putString(key: String, value: String) =
        AppConfigStore.putString(key, value)

    suspend fun putStrings(values: Map<String, String>) {
        AppConfigStore.putAll(values)
    }

    // Int 类型的快捷访问
    fun getInt(key: String, defaultValue: Int = 0): Flow<Int> =
        getPreference(intPreferencesKey(key), defaultValue)

    suspend fun putInt(key: String, value: Int) =
        updatePreference(intPreferencesKey(key), value)

    // Boolean 类型的快捷访问
    fun getBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean> =
        getPreference(booleanPreferencesKey(key), defaultValue)

    suspend fun putBoolean(key: String, value: Boolean) =
        updatePreference(booleanPreferencesKey(key), value)

    // Long 类型的快捷访问
    fun getLong(key: String, defaultValue: Long = 0L): Flow<Long> =
        getPreference(longPreferencesKey(key), defaultValue)

    suspend fun putLong(key: String, value: Long) =
        updatePreference(longPreferencesKey(key), value)

    // Float 类型的快捷访问
    fun getFloat(key: String, defaultValue: Float = 0f): Flow<Float> =
        getPreference(floatPreferencesKey(key), defaultValue)

    suspend fun putFloat(key: String, value: Float) =
        updatePreference(floatPreferencesKey(key), value)

    // Set<String> 类型的快捷访问
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Flow<Set<String>> =
        getPreference(stringSetPreferencesKey(key), defaultValue)

    suspend fun putStringSet(key: String, value: Set<String>) =
        updatePreference(stringSetPreferencesKey(key), value)

    // 移除配置
    suspend fun remove(key: String) {
        AppConfigStore.remove(key)
    }
}
