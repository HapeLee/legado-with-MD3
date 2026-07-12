package io.legado.app.ui.config

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.data.repository.dataStore
import io.legado.app.help.config.DsSync
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefFloat
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefFloat
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.putPrefStringSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.IOException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface PrefDelegate<T> : ReadWriteProperty<Any?, T> {
    val state: State<T>
    fun dispose()
}

class PrefStateDelegate<T>(private val delegate: PrefDelegate<T>) : ReadWriteProperty<Any?, T> by delegate {
    val state: State<T> get() = delegate.state
}

fun <T> prefDelegate(
    key: String,
    defaultValue: T,
    lifecycleOwner: LifecycleOwner? = null,
    sync: Boolean = false,
    onValueChange: ((T) -> Unit)? = null
): PrefDelegate<T> {
    return object : PrefDelegate<T>, DefaultLifecycleObserver {
        private var _value: MutableState<T>
        override val state: State<T> get() = _value

        @Volatile
        private var currentValue: T = defaultValue
        private val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        private var dsObserverJob: Job? = null

        init {
            // 同步从 DataStore 读取初始值，确保构造完成后即为最新值
            val initialValue = runBlocking { readFromDs() } ?: defaultValue
            _value = mutableStateOf(initialValue)
            currentValue = initialValue

            // 观察 DataStore 变化，用于跨实例同步
            dsObserverJob = scope.launch {
                appCtx.dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { prefs ->
                        val strVal = runCatching { prefs[stringPreferencesKey(key)] }.getOrNull()
                        @Suppress("UNCHECKED_CAST")
                        when {
                            defaultValue is String || defaultValue == null ->
                                strVal as T?
                            defaultValue is Int ->
                                (runCatching { prefs[intPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toIntOrNull()) as T?
                            defaultValue is Boolean ->
                                (runCatching { prefs[booleanPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toBooleanStrictOrNull()) as T?
                            defaultValue is Long ->
                                (runCatching { prefs[longPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toLongOrNull()) as T?
                            defaultValue is Float ->
                                (runCatching { prefs[floatPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toFloatOrNull()) as T?
                            else -> null
                        }
                    }
                    .distinctUntilChanged()
                    .collect { dsValue ->
                        if (dsValue != null && currentValue != dsValue) {
                            updateValue(dsValue)
                            onValueChange?.invoke(dsValue)
                        }
                    }
            }
            // 注册生命周期观察者（如果有）
            if (lifecycleOwner != null) {
                lifecycleOwner.lifecycle.addObserver(this)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            dispose()
        }

        override fun dispose() {
            dsObserverJob?.cancel()
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return _value.value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (currentValue != value) {
                updateValue(value)
                // 同步写入 SP（向后兼容：AppConfig/MainViewModel 等仍直接读 SP）
                when (value) {
                    is String? -> if (sync) appCtx.putPrefStringSync(key, value) else appCtx.putPrefString(key, value)
                    is Int -> appCtx.putPrefInt(key, value)
                    is Boolean -> appCtx.putPrefBoolean(key, value)
                    is Long -> appCtx.putPrefLong(key, value)
                    is Float -> appCtx.putPrefFloat(key, value)
                }
                // 同步写入 DataStore，确保持久化后再返回
                runCatching {
                    runBlocking {
                        when (value) {
                            is String? -> DsSync.putString(key, value)
                            is Int -> DsSync.putInt(key, value)
                            is Boolean -> DsSync.putBoolean(key, value)
                            is Long -> DsSync.putLong(key, value)
                            is Float -> DsSync.putFloat(key, value)
                        }
                    }
                }
                onValueChange?.invoke(value)
            }
        }

        /**
         * 从 DataStore 读取当前值，DS 读不到时回退到 SP 并补写入 DS。
         */
        @Suppress("UNCHECKED_CAST")
        private suspend fun readFromDs(): T? {
            val dsValue = try {
                appCtx.dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { prefs ->
                        val strVal = runCatching { prefs[stringPreferencesKey(key)] }.getOrNull()
                        when {
                            defaultValue is String || defaultValue == null ->
                                strVal as T?
                            defaultValue is Int ->
                                (runCatching { prefs[intPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toIntOrNull()) as T?
                            defaultValue is Boolean ->
                                (runCatching { prefs[booleanPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toBooleanStrictOrNull()) as T?
                            defaultValue is Long ->
                                (runCatching { prefs[longPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toLongOrNull()) as T?
                            defaultValue is Float ->
                                (runCatching { prefs[floatPreferencesKey(key)] }.getOrNull()
                                    ?: strVal?.toFloatOrNull()) as T?
                            else -> null
                        }
                    }
                    .first()
            } catch (e: Exception) {
                null
            }
            // DS 有值，直接返回
            if (dsValue != null) return dsValue
            // DS 无值，回退到 SP（迁移遗漏时的补偿）
            val spValue: T? = when {
                defaultValue is String || defaultValue == null ->
                    appCtx.getPrefString(key, defaultValue as String?) as T?
                defaultValue is Int ->
                    appCtx.getPrefInt(key, defaultValue) as T
                defaultValue is Boolean ->
                    appCtx.getPrefBoolean(key, defaultValue) as T
                defaultValue is Long ->
                    appCtx.getPrefLong(key, defaultValue) as T
                defaultValue is Float ->
                    appCtx.getPrefFloat(key, defaultValue) as T
                else -> null
            }
            // DS 无值时，将 SP 值补写入 DS（修复迁移遗漏）
            if (spValue != null) {
                runCatching {
                    when (spValue) {
                        is String? -> DsSync.putString(key, spValue)
                        is Int -> DsSync.putInt(key, spValue)
                        is Boolean -> DsSync.putBoolean(key, spValue)
                        is Long -> DsSync.putLong(key, spValue)
                        is Float -> DsSync.putFloat(key, spValue)
                    }
                }
            }
            return spValue
        }

        private fun updateValue(value: T) {
            currentValue = value
            Snapshot.withMutableSnapshot {
                _value.value = value
            }
        }
    }
}

fun <T> prefStateDelegate(
    key: String,
    defaultValue: T,
    lifecycleOwner: LifecycleOwner? = null,
    sync: Boolean = false,
    onValueChange: ((T) -> Unit)? = null
): PrefStateDelegate<T> {
    val delegate = prefDelegate(key, defaultValue, lifecycleOwner, sync, onValueChange)
    return PrefStateDelegate(delegate)
}
