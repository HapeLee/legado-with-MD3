package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.DatabaseMaintenanceGateway

class ShrinkDatabaseUseCase(
    private val databaseMaintenanceGateway: DatabaseMaintenanceGateway
) {
    suspend fun execute() {
        databaseMaintenanceGateway.shrink()
    }
}
