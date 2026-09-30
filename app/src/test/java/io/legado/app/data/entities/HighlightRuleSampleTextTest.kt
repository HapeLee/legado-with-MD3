package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预览的示例句固定、上样区间也固定：规则编辑的每一项改动都必须落在「我是李四。」上。
 *
 * 曾经的做法是按正则换示例句去迁就用户填的模式，但总有些模式命不中任何候选句，
 * 那时整块预览是死的——字色、下划线、背景图、命中字距怎么调都看不出差别。
 */
class HighlightRuleSampleTextTest {

    @Test
    fun `the sample sentence is the fixed one`() {
        assertEquals("张三：我是李四。他惊了！", HighlightRule.DEFAULT_SAMPLE_TEXT)
        assertEquals(
            HighlightRule.DEFAULT_SAMPLE_TEXT,
            HighlightRule(sampleText = "   ").normalizedSampleText(),
        )
    }

    /** 「张三：」占 0..2，命中段从「我」起、到「。」后一位止，引号外两侧都还剩字可以参照。 */
    @Test
    fun `the hit range covers exactly the spoken sentence`() {
        val range = requireNotNull(HighlightRule().previewHitRange())

        assertEquals(3, range.first)
        assertEquals(8, range.second)
        assertEquals(
            HighlightRule.SAMPLE_HIT_TEXT,
            HighlightRule.DEFAULT_SAMPLE_TEXT.substring(range.first, range.second),
        )
    }

    /** 用户自己写的示例句里带那一段时，区间跟着实际位置走。 */
    @Test
    fun `the hit range follows the text the user typed`() {
        val rule = HighlightRule(sampleText = "她说：我是李四。然后走了")

        assertEquals(3 to 8, rule.previewHitRange())
    }

    /** 示例句被改成不含那一段：没有可钉的区间，交给调用方退回按正则命中。 */
    @Test
    fun `a sample without the hit text yields no range`() {
        assertNull(HighlightRule(sampleText = "她轻声说：今晚就出发。").previewHitRange())
    }
}
