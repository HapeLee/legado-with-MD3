package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.feature.settings.reading.ReadSetting
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SettingsSearchNavigationTest {
    @Test fun `appearance advanced and privacy results retain search and serialize independently`() {
        for (id in listOf("theme.font_setting", "other.language", "private.set_local_password")) {
            val target: MainRoute = MainRouteSettingsTarget(id)
            assertEquals(target, Json.decodeFromString<MainRoute>(Json.encodeToString(target)))
            val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch)
            MainNavigator.navigateToRoute(stack, target)
            assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch, target), stack)
        }
    }

    @Test fun `pane selection can switch between appearance and advanced results`() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch)
        val tracker = MainNavRouteTracker()
        val source = MainEntryNavigation(MainRouteSettingsSearch, stack, true, tracker) {
            error("Search result must preserve its source")
        }
        source.pushRoute(MainRouteSettingsTarget("theme.font_setting"))
        source.pushRoute(MainRouteSettingsTarget("other.language"))
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch,
            MainRouteSettingsTarget("other.language")), stack)
    }

    @Test fun `result navigation keeps settings and search as parents`() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch)
        val target = MainRouteSettingsReadTarget(ReadSetting.VolumeKeys.id)
        MainNavigator.navigateToRoute(stack, target)
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch, target), stack)
        MainNavigator.navigateToRoute(stack, target)
        assertEquals(4, stack.size)
    }

    @Test fun `pane result selection replaces detail without losing search source`() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch)
        val tracker = MainNavRouteTracker()
        val source = MainEntryNavigation(MainRouteSettingsSearch, stack, true, tracker) {
            error("Pane selection should not rebuild the root stack")
        }
        val first = MainRouteSettingsReadTarget(ReadSetting.VolumeKeys.id)
        val second = MainRouteSettingsReadTarget(ReadSetting.PageKeys.id)
        source.pushRoute(first)
        source.pushRoute(second)
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsSearch, second), stack)
        assertEquals(second, tracker.currentRoute)
    }

    @Test fun `new target route survives serialization and legacy route remains an object`() {
        val target: MainRoute = MainRouteSettingsReadTarget(ReadSetting.MenuIcons.id)
        assertEquals(target, Json.decodeFromString<MainRoute>(Json.encodeToString(target)))
        val legacy: MainRoute = MainRouteSettingsRead
        assertEquals(legacy, Json.decodeFromString<MainRoute>(Json.encodeToString(legacy)))
    }
}
