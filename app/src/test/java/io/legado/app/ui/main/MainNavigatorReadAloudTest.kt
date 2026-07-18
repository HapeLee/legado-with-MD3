package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorReadAloudTest {

    @Test
    fun `opens read aloud player on top of reader`() {
        val reader = MainRouteReadBook(bookUrl = "book")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        MainNavigator.navigateToRoute(backStack, MainRouteReadAloudPlayer)

        assertEquals(listOf(MainRouteHome, reader, MainRouteReadAloudPlayer), backStack)
    }

    @Test
    fun `opens cloud TTS manager on top of player`() {
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            MainRouteReadBook(bookUrl = "book"),
            MainRouteReadAloudPlayer,
        )

        MainNavigator.navigateToRoute(backStack, MainRouteCloudTtsEngines)

        assertEquals(MainRouteCloudTtsEngines, backStack.last())
    }
}
