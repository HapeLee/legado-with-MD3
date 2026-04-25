package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AppStartupGateway

class AppStartupMaintenanceUseCase(
    private val appStartupGateway: AppStartupGateway
) {

    suspend fun deleteNotShelfBooks() {
        appStartupGateway.deleteNotShelfBooks()
    }

    suspend fun ensureDefaultHttpTts() {
        appStartupGateway.ensureDefaultHttpTts()
    }
}
