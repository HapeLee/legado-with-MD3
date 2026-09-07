package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.DatabaseMaintenanceGateway

class ShrinkDatabaseUseCase(
    private val databaseMaintenanceGateway: DatabaseMaintenanceGateway
) {
    fun execute() {
        databaseMaintenanceGateway.shrink()
    }
}
