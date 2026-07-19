package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.help.config.setPrefValue
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSettingsMappingTest {

    @Test
    fun `备份设置各字段写读映射往返恒等且可识别布尔键交换`() {
        val base = BackupSettings(
            webDavUrl = "https://backup.example.test/dav",
            webDavAccount = "backup-account",
            webDavPassword = "backup-password",
            webDavDir = "backup-directory",
            webDavDeviceName = "backup-device",
            backupSyncMode = "local",
            backupPath = "content://backup/path",
        )
        val samples = listOf(
            base,
            base.copy(syncBookProgress = false),
            base.copy(syncBookProgressPlus = true),
            base.copy(autoCheckNewBackup = false),
            base.copy(onlyLatestBackup = false),
        )

        samples.forEach { expected ->
            assertEquals(expected, expected.toPrefMap().toPreferences().toBackupSettings())
        }
    }

    @Test
    fun `copy 同时变更多字段只产生对应差量并支持删除 nullable 键`() {
        val current = BackupSettings(
            webDavAccount = "old-account",
            webDavPassword = "old-password",
            syncBookProgress = true,
            syncBookProgressPlus = true,
            backupPath = "content://old/path",
        )

        val diff = current.diffPrefMap(
            transform = {
                it.copy(
                    webDavAccount = "new-account",
                    webDavPassword = "new-password",
                    syncBookProgress = false,
                    syncBookProgressPlus = false,
                    backupPath = null,
                )
            },
            toPrefMap = BackupSettings::toPrefMap,
        )

        assertEquals(
            mapOf(
                PreferKey.webDavAccount to "new-account",
                PreferKey.webDavPassword to "new-password",
                PreferKey.syncBookProgress to false,
                PreferKey.syncBookProgressPlus to false,
                PreferKey.backupPath to null,
            ),
            diff,
        )
    }
}

private fun Map<String, Any?>.toPreferences(): Preferences {
    val preferences = mutablePreferencesOf()
    forEach { (key, value) -> preferences.setPrefValue(key, value) }
    return preferences
}
