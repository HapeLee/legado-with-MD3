package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.HomepageSettings
import kotlinx.coroutines.flow.Flow

interface HomepageSettingsGateway {
    val currentSettings: HomepageSettings
    val settings: Flow<HomepageSettings>
    suspend fun setHiddenSourceUrlsJson(value: String)
}
