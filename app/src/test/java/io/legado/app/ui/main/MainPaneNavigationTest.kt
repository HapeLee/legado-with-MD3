package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPaneNavigationTest {
    @Test
    fun changingHomeTabClearsPreviousRightPane() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        assertTrue(selectMainPaneChild(stack, MainRouteHome, MainRouteHome))
        assertEquals(listOf(MainRouteHome), stack)
    }

    @Test
    fun wideNavigationReplacesChildAndSynchronizesTracker() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        val tracker = MainNavRouteTracker()
        val navigation = MainEntryNavigation(MainRouteHome, stack, true, tracker) {
            error("Ordinary pane selection must not use legacy navigation")
        }
        navigation.onNavigateToRoute(MainRouteReplaceRules())
        assertEquals(listOf(MainRouteHome, MainRouteReplaceRules()), stack)
        assertEquals(stack, tracker.backStack.value)
    }

    @Test
    fun compactNavigationKeepsLegacyNavigateAndPushSemantics() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        val tracker = MainNavRouteTracker()
        var requested: NavKey? = null
        val navigation = MainEntryNavigation(MainRouteSettings, stack, false, tracker) {
            requested = it
        }
        navigation.onNavigateToRoute(MainRouteAbout)
        assertEquals(MainRouteAbout, requested)
        assertEquals(listOf(MainRouteHome, MainRouteSettings), stack)
        navigation.pushRoute(MainRouteSettingsRead)
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsRead), stack)
        assertEquals(stack, tracker.backStack.value)
    }

    @Test
    fun retiredEntryCallbackDoesNotNavigate() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsRead)
        val navigation = MainEntryNavigation(MainRouteHome, stack, true, MainNavRouteTracker()) {
            error("Retired entry must not navigate")
        }
        navigation.onNavigateToRoute(MainRouteAbout)
        navigation.pushRoute(MainRouteSettingsTheme)
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsRead), stack)
    }

    @Test
    fun fullScreenReaderIsOpenedFromActualLeftPane() {
        val parent = MainRouteBookInfo(name = null, author = null, bookUrl = "book")
        val stack = mutableListOf<NavKey>(MainRouteHome, parent, MainRouteBookCharacterList("book"))
        val reader = MainRouteReadBook("book")
        val navigation = MainEntryNavigation(parent, stack, true, MainNavRouteTracker()) {
            assertEquals(listOf(MainRouteHome, parent), stack)
            stack.add(it)
        }
        navigation.onNavigateToRoute(reader)
        assertEquals(listOf(MainRouteHome, parent, reader), stack)
    }

    @Test
    fun readerAndPlayerActionsKeepExistingHostNavigation() {
        val reader = MainRouteReadBook("book")
        val stack = mutableListOf<NavKey>(MainRouteHome, reader)
        val navigation = MainEntryNavigation(reader, stack, true, MainNavRouteTracker()) {
            stack.add(it)
        }
        navigation.onNavigateToRoute(MainRouteToc("book"))
        assertEquals(listOf(MainRouteHome, reader, MainRouteToc("book")), stack)
        stack.clear()
        stack.addAll(listOf(MainRouteHome, MainRouteSettings))
        val playerNavigation =
            MainEntryNavigation(MainRouteHome, stack, true, MainNavRouteTracker()) {
                assertTrue(it is MainRouteAudioPlay)
            }
        playerNavigation.onNavigateToRoute(MainRouteAudioPlay("book"))
        assertEquals(listOf(MainRouteHome, MainRouteSettings), stack)
    }

    @Test
    fun underlyingPageCannotNavigateThroughOverlay() {
        val login = MainRouteSourceLogin(io.legado.app.ui.login.SourceLoginType.BookSource)
        val stack = mutableListOf<NavKey>(MainRouteHome, login)
        val navigation = MainEntryNavigation(MainRouteHome, stack, true, MainNavRouteTracker()) {
            error("Covered page must not navigate")
        }
        navigation.onNavigateToRoute(MainRouteSettings)
        assertEquals(listOf(MainRouteHome, login), stack)
    }

    @Test
    fun providerMakesOrdinaryDestinationsNestedPanesButKeepsReaderStandalone() {
        val stack = mutableListOf<NavKey>(MainRouteHome)
        val provider = mainPaneEntryProvider(stack, true, MainNavRouteTracker(), {}) {
            entry<MainRouteHome> {}
            entry<MainRouteSettings> {}
            entry<MainRouteSettingsTheme> {}
            entry<MainRouteSettingsCustomTheme> {}
            entry<MainRouteReadBook> {}
        }
        val settings = provider(MainRouteSettings)
        val theme = provider(MainRouteSettingsTheme)
        val custom = provider(MainRouteSettingsCustomTheme)
        assertTrue(listDetailEntries(listOf(provider(MainRouteHome), settings)) != null)
        assertTrue(listDetailEntries(listOf(settings, theme)) != null)
        assertTrue(listDetailEntries(listOf(theme, custom)) != null)
        assertEquals(null, listDetailEntries(listOf(settings, provider(MainRouteReadBook("book")))))
    }

    @Test
    fun homeSelectionReplacesRightPane() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        assertTrue(selectMainPaneChild(stack, MainRouteHome, MainRouteReplaceRules()))
        assertEquals(listOf(MainRouteHome, MainRouteReplaceRules()), stack)
    }

    @Test
    fun drillDownPromotesParentAndBackRestoresPreviousPair() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        assertTrue(selectMainPaneChild(stack, MainRouteSettings, MainRouteSettingsRead))
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsRead), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf(MainRouteHome, MainRouteSettings), stack)
    }

    @Test
    fun settingsSelectionReplacesSiblingWithoutRemovingHome() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsRead)
        assertTrue(selectMainPaneChild(stack, MainRouteSettings, MainRouteSettingsTheme))
        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsTheme), stack)
    }

    @Test
    fun sourceAndKnowledgePagesUseTheSameRule() {
        val source = MainRouteBookSourceManage()
        val stack = mutableListOf<NavKey>(MainRouteHome, source, MainRouteBookSourceEdit("a"))
        assertTrue(selectMainPaneChild(stack, source, MainRouteBookSourceEdit("b")))
        assertEquals(listOf(MainRouteHome, source, MainRouteBookSourceEdit("b")), stack)
        val details = MainRouteBookInfo(name = null, author = null, bookUrl = "book")
        val knowledge = MainRouteBookKnowledgeList("book")
        stack.clear()
        stack.addAll(listOf(MainRouteHome, details, knowledge))
        assertTrue(selectMainPaneChild(stack, details, MainRouteBookCharacterList("book")))
        assertEquals(listOf(MainRouteHome, details, MainRouteBookCharacterList("book")), stack)
    }

    @Test
    fun selectingCurrentChildPreservesItsInstance() {
        val selected = MainRouteBookSourceEdit("a")
        val parent = MainRouteBookSourceManage()
        val stack = mutableListOf<NavKey>(parent, selected)
        assertTrue(selectMainPaneChild(stack, parent, MainRouteBookSourceEdit("a")))
        assertTrue(stack.last() === selected)
    }

    @Test
    fun staleSourceAndReadersDoNotChangeThePath() {
        val stack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsRead)
        val initial = stack.toList()
        assertFalse(selectMainPaneChild(stack, MainRouteHome, MainRouteAbout))
        assertFalse(selectMainPaneChild(stack, MainRouteSettingsRead, MainRouteReadBook("book")))
        assertFalse(selectMainPaneChild(stack, MainRouteSettingsRead, MainRouteReadManga("book")))
        assertEquals(initial, stack)
        val reader = MainRouteReadBook("book")
        stack.clear()
        stack.add(reader)
        assertFalse(selectMainPaneChild(stack, reader, MainRouteToc("book")))
        assertEquals(listOf(reader), stack)
    }

    @Test
    fun overlaysCannotBeReplacedByUnderlyingPane() {
        val login = MainRouteSourceLogin(io.legado.app.ui.login.SourceLoginType.BookSource)
        val stack = mutableListOf<NavKey>(MainRouteHome, login)
        assertFalse(selectMainPaneChild(stack, MainRouteHome, MainRouteSettings))
        assertEquals(listOf(MainRouteHome, login), stack)
    }
}
