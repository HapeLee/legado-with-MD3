package io.legado.app.service

import io.legado.app.data.entities.VoiceEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁住「跟随系统 = 不下发引擎语速」这条语义：
 * 语速为 null 时绝不能兜底成 1f，否则引擎的系统默认速率会被显式覆盖掉。
 */
class CueSpeechPresetTest {

    private fun effect(speed: Float = 1f, pitch: Float = 1f) = VoiceEffectPreset(
        name = "effect",
        speed = speed,
        pitch = pitch,
    )

    private fun preset(
        followSys: Boolean = false,
        characterRate: Float? = null,
        characterPitch: Float? = null,
        speechRateSetting: Int = 5,
        effect: VoiceEffectPreset? = null,
    ) = cueSpeechPreset(
        followSys = followSys,
        characterRate = characterRate,
        characterPitch = characterPitch,
        speechRateSetting = speechRateSetting,
        effect = effect,
    )

    @Test
    fun notFollowSysUsesGlobalRateWhenCharacterHasNone() {
        val result = preset(speechRateSetting = 8)

        assertEquals(1.3f, result.rate!!, 0.0001f)
        assertEquals(1f, result.pitch, 0.0001f)
    }

    @Test
    fun followSysWithoutCharacterRateMustNotDownrate() {
        // 跟随系统且角色没配语速 → null = 不下发，绝不能是 1f
        val result = preset(followSys = true)

        assertEquals(null, result.rate)
    }

    @Test
    fun followSysStillHonorsExplicitCharacterRate() {
        val result = preset(followSys = true, characterRate = 1.3f)

        assertEquals(1.3f, result.rate!!, 0.0001f)
    }

    @Test
    fun characterRateWinsOverGlobalRate() {
        val result = preset(speechRateSetting = 8, characterRate = 0.5f)

        assertEquals(0.5f, result.rate!!, 0.0001f)
    }

    @Test
    fun followSysDropsEffectSpeedTierButKeepsEffectPitch() {
        val result = preset(
            followSys = true,
            characterRate = null,
            effect = effect(speed = 1.5f, pitch = 1.2f),
        )

        // 无基准语速可乘 → 速度档一起丢，音高层不受影响
        assertEquals(null, result.rate)
        assertEquals(1.2f, result.pitch, 0.0001f)
    }

    @Test
    fun effectSpeedAndPitchApplyOnExplicitRate() {
        val result = preset(
            followSys = false,
            speechRateSetting = 5,
            characterPitch = null,
            effect = effect(speed = 2f, pitch = 2f),
        )

        assertEquals(2f, result.rate!!, 0.0001f) // 1f × 2f，预设倍率封顶 MAX_SPEED = 2f
        assertEquals(2f, result.pitch, 0.0001f)
    }

    @Test
    fun effectSpeedMultipliesCharacterRate() {
        val result = preset(characterRate = 1.2f, effect = effect(speed = 1.5f))

        assertEquals(1.8f, result.rate!!, 0.0001f)
    }

    @Test
    fun characterPitchWinsOverDefault() {
        val result = preset(characterPitch = 0.8f, effect = effect(pitch = 1.5f))

        assertEquals(1.2f, result.pitch, 0.0001f) // 0.8 × 1.5
    }

    @Test
    fun globalRateAboveEffectSafetyRangeIsNotCapped() {
        val result = preset(speechRateSetting = 80)

        // 滑块 80 → (80 + 5) / 10 = 8.5×，不能被预设倍率范围 0.5..2.0 夹掉
        assertEquals(8.5f, result.rate!!, 0.0001f)
    }

    @Test
    fun effectSpeedMultipliesHighGlobalRate() {
        val result = preset(speechRateSetting = 30, effect = effect(speed = 1.25f))

        // 3.5 × 1.25 = 4.375，预设倍率与全局语速保持相乘
        assertEquals(4.375f, result.rate!!, 0.0001f)
    }

    @Test
    fun finalRateStopsAtPlaybackMax() {
        val result = preset(speechRateSetting = 80, effect = effect(speed = 2f))

        // 8.5 × 2 超过最终播放上限，只夹最终结果，预设编辑器仍按 0.5..2.0 校验
        assertEquals(8.5f, result.rate!!, 0.0001f)
    }
}
