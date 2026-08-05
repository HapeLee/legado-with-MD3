package io.legado.app.domain.gateway

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
