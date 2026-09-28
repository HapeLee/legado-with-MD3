package io.legado.app.ui.book.readaloud

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadAloudBarCapsuleInteractionTest {
    @Test
    fun expandedCoverLongPressRestoresTheDockWithoutOpeningOrControllingPlayback() {
        val application = RuntimeEnvironment.getApplication()
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
        var expanded by mutableStateOf(true)
        var opens = 0
        var bounds = Rect.Zero
        val settings = ReadAloudSettingsRepository()
        val playback = object : PlaybackCapsuleGateway {
            override val state = MutableStateFlow(
                PlaybackCapsuleState(
                    source = PlaybackCapsuleSource.AudioBook,
                    bookName = "Book",
                    chapterTitle = "Chapter",
                )
            )

            override fun setSessionAvailable(source: PlaybackCapsuleSource, available: Boolean) =
                error("unexpected playback action")

            override fun prepareAudioBook(bookUrl: String) = error("unexpected playback action")
            override fun clearPreparedAudioBook(bookUrl: String) =
                error("unexpected playback action")

            override fun togglePause(source: PlaybackCapsuleSource) =
                error("unexpected playback action")

            override fun stop(source: PlaybackCapsuleSource) = error("unexpected playback action")
        }
        try {
            activity.setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AppTheme(AppUiConfiguration(), applyBackground = false) {
                        Box(Modifier.fillMaxSize()) {
                            ReadAloudBarCapsuleSlot(
                                enabled = true, morph = null,
                                onOpenPlayer = { opens++ }, playbackGateway = playback,
                                settingsGateway = settings,
                                controlsExpanded = expanded, expansion = if (expanded) 1f else 0f,
                                width = if (expanded) 288.dp else 64.dp, expandedWidth = 288.dp,
                                onControlsExpandedChange = { expanded = it },
                                modifier = Modifier.onGloballyPositioned {
                                    bounds = it.boundsInWindow()
                                },
                            )
                        }
                    }
                }
            }
            val decor = activity.window.decorView
            repeat(5) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                )
                decor.layout(0, 0, 1080, 1920)
            }
            assertFalse(bounds.isEmpty)
            val downTime = SystemClock.uptimeMillis()
            // 64dp 封面区的中心，远离右侧两个控制按钮。
            val x = bounds.left + bounds.height / 2f
            val y = bounds.center.y
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
                activity.dispatchTouchEvent(it)
                it.recycle()
            }
            // Deliver the down event first; Compose timeout and Robolectric looper use
            // different clocks, so let both reach the long-press deadline.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            val holdMillis = ViewConfiguration.getLongPressTimeout().toLong() + 200
            Thread.sleep(holdMillis)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(holdMillis))
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
                .also {
                    activity.dispatchTouchEvent(it)
                    it.recycle()
                }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            assertFalse("bounds=$bounds opens=$opens", expanded)
            assertEquals(0, opens)
        } finally {
            controller.pause().stop().destroy()
            stopKoin()
        }
    }
}
