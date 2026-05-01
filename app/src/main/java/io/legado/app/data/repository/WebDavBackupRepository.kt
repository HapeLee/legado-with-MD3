package io.legado.app.data.repository

import io.legado.app.domain.gateway.WebDavBackupGateway
import io.legado.app.domain.model.WebDavBackup
import io.legado.app.help.AppWebDav

class WebDavBackupRepository : WebDavBackupGateway {

    override val isJianGuoYun: Boolean
        get() = AppWebDav.isJianGuoYun

    override suspend fun syncConfig() {
        AppWebDav.upConfig()
    }

    override suspend fun test(): Boolean {
        return AppWebDav.testWebDav()
    }

    override suspend fun getBackupNames(): List<String> {
        return AppWebDav.getBackupNames()
    }

    override suspend fun getLatestBackup(): WebDavBackup? {
        return AppWebDav.lastBackUp().getOrNull()?.let {
            WebDavBackup(
                name = it.displayName,
                lastModify = it.lastModify
            )
        }
    }

    override suspend fun restore(name: String) {
        AppWebDav.restoreWebDav(name)
    }
}
