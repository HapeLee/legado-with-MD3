package io.legado.app.ui.widget.components.menuItem

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.time.Duration
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RoundDropdownMenuLazyTest {
    @Test
    fun `lazy menu supports intrinsic measurement and bounded long and short lists`() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        stopKoin()
        startKoin {
            modules(module {
                single {
                    CoverAlbumUseCase(
                        CoverAlbumRepository(
                            application,
                            SettingsRepository()
                        )
                    )
                }
            })
        }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        var menuSize = IntSize.Zero
        var sourceCount by mutableIntStateOf(100)
        var viewportHeight by mutableStateOf(320.dp)
        var useIntrinsicHeight by mutableStateOf(false)
        try {
            activity.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AppTheme(AppUiConfiguration(), applyBackground = false) {
                        Box(Modifier.fillMaxSize()) {
                            // DropdownMenu uses this intrinsic-width parent around its content.
                            key(sourceCount, viewportHeight, useIntrinsicHeight) {
                                Column(
                                    Modifier
                                        .width(IntrinsicSize.Max)
                                        .then(
                                            if (useIntrinsicHeight) Modifier.height(IntrinsicSize.Min)
                                            else Modifier
                                        )
                                        .onSizeChanged { menuSize = it }
                                ) {
                                    LazyMenuViewport(280.dp, viewportHeight) {
                                        LazyColumn {
                                            items((1..sourceCount).toList()) { index ->
                                                RoundDropdownMenuItem(
                                                    text = "Source $index",
                                                    onClick = {})
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            fun measureFrames() {
                // 先排空主 Looper，保证状态变化引起的重组已经提交，再强制走测量/布局，
                // 否则 onSizeChanged 可能仍停留在上一组参数的结果上。
                shadowOf(Looper.getMainLooper()).idle()
                repeat(10) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    val decor = activity.window.decorView
                    decor.requestLayout()
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    )
                    decor.layout(0, 0, 1080, 1920)
                }
            }
            measureFrames()
            assertTrue("Menu must actually be measured", menuSize.width > 0 && menuSize.height > 0)
            val cappedMenuHeight = menuSize.height
            viewportHeight = 660.dp
            measureFrames()
            assertTrue(
                "Long menu should use the larger available height",
                menuSize.height > cappedMenuHeight
            )
            val longMenuHeight = menuSize.height
            sourceCount = 1
            measureFrames()
            assertTrue(
                "Short menu should shrink: long=$longMenuHeight short=${menuSize.height}",
                menuSize.height < longMenuHeight
            )

            // intrinsic 高度契约：不能返回 0，否则按 intrinsic 约束的父容器会让菜单不可见。
            // 代价是这类父容器下短菜单会被撑到 viewportHeight（见 LazyMenuViewport 注释）。
            val density = activity.resources.displayMetrics.density
            useIntrinsicHeight = true
            measureFrames()
            val expectedIntrinsicHeight = (660f * density).roundToInt()
            assertEquals(
                "Intrinsic height must report the viewport cap",
                expectedIntrinsicHeight,
                menuSize.height,
            )
        } finally {
            controller.pause().stop().destroy()
            stopKoin()
        }
    }
}
