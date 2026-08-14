package io.legado.app.ui.book.readaloud.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语速调节范围相关纯逻辑测试。
 *
 * 背景：听书播放器与经典朗读控制（ReadAloudScreen.kt 的 valueRange = 0f..80f）应使用
 * 一致的语速范围；此前播放器为 5f..20f 与经典控制 0f..80f 不一致（issue #2032）。
 */
class ReadAloudPlayerSpeedRangeTest {

    @Test
    fun `speed range matches classic read aloud control`() {
        // 与 ReadAloudScreen.kt 经典朗读控制的 valueRange = 0f..80f 保持一致
        assertEquals(0, READ_ALOUD_SPEED_MIN)
        assertEquals(80, READ_ALOUD_SPEED_MAX)
    }

    @Test
    fun `slider steps cover every integer in the range`() {
        // 0..80 共 81 个刻度点，steps = 刻度点 - 1 = 79
        assertEquals(79, READ_ALOUD_SPEED_MAX - READ_ALOUD_SPEED_MIN - 1)
    }

    @Test
    fun `coerce clamps below minimum`() {
        assertEquals(READ_ALOUD_SPEED_MIN, coerceReadAloudSpeed(-5))
        assertEquals(READ_ALOUD_SPEED_MIN, coerceReadAloudSpeed(Int.MIN_VALUE))
    }

    @Test
    fun `coerce clamps above maximum`() {
        assertEquals(READ_ALOUD_SPEED_MAX, coerceReadAloudSpeed(999))
        assertEquals(READ_ALOUD_SPEED_MAX, coerceReadAloudSpeed(Int.MAX_VALUE))
    }

    @Test
    fun `coerce keeps in-range values unchanged`() {
        assertEquals(0, coerceReadAloudSpeed(0))
        assertEquals(20, coerceReadAloudSpeed(20))
        assertEquals(40, coerceReadAloudSpeed(40))
        assertEquals(80, coerceReadAloudSpeed(80))
    }

    @Test
    fun `format label shows one decimal place`() {
        assertEquals("2.0", formatReadAloudSpeedLabel(20))
        assertEquals("8.0", formatReadAloudSpeedLabel(80))
        assertEquals("0.0", formatReadAloudSpeedLabel(0))
    }

    @Test
    fun `format label keeps fractional display`() {
        assertEquals("0.5", formatReadAloudSpeedLabel(5))
        assertEquals("1.5", formatReadAloudSpeedLabel(15))
        assertEquals("0.7", formatReadAloudSpeedLabel(7))
    }

    @Test
    fun `player slider value beyond old 20 cap is now supported`() {
        // 回归：旧播放器上限 20，修复后 0..80 内任意值都应可用
        assertEquals(40, coerceReadAloudSpeed(40))
        assertEquals("4.0", formatReadAloudSpeedLabel(40))
    }
}
