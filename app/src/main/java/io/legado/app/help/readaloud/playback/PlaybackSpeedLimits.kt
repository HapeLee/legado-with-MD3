package io.legado.app.help.readaloud.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import io.legado.app.domain.model.readaloud.ReadAloudSpeedLimit

/**
 * 播放侧可用的最终倍率上限。
 *
 * 系统 TTS 直读由引擎自己实现语速；HTTP / 云端 / 系统文件合成走 ExoPlayer 默认音频链路，
 * Media3 的 [DefaultAudioSink.setPlaybackParameters] 会把倍率夹到
 * [DefaultAudioSink.MAX_PLAYBACK_SPEED]。两条链路上限不同，播放端必须按当前链路取上限；
 * 界面只使用 [ReadAloudSpeedLimit] 的纯数值换算，不引用这里的 Media3 类型。
 */
@androidx.annotation.OptIn(UnstableApi::class)
object PlaybackSpeedLimits {
    /** 系统 TTS 直读：沿用现有滑杆上限 8.5×。 */
    const val SYSTEM_TTS_MAX = ReadAloudSpeedLimit.SYSTEM_TTS_MAX

    /** ExoPlayer 默认音频链路：直接跟随 Media3 的公开上限，避免升级后再次漂移。 */
    const val EXOPLAYER_MAX = DefaultAudioSink.MAX_PLAYBACK_SPEED
}
