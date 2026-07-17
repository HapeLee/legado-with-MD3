package io.legado.app.help.config

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore 值的类型兼容读写。
 *
 * 历史数据（SP 迁移、旧版本写入）里同一 key 的存储类型可能与读取类型不一致：
 * int 存成 string（"1"）、long 存成 int 等。这里统一做"按实际存储类型取值，再转换到目标类型"。
 *
 * 注意：依赖 [Preferences.Key] 的 equals/hashCode 只比较 name（datastore 既有行为，
 * 也是同名不同类型 key 会互相覆盖的原因），因此用任意类型的 key 都能取到该 name 下的原始值。
 */

/**
 * 取 key 对应的原始存储值（不做类型断言）。
 * 声明为 Any? 避免泛型擦除后的 checkcast，存储类型不匹配也不会抛 ClassCastException。
 */
fun Preferences.rawPrefValue(key: String): Any? = this[stringPreferencesKey(key)]

fun Preferences.compatDsString(key: String): String? = rawPrefValue(key) as? String

fun Preferences.compatDsInt(key: String): Int? = when (val raw = rawPrefValue(key)) {
    is Int -> raw
    is String -> raw.toIntOrNull()
    else -> null
}

fun Preferences.compatDsBoolean(key: String): Boolean? = when (val raw = rawPrefValue(key)) {
    is Boolean -> raw
    is String -> raw.toBooleanStrictOrNull()
    else -> null
}

fun Preferences.compatDsLong(key: String): Long? = when (val raw = rawPrefValue(key)) {
    is Long -> raw
    is Int -> raw.toLong()
    is String -> raw.toLongOrNull()
    else -> null
}

fun Preferences.compatDsFloat(key: String): Float? = when (val raw = rawPrefValue(key)) {
    is Float -> raw
    is Int -> raw.toFloat()
    is String -> raw.toFloatOrNull()
    else -> null
}

fun Preferences.compatDsStringSet(key: String): Set<String>? = when (val raw = rawPrefValue(key)) {
    is Set<*> -> @Suppress("UNCHECKED_CAST") (raw as Set<String>)
    else -> null
}

/**
 * 按 value 的运行时类型写入对应类型的 key；value 为 null 表示移除。
 * 由于 Key 按 name 判等，写入会覆盖同名的旧类型条目，移除一次即可清掉任意类型的条目。
 */
fun MutablePreferences.setPrefValue(key: String, value: Any?) {
    when (value) {
        null -> remove(stringPreferencesKey(key))
        is String -> this[stringPreferencesKey(key)] = value
        is Int -> this[intPreferencesKey(key)] = value
        is Boolean -> this[booleanPreferencesKey(key)] = value
        is Long -> this[longPreferencesKey(key)] = value
        is Float -> this[floatPreferencesKey(key)] = value
        is Set<*> -> @Suppress("UNCHECKED_CAST") {
            this[stringSetPreferencesKey(key)] = value as Set<String>
        }
    }
}
