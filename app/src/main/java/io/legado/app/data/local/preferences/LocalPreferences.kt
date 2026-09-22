package io.legado.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.localDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_ui_status")

object LocalPreferencesKeys {
    val MIGRATED_TO_SETTINGS = booleanPreferencesKey("__local_ui_status_migrated_to_settings")
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

    // 私密内容解锁：password 仍是唯一权威凭据，下面几项只是它的校验值与生物快捷解锁信封。
    // salt/verifier 由密码派生，绝不存放明文密码；envelope 是 Keystore 中
    // 「每次使用都需生物验证」密钥加密后的密码，只有生物验证成功才能解开。
    val PRIVATE_PASSWORD_SALT = stringPreferencesKey("private_password_salt")
    val PRIVATE_PASSWORD_VERIFIER = stringPreferencesKey("private_password_verifier")
    val PRIVATE_BIOMETRIC_ENABLED = booleanPreferencesKey("private_biometric_enabled")
    val PRIVATE_BIOMETRIC_ENVELOPE = stringPreferencesKey("private_biometric_envelope")
    val PRIVATE_BIOMETRIC_IV = stringPreferencesKey("private_biometric_iv")
    val PRIVATE_VERIFY_ON_ENTER_GROUP = booleanPreferencesKey("private_verify_on_enter_group")
    val PRIVATE_VERIFY_ON_OPEN_BOOK = booleanPreferencesKey("private_verify_on_open_book")
    val PRIVATE_VERIFY_ON_APP_START = booleanPreferencesKey("private_verify_on_app_start")
    val PRIVATE_UNLOCK_SCOPE = stringPreferencesKey("private_unlock_scope")

    // 私密功能总开关：关掉即整体休眠（不验证、不脱敏、不守卫），密码与标记都留着
    val PRIVATE_ENABLED = booleanPreferencesKey("private_enabled")

    // "离开应用前有效"频率下的宽限期（秒）：0 = 离开即失效
    val PRIVATE_BACKGROUND_GRACE_SECONDS = intPreferencesKey("private_background_grace_seconds")

    val PRIVACY_POLICY_OK = booleanPreferencesKey("privacy_policy_ok")
    val PERMISSION_CHECKED = booleanPreferencesKey("permission_checked")
    val DAILY_READING_GOAL_MINUTES = intPreferencesKey("daily_reading_goal_minutes")
    val READ_ALOUD_CAPSULE_OFFSET_X = floatPreferencesKey("read_aloud_capsule_offset_x")
    val READ_ALOUD_CAPSULE_OFFSET_Y = floatPreferencesKey("read_aloud_capsule_offset_y")

    // ENABLE_READ_RECORD也许需要换个地方存，但先放这
    val ENABLE_READ_RECORD = booleanPreferencesKey("enableReadRecord")
    val HOME_SOURCE_SET_URL = stringPreferencesKey("home_source_set_url")
    val HOME_DASHBOARD_SECTIONS = stringPreferencesKey("home_dashboard_sections")
    val READ_RECORD_DISPLAY_MODE = stringPreferencesKey("readRecordDisplayMode")
    val READ_RECORD_ALIAS_DECISIONS = stringPreferencesKey("readRecordAliasDecisions")
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

    // Book source check options
    val CHECK_SOURCE_TIMEOUT = longPreferencesKey("checkSourceTimeout")
    val CHECK_SOURCE_SEARCH = booleanPreferencesKey("checkSearch")
    val CHECK_SOURCE_DISCOVERY = booleanPreferencesKey("checkDiscovery")
    val CHECK_SOURCE_INFO = booleanPreferencesKey("checkInfo")
    val CHECK_SOURCE_CATEGORY = booleanPreferencesKey("checkCategory")
    val CHECK_SOURCE_CONTENT = booleanPreferencesKey("checkContent")
}
