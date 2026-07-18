package io.legado.app.help.storage

import io.legado.app.data.local.preferences.LocalPreferencesKeys
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConfigSecurityTest {

    @Test
    fun `local password and migration marker are never exported`() {
        assertTrue(LocalPreferencesKeys.PASSWORD.name in alwaysIgnoredPreferenceKeys)
        assertTrue(LocalPreferencesKeys.MIGRATED_TO_SETTINGS.name in alwaysIgnoredPreferenceKeys)
    }
}
