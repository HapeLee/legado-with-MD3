package io.legado.app.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.removePref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                "${context.packageName}_preferences"
            )
        )
    }
)

/**
 * 设置仓储
 * 以 DataStore 为唯一写入源，读取以 DataStore 为准。
 * SP 同步由 PrefDelegate 双写保证（阶段 1），此处不再回写 SP。
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

    // 批量从 Map 恢复到 DataStore (用于兼容 Restore 逻辑)
    suspend fun batchPutFromMap(map: Map<String, *>) {
        dataStore.edit { preferences ->
            map.forEach { (key, value) ->
                when (value) {
                    is String -> preferences[stringPreferencesKey(key)] = value
                    is Int -> preferences[intPreferencesKey(key)] = value
                    is Boolean -> preferences[booleanPreferencesKey(key)] = value
                    is Long -> preferences[longPreferencesKey(key)] = value
                    is Float -> preferences[floatPreferencesKey(key)] = value
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        preferences[stringSetPreferencesKey(key)] = value as Set<String>
                    }
                }
            }
        }
    }

    /**
     * DataStore 迁移后同步校验：确保 SP 中的主题配置值未被迁移过程丢失。
     *
     * SharedPreferencesMigration 在首次访问 DataStore 时运行，将 SP 值复制到 DataStore。
     * 在某些场景下（进程被杀、ROM 行为等），迁移可能导致 SP 中的值丢失。
     * 此方法从 DataStore 读取关键主题配置，如果 SP 中缺失则补写回去。
     */
    suspend fun postMigrationSync() {
        val prefs = dataStore.data.first()
        val sp = context.defaultSharedPreferences
        val themeKeys = listOf(
            PreferKey.composeEngine,
            PreferKey.appTheme,
            PreferKey.themeMode,
            PreferKey.paletteStyle,
            PreferKey.materialVersion,
            PreferKey.customContrast,
            PreferKey.customMode,
        )
        var needsWrite = false
        val editor = mutableMapOf<String, String>()
        for (key in themeKeys) {
            if (!sp.contains(key)) {
                val dsValue = runCatching { prefs[stringPreferencesKey(key)] }.getOrNull()
                if (dsValue != null) {
                    editor[key] = dsValue
                    needsWrite = true
                }
            }
        }
        if (needsWrite) {
            sp.edit {
                editor.forEach { (key, value) ->
                    putString(key, value)
                }
            }
        }
    }

    // 移除配置
    suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
            preferences.remove(intPreferencesKey(key))
            preferences.remove(booleanPreferencesKey(key))
            preferences.remove(longPreferencesKey(key))
            preferences.remove(floatPreferencesKey(key))
        }
        context.removePref(key)
    }
}
