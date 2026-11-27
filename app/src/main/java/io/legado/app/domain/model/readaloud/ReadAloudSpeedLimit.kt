package io.legado.app.domain.model.readaloud

import kotlin.math.roundToInt

/**
 * 朗读语速滑杆与最终倍率之间的换算。
 *
 * 这里只做纯数值换算，不依赖具体播放实现：当前链路允许的上限由平台侧各自传入
 * （系统 TTS 直读 8.5×、ExoPlayer 默认音频链路 8.0×，见
 * `io.legado.app.help.readaloud.playback.PlaybackSpeedLimits`），
 * 这样 Compose 界面不必引用 Media3 的播放实现类。
 */
object ReadAloudSpeedLimit {
    /** 系统 TTS 直读：沿用现有滑杆上限 8.5×。 */
    const val SYSTEM_TTS_MAX = 8.5f

    /**
     * 把倍率上限换算成 `ttsSpeechRate` 的滑杆档位。
     *
     * 滑杆档位与倍率的换算是 `(v + 5) / 10`，所以 8.0× 对应 75、8.5× 对应 80。
     */
    fun sliderMax(maxSpeed: Float): Int =
        ((maxSpeed * 10f) - 5f).roundToInt().coerceIn(0, 80)
}
