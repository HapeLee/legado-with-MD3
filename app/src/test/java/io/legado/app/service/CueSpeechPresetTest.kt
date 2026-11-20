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

        assertEquals(2f, result.rate!!, 0.0001f) // 1f × 2f，封顶 MAX_SPEED = 2f
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
}
