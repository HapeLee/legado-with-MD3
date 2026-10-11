package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定标题字号的解析口径。
 *
 * 相对模式必须与旧 View 版完全一致：标题字号 = 正文字号 + 偏移（旧 `ChapterProvider` 即
 * `(textSize + titleSize).spToPx()`）。绝对模式才直接用存储值。`titleSizeRelative` 未显式
 * 设置（旧配置读出来是 null）时沿用「< 8 相对、>= 8 绝对」的历史识别规则。
 */
class TitleSizeResolutionTest {

    @Test
    fun `相对模式等于正文加偏移`() {
        val config = ReadBookConfig.Config(
            textSize = 24,
            titleSize = 4,
            titleSizeRelative = true,
        )

        assertTrue(config.isTitleSizeRelative())
        assertEquals(28, config.resolvedTitleSizeSp())
    }

    @Test
    fun `绝对模式直接使用自身字号`() {
        val config = ReadBookConfig.Config(
            textSize = 24,
            titleSize = 30,
            titleSizeRelative = false,
        )

        assertFalse(config.isTitleSizeRelative())
        assertEquals(30, config.resolvedTitleSizeSp())
    }

    @Test
    fun `未显式设置时小值按旧相对偏移解释`() {
        // 旧 View 版偏移范围 0..20，0..7 这一档不会被绝对值化期间的值污染，识别为相对。
        val config = ReadBookConfig.Config(textSize = 24, titleSize = 4, titleSizeRelative = null)

        assertTrue(config.isTitleSizeRelative())
        assertEquals(28, config.resolvedTitleSizeSp())
    }

    @Test
    fun `未显式设置时大值按绝对字号解释`() {
        // 绝对值化期间默认写入 20，这一档要保持原样（不再叠加正文字号）。
        val config = ReadBookConfig.Config(textSize = 24, titleSize = 20, titleSizeRelative = null)

        assertFalse(config.isTitleSizeRelative())
        assertEquals(20, config.resolvedTitleSizeSp())
    }

    @Test
    fun `相对偏移随正文字号联动`() {
        val small = ReadBookConfig.Config(textSize = 20, titleSize = 4, titleSizeRelative = true)
        val large = ReadBookConfig.Config(textSize = 30, titleSize = 4, titleSizeRelative = true)

        assertEquals(24, small.resolvedTitleSizeSp())
        assertEquals(34, large.resolvedTitleSizeSp())
    }

    @Test
    fun `字号下限兜底为一`() {
        val config = ReadBookConfig.Config(
            textSize = 10,
            titleSize = -20,
            titleSizeRelative = true,
        )

        assertEquals(1, config.resolvedTitleSizeSp())
    }
}
