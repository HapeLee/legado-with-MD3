package io.legado.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.localDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_ui_status")

object LocalPreferencesKeys {
    val SHOW_THEME_REFACTOR_TIP = booleanPreferencesKey("show_theme_refactor_tip")
}
