package io.legado.app.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Shared typed preference access for repositories.
 *
 * Storage, migration, and write-through timing remain platform implementation
 * concerns. Callers only observe effective values and request updates by key.
 */
interface PreferenceStore {
    fun observeInt(key: String, defaultValue: Int = 0): Flow<Int>

    suspend fun setInt(key: String, value: Int)

    fun observeString(key: String, defaultValue: String = ""): Flow<String>

    suspend fun setString(key: String, value: String)
}
