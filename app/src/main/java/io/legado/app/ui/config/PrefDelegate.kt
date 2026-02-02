package io.legado.app.ui.config

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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

fun <T> prefDelegate(
    key: String,
    defaultValue: T,
    onValueChange: ((T) -> Unit)? = null
): ReadWriteProperty<Any?, T> {
    return object : ReadWriteProperty<Any?, T> {
        private var _value: MutableState<T> = mutableStateOf(readInitialValue())

        @Suppress("UNCHECKED_CAST")
        private fun readInitialValue(): T {
            return when (defaultValue) {
                is String -> appCtx.getPrefString(key, defaultValue) as T
                is Int -> appCtx.getPrefInt(key, defaultValue) as T
                is Boolean -> appCtx.getPrefBoolean(key, defaultValue) as T
                is Long -> appCtx.getPrefLong(key, defaultValue) as T
                else -> defaultValue
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return _value.value
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (_value.value != value) {
                when (value) {
                    is String -> appCtx.putPrefString(key, value)
                    is Int -> appCtx.putPrefInt(key, value)
                    is Boolean -> appCtx.putPrefBoolean(key, value)
                    is Long -> appCtx.putPrefLong(key, value)
                }
                _value.value = value
                onValueChange?.invoke(value)
            }
        }
    }
}