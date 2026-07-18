package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.AppShellSettings
import kotlinx.coroutines.flow.Flow

interface AppShellSettingsGateway {
    val currentSettings: AppShellSettings
    val settings: Flow<AppShellSettings>
    suspend fun update(update: AppShellSettingsUpdate)
    suspend fun updateAll(updates: List<AppShellSettingsUpdate>)
}

sealed interface AppShellSettingsUpdate {
    data class ThemeMode(val value: String) : AppShellSettingsUpdate
    data class Language(val value: String) : AppShellSettingsUpdate
    data class FontScale(val value: Int) : AppShellSettingsUpdate
    data class ComposeEngine(val value: String) : AppShellSettingsUpdate
    data class MainNavigationOrder(val value: String) : AppShellSettingsUpdate
    data class BooleanValue(
        val setting: AppShellBooleanSetting,
        val value: Boolean,
    ) : AppShellSettingsUpdate
    data class StringValue(
        val setting: AppShellStringSetting,
        val value: String,
    ) : AppShellSettingsUpdate
}

enum class AppShellBooleanSetting {
    ShowHome,
    ShowDiscovery,
    ShowRss,
    ShowStatusBar,
    SwipeAnimation,
    PredictiveBack,
    ShowBottomView,
    UseFloatingBottomBar,
    UseFloatingBottomBarLiquidGlass,
    NavExtended,
}

enum class AppShellStringSetting {
    TabletInterface,
    LabelVisibilityMode,
    DefaultHomePage,
    NavIconHome,
    NavIconBookshelf,
    NavIconExplore,
    NavIconRss,
    NavIconMy,
    LauncherIcon,
}
