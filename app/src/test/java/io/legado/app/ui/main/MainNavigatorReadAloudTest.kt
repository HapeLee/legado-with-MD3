package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorReadAloudTest {

    @Test
    fun `opens cloud TTS manager on top of reader`() {
        val reader = MainRouteReadBook(bookUrl = "book")
        val cloudTts = MainRouteCloudTtsEngines(bookUrl = "book")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        MainNavigator.navigateToRoute(backStack, cloudTts)

        assertEquals(listOf(MainRouteHome, reader, cloudTts), backStack)
    }

    @Test
    fun `resets to home before cloud TTS manager from unrelated route`() {
        val cloudTts = MainRouteCloudTtsEngines()
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            MainRouteSettings,
        )

        MainNavigator.navigateToRoute(backStack, cloudTts)

        assertEquals(listOf(MainRouteHome, cloudTts), backStack)
    }
}
