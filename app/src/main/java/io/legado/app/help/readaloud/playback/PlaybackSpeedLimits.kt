package io.legado.app.help.readaloud.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlin.math.roundToInt

/**
 * 朗读可用的最终播放倍率上限。
 *
 * 系统 TTS 直读由引擎自己实现语速；HTTP / 云端 / 系统文件合成走 ExoPlayer 默认音频链路，
 * Media3 的 [DefaultAudioSink.setPlaybackParameters] 会把倍率夹到
 * [DefaultAudioSink.MAX_PLAYBACK_SPEED]。两条链路上限不同，UI 和播放端都必须按当前链路取上限。
 */
@androidx.annotation.OptIn(UnstableApi::class)
object PlaybackSpeedLimits {
    /** 系统 TTS 直读：沿用现有滑杆上限 8.5×。 */
    const val SYSTEM_TTS_MAX = 8.5f

    /** ExoPlayer 默认音频链路：直接跟随 Media3 的公开上限，避免升级后再次漂移。 */
    const val EXOPLAYER_MAX = DefaultAudioSink.MAX_PLAYBACK_SPEED

    /**
     * 把倍率上限换算成 `ttsSpeechRate` 的滑杆档位。
     *
     * 滑杆档位与倍率的换算是 `(v + 5) / 10`，所以 8.0× 对应 75、8.5× 对应 80。
     */
    fun sliderMax(maxSpeed: Float): Int =
        ((maxSpeed * 10f) - 5f).roundToInt().coerceIn(0, 80)
}
