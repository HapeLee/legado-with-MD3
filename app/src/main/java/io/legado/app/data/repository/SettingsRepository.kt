package io.legado.app.data.repository

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.legado.app.constant.PreferKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                "${context.packageName}_preferences"
            ),
            ShowBrightnessViewMigration,
        )
    }
)

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
 * 以 DataStore 为唯一写入源，读取以 DataStore 为准。
 */
class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore

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

    // String 类型的快捷访问
    fun getString(key: String, defaultValue: String = ""): Flow<String> =
        getPreference(stringPreferencesKey(key), defaultValue)

    suspend fun putString(key: String, value: String) =
        updatePreference(stringPreferencesKey(key), value)

    suspend fun putStrings(values: Map<String, String>) {
        dataStore.edit { preferences ->
            values.forEach { (key, value) ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
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
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
            preferences.remove(intPreferencesKey(key))
            preferences.remove(booleanPreferencesKey(key))
            preferences.remove(longPreferencesKey(key))
            preferences.remove(floatPreferencesKey(key))
        }
    }
}
