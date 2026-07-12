package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowBrightnessViewMigrationTest {

    private val booleanKey = booleanPreferencesKey(PreferKey.showBrightnessView)
    private val stringKey = stringPreferencesKey(PreferKey.showBrightnessView)

    @Test
    fun `migrates legacy enabled value to horizontal mode`() = runBlocking {
        val preferences = mutablePreferencesOf(booleanKey to true)

        assertTrue(ShowBrightnessViewMigration.shouldMigrate(preferences))

        val migrated = ShowBrightnessViewMigration.migrate(preferences)
        assertEquals("1", migrated[stringKey])
    }

    @Test
    fun `migrates legacy disabled value to off mode`() = runBlocking {
        val preferences = mutablePreferencesOf(booleanKey to false)

        val migrated = ShowBrightnessViewMigration.migrate(preferences)
        assertEquals("0", migrated[stringKey])
    }

    @Test
    fun `does not migrate current string value`() = runBlocking {
        val preferences = mutablePreferencesOf(stringKey to "2")

        assertFalse(ShowBrightnessViewMigration.shouldMigrate(preferences))
        assertEquals(preferences, ShowBrightnessViewMigration.migrate(preferences))
    }
}
