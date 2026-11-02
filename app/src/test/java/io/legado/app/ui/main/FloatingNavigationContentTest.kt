package io.legado.app.ui.main

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowUiState
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalBottomOverlayPadding
import io.legado.app.ui.theme.LocalLegadoThemeColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingNavigationContentTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var fixture: InMemoryAppDatabaseFixture

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        fixture = InMemoryAppDatabaseFixture(application)
        stopKoin()
        startKoin {
            modules(module {
                single<ExploreRepository> { ExploreRepositoryImpl(fixture.database) }
                single { ExploreKindUiUseCase(fixture.database.bookSourceDao) }
            })
        }
    }

    @After
    fun tearDown() {
        fixture.close()
        stopKoin()
    }

    @Test
    fun discoveryEndActionClearsFloatingFooterWithoutShrinkingViewport() {
        data class Case(val engine: String, val grid: Int, val inset: Int, val consumed: Boolean)
        var case by mutableStateOf(Case("material", 0, 48, false))
        var overlayHeight by mutableStateOf(0.dp)
        var reserveOverlay by mutableStateOf(false)
        var footerBounds = Rect.Zero
        var viewportBounds = Rect.Zero
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalDensity provides Density(1f)) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    CompositionLocalProvider(
                        LocalLegadoThemeColors provides LocalLegadoThemeColors.current.copy(composeEngine = case.engine),
                    ) {
                        key(case) {
                            val insets = WindowInsets(bottom = case.inset)
                            Box(
                                Modifier.requiredSize(400.dp, (200 + if (case.consumed) case.inset else 0).dp)
                                    .then(if (case.consumed) Modifier.windowInsetsPadding(insets) else Modifier)
                            ) {
                                CompositionLocalProvider(
                                    LocalBottomOverlayPadding provides if (reserveOverlay) overlayHeight + 12.dp else 0.dp,
                                ) {
                                    Box(Modifier.fillMaxSize().onGloballyPositioned { viewportBounds = it.boundsInRoot() }) {
                                        ExploreShowScreen(
                                            state = ExploreShowUiState(isEnd = true, layoutState = case.grid),
                                            onIntent = {}, onBack = {}, onBookClick = { _, _ -> },
                                            embeddedInMain = true,
                                        )
                                    }
                                }
                                FloatingNavigationContainer(
                                    Modifier.align(Alignment.BottomCenter), insets,
                                    onHeightChanged = { overlayHeight = it },
                                ) {
                                    Box(Modifier.fillMaxWidth().height(64.dp)
                                        .onGloballyPositioned { footerBounds = it.boundsInRoot() })
                                }
                            }
                        }
                    }
                }
            }
        }
        val action = RuntimeEnvironment.getApplication().getString(R.string.load_next_page)
        // Without the host's measured reserve this is the old 108dp padding: even at
        // maximum scroll the end action is partially covered on a 48dp navigation bar.
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.waitForIdle()
        assertTrue("baseline must reproduce the covered action",
            compose.onNodeWithText(action).fetchSemanticsNode().boundsInRoot.bottom > footerBounds.top)
        compose.runOnIdle { reserveOverlay = true }
        for (engine in listOf("material", "miuix")) {
            for (grid in listOf(0, 1)) {
                for (inset in listOf(24, 48, 80)) {
                    for (consumed in listOf(false, true)) {
                        compose.runOnIdle { case = Case(engine, grid, inset, consumed) }
                        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                            it(0f, 10_000f)
                        }
                        compose.waitForIdle()
                        val button = compose.onNodeWithText(action).fetchSemanticsNode().boundsInRoot
                        assertTrue("$case: action $button under footer $footerBounds", button.bottom <= footerBounds.top)
                        assertEquals("$case: viewport must draw to its parent's bottom",
                            200f, viewportBounds.height, 0.5f)
                        assertEquals("$case: only unconsumed inset belongs to overlay",
                            76f + if (consumed) 0 else inset, overlayHeight.value, 0.5f)
                    }
                }
            }
        }
    }
}
