package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.Flow

data class CheckSourceSettings(
    val timeoutMillis: Long = 180_000L,
    val checkSearch: Boolean = true,
    val checkDiscovery: Boolean = true,
    val checkInfo: Boolean = true,
    val checkCategory: Boolean = true,
    val checkContent: Boolean = true,
)

interface CheckSourceSettingsGateway {
    val currentSettings: CheckSourceSettings
    val settings: Flow<CheckSourceSettings>

    suspend fun update(settings: CheckSourceSettings)
}

data class DirectLinkRule(
    val uploadUrl: String,
    val downloadUrlRule: String,
    val summary: String,
    val compress: Boolean = false,
)

interface DirectLinkSettingsGateway {
    suspend fun loadRule(): DirectLinkRule
    suspend fun loadDefaultRules(): List<DirectLinkRule>
    suspend fun saveRule(rule: DirectLinkRule)
    suspend fun testRule(rule: DirectLinkRule): String
}

interface LocalPasswordGateway {
    suspend fun setPassword(password: String?)
}

interface OtherConfigSystemGateway {
    fun isProcessTextEnabled(): Boolean
    suspend fun setProcessTextEnabled(enabled: Boolean)
    suspend fun clearWebViewData()
}
