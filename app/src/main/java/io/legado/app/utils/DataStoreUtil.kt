package io.legado.app.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object DataStoreUtil {

    suspend fun <T> save(context: Context, key: String, value: T) {
        context.dataStore.edit { preferences ->
            when (value) {
                is String -> preferences[stringPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                is Float -> preferences[floatPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> read(context: Context, key: String, defaultValue: T): T {
        return context.dataStore.data.map { preferences ->
            when (defaultValue) {
                is String -> preferences[stringPreferencesKey(key)] ?: defaultValue
                is Int -> preferences[intPreferencesKey(key)] ?: defaultValue
                is Boolean -> preferences[booleanPreferencesKey(key)] ?: defaultValue
                is Float -> preferences[floatPreferencesKey(key)] ?: defaultValue
                is Long -> preferences[longPreferencesKey(key)] ?: defaultValue
                else -> defaultValue
            } as T
        }.first()
    }
}