package io.legado.app.ui.book.video

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VideoFullscreenOrientationTest {
    @Test
    fun `unknown or incomplete dimensions do not request a window rotation`() {
        assertNull(VideoSize.UNKNOWN.fullscreenOrientationOrNull())
        assertNull(VideoSize(0, 640).fullscreenOrientationOrNull())
        assertNull(VideoSize(360, 0).fullscreenOrientationOrNull())
    }

    @Test
    fun `portrait episode transition does not insert a landscape request`() {
        val requests = listOf(VideoSize(360, 640), VideoSize.UNKNOWN, VideoSize(360, 640))
            .mapNotNull { it.fullscreenOrientationOrNull() }
        assertEquals(
            listOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
            requests,
        )
    }

    @Test
    fun `valid new dimensions can change orientation and respect pixel aspect ratio`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, VideoSize(640, 360).fullscreenOrientationOrNull())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, VideoSize(640, 720).fullscreenOrientationOrNull())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, VideoSize(640, 720, 2f).fullscreenOrientationOrNull())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, VideoSize(720, 720).fullscreenOrientationOrNull())
    }
}
