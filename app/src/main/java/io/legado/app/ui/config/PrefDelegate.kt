package io.legado.app.ui.config

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface PrefDelegate<T> : ReadWriteProperty<Any?, T> {
    fun dispose()
}

fun <T> prefDelegate(
    key: String,
    defaultValue: T,
    lifecycleOwner: LifecycleOwner? = null,
    onValueChange: ((T) -> Unit)? = null
): PrefDelegate<T> {
    return object : PrefDelegate<T>, SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {
        private var _value: MutableState<T> = mutableStateOf(readInitialValue())

        init {
            if (lifecycleOwner != null) {
                lifecycleOwner.lifecycle.addObserver(this)
            } else {
                appCtx.defaultSharedPreferences.registerOnSharedPreferenceChangeListener(this)
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
                else -> defaultValue
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return _value.value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (_value.value != value) {
                when (value) {
                    is String? -> appCtx.putPrefString(key, value)
                    is Int -> appCtx.putPrefInt(key, value)
                    is Boolean -> appCtx.putPrefBoolean(key, value)
                    is Long -> appCtx.putPrefLong(key, value)
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