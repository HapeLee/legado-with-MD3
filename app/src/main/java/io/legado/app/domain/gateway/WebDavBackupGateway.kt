package io.legado.app.domain.gateway

import io.legado.app.domain.model.WebDavBackup

interface WebDavBackupGateway {
    val isJianGuoYun: Boolean

    suspend fun syncConfig()
    suspend fun test(): Boolean
    suspend fun getBackupNames(): List<String>
    suspend fun getLatestBackup(): WebDavBackup?
    suspend fun restore(name: String)
}
