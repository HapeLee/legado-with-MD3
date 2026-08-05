package io.legado.app.ui.config.backupConfig

import io.legado.app.domain.gateway.BackupSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 BackupSettingsGateway.currentSettings")
object BackupConfig {
    private val settings get() = GlobalContext.get().get<BackupSettingsGateway>().currentSettings
    val webDavUrl get() = settings.webDavUrl
    val webDavAccount get() = settings.webDavAccount
    val webDavPassword get() = settings.webDavPassword
    val webDavDir get() = settings.webDavDir
    val webDavDeviceName get() = settings.webDavDeviceName
    val syncBookProgress get() = settings.syncBookProgress
    val syncBookProgressPlus get() = settings.syncBookProgressPlus
    val autoCheckNewBackup get() = settings.autoCheckNewBackup
    val onlyLatestBackup get() = settings.onlyLatestBackup
    val backupSyncMode get() = settings.backupSyncMode
    val backupPath get() = settings.backupPath
}
