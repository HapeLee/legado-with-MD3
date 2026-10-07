package io.legado.app.help.readaloud.effect

import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.VoiceEffectPreset

/**
 * 把一个变声器预设套到「我们自己持有的播放器」上。
 *
 * 分两条路，因为能力不一样：
 * - **音高/语速**：ExoPlayer 用 `PlaybackParameters`（Media3 内置 Sonic 做变调不变速），
 *   系统 TTS 直读用 `setPitch/setSpeechRate`。两条路都是逐条生效，任何引擎都能用。
 * - **混响/金属感**：平台 AudioEffect 只能挂在整条音频会话上，所以切到带混响的那句时
 *   开、切走时关（[apply]）。设备不支持该效果时只丢这一层，音高语速照旧生效。
 *
 * 会话号有个坑：Media3 是在播放线程**异步**生成会话号的（`ExoPlayerImpl` 构造后立刻取
 * 只会拿到 `C.AUDIO_SESSION_ID_UNSET` = 0），所以刚 build/prepare 完就 attach 会拿到 0，
 * 效果永远挂不上。这里把 [pending] 存住，等下一次带真会话号的 [attach] 再补挂；
 * 调用方也要在开播回调里再 attach 一次（见 `HttpReadAloudService`、`VoiceAuditionButton`）。
 */
@OptIn(UnstableApi::class)
class VoiceEffectAudio {

    private var sessionId = 0
    private var reverb: PresetReverb? = null
    private var equalizer: Equalizer? = null
    private var applied = ""
    private var pending: VoiceEffectPreset? = null

    /** 换播放器（或首次拿到会话号）时调用，旧会话上的效果随之释放。 */
    fun attach(audioSessionId: Int) {
        // 0 = 还没生成，直接返回：把已有会话丢掉反而会让挂好的效果消失
        if (audioSessionId == AUDIO_SESSION_ID_UNSET) return
        if (audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        // 会话号为空时 apply 会挂进 pending，会话建立后在这里补上
        pending?.let { apply(it) }
    }

    /** 把会话级效果（混响/带通）切到 [preset] 所需的状态；重复调用同一预设不折腾设备。 */
    fun apply(preset: VoiceEffectPreset?) {
        pending = preset
        if (sessionId == AUDIO_SESSION_ID_UNSET) return
        val key = preset?.let { "${it.name}#${it.reverbPreset}#${it.metal}" }.orEmpty()
        if (key == applied) return
        applied = key
        applyReverb(preset?.reverbPreset ?: VoiceEffectStore.REVERB_NONE)
        applyMetal(preset?.metal == true)
    }

    fun release() {
        runCatching { reverb?.release() }
        runCatching { equalizer?.release() }
        reverb = null
        equalizer = null
        sessionId = 0
        applied = ""
    }

    /** 设备/ROM 挂不上这一层时留个痕迹，别让用户对着原声猜是不是自己设错了。 */
    private fun logUnsupported(layer: String, error: Throwable) {
        AppLog.putDebug("变声器${layer}挂不上(会话 $sessionId): ${error.message}")
    }

    private fun applyReverb(preset: Int) {
        // 每次换档都整条重建，不是只关 enabled：PresetReverb 的延迟线里存着上一句的余音，
        // 关掉再开也还会放出来，听感就是「旁白开头一秒还带着上一句角色的混响」。
        runCatching { reverb?.release() }
        reverb = null
        if (preset == VoiceEffectStore.REVERB_NONE) return
        val effect = runCatching {
            PresetReverb(EFFECT_PRIORITY, sessionId)
        }.onFailure {
            logUnsupported("混响", it)
        }.getOrNull() ?: return
        runCatching {
            effect.preset = preset.toShort()
            effect.enabled = true
        }.onFailure {
            logUnsupported("混响", it)
            runCatching { effect.release() }
            return
        }
        reverb = effect
    }

    private fun applyMetal(on: Boolean) {
        // 同混响：带通的 Equalizer 也要重建，不然上一句的金属感会糊在下一句开头
        runCatching { equalizer?.release() }
        equalizer = null
        if (!on) return
        val effect = runCatching {
            Equalizer(EFFECT_PRIORITY, sessionId).takeIf { it.numberOfBands.toInt() > 0 }
        }.onFailure {
            logUnsupported("金属感", it)
        }.getOrNull() ?: return
        val ok = runCatching {
            val range = effect.bandLevelRange
            val floor = range[0].toInt()
            val ceiling = range[1].toInt()
            for (band in 0 until effect.numberOfBands.toInt()) {
                // 中心频率单位是毫赫：只留 600Hz–4.5kHz 这一段、其余压到底，就是那种金属/电话音
                val hz = runCatching {
                    effect.getCenterFreq(band.toShort()).toFloat() / 1000f
                }.getOrDefault(1000f)
                val level = if (hz in 600f..4500f) ceiling * METAL_BOOST else floor * METAL_CUT
                effect.setBandLevel(band.toShort(), level.toInt().coerceIn(floor, ceiling).toShort())
            }
            effect.enabled = true
        }.isSuccess
        if (!ok) {
            logUnsupported("金属感", Throwable("设置带通失败"))
            runCatching { effect.release() }
            return
        }
        equalizer = effect
    }

    companion object {
        private const val EFFECT_PRIORITY = 0
        private const val METAL_BOOST = 0.7f
        private const val METAL_CUT = 0.85f

        /** 就是 `androidx.media3.common.C.AUDIO_SESSION_ID_UNSET`（= 平台那个占位值）。 */
        private const val AUDIO_SESSION_ID_UNSET = 0

        /** 音高与语速倍率的上限：预设写错时也不至把声音推成噪音。 */
        const val MIN_PITCH = 0.4f
        const val MAX_PITCH = 2.5f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f

        fun pitchOf(preset: VoiceEffectPreset?, base: Float = 1f): Float =
            (base * (preset?.pitch ?: 1f)).coerceIn(MIN_PITCH, MAX_PITCH)

        fun speedOf(preset: VoiceEffectPreset?, base: Float = 1f): Float =
            (base * (preset?.speed ?: 1f)).coerceIn(MIN_SPEED, MAX_SPEED)

        fun parameters(preset: VoiceEffectPreset?, baseSpeed: Float): PlaybackParameters =
            PlaybackParameters(speedOf(preset, baseSpeed), pitchOf(preset))
    }
}
