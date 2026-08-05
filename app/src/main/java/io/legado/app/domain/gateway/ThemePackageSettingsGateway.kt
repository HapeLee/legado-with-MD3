package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ThemeExportData

interface ThemePackageSettingsGateway {
    fun exportCurrent(): ThemeExportData
    suspend fun applyAndAwait(data: ThemeExportData)
}
