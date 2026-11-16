package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CheckSourceSettingsRepository : CheckSourceSettingsGateway {

    override val currentSettings: CheckSourceSettings
        get() = AppConfigStore.preferences.toSettings()

    override val settings = AppConfigStore.preferencesFlow
        .map(Preferences::toSettings)
        .distinctUntilChanged()

    override suspend fun update(settings: CheckSourceSettings) {
        // 归一化而不是抛异常：设置页的滑块可以到边界值，UI 路径上的崩溃不可接受。
        val normalized = settings.normalized()
        AppConfigStore.putAllAndAwait(
            mapOf(
                LocalPreferencesKeys.CHECK_SOURCE_TIMEOUT.name to normalized.timeoutMillis,
                LocalPreferencesKeys.CHECK_SOURCE_THREAD_COUNT.name to normalized.threadCount,
                LocalPreferencesKeys.CHECK_SOURCE_WRITE_COMMENT.name to
                    normalized.writeSourceComment,
                LocalPreferencesKeys.CHECK_SOURCE_QUICK_MODE.name to normalized.quickMode,
                LocalPreferencesKeys.CHECK_SOURCE_DOMAIN.name to normalized.checkDomain,
                LocalPreferencesKeys.CHECK_SOURCE_SEARCH.name to normalized.checkSearch,
                LocalPreferencesKeys.CHECK_SOURCE_DISCOVERY.name to normalized.checkDiscovery,
                LocalPreferencesKeys.CHECK_SOURCE_INFO.name to normalized.checkInfo,
                LocalPreferencesKeys.CHECK_SOURCE_CATEGORY.name to normalized.checkCategory,
                LocalPreferencesKeys.CHECK_SOURCE_CONTENT.name to normalized.checkContent,
            )
        )
    }
}

private fun Preferences.toSettings() = CheckSourceSettings(
    timeoutMillis = compatDsValue(
        LocalPreferencesKeys.CHECK_SOURCE_TIMEOUT,
        CheckSourceSettings.DEFAULT_TIMEOUT_MILLIS,
    ),
    threadCount = compatDsValue(
        LocalPreferencesKeys.CHECK_SOURCE_THREAD_COUNT,
        CheckSourceSettings.DEFAULT_THREAD_COUNT,
    ),
    writeSourceComment = compatDsValue(
        LocalPreferencesKeys.CHECK_SOURCE_WRITE_COMMENT,
        true,
    ),
    quickMode = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_QUICK_MODE, false),
    checkDomain = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_DOMAIN, false),
    checkSearch = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_SEARCH, true),
    checkDiscovery = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_DISCOVERY, true),
    checkInfo = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_INFO, true),
    checkCategory = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_CATEGORY, true),
    checkContent = compatDsValue(LocalPreferencesKeys.CHECK_SOURCE_CONTENT, true),
).normalized()
