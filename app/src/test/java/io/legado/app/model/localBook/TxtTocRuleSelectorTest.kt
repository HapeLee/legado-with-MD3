package io.legado.app.model.localBook

import io.legado.app.data.entities.TxtTocRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.regex.PatternSyntaxException

class TxtTocRuleSelectorTest {

    /** 1500 字符，保证相邻章节匹配的间隔大于计数的 1000 字符阈值。 */
    private val filler = "正文内容。".repeat(300)

    @Test
    fun `rule matched once by an incidental keyword is not selected`() {
        // “正文”在简介里只出现一次：按旧逻辑会被判为命中，随后把整本书压成一章
        val sample = "正文\n" + "这是一段很长的简介。".repeat(300)

        val selected = selectTxtTocRule(
            rules = listOf(rule(id = 1, chapterRule = "正文")),
            sample = sample,
        )

        assertNull(selected)
    }

    @Test
    fun `real chapter rule is selected`() {
        val chapterRule = rule(
            id = 1,
            chapterRule = "^第[0-9一二三四五六七八九十]+章.*$",
        )
        val sample = sampleWithChapters(5)

        val selected = selectTxtTocRule(rules = listOf(chapterRule), sample = sample)

        assertSame(chapterRule, selected)
    }

    @Test
    fun `rule with more chapter like matches wins`() {
        val inlineMentions = rule(id = 1, chapterRule = "第[0-9]+章")
        val lineStartOnly = rule(id = 2, chapterRule = "^第[0-9]+章")
        // 正文里再补一句“详见第9章”，只有不锚行首的规则会命中
        val sample = sampleWithChapters(6) + "\n详见第9章\n" + filler

        assertEquals(
            inlineMentions,
            selectTxtTocRule(listOf(inlineMentions, lineStartOnly), sample),
        )
    }

    @Test
    fun `equal match counts prefer the lower serial number`() {
        val lowSerial = rule(id = 1, chapterRule = "第[0-9]+章")
        val highSerial = rule(id = 2, chapterRule = "第[0-9]+章")
        val sample = sampleWithChapters(4)

        // 传入顺序即 serialNumber 升序；命中数相等时低序号优先
        assertEquals(lowSerial, selectTxtTocRule(listOf(lowSerial, highSerial), sample))
    }

    @Test
    fun `zero width matches are not counted as chapters`() {
        val lookahead = rule(id = 1, chapterRule = "(?=第)")
        val sample = sampleWithChapters(6)

        assertEquals(0, countChapterLikeMatches(Regex("(?=第)", RegexOption.MULTILINE), sample))
        assertNull(selectTxtTocRule(listOf(lookahead), sample))
    }

    @Test
    fun `chapter like counting skips matches inside the same paragraph`() {
        // 同一段里连续命中只算一处：章节之间总是隔着正文
        val sample = "第一章 标题\n第2章 标题\n第3章 标题\n" + filler

        assertEquals(1, countChapterLikeMatches(Regex("^第.章", RegexOption.MULTILINE), sample))
    }

    @Test
    fun `empty chapter rule is not selected`() {
        assertNull(
            selectTxtTocRule(
                rules = listOf(rule(id = 1, chapterRule = "")),
                sample = filler,
            )
        )
    }

    @Test
    fun `invalid pattern is skipped and reported`() {
        val broken = rule(id = 1, chapterRule = "(", name = "坏规则")
        val good = rule(id = 2, chapterRule = "^第[0-9]+章")
        val errors = mutableListOf<Pair<String, PatternSyntaxException>>()

        val selected = selectTxtTocRule(
            rules = listOf(broken, good),
            sample = sampleWithChapters(4),
            onInvalidPattern = { rule, error -> errors += rule.name to error },
        )

        assertSame(good, selected)
        assertEquals(listOf("坏规则"), errors.map { it.first })
        assertNotNull(errors.single().second)
    }

    @Test
    fun `no rules means no selection`() {
        assertNull(selectTxtTocRule(emptyList(), sampleWithChapters(3)))
    }

    private fun sampleWithChapters(count: Int) = buildString {
        append("简介：")
        repeat(count) { index ->
            append("\n第${index + 1}章 标题$index\n")
            append(filler)
        }
    }

    private fun rule(
        id: Long,
        chapterRule: String,
        name: String = "规则$id",
        serialNumber: Int = (id - 1).toInt(),
    ) = TxtTocRule(
        id = id,
        name = name,
        chapterRule = chapterRule,
        serialNumber = serialNumber,
    )
}
