package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkingEffectTest {

    @Test
    fun `效果到样式 - 单实线是 mode 1 加线色`() {
        val style = MarkingEffect.SOLID.toStyle(0xFFFF0000.toInt())
        assertEquals(1, style.underlineMode)
        assertEquals(0xFFFF0000.toInt(), style.underlineColor)
        assertEquals(null, style.bgColor)
        assertEquals(null, style.textColor)
        assertTrue(MarkingEffect.SOLID.isUnderline)
    }

    @Test
    fun `效果到样式 - 波浪线和虚线映射到对应 mode`() {
        assertEquals(3, MarkingEffect.WAVE.toStyle(0xFFFF0000.toInt()).underlineMode)
        assertEquals(2, MarkingEffect.DASHED.toStyle(0xFFFF0000.toInt()).underlineMode)
    }

    @Test
    fun `效果到样式 - 背景色自动加 ~20% 透明度`() {
        val style = MarkingEffect.BG.toStyle(0xFFFFD54F.toInt())
        assertEquals(0x33FFD54F.toInt(), style.bgColor)
        assertEquals(0, style.underlineMode)
        assertFalse(MarkingEffect.BG.isUnderline)
    }

    @Test
    fun `效果到样式 - 字体色是字色`() {
        val style = MarkingEffect.TEXT.toStyle(0xFFFF0000.toInt())
        assertEquals(0xFFFF0000.toInt(), style.textColor)
        assertEquals(null, style.bgColor)
        assertEquals(0, style.underlineMode)
        assertFalse(MarkingEffect.TEXT.isUnderline)
    }

    @Test
    fun `样式到效果 - 下划线 mode 反推正确`() {
        assertEquals(
            MarkingEffect.SOLID,
            MarkingEffect.fromStyle(TextProcessStyle(underlineMode = 1))
        )
        assertEquals(
            MarkingEffect.DASHED,
            MarkingEffect.fromStyle(TextProcessStyle(underlineMode = 2))
        )
        assertEquals(
            MarkingEffect.WAVE,
            MarkingEffect.fromStyle(TextProcessStyle(underlineMode = 3))
        )
    }

    @Test
    fun `样式到效果 - 背景与字体色反推`() {
        assertEquals(
            MarkingEffect.BG,
            MarkingEffect.fromStyle(TextProcessStyle(bgColor = 0x33FFD54F.toInt()))
        )
        assertEquals(
            MarkingEffect.TEXT,
            MarkingEffect.fromStyle(TextProcessStyle(textColor = 0xFFFF0000.toInt()))
        )
    }

    @Test
    fun `样式到效果 - 未知模式回退单实线`() {
        assertEquals(MarkingEffect.SOLID, MarkingEffect.fromStyle(TextProcessStyle()))
        assertEquals(MarkingEffect.SOLID, MarkingEffect.fromStyle(null))
        // mode 4（标题强调条）/ mode 5（SVG）不属于 5x1，回退单实线
        assertEquals(
            MarkingEffect.SOLID,
            MarkingEffect.fromStyle(TextProcessStyle(underlineMode = 4))
        )
    }

    @Test
    fun `展示色 - 背景剥 alpha 取底色，下划线取线色`() {
        assertEquals(
            0xFFFFD54F.toInt(),
            MarkingEffect.colorOf(TextProcessStyle(bgColor = 0x33FFD54F.toInt()))
        )
        assertEquals(
            0xFFFF0000.toInt(),
            MarkingEffect.colorOf(
                TextProcessStyle(
                    underlineMode = 1,
                    underlineColor = 0xFFFF0000.toInt()
                )
            ),
        )
        assertEquals(
            MarkingEffect.DEFAULT_COLOR,
            MarkingEffect.colorOf(null),
        )
    }
}
