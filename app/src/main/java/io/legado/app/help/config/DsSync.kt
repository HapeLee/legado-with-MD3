package io.legado.app.help.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.data.repository.dataStore
import splitties.init.appCtx

/**
 * DataStore 写入辅助对象。
 *
 * 让 [io.legado.app.ui.config.PrefDelegate] 在写入 SP 的同时同步写入 DataStore，
 * 保证两边数据一致，避免 DataStore 迁移/读取时出现值缺失。
 */
object DsSync {

    suspend fun putString(key: String, value: String?) {
        appCtx.dataStore.edit { prefs ->
            val dsKey = stringPreferencesKey(key)
            if (value == null) prefs.remove(dsKey) else prefs[dsKey] = value
        }
    }

    suspend fun putInt(key: String, value: Int) {
        appCtx.dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        appCtx.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun putLong(key: String, value: Long) {
        appCtx.dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun putFloat(key: String, value: Float) {
        appCtx.dataStore.edit { it[floatPreferencesKey(key)] = value }
    }
}
