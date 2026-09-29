package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 预览只给正则命中的那一段上样式，示例句一旦命不中，整块预览就是死的：
 * 字色、下划线、背景图、图片大小、命中字距怎么调都看不出差别。
 */
class HighlightRuleSampleTextTest {

    @Test
    fun `keeps a sample the pattern already hits`() {
        val kept = "她轻声说：“今晚就出发。”"

        assertEquals(kept, HighlightRule.matchingSampleText("“[^”]*”", kept))
    }

    @Test
    fun `falls back to the quoted zhang san line for a curly quote pattern`() {
        assertEquals(
            "张三：“我是李四。”他惊了！",
            HighlightRule.matchingSampleText("“[^”]*”", "她轻声说：今晚就出发。"),
        )
    }

    @Test
    fun `the wrapping symbol follows the pattern`() {
        assertEquals(
            "张三：《我是李四。》他惊了！",
            HighlightRule.matchingSampleText("《[^》]*》", ""),
        )
        assertEquals(
            "张三：（我是李四。）他惊了！",
            HighlightRule.matchingSampleText("（[^）]*）", ""),
        )
        assertEquals(
            "张三：「我是李四。」他惊了！",
            HighlightRule.matchingSampleText("「[^」]*」", ""),
        )
    }

    /** 正则没有成对符号时，示例句里不该凭空多出引号。 */
    @Test
    fun `a plain pattern gets the plain sample`() {
        assertEquals(
            "张三：我是李四。他惊了！",
            HighlightRule.matchingSampleText("我是李四", ""),
        )
    }

    /** 半截正则（编辑中）不该让弹层崩掉，也照样给出默认示例。 */
    @Test
    fun `an invalid pattern keeps the current sample`() {
        assertEquals(
            HighlightRule.DEFAULT_SAMPLE_TEXT,
            HighlightRule.matchingSampleText("[", ""),
        )
    }

    @Test
    fun `every candidate sentence is matched by its own pair pattern`() {
        listOf(
            "“[^”]*”" to "张三：“我是李四。”他惊了！",
            "\"[^\"]*\"" to "张三：\"我是李四。\"他惊了！",
            "「[^」]*」" to "张三：「我是李四。」他惊了！",
            "『[^』]*』" to "张三：『我是李四。』他惊了！",
            "（[^）]*）" to "张三：（我是李四。）他惊了！",
            "\\([^)]*\\)" to "张三：(我是李四。)他惊了！",
            "《[^》]*》" to "张三：《我是李四。》他惊了！",
            "【[^】]*】" to "张三：【我是李四。】他惊了！",
        ).forEach { (pattern, sample) ->
            val picked = HighlightRule.matchingSampleText(pattern, "")
            assertEquals(sample, picked)
            assertEquals(true, Regex(pattern).containsMatchIn(picked))
        }
    }
}
