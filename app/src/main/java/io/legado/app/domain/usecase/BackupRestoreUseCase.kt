package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BackupRestoreGateway

class BackupRestoreUseCase(
    private val gateway: BackupRestoreGateway,
) {
    suspend fun backup(path: String?, mode: String) {
        gateway.backup(path, mode)
    }

    suspend fun restoreLocal(uri: String) {
        gateway.restoreLocal(uri)
    }
}
