package io.legado.app.ui.widget.components

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.main.FloatingNavigationContainer
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "mdpi")
class AppScaffoldInsetsTest {
    private data class Case(
        val engine: String, val floatingPreference: Boolean, val behindBars: Boolean,
        val parentConsumed: Boolean, val hasBottomBar: Boolean = true
    )

    @Test
    fun actualBarsKeepTheirSpaceWithEitherEnginePreferenceAndInsetOwner() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        var case by mutableStateOf(Case("material", false, false, false))
        var probe by mutableStateOf("scaffold")
        var mediumBar by mutableStateOf(false)
        var contentBounds = Rect.Zero
        var footerBounds = Rect.Zero
        var topBarHeight = 0
        val insets = WindowInsets(left = 0, top = 24, right = 0, bottom = 32)
        try {
            activity.setContent {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalDensity provides Density(1f)
                ) {
                    AppTheme(
                        AppUiConfiguration(appShell = AppShellSettings(useFloatingBottomBar = case.floatingPreference)),
                        applyBackground = false
                    ) {
                        val colors =
                            LocalLegadoThemeColors.current.copy(composeEngine = case.engine)
                        CompositionLocalProvider(LocalLegadoThemeColors provides colors) {
                            Box(
                                Modifier
                                    .requiredSize(400.dp, 600.dp)
                                    .then(
                                        if (case.parentConsumed) Modifier.windowInsetsPadding(insets) else Modifier
                                    )
                            ) {
                                if (probe == "footer") {
                                    FloatingNavigationContainer(
                                        Modifier.align(Alignment.BottomCenter),
                                        insets
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .onGloballyPositioned {
                                                    footerBounds = it.boundsInRoot()
                                                })
                                    }
                                } else if (probe == "topbar") {
                                    val modifier = Modifier.onGloballyPositioned {
                                        topBarHeight = it.size.height
                                    }
                                    if (mediumBar) GlassMediumFlexibleTopAppBar(
                                        "Title",
                                        modifier = modifier
                                    )
                                    else GlassTopAppBar("Title", modifier = modifier)
                                } else {
                                    AppScaffold(
                                        contentWindowInsets = insets,
                                        alwaysDrawBehindBars = case.behindBars,
                                        disableContentSampling = true,
                                        topBar = {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .windowInsetsPadding(
                                                        insets.only(WindowInsetsSides.Top)
                                                    )
                                                    .height(56.dp)
                                            )
                                        },
                                        bottomBar = {
                                            if (case.hasBottomBar) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .windowInsetsPadding(
                                                            insets.only(WindowInsetsSides.Bottom)
                                                        )
                                                        .height(56.dp)
                                                )
                                            }
                                        },
                                    ) { padding ->
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(padding)
                                                .consumeWindowInsets(padding)
                                                .onGloballyPositioned {
                                                    contentBounds = it.boundsInRoot()
                                                })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fun frames() {
                repeat(8) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    composeRoot(activity.window.decorView)?.requestLayout()
                    activity.window.decorView.apply {
                        forceLayout()
                        measure(
                            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
                        )
                        layout(0, 0, 800, 1200)
                    }
                }
            }
            for (engine in listOf("material", "miuix")) {
                for (floating in listOf(false, true)) {
                    for (behind in listOf(false, true)) {
                        for (consumed in listOf(false, true)) {
                            for (hasBottom in listOf(false, true)) {
                                case = Case(engine, floating, behind, consumed, hasBottom)
                                frames()
                                assertEquals("$case top", 80f, contentBounds.top, 0.5f)
                                assertEquals(
                                    "$case bottom",
                                    if (hasBottom) 512f else 568f,
                                    contentBounds.bottom,
                                    0.5f
                                )
                            }
                        }
                    }
                }
            }
            probe = "footer"
            for (consumed in listOf(false, true)) {
                case = Case("material", true, false, consumed)
                frames()
                assertEquals("footer parentConsumed=$consumed", 556f, footerBounds.bottom, 0.5f)
            }
            probe = "topbar"
            for (engine in listOf("material", "miuix")) {
                for (medium in listOf(false, true)) {
                    for (consumed in listOf(false, true)) {
                        case = Case(engine, false, false, consumed)
                        mediumBar = medium
                        frames()
                        val root = checkNotNull(composeRoot(activity.window.decorView))
                        fun statusBar(visible: Boolean) {
                            val status = WindowInsetsCompat.Type.statusBars()
                            root.dispatchApplyWindowInsets(
                                WindowInsetsCompat.Builder()
                                    .setInsets(status, Insets.of(0, if (visible) 24 else 0, 0, 0))
                                    .setInsetsIgnoringVisibility(status, Insets.of(0, 24, 0, 0))
                                    .setVisible(status, visible).build().toWindowInsets()!!
                            )
                            frames()
                        }
                        statusBar(true)
                        val visibleHeight = topBarHeight
                        statusBar(false)
                        assertEquals(
                            "$engine medium=$medium parentConsumed=$consumed status bar height delta",
                            if (consumed) 0 else 24, visibleHeight - topBarHeight
                        )
                    }
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun composeRoot(view: View): View? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) composeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
