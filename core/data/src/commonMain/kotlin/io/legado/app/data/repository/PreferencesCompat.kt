package io.legado.app.data.repository

/**
 * Type-compatible reads over a preference snapshot, mirroring the historical
 * DataStore semantics of `compatDs*` in the app layer.
 *
 * Historical data (SharedPreferences migration, older releases) may store a value
 * under a key whose storage type differs from the read type: int stored as string
 * ("1"), long stored as int, etc. These helpers read by the actual stored type and
 * convert to the requested type, tolerating the drift.
 */
fun Map<String, PreferenceValue>.compatString(key: String): String? = when (val raw = this[key]) {
    is PreferenceValue.StringValue -> raw.value
    is PreferenceValue.LongValue -> raw.value.toString()
    is PreferenceValue.IntValue -> raw.value.toString()
    is PreferenceValue.FloatValue -> raw.value.toString()
    is PreferenceValue.BooleanValue -> raw.value.toString()
    else -> null
}

fun Map<String, PreferenceValue>.compatInt(key: String): Int? = when (val raw = this[key]) {
    is PreferenceValue.IntValue -> raw.value
    is PreferenceValue.LongValue -> raw.value.toInt()
    is PreferenceValue.FloatValue -> raw.value.toInt()
    is PreferenceValue.StringValue -> raw.value.toIntOrNull()
    else -> null
}

fun Map<String, PreferenceValue>.compatBoolean(key: String): Boolean? = when (val raw = this[key]) {
    is PreferenceValue.BooleanValue -> raw.value
    is PreferenceValue.StringValue -> raw.value.toBooleanStrictOrNull() ?: when (raw.value) {
        "1" -> true
        "0" -> false
        else -> null
    }
    is PreferenceValue.IntValue -> when (raw.value) {
        1 -> true
        0 -> false
        else -> null
    }
    is PreferenceValue.LongValue -> when (raw.value) {
        1L -> true
        0L -> false
        else -> null
    }
    else -> null
}

fun Map<String, PreferenceValue>.compatLong(key: String): Long? = when (val raw = this[key]) {
    is PreferenceValue.LongValue -> raw.value
    is PreferenceValue.IntValue -> raw.value.toLong()
    is PreferenceValue.FloatValue -> raw.value.toLong()
    is PreferenceValue.StringValue -> raw.value.toLongOrNull()
    else -> null
}

fun Map<String, PreferenceValue>.compatFloat(key: String): Float? = when (val raw = this[key]) {
    is PreferenceValue.FloatValue -> raw.value
    is PreferenceValue.IntValue -> raw.value.toFloat()
    is PreferenceValue.LongValue -> raw.value.toFloat()
    is PreferenceValue.StringValue -> raw.value.toFloatOrNull()
    else -> null
}

/**
 * Reads by the runtime type of [defaultValue], tolerating historical type drift of
 * the stored value under [key]. [defaultValue] must be a recognizable non-null type.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Map<String, PreferenceValue>.compatValue(key: String, defaultValue: T): T =
    when (defaultValue) {
        is String -> (compatString(key) ?: defaultValue) as T
        is Int -> (compatInt(key) ?: defaultValue) as T
        is Boolean -> (compatBoolean(key) ?: defaultValue) as T
        is Long -> (compatLong(key) ?: defaultValue) as T
        is Float -> (compatFloat(key) ?: defaultValue) as T
        else -> defaultValue
    }

/** Wraps a heterogeneous value as the matching [PreferenceValue], or null for null. */
fun preferenceValueOf(value: Any?): PreferenceValue? = when (value) {
    null -> null
    is String -> PreferenceValue.StringValue(value)
    is Int -> PreferenceValue.IntValue(value)
    is Boolean -> PreferenceValue.BooleanValue(value)
    is Long -> PreferenceValue.LongValue(value)
    is Float -> PreferenceValue.FloatValue(value)
    else -> null
}

/**
 * 通用 settings 原子更新：读快照为 domain model，套用纯 [transform]，再把
 * [toPrefMap]（`Map<String, Any?>` 形态，与历史 DataStore 层一致）写回快照。
 * 差量落盘由 [PreferenceStore.atomicUpdate] 的平台实现负责。
 */
suspend inline fun <T> PreferenceStore.atomicUpdateSettings(
    crossinline read: (Map<String, PreferenceValue>) -> T,
    crossinline toPrefMap: (T) -> Map<String, Any?>,
    crossinline transform: (T) -> T,
) {
    atomicUpdate { snapshot ->
        val updated = transform(read(snapshot))
        snapshot + toPrefMap(updated).mapValues { (_, value) -> preferenceValueOf(value) }
    }
}
