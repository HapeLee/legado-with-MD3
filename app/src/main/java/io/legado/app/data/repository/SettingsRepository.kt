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
class SettingsRepository : PreferenceStore {
    override fun currentValues(defaults: Map<String, PreferenceValue>): Map<String, PreferenceValue> =
        AppConfigStore.preferences.toPreferenceValues(defaults)

    override fun observeValues(
        defaults: Map<String, PreferenceValue>,
    ): Flow<Map<String, PreferenceValue>> = AppConfigStore.preferencesFlow.map { preferences ->
        preferences.toPreferenceValues(defaults)
    }

    override fun currentLong(key: String, defaultValue: Long): Long =
        AppConfigStore.getLong(key) ?: defaultValue

    override fun currentBoolean(key: String, defaultValue: Boolean): Boolean =
        AppConfigStore.getBoolean(key) ?: defaultValue

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> =
        getLong(key, defaultValue)

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
        getBoolean(key, defaultValue)

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> = getInt(key, defaultValue)

    override suspend fun setInt(key: String, value: Int) = putInt(key, value)

    override suspend fun setBoolean(key: String, value: Boolean) = putBoolean(key, value)

    override suspend fun setFloat(key: String, value: Float) = putFloat(key, value)

    override suspend fun setStrings(values: Map<String, String>) = putStrings(values)

    override fun observeString(key: String, defaultValue: String): Flow<String> = getString(key, defaultValue)

    override suspend fun setString(key: String, value: String) = putString(key, value)

    override suspend fun setAllAndAwait(values: Map<String, PreferenceValue>) {
        AppConfigStore.putAllAndAwait(values.mapValues { (_, value) -> value.raw })
    }

    override fun currentSnapshot(): Map<String, PreferenceValue> =
        AppConfigStore.preferences.toSnapshot()

    override fun observeSnapshot(): Flow<Map<String, PreferenceValue>> =
        AppConfigStore.preferencesFlow.map { it.toSnapshot() }

    override suspend fun atomicUpdate(
        transform: (Map<String, PreferenceValue>) -> Map<String, PreferenceValue?>,
    ) {
        AppConfigStore.atomicUpdateAndAwait(
            read = { it.toSnapshot() as Map<String, PreferenceValue?> },
            toPrefMap = { it.mapValues { (_, v) -> v?.raw } },
            transform = transform as (Map<String, PreferenceValue?>) -> Map<String, PreferenceValue?>,
        )
    }

    private val PreferenceValue.raw: Any?
        get() = when (this) {
            is PreferenceValue.LongValue -> value
            is PreferenceValue.BooleanValue -> value
            is PreferenceValue.IntValue -> value
            is PreferenceValue.FloatValue -> value
            is PreferenceValue.StringValue -> value
        }

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

private fun Preferences.toPreferenceValues(
    defaults: Map<String, PreferenceValue>,
): Map<String, PreferenceValue> = defaults.mapValues { (key, defaultValue) ->
    when (defaultValue) {
        is PreferenceValue.LongValue -> PreferenceValue.LongValue(
            compatDsValue(longPreferencesKey(key), defaultValue.value),
        )
        is PreferenceValue.BooleanValue -> PreferenceValue.BooleanValue(
            compatDsValue(booleanPreferencesKey(key), defaultValue.value),
        )
        is PreferenceValue.IntValue -> PreferenceValue.IntValue(
            compatDsValue(intPreferencesKey(key), defaultValue.value),
        )
        is PreferenceValue.FloatValue -> PreferenceValue.FloatValue(
            compatDsValue(floatPreferencesKey(key), defaultValue.value),
        )
        is PreferenceValue.StringValue -> PreferenceValue.StringValue(
            compatDsValue(stringPreferencesKey(key), defaultValue.value),
        )
    }
}

/**
 * 把整块 Preferences 快照转成 core:data 的 [PreferenceValue] 映射。
 * 保留原始存储类型（不做历史漂移转换），漂移兼容交由 core:data 的
 * [io.legado.app.data.repository.compatValue] 在消费侧完成。
 */
private fun Preferences.toSnapshot(): Map<String, PreferenceValue> = buildMap {
    asMap().forEach { (key, value) ->
        val wrapped = when (value) {
            is String -> PreferenceValue.StringValue(value)
            is Int -> PreferenceValue.IntValue(value)
            is Boolean -> PreferenceValue.BooleanValue(value)
            is Long -> PreferenceValue.LongValue(value)
            is Float -> PreferenceValue.FloatValue(value)
            else -> return@forEach
        }
        put(key.name, wrapped)
    }
}

