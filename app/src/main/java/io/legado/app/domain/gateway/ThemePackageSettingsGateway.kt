package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ThemeExportData

interface ThemePackageSettingsGateway {
    fun exportCurrent(): ThemeExportData
    fun apply(data: ThemeExportData)
}
