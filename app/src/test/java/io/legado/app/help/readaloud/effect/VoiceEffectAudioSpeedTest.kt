package io.legado.app.help.readaloud.effect

import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.help.readaloud.playback.PlaybackSpeedLimits
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁住两层倍率钳制：
 * - 变声器预设自己的倍率在 0.5..2.0；
 * - 最终播放倍率仍能到朗读语速滑块的上限 8.5×。
 */
class VoiceEffectAudioSpeedTest {

    private fun preset(speed: Float = 1f) = VoiceEffectPreset(name = "effect", speed = speed)

    @Test
    fun globalSpeedAbovePresetRangeIsPreserved() {
        assertEquals(8.5f, VoiceEffectAudio.speedOf(null, 8.5f), 0.0001f)
    }

    @Test
    fun presetSpeedMultipliesGlobalSpeed() {
        assertEquals(4.375f, VoiceEffectAudio.speedOf(preset(1.25f), 3.5f), 0.0001f)
    }

    @Test
    fun presetFactorIsClampedBeforeMultiply() {
        // 预设写坏成 5× 时只按 2× 参与计算
        assertEquals(8.5f, VoiceEffectAudio.speedOf(preset(5f), 8.5f), 0.0001f)
    }

    @Test
    fun playbackParametersUseTheSameFinalSpeed() {
        assertEquals(8.5f, VoiceEffectAudio.parameters(preset(2f), 8.5f).speed, 0.0001f)
    }

    @Test
    fun exoPlayerLimitClampsFinalSpeed() {
        assertEquals(
            8f,
            VoiceEffectAudio.speedOf(preset(2f), 8.5f, PlaybackSpeedLimits.EXOPLAYER_MAX),
            0.0001f,
        )
    }

    @Test
    fun exoPlayerParametersUseTheSinkLimit() {
        assertEquals(
            8f,
            VoiceEffectAudio.parameters(
                preset(2f),
                8.5f,
                PlaybackSpeedLimits.EXOPLAYER_MAX,
            ).speed,
            0.0001f,
        )
    }
}
