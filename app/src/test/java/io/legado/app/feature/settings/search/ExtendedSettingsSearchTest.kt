package io.legado.app.feature.settings.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.NavigationAnimationStyle
import io.legado.app.domain.model.settings.TextAnimationLevel
import io.legado.app.feature.settings.reading.ReadSetting
import io.legado.app.ui.config.labConfig.LabConfigScreen
import io.legado.app.ui.config.labConfig.LabConfigUiState
import io.legado.app.ui.config.otherConfig.OtherConfigIntent
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.otherConfig.OtherConfigUiState
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigIntent
import io.legado.app.ui.config.themeConfig.ThemeConfigUiState
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@Suppress("DEPRECATION") // StateRestorationTester uses the JUnit4 ComposeContentTestRule.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExtendedSettingsSearchTest {
    @get:Rule val compose = createComposeRule()

    private fun checkCatalog() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val vm = SettingsSearchViewModel(SavedStateHandle())
        compose.setContent { AppTheme(AppUiConfiguration()) { SettingsSearchRouteScreen({}, {}, vm) } }
        val fixed = SettingsSearchCatalog.categories + SettingsSearchCatalog.items
        compose.runOnIdle {
            assertEquals(fixed.size, fixed.map { it.id }.toSet().size)
            assertEquals(SettingsSearchPage.entries.toSet(), SettingsSearchCatalog.categories.map { it.page }.toSet())
            for (item in fixed) {
                vm.onIntent(SettingsSearchIntent.QueryChanged(app.getString(item.titleRes)))
                assertTrue("Missing result: ${item.id}", vm.uiState.value.results.any { it.id == item.id })
                val visited = mutableSetOf<String>()
                var parent: SettingsSearchItem? = item
                while (parent != null) {
                    assertTrue("Cyclic prerequisite: ${item.id}", visited.add(parent.id))
                    parent = parent.parentId?.let { assertNotNull(SettingsSearchCatalog.find(it)); SettingsSearchCatalog.find(it) }
                }
            }
            for (item in ReadSetting.entries) {
                vm.onIntent(SettingsSearchIntent.QueryChanged(app.getString(item.titleRes)))
                assertTrue(vm.uiState.value.results.any { it.id == item.id })
            }
            assertNull(SettingsSearchCatalog.find("ai.ai_new_skill"))
        }
    }

    @Test fun `Chinese search covers every category and fixed item`() = checkCatalog()
    @Test @Config(qualifiers = "en-w400dp-h800dp")
    fun `English search covers every category and fixed item`() = checkCatalog()

    @Test
    fun `text animation level can be selected and previewed in Material`() =
        selectTextAnimation("material")

    @Test
    fun `text animation level can be selected and previewed in Mochi`() =
        selectTextAnimation("mochi")

    private fun selectTextAnimation(engine: String) {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val target = requireNotNull(SettingsSearchCatalog.find("theme.text_animation_level"))
        var shell by mutableStateOf(AppShellSettings(composeEngine = engine))
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell)) {
                SettingsSearchTargetScope(target) {
                    ThemeConfigScreen(
                        ThemeConfigUiState(appShell = shell),
                        { intent ->
                            if (intent is ThemeConfigIntent.SetTextAnimationLevel) {
                                shell = shell.copy(textAnimationLevel = intent.level)
                            }
                        },
                        {}, {}, {},
                    )
                }
            }
        }
        val row = hasAnyAncestor(hasTestTag("setting:theme.text_animation_level"))
        compose.onNode(hasText(app.getString(R.string.text_animation_level)) and hasClickAction() and row)
            .performTouchInput { click() }
        compose.onNodeWithText(app.getString(R.string.text_animation_strong)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(TextAnimationLevel.Strong, shell.textAnimationLevel)
            assertEquals(engine, shell.composeEngine)
        }
        compose.onNodeWithText(app.getString(R.string.text_animation_preview))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText(app.getString(R.string.text_animation_example_finished)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.text_animation_example_reading)).assertDoesNotExist()
    }

    @Test
    fun `navigation animation dropdown selects Miuix in the Material theme`() =
        selectNavigationAnimation("material")

    @Test
    fun `navigation animation dropdown opens and selects in the Mochi theme`() =
        selectNavigationAnimation("mochi")

    private fun selectNavigationAnimation(engine: String) {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val target = requireNotNull(SettingsSearchCatalog.find("theme.navigation_animation"))
        var shell by mutableStateOf(AppShellSettings(composeEngine = engine))
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = shell)) {
                SettingsSearchTargetScope(target) {
                    ThemeConfigScreen(
                        ThemeConfigUiState(appShell = shell),
                        { intent ->
                            if (intent is ThemeConfigIntent.SetNavigationAnimationStyle) {
                                shell = shell.copy(navigationAnimationStyle = intent.style)
                            }
                        },
                        {}, {}, {},
                    )
                }
            }
        }
        val row = hasAnyAncestor(hasTestTag("setting:theme.navigation_animation"))
        compose.onNode(hasText(app.getString(R.string.navigation_animation)) and hasClickAction() and row)
            .performTouchInput { click() }
        val themeStyleRow = hasAnyAncestor(hasTestTag("setting:theme.compose_engine"))
        compose.onNodeWithText("AOSP").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.navigation_animation_scale)).assertIsDisplayed()
        compose.onNode(hasText("Miuix") and hasClickAction() and !themeStyleRow)
            .assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(NavigationAnimationStyle.Miuix, shell.navigationAnimationStyle)
            assertEquals(engine, shell.composeEngine)
        }
    }

    private fun assertThemeUnchanged(events: List<ThemeConfigIntent>, shell: AppShellSettings = AppShellSettings()) {
        for (event in events) when (event) {
            ThemeConfigIntent.DismissSheet -> Unit
            // The existing navigation sheet canonicalizes its order even when hidden.
            is ThemeConfigIntent.SetMainNavigationOrder -> assertEquals(shell.mainNavigationOrder, event.routes)
            else -> fail("Unexpected setting mutation: $event")
        }
    }

    private fun searchAndReturn(engine: String) {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val vm = SettingsSearchViewModel(SavedStateHandle())
        val shell = AppShellSettings(composeEngine = engine)
        val themeEvents = mutableListOf<ThemeConfigIntent>()
        val otherEvents = mutableListOf<OtherConfigIntent>()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            AppTheme(AppUiConfiguration(appShell = shell)) {
                var target by rememberSaveable { mutableStateOf<String?>(null) }
                val holder = rememberSaveableStateHolder()
                holder.SaveableStateProvider(target ?: "search") {
                    val item = target?.let(SettingsSearchCatalog::find)
                    if (item == null) SettingsSearchRouteScreen({}, { target = it }, vm)
                    else SettingsSearchTargetScope(item) {
                        when (item.page) {
                            SettingsSearchPage.Theme -> ThemeConfigScreen(
                                ThemeConfigUiState(appShell = shell), themeEvents::add, { target = null }, {}, {},
                            )
                            SettingsSearchPage.Other -> OtherConfigScreen(
                                OtherConfigUiState(), otherEvents::add, { target = null },
                            )
                            else -> error("Unexpected destination: ${item.page}")
                        }
                    }
                }
            }
        }
        for ((res, id) in listOf(
            R.string.font_setting to "theme.font_setting",
            R.string.language to "other.language",
            R.string.clear_webview_data to "other.clear_webview_data",
        )) {
            val title = app.getString(res)
            compose.onNode(hasSetTextAction()).performTextReplacement(title)
            compose.onNode(hasText(title) and hasClickAction() and
                hasAnyAncestor(hasTestTag("settings-search-result:$id"))).performClick()
            compose.onNodeWithTag("setting:$id").assertIsDisplayed()
            assertThemeUnchanged(themeEvents, shell)
            assertTrue(otherEvents.isEmpty())
            restore.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("setting:$id").assertIsDisplayed()
            compose.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
            compose.onNode(hasSetTextAction()).assertTextContains(title)
        }
    }

    @Test fun `appearance font and advanced language locate and return in Material`() = searchAndReturn("material")
    @Test fun `appearance font and advanced language locate and return in Miuix`() = searchAndReturn("miuix")
    @Test @Config(qualifiers = "zh-rCN-w1000dp-h700dp")
    fun `appearance and advanced remain reachable in an expanded window`() = searchAndReturn("material")

    @Test fun `hidden appearance divider option locates its visible prerequisite without a mutation`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val item = SettingsSearchCatalog.find("theme.theme_config_divider_width")!!
        val themeEvents = mutableListOf<ThemeConfigIntent>()
        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                SettingsSearchTargetScope(item) {
                    ThemeConfigScreen(ThemeConfigUiState(), themeEvents::add, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("setting:theme.show_divider_line").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.settings_search_prerequisite,
            app.getString(item.titleRes), app.getString(R.string.show_divider_line))).assertIsDisplayed()
        assertThemeUnchanged(themeEvents)
    }

    @Test fun `disabled laboratory option guides to the master switch`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val item = SettingsSearchCatalog.find("lab.lab_eink_display_title")!!
        val mutations = mutableListOf<Any>()
        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                SettingsSearchTargetScope(item) {
                    LabConfigScreen(LabConfigUiState(), { mutations += it }, {})
                }
            }
        }
        compose.onNodeWithTag("setting:lab.lab_enabled_title").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.settings_search_prerequisite,
            app.getString(item.titleRes), app.getString(R.string.lab_enabled_title))).assertIsDisplayed()
        assertTrue("Unexpected setting write: $mutations", mutations.isEmpty())
    }
}
