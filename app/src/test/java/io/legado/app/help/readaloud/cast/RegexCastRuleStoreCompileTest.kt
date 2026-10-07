package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RegexCastRuleStore.compile] 的口径：正则开关只改「这一串怎么解释」，
 * 不改命中之后的处理。产物由 [RegexCastSplitter.split] 消费。
 */
class RegexCastRuleStoreCompileTest {

    @Test
    fun `literal mode treats regex metacharacters as plain text`() {
        val pattern = RegexCastRuleStore.compile("(山体崩碎音效", useRegex = false)!!
        assertEquals(listOf("(山体崩碎音效"), pattern.findAll("(山体崩碎音效崩了").map { it.value }.toList())
    }

    @Test
    fun `literal mode does not match a regex that would otherwise match`() {
        val pattern = RegexCastRuleStore.compile("a.c", useRegex = false)!!
        assertEquals(listOf("a.c"), pattern.findAll("abc a.c").map { it.value }.toList())
    }

    @Test
    fun `regex mode keeps metacharacters live`() {
        val pattern = RegexCastRuleStore.compile("a.c", useRegex = true)!!
        assertEquals(listOf("abc", "a.c"), pattern.findAll("abc a.c").map { it.value }.toList())
        assertEquals(2, RegexCastRuleStore.compile("爆炸|雷声", useRegex = true)!!
            .findAll("爆炸和雷声").count())
    }

    @Test
    fun `plain words match themselves in both modes`() {
        assertEquals(1, RegexCastRuleStore.compile("爆炸", useRegex = true)!!
            .findAll("前爆炸后").count())
        assertEquals(1, RegexCastRuleStore.compile("爆炸", useRegex = false)!!
            .findAll("前爆炸后").count())
    }

    @Test
    fun `a broken regex falls back to literal instead of dropping the rule`() {
        val pattern = RegexCastRuleStore.compile("(山体崩碎音效", useRegex = true)!!
        assertEquals(listOf("(山体崩碎音效"), pattern.findAll("前(山体崩碎音效)后").map { it.value }.toList())
    }

    @Test
    fun `blank pattern has no effect`() {
        assertNull(RegexCastRuleStore.compile("   ", useRegex = true))
        assertNull(RegexCastRuleStore.compile("   ", useRegex = false))
    }
}
