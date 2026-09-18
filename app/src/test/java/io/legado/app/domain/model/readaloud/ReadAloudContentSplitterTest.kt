package io.legado.app.domain.model.readaloud

import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudContentSplitterTest {

    @Test
    fun `paragraph mode keeps whole lines untouched`() {
        val units = ReadAloudContentSplitter.splitLines(
            semanticContent = "甲乙丙丁\n戊己\n",
            policy = ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Paragraph),
        )

        assertEquals(listOf("甲乙丙丁", "戊己"), units.map { it.text })
        assertEquals(listOf(0, 5), units.map { it.chapterPosition })
    }

    @Test
    fun `default mode splits after sentence-ending punctuation`() {
        val units = ReadAloudContentSplitter.splitLines(
            semanticContent = "他来了。她走了！你呢？\n下一段",
            policy = ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Default),
        )

        assertEquals(listOf("他来了。", "她走了！", "你呢？", "下一段"), units.map { it.text })
        assertEquals(listOf(0, 4, 8, 12), units.map { it.chapterPosition })
    }

    @Test
    fun `punctuation is kept at the end of the unit and no text is lost`() {
        val content = "甲乙，丙丁、戊己。庚辛；壬癸"
        val units = ReadAloudContentSplitter.splitLines(
            semanticContent = content,
            policy = ContentSplitPolicies.forMode(
                ReadAloudContentSplitMode.Symbols,
                listOf("，", "、", "。", "；"),
            ),
        )

        assertEquals(listOf("甲乙，", "丙丁、", "戊己。", "庚辛；", "壬癸"), units.map { it.text })
        assertEquals(content, units.joinToString("") { it.text })
    }

    @Test
    fun `consecutive punctuation stays in one unit`() {
        val units = ReadAloudContentSplitter.splitLines(
            semanticContent = "什么？！真的吗",
            policy = ContentSplitPolicies.forMode(
                ReadAloudContentSplitMode.Symbols,
                listOf("？", "！"),
            ),
        )

        assertEquals(listOf("什么？！", "真的吗"), units.map { it.text })
    }

    @Test
    fun `empty stored symbols fall back to sentence ends`() {
        val policy = ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Symbols, emptyList())

        assertEquals(ReadAloudSplitSymbol.sentenceEnds, policy.symbols)
    }

    @Test
    fun `unknown stored symbols are ignored`() {
        val policy = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，", "x", ""),
        )

        assertEquals(setOf('，'), policy.symbols)
    }

    @Test
    fun `role splits are disabled for paragraph and page modes`() {
        assertFalse(ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Paragraph).allowRoleSplits)
        assertFalse(ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Page).allowRoleSplits)
        assertTrue(ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Default).allowRoleSplits)
    }

    @Test
    fun `default resolves to paragraph when multi-speaker is off`() {
        assertEquals(
            ReadAloudContentSplitMode.Paragraph,
            ContentSplitPolicies.resolve(
                ReadAloudContentSplitMode.Default,
                useMultiSpeaker = false
            ),
        )
        assertEquals(
            ReadAloudContentSplitMode.Default,
            ContentSplitPolicies.resolve(ReadAloudContentSplitMode.Default, useMultiSpeaker = true),
        )
    }

    @Test
    fun `explicit modes ignore the multi-speaker switch`() {
        listOf(
            ReadAloudContentSplitMode.Paragraph,
            ReadAloudContentSplitMode.Page,
            ReadAloudContentSplitMode.Symbols,
        ).forEach { explicit ->
            assertEquals(explicit, ContentSplitPolicies.resolve(explicit, useMultiSpeaker = false))
            assertEquals(explicit, ContentSplitPolicies.resolve(explicit, useMultiSpeaker = true))
        }
    }

    @Test
    fun `multi-speaker off keeps a whole paragraph as one unit`() {
        val content = "张三停下脚步。“你终于来了！”他看向门口。\n第二段。\n"
        val policy = ContentSplitPolicies.forMode(
            ContentSplitPolicies.resolve(
                ReadAloudContentSplitMode.Default,
                useMultiSpeaker = false,
            )
        )

        val units = ReadAloudContentSplitter.splitLines(content, policy)

        assertEquals(
            listOf("张三停下脚步。“你终于来了！”他看向门口。", "第二段。"),
            units.map { it.text },
        )
        assertFalse(policy.allowRoleSplits)
    }

    @Test
    fun `policy identifier changes with mode and symbols so caches invalidate`() {
        val paragraph = ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Paragraph)
        val symbols = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，"),
        )

        assertTrue(paragraph.identifier != symbols.identifier)
        assertTrue(
            symbols.identifier !=
                    ContentSplitPolicies.forMode(ReadAloudContentSplitMode.Symbols, listOf("。"))
                        .identifier
        )
    }

    @Test
    fun `setting codec round-trips mode and symbols`() {
        val encoded = ReadAloudContentSplitSetting.encode(
            mode = ReadAloudContentSplitMode.Symbols,
            symbols = listOf('，', '。'),
        )

        assertEquals("symbols|。，", encoded)
        assertEquals(
            ReadAloudContentSplitMode.Symbols to setOf('。', '，'),
            ReadAloudContentSplitSetting.decode(encoded),
        )
    }

    @Test
    fun `setting codec tolerates mode-only payload`() {
        assertEquals(
            ReadAloudContentSplitMode.Paragraph to emptySet<Char>(),
            ReadAloudContentSplitSetting.decode(ReadAloudContentSplitMode.Paragraph.storageValue),
        )
        assertEquals(
            ReadAloudContentSplitMode.Default to emptySet<Char>(),
            ReadAloudContentSplitSetting.decode("不认识的取值"),
        )
    }
}
