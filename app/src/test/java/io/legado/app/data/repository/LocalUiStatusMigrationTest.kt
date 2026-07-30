package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.help.config.rawPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalUiStatusMigrationTest {

    @Test
    fun `copies legacy values missing from settings`() {
        val current = mutablePreferencesOf(stringPreferencesKey("existing") to "current")
        val local = mutablePreferencesOf(
            intPreferencesKey("search_layout_mode") to 2,
            booleanPreferencesKey("privacy_policy_ok") to true,
        )

        val migrated = mergeMissingLocalPreferences(current, local)

        assertEquals(2, migrated.rawPrefValue("search_layout_mode"))
        assertEquals(true, migrated.rawPrefValue("privacy_policy_ok"))
        assertEquals("current", migrated.rawPrefValue("existing"))
        assertTrue(migrated[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] == true)
    }

    @Test
    fun `keeps newer settings value when legacy contains same key`() {
        val current = mutablePreferencesOf(intPreferencesKey("search_layout_mode") to 3)
        val local = mutablePreferencesOf(intPreferencesKey("search_layout_mode") to 1)

        val migrated = mergeMissingLocalPreferences(current, local)

        assertEquals(3, migrated.rawPrefValue("search_layout_mode"))
    }

    @Test
    fun `marks migration complete when legacy store is empty`() {
        val migrated = mergeMissingLocalPreferences(
            currentData = mutablePreferencesOf(),
            localPreferences = mutablePreferencesOf(),
        )

        assertTrue(migrated[LocalPreferencesKeys.MIGRATED_TO_SETTINGS] == true)
    }
}
