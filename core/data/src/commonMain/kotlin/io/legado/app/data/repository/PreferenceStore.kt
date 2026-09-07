package io.legado.app.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Shared typed preference access for repositories.
 *
 * Storage, migration, and write-through timing remain platform implementation
 * concerns. Callers only observe effective values and request updates by key.
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

    fun observeString(key: String, defaultValue: String = ""): Flow<String>

    suspend fun setString(key: String, value: String)

    /** Applies all values as one durable update, or throws without reporting success. */
    suspend fun setAllAndAwait(values: Map<String, PreferenceValue>)
}

sealed interface PreferenceValue {
    data class LongValue(val value: Long) : PreferenceValue
    data class BooleanValue(val value: Boolean) : PreferenceValue
    data class IntValue(val value: Int) : PreferenceValue
    data class StringValue(val value: String) : PreferenceValue
}
