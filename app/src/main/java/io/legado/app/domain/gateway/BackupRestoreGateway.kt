package io.legado.app.domain.gateway

interface BackupRestoreGateway {
    suspend fun backup(path: String?, mode: String)
    suspend fun restoreLocal(uri: String)
}
