package io.legado.app.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Shared typed preference access for repositories.
 *
 * Storage, migration, and write-through timing remain platform implementation
 * concerns. Callers only observe effective values and request updates by key.
 *
 * Two access styles coexist:
 * 1. Flat typed access (`currentLong`/`observeInt`/`setString`/…) for repositories
 *    that read a handful of keys directly.
 * 2. Snapshot + atomic-update access (`currentSnapshot`/`observeSnapshot`/`atomicUpdate`)
 *    for settings repositories that map a whole snapshot to a domain model and write
 *    via a pure `transform` function.
 */
interface PreferenceStore {
    fun currentValues(defaults: Map<String, PreferenceValue>): Map<String, PreferenceValue>

    fun observeValues(defaults: Map<String, PreferenceValue>): Flow<Map<String, PreferenceValue>>

    fun currentLong(key: String, defaultValue: Long = 0L): Long

    fun currentBoolean(key: String, defaultValue: Boolean = false): Boolean

    fun observeLong(key: String, defaultValue: Long = 0L): Flow<Long>

    fun observeBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean>

    fun observeInt(key: String, defaultValue: Int = 0): Flow<Int>

    suspend fun setInt(key: String, value: Int)

    suspend fun setBoolean(key: String, value: Boolean)

    suspend fun setFloat(key: String, value: Float)

    /** Applies several string values as one durable update. */
    suspend fun setStrings(values: Map<String, String>)

    fun observeString(key: String, defaultValue: String = ""): Flow<String>

    suspend fun setString(key: String, value: String)

    /** Applies all values as one durable update, or throws without reporting success. */
    suspend fun setAllAndAwait(values: Map<String, PreferenceValue>)

    /** Current effective snapshot keyed by preference key name. */
    fun currentSnapshot(): Map<String, PreferenceValue>

    /** Streams the effective snapshot keyed by preference key name. */
    fun observeSnapshot(): Flow<Map<String, PreferenceValue>>

    /**
     * Atomically replaces the snapshot using a pure [transform] (read-modify-write
     * under the store's own lock). The returned map becomes the new effective state;
     * a null value means "remove that key". The platform implementation persists the
     * diff. [transform] must be pure, fast, and non-suspending.
     */
    suspend fun atomicUpdate(transform: (Map<String, PreferenceValue>) -> Map<String, PreferenceValue?>)
}

sealed interface PreferenceValue {
    data class LongValue(val value: Long) : PreferenceValue
    data class BooleanValue(val value: Boolean) : PreferenceValue
    data class IntValue(val value: Int) : PreferenceValue
    data class FloatValue(val value: Float) : PreferenceValue
    data class StringValue(val value: String) : PreferenceValue
}
