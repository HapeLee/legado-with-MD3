package io.legado.app.domain.gateway

interface DatabaseMaintenanceGateway {
    suspend fun shrink()
}
