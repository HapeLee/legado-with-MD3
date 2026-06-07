package io.legado.app.ui.config

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.utils.defaultSharedPreferences
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
import io.legado.app.help.config.DsSync
import androidx.datastore.preferences.core.*
import io.legado.app.data.repository.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    return object : PrefDelegate<T>, SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {
        private var _value: MutableState<T> = mutableStateOf(readInitialValue())
        override val state: State<T> get() = _value
        private var dsObserverJob: Job? = null

        init {
            if (lifecycleOwner != null) {
                lifecycleOwner.lifecycle.addObserver(this)
            } else {
                appCtx.defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
            }
            // 观察 DataStore，以 DS 为读取权威源
            dsObserverJob = CoroutineScope(Dispatchers.IO).launch {
                appCtx.dataStore.data
                    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                    .map { prefs ->
                        // 安全读取 String 值，用于类型回退（类型不匹配时返回 null）
                        val strVal = runCatching { prefs[stringPreferencesKey(key)] }.getOrNull()
                        @Suppress("UNCHECKED_CAST")
                        when {
                            defaultValue is String || defaultValue == null ->
                                strVal as T?
                            defaultValue is Int ->
                                (prefs[intPreferencesKey(key)]
                                    ?: strVal?.toIntOrNull()) as T?
                            defaultValue is Boolean ->
                                (prefs[booleanPreferencesKey(key)]
                                    ?: strVal?.toBooleanStrictOrNull()) as T?
                            defaultValue is Long ->
                                (prefs[longPreferencesKey(key)]
                                    ?: strVal?.toLongOrNull()) as T?
                            defaultValue is Float ->
                                (prefs[floatPreferencesKey(key)]
                                    ?: strVal?.toFloatOrNull()) as T?
                            else -> null
                        }
                    }
                    .distinctUntilChanged()
                    .collect { dsValue ->
                        if (dsValue != null && _value.value != dsValue) {
                            _value.value = dsValue
                            onValueChange?.invoke(dsValue)
                        }
                    }
            }
        }

        override fun onCreate(owner: LifecycleOwner) {
            appCtx.defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            dispose()
        }

        override fun dispose() {
            appCtx.defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            dsObserverJob?.cancel()
        }

        @Suppress("UNCHECKED_CAST")
        private fun readInitialValue(): T {
            val prefs = appCtx.defaultSharedPreferences
            return when {
                defaultValue is String -> appCtx.getPrefString(key, defaultValue) as T
                defaultValue == null && prefs.contains(key) -> {
                    appCtx.getPrefString(key, null) as T
                }

                defaultValue is Int -> appCtx.getPrefInt(key, defaultValue) as T
                defaultValue is Boolean -> appCtx.getPrefBoolean(key, defaultValue) as T
                defaultValue is Long -> appCtx.getPrefLong(key, defaultValue) as T
                defaultValue is Float -> appCtx.getPrefFloat(key, defaultValue) as T
                else -> defaultValue
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return _value.value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (_value.value != value) {
                when (value) {
                    is String? -> if (sync) appCtx.putPrefStringSync(key, value) else appCtx.putPrefString(key, value)
                    is Int -> appCtx.putPrefInt(key, value)
                    is Boolean -> appCtx.putPrefBoolean(key, value)
                    is Long -> appCtx.putPrefLong(key, value)
                    is Float -> appCtx.putPrefFloat(key, value)
                }
                // 同步写入 DataStore，保持 SP/DS 一致
                CoroutineScope(Dispatchers.IO).launch {
                    when (value) {
                        is String? -> DsSync.putString(key, value)
                        is Int -> DsSync.putInt(key, value)
                        is Boolean -> DsSync.putBoolean(key, value)
                        is Long -> DsSync.putLong(key, value)
                        is Float -> DsSync.putFloat(key, value)
                    }
                }
                _value.value = value
                onValueChange?.invoke(value)
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, changedKey: String?) {
            if (changedKey == key) {
                val newValue = readInitialValue()
                if (_value.value != newValue) {
                    _value.value = newValue
                    onValueChange?.invoke(newValue)
                }
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