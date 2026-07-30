package io.legado.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.help.CacheManager
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.webView.WebViewDataCleaner
import io.legado.app.receiver.SharedReceiverActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class CheckSourceSettingsRepository(
    private val context: Context,
) : CheckSourceSettingsGateway {

    private val state = MutableStateFlow(loadSettings())

    override val currentSettings: CheckSourceSettings
        get() = state.value

    override val settings = state.asStateFlow()

    override suspend fun update(settings: CheckSourceSettings) {
        require(settings.timeoutMillis > 0L)
        require(settings.checkSearch || settings.checkDiscovery)

        val previous = state.value
        try {
            withContext(Dispatchers.IO) { persistCache(settings) }
            AppConfigStore.putAllAndAwait(
                mapOf(PreferKey.checkSource to buildSummary(context, settings))
            )
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { persistCache(previous) }
            throw error
        }
        state.value = settings
    }

    private fun persistCache(settings: CheckSourceSettings) {
        CacheManager.put(CHECK_SOURCE_TIMEOUT, settings.timeoutMillis)
        CacheManager.put(CHECK_SEARCH, settings.checkSearch)
        CacheManager.put(CHECK_DISCOVERY, settings.checkDiscovery)
        CacheManager.put(CHECK_INFO, settings.checkInfo)
        CacheManager.put(CHECK_CATEGORY, settings.checkCategory)
        CacheManager.put(CHECK_CONTENT, settings.checkContent)
    }

    private fun loadSettings() = CheckSourceSettings(
        timeoutMillis = CacheManager.getLong(CHECK_SOURCE_TIMEOUT) ?: 180_000L,
        checkSearch = CacheManager.get(CHECK_SEARCH)?.toBoolean() ?: true,
        checkDiscovery = CacheManager.get(CHECK_DISCOVERY)?.toBoolean() ?: true,
        checkInfo = CacheManager.get(CHECK_INFO)?.toBoolean() ?: true,
        checkCategory = CacheManager.get(CHECK_CATEGORY)?.toBoolean() ?: true,
        checkContent = CacheManager.get(CHECK_CONTENT)?.toBoolean() ?: true,
    )

    private fun buildSummary(context: Context, settings: CheckSourceSettings): String {
        val checkedItems = buildList {
            if (settings.checkSearch) add(context.getString(R.string.search))
            if (settings.checkDiscovery) add(context.getString(R.string.discovery))
            if (settings.checkInfo) add(context.getString(R.string.source_tab_info))
            if (settings.checkCategory) add(context.getString(R.string.chapter_list))
            if (settings.checkContent) add(context.getString(R.string.main_body))
        }.joinToString(separator = " ")
        return context.getString(
            R.string.check_source_config_summary,
            (settings.timeoutMillis / 1_000L).toString(),
            checkedItems,
        )
    }

    private companion object {
        const val CHECK_SOURCE_TIMEOUT = "checkSourceTimeout"
        const val CHECK_SEARCH = "checkSearch"
        const val CHECK_DISCOVERY = "checkDiscovery"
        const val CHECK_INFO = "checkInfo"
        const val CHECK_CATEGORY = "checkCategory"
        const val CHECK_CONTENT = "checkContent"
    }
}

class DirectLinkSettingsRepository : DirectLinkSettingsGateway {

    override suspend fun loadRule(): DirectLinkRule = withContext(Dispatchers.IO) {
        DirectLinkUpload.getRule().toDomain()
    }

    override suspend fun loadDefaultRules(): List<DirectLinkRule> =
        withContext(Dispatchers.IO) { DirectLinkUpload.defaultRules.map { it.toDomain() } }

    override suspend fun saveRule(rule: DirectLinkRule) = withContext(Dispatchers.IO) {
        DirectLinkUpload.putConfig(rule.toLegacy())
    }

    override suspend fun testRule(rule: DirectLinkRule): String =
        DirectLinkUpload.upLoad(
            fileName = "test.json",
            file = "{}",
            contentType = "application/json",
            rule = rule.toLegacy(),
        )

    private fun DirectLinkUpload.Rule.toDomain() = DirectLinkRule(
        uploadUrl = uploadUrl,
        downloadUrlRule = downloadUrlRule,
        summary = summary,
        compress = compress,
    )

    private fun DirectLinkRule.toLegacy() = DirectLinkUpload.Rule(
        uploadUrl = uploadUrl,
        downloadUrlRule = downloadUrlRule,
        summary = summary,
        compress = compress,
    )
}

class LocalPasswordRepository : LocalPasswordGateway {

    override suspend fun setPassword(password: String?) {
        AppConfigStore.putAllAndAwait(
            mapOf(LocalPreferencesKeys.PASSWORD.name to password.orEmpty())
        )
    }
}

class OtherConfigSystemRepository(
    private val context: Context,
) : OtherConfigSystemGateway {

    private val componentName = ComponentName(
        context,
        SharedReceiverActivity::class.java.name,
    )

    override fun isProcessTextEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(componentName) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    override suspend fun setProcessTextEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.packageManager.setComponentEnabledSetting(
            componentName,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    override suspend fun clearWebViewData() {
        WebViewDataCleaner.clear(context)
    }
}
