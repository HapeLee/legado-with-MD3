package io.legado.app.help.readaloud.playback

import android.app.Application
import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 覆盖音频输出端：Media3 的 DefaultAudioSink 才是 HTTP 朗读最终生效的倍率。
 *
 * 传入 ExoPlayer 的 PlaybackParameters 可以写 8.5×，但 DefaultAudioSink 会在
 * setPlaybackParameters() 里把它夹到 MAX_PLAYBACK_SPEED（1.11.0 为 8.0×）。
 * 这个测试锁住我们依赖的实际上限，避免以后升级 Media3 或改链路时 UI 再次虚标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@androidx.annotation.OptIn(UnstableApi::class)
class DefaultAudioSinkSpeedTest {

    @Test
    fun `media3 sink clamps speed to its published max`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sink = DefaultAudioSink.Builder(context).build()

        sink.setPlaybackParameters(PlaybackParameters(8.5f))

        assertEquals(DefaultAudioSink.MAX_PLAYBACK_SPEED, sink.playbackParameters.speed, 0f)
    }
}
