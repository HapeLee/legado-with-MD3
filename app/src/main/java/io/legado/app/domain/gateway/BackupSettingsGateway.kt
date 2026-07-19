package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BackupSettings
import kotlinx.coroutines.flow.Flow

interface BackupSettingsGateway {
    val currentSettings: BackupSettings
    val settings: Flow<BackupSettings>
    suspend fun update(update: BackupSettingsUpdate)
    suspend fun updateAll(updates: List<BackupSettingsUpdate>)
}

sealed interface BackupSettingsUpdate {
    data class WebDavUrl(val value: String) : BackupSettingsUpdate
    data class WebDavCredentials(val account: String, val password: String) : BackupSettingsUpdate
    data class WebDavDir(val value: String) : BackupSettingsUpdate
    data class WebDavDeviceName(val value: String) : BackupSettingsUpdate
    data class SyncBookProgress(val value: Boolean) : BackupSettingsUpdate
    data class SyncBookProgressPlus(val value: Boolean) : BackupSettingsUpdate
    data class AutoCheckNewBackup(val value: Boolean) : BackupSettingsUpdate
    data class OnlyLatestBackup(val value: Boolean) : BackupSettingsUpdate
    data class BackupSyncMode(val value: String) : BackupSettingsUpdate
    data class BackupPath(val value: String?) : BackupSettingsUpdate
}
