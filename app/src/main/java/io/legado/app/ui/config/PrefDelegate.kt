package io.legado.app.ui.config

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsFloat
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsLong
import io.legado.app.help.config.compatDsString
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
import io.legado.app.utils.removePref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import splitties.init.appCtx
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
    onValueChange: ((T) -> Unit)? = null
): PrefDelegate<T> {
    return object : PrefDelegate<T>, DefaultLifecycleObserver {
        private var _value: MutableState<T>
        override val state: State<T> get() = _value

        @Volatile
        private var currentValue: T = defaultValue
        private val scope = CoroutineScope(Dispatchers.IO)
        private var dsObserverJob: Job? = null

        init {
            // 初值走门面：AppConfigStore 内存快照（DataStore 唯一真源）
            val initialValue = readInitial()
            _value = mutableStateOf(initialValue)
            currentValue = initialValue

            // 观察快照变化，用于跨实例同步（含未落盘的 pending 写入，写后立即可见）
            dsObserverJob = scope.launch {
                AppConfigStore.preferencesFlow
                    .map { prefs -> readTyped(prefs) }
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
                if (value == null) {
                    appCtx.removePref(key)
                } else {
                    when (value) {
                        is String -> appCtx.putPrefString(key, value)
                        is Int -> appCtx.putPrefInt(key, value)
                        is Boolean -> appCtx.putPrefBoolean(key, value)
                        is Long -> appCtx.putPrefLong(key, value)
                        is Float -> appCtx.putPrefFloat(key, value)
                    }
                }
                onValueChange?.invoke(value)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun readInitial(): T = when {
            defaultValue is String || defaultValue == null ->
                appCtx.getPrefString(key, defaultValue as String?) as T
            defaultValue is Int -> appCtx.getPrefInt(key, defaultValue) as T
            defaultValue is Boolean -> appCtx.getPrefBoolean(key, defaultValue) as T
            defaultValue is Long -> appCtx.getPrefLong(key, defaultValue) as T
            defaultValue is Float -> appCtx.getPrefFloat(key, defaultValue) as T
            else -> defaultValue
        }

        @Suppress("UNCHECKED_CAST")
        private fun readTyped(prefs: Preferences): T? = when {
            defaultValue is String || defaultValue == null -> prefs.compatDsString(key) as T?
            defaultValue is Int -> prefs.compatDsInt(key) as T?
            defaultValue is Boolean -> prefs.compatDsBoolean(key) as T?
            defaultValue is Long -> prefs.compatDsLong(key) as T?
            defaultValue is Float -> prefs.compatDsFloat(key) as T?
            else -> null
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
    onValueChange: ((T) -> Unit)? = null
): PrefStateDelegate<T> {
    val delegate = prefDelegate(key, defaultValue, lifecycleOwner, onValueChange)
    return PrefStateDelegate(delegate)
}
