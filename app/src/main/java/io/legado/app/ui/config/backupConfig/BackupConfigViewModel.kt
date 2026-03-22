package io.legado.app.ui.config.backupConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.AppWebDav
import io.legado.app.help.storage.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BackupConfigViewModel : ViewModel() {

    fun setWebDavAccount(account: String, password: String) {
        BackupConfig.webDavAccount = account
        BackupConfig.webDavPassword = password
    }

    fun getWebDavAccount(): String {
        return BackupConfig.webDavAccount
    }

    fun getWebDavPassword(): String {
        return BackupConfig.webDavPassword
    }

    suspend fun testWebDav(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                AppWebDav.testWebDav()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun backup(backupPath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Backup.backupLocked(appCtx, backupPath)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "备份出错")
                }
            }
        }
    }

    suspend fun getBackupNames(): List<String> {
        return withContext(Dispatchers.IO) {
            AppWebDav.getBackupNames()
        }
    }

    fun restoreWebDav(name: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppWebDav.restoreWebDav(name)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "恢复出错")
                }
            }
        }
    }

}
