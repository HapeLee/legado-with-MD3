package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.AppShellSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellSettingsMappingTest {

    @Test
    fun `AppShell 23 键写读映射逐字段对应`() {
        appShellMappingSamples().forEach { expected ->
            assertEquals(expected.expectedPrefMap(), expected.toPrefMap())
            assertEquals(
                expected,
                expected.expectedPrefMap().toTestPreferences().toAppShellSettings(),
            )
        }
    }

    @Test
    fun `导航可见性与默认首页通过真实原子路径单批写入`() {
        val values = captureAtomicUpdateValues(
            current = AppShellSettings(showHome = true, defaultHomePage = "home"),
            read = { it.toAppShellSettings() },
            toPrefMap = AppShellSettings::toPrefMap,
            transform = {
                it.copy(
                    showHome = false,
                    defaultHomePage = "bookshelf",
                )
            },
        )

        assertEquals(
            mapOf(
                PreferKey.showHome to false,
                PreferKey.defaultHomePage to "bookshelf",
            ),
            values,
        )
    }
}

private fun appShellMappingSamples(): List<AppShellSettings> {
    val base = AppShellSettings(
        themeMode = "theme-mode",
        fontScale = 13,
        composeEngine = "compose-engine",
        tabletInterface = "tablet-interface",
        labelVisibilityMode = "label-mode",
        defaultHomePage = "default-page",
        mainNavigationOrder = "navigation-order",
        navIconHome = "icon-home",
        navIconBookshelf = "icon-bookshelf",
        navIconExplore = "icon-explore",
        navIconRss = "icon-rss",
        navIconMy = "icon-my",
        launcherIcon = "launcher-icon",
    )
    return listOf(
        base,
        base.copy(showHome = false),
        base.copy(showDiscovery = false),
        base.copy(showRss = false),
        base.copy(showStatusBar = false),
        base.copy(swipeAnimation = false),
        base.copy(predictiveBackEnabled = false),
        base.copy(showBottomView = false),
        base.copy(useFloatingBottomBar = true),
        base.copy(useFloatingBottomBarLiquidGlass = true),
        base.copy(navExtended = true),
    )
}

private fun AppShellSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.themeMode to themeMode,
    PreferKey.fontScale to fontScale,
    PreferKey.composeEngine to composeEngine,
    PreferKey.showHome to showHome,
    PreferKey.showDiscovery to showDiscovery,
    PreferKey.showRss to showRss,
    PreferKey.showStatusBar to showStatusBar,
    PreferKey.swipeAnimation to swipeAnimation,
    PreferKey.isPredictiveBackEnabled to predictiveBackEnabled,
    PreferKey.showBottomView to showBottomView,
    PreferKey.useFloatingBottomBar to useFloatingBottomBar,
    PreferKey.useFloatingBottomBarLiquidGlass to useFloatingBottomBarLiquidGlass,
    PreferKey.tabletInterface to tabletInterface,
    PreferKey.labelVisibilityMode to labelVisibilityMode,
    PreferKey.defaultHomePage to defaultHomePage,
    PreferKey.mainNavigationOrder to mainNavigationOrder,
    PreferKey.navExtended to navExtended,
    PreferKey.navIconHome to navIconHome,
    PreferKey.navIconBookshelf to navIconBookshelf,
    PreferKey.navIconExplore to navIconExplore,
    PreferKey.navIconRss to navIconRss,
    PreferKey.navIconMy to navIconMy,
    PreferKey.launcherIcon to launcherIcon,
)
