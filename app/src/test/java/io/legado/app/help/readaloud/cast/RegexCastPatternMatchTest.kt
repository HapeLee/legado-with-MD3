package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正则角色在两种匹配模式下的命中情况。
 *
 * 用户报的现象是「开着使用正则什么都命中不了，关掉按字面量就正常」。这里把编译入口
 * [RegexCastRuleStore.compile] 与切分器 [RegexCastSplitter.split] 串起来跑真实中文文本，
 * 钉住两件事：正则模式确实能命中；语法不通过时它会**退回字面量**（这正是「看起来设了
 * 却什么都不发生」的那条路，界面标题与朗读日志都要把这件事说出来）。
 */
class RegexCastPatternMatchTest {

    private fun parts(pattern: String, useRegex: Boolean, text: String): List<RegexCastSplitter.Part> {
        val regex = RegexCastRuleStore.compile(pattern, useRegex)
        assertTrue("编译不出正则：$pattern", regex != null)
        return RegexCastSplitter.split(0, text, text, listOf(RegexCastEffect("测试规则", regex!!, voiceId = "voice-1"))).parts
    }

    private fun voiced(parts: List<RegexCastSplitter.Part>) = parts.filter { it.voiceId != null }

    @Test
    fun `regex mode matches a chinese speech label`() {
        val parts = parts("她(说|道)", useRegex = true, text = "她说道：我走了。")

        assertEquals(listOf("她说"), voiced(parts).map { it.text })
    }

    @Test
    fun `regex mode matches text inside full width quotes`() {
        val parts = parts("[“][^”]+[”]", useRegex = true, text = "她开口：“我走了。”")

        assertEquals(listOf("“我走了。”"), voiced(parts).map { it.text })
        assertEquals("她开口：", parts.first().text)
        assertNull(parts.first().voiceId)
    }

    @Test
    fun `literal mode treats metacharacters as plain text`() {
        val parts = parts("她(说|道)", useRegex = false, text = "她(说|道)：我走了。")

        assertEquals(listOf("她(说|道)"), voiced(parts).map { it.text })
    }

    @Test
    fun `literal mode matches nothing when the literal is absent`() {
        val parts = parts("她(说|道)", useRegex = false, text = "她说道：我走了。")

        assertEquals(emptyList<String>(), voiced(parts).map { it.text })
    }

    /**
     * 编不过的正则（少一个括号是最常见的一种）会整串退回字面量：于是谁都匹配不上，
     * 用户侧只看得到「开了正则就不生效」，界面上却没有任何区别。
     * 现在编译入口留日志、弹窗标题跟着说明。
     */
    @Test
    fun `uncompilable regex silently falls back to literal`() {
        val pattern = "她(说|道"

        assertFalse(RegexCastRuleStore.isRegexSyntaxValid(pattern))
        assertEquals(emptyList<String>(), voiced(parts(pattern, useRegex = true, text = "她说：我走了。")).map { it.text })
    }

    /**
     * 正文里本来就带半角括号时，同一条串两种模式的结论相反：
     * 按字面量命中，按正则解释括号成了捕获组、要匹配的「她(说)」根本不存在 → 什么都不命中。
     * 这就是「开着使用正则不生效、关掉就生效」最常见的一种成因，不是管线坏了。
     */
    @Test
    fun `metacharacters in the source text make regex mode miss while literal mode hits`() {
        val text = "她说(小声)道：我走了。"

        assertEquals(
            listOf("她说(小声)道"),
            voiced(parts("她说(小声)道", useRegex = false, text = text)).map { it.text },
        )
        assertEquals(
            emptyList<String>(),
            voiced(parts("她说(小声)道", useRegex = true, text = text)).map { it.text },
        )
    }

    /**
     * 用户给的实际写法：心声 `‘[^’]*’`。走朗读侧真实的那一步（在等长抹平版上匹配，
     * 标记 `<<名（池）>>` 已被抹成空格），确认这类正则到底能不能命中。
     */
    @Test
    fun `thought quote regex hits on the marker-blanked text the reader actually uses`() {
        val raw = "她心想<<李四（女声）>>‘今天天气不错’，然后说：走吧。"
        val blanked = io.legado.app.feature.reader.core.cast.CastMarkers.blank(raw)

        assertEquals(raw.length, blanked.length)
        val hits = voiced(parts("‘[^’]*’", useRegex = true, text = blanked)).map { it.text }

        assertEquals(listOf("‘今天天气不错’"), hits)
    }

    @Test
    fun `syntax validity agrees with what the literal mode accepts`() {
        assertTrue(RegexCastRuleStore.isRegexSyntaxValid("她(说|道)"))
        assertFalse(RegexCastRuleStore.isRegexSyntaxValid("   "))
    }
}
