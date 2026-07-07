package io.legado.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.localDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_ui_status")

object LocalPreferencesKeys {
    val SHOW_THEME_REFACTOR_TIP = booleanPreferencesKey("show_theme_refactor_tip")
    val SEARCH_LAYOUT_MODE = intPreferencesKey("search_layout_mode")
    val SEARCH_SCOPE = stringPreferencesKey("search_scope")
    val MATCH_MODE = intPreferencesKey("match_mode")
    val EXPLORE_LAYOUT_MODE = intPreferencesKey("explore_layout_mode")
    val EXPLORE_LAYOUT_GRID_PORTRAIT = intPreferencesKey("explore_layout_grid_portrait")
    val EXPLORE_LAYOUT_GRID_LANDSCAPE = intPreferencesKey("explore_layout_grid_landscape")
    val READ_URL_IN_BROWSER = booleanPreferencesKey("read_url_in_browser")
    val LAST_BACKUP = longPreferencesKey("last_backup")
    val PASSWORD = stringPreferencesKey("password")
    val PRIVACY_POLICY_OK = booleanPreferencesKey("privacy_policy_ok")
    val PERMISSION_CHECKED = booleanPreferencesKey("permission_checked")
    val DAILY_READING_GOAL_MINUTES = intPreferencesKey("daily_reading_goal_minutes")

    // ENABLE_READ_RECORD也许需要换个地方存，但先放这
    val ENABLE_READ_RECORD = booleanPreferencesKey("enableReadRecord")
    val HOME_SOURCE_SET_URL = stringPreferencesKey("home_source_set_url")
    val HOME_DASHBOARD_SECTIONS = stringPreferencesKey("home_dashboard_sections")
    val COVER_ALBUM_MIGRATED = booleanPreferencesKey("cover_album_migrated")
    val SELECTED_COVER_ALBUM_ID = stringPreferencesKey("selected_cover_album_id")
    val SELECTED_LIGHT_COVER_ALBUM_ID = stringPreferencesKey("selected_light_cover_album_id")
    val SELECTED_DARK_COVER_ALBUM_ID = stringPreferencesKey("selected_dark_cover_album_id")

    // Change source options
    val CHANGE_SOURCE_CHECK_AUTHOR = booleanPreferencesKey("changeSourceCheckAuthor")
    val CHANGE_SOURCE_LOAD_INFO = booleanPreferencesKey("changeSourceLoadInfo")
    val CHANGE_SOURCE_LOAD_TOC = booleanPreferencesKey("changeSourceLoadToc")
    val CHANGE_SOURCE_LOAD_WORD_COUNT = booleanPreferencesKey("changeSourceLoadWordCount")
    val CHANGE_SOURCE_SEARCH_SCOPE = stringPreferencesKey("changeSourceSearchScope")
}
