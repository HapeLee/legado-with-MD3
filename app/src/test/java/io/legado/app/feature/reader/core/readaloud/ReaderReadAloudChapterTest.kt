package io.legado.app.feature.reader.core.readaloud

import io.legado.app.domain.model.readaloud.ContentSplitPolicies
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.feature.reader.core.cast.CastMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadAloudChapterTest {
    @Test
    fun buildsParagraphAndPageSplitViewsInCanvasPositionSpace() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 3,
            title = "第三章",
            semanticContent = "甲乙丙丁\n戊己\n",
            pageStarts = listOf(0, 2, 5),
        )

        assertEquals(
            listOf("甲乙丙丁", "戊己"),
            chapter.paragraphs(splitByPage = false).map { it.text })
        assertEquals(
            listOf("甲乙", "丙丁", "戊己"),
            chapter.paragraphs(splitByPage = true).map { it.text })
        assertEquals(
            listOf(false, true, true),
            chapter.paragraphs(splitByPage = true).map { it.isParagraphEnd },
        )
        assertEquals(
            listOf(0, 2, 5),
            chapter.paragraphs(splitByPage = true).map { it.chapterPosition })
        assertEquals(7, chapter.chapterLength)
        assertEquals(1, chapter.pageIndexAt(4))
        assertEquals(0, chapter.paragraphIndexAtOrAfter(4, splitByPage = false))
        assertEquals(ReadAloudContentSplitMode.Paragraph, chapter.contentSplitMode)
    }

    @Test
    fun canonicalSpeechParagraphsUseDisplayedTextPositions() {
        val chapter = ReaderReadAloudChapter.create(0, "", "袮甲\n乙\uFFFC\n", listOf(0))
        val paragraphs = chapter.canonicalSpeechParagraphs(splitByPage = false)
        assertEquals(" 甲", paragraphs[0].text)
        assertEquals("乙 ", paragraphs[1].text)
        assertEquals(3, paragraphs[1].chapterPosition)
    }

    @Test
    fun defaultSplitModeCreatesOneUnitPerParagraph() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "他来了。她走了！\n下一段。\n",
            pageStarts = listOf(0),
            contentSplitMode = ReadAloudContentSplitMode.Default,
        )

        // 「默认」= 整段：划分粒度停在段落，段内台词边界另由 CastSpeechOverlay 处理
        assertEquals(
            listOf("他来了。她走了！", "下一段。"),
            chapter.paragraphs(splitByPage = false).map { it.text },
        )
        assertEquals(
            listOf(0, 9),
            chapter.paragraphs(splitByPage = false).map { it.chapterPosition },
        )
        assertEquals(13, chapter.chapterLength)
        assertEquals(0, chapter.paragraphIndexAtOrAfter(1, splitByPage = false))
        assertEquals(0, chapter.paragraphIndexAtOrAfter(5, splitByPage = false))
    }

    @Test
    fun blankUnitsAreNotCursorLandings() {
        // 一个字符都没有的段（只剩缩进空格）念不出东西：不进朗读单元集合，
        // 游标落在它上面时交给后面第一个可念单元
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "第一段。\n　　\n第二段。\n",
            pageStarts = listOf(0),
        )

        assertEquals(
            listOf("第一段。", "　　", "第二段。"),
            chapter.paragraphs(splitByPage = false).map { it.text },
        )
        assertEquals(
            listOf("第一段。", "第二段。"),
            chapter.readAloudParagraphs(splitByPage = false).map { it.text },
        )
        assertEquals(0, chapter.paragraphIndexAtOrAfter(4, splitByPage = false))
        // 落在空白段（章内位置 5..6）上时，定位交给它后面第一个可念单元
        assertEquals(1, chapter.paragraphIndexAtOrAfter(6, splitByPage = false))
        assertEquals(1, chapter.paragraphIndexAtOrAfter(8, splitByPage = false))
    }

    @Test
    fun symbolOnlyUnitsAreNotSpeechLandings() {
        // 整段 `……`/`——` 送进 HTTP/云端引擎只会得到 TTS Generation Failed：合成失败改成
        // 显式报错后，这种段会让朗读直接暂停；留在集合里还会让「上一句」停在一个不会
        // 出声的单元上。判据必须和送引擎前的 readAloudSpeechText 同源。
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "第一段。\n……\n——\n第二段。\n",
            pageStarts = listOf(0),
        )

        assertEquals(
            listOf("第一段。", "……", "——", "第二段。"),
            chapter.paragraphs(splitByPage = false).map { it.text },
        )
        assertEquals(
            listOf("第一段。", "第二段。"),
            chapter.readAloudParagraphs(splitByPage = false).map { it.text },
        )
        // 送引擎的文本为空：文件合成侧据此落静音占位，不会拿纯符号去请求引擎
        assertEquals("", "……".readAloudSpeechText())
        assertEquals("", "——".readAloudSpeechText())
        assertEquals("第一段。", "第一段。".readAloudSpeechText())
        // 过滤后的集合就是「上一句/下一句」的落点：从「第二段。」退一格应直接到「第一段。」
        assertEquals(0, chapter.paragraphIndexAtOrAfter(4, splitByPage = false))
        assertEquals(1, chapter.paragraphIndexAtOrAfter(6, splitByPage = false))
        assertEquals(1, chapter.paragraphIndexAtOrAfter(9, splitByPage = false))
    }

    @Test
    fun markerOnlyUnitIsStillDropped() {
        // 整段只剩角色标记时 strip 之后是空串：只按「至少一个标点/符号」的旧正则会漏掉它，
        // 游标与引擎回调因此错开。补回纯符号判据时不能把这一条一起回退掉。
        val marker = requireNotNull(CastMarkers.markerText("甲", "旁白"))
        assertTrue(marker.isBlankReadAloudUnit())
        assertEquals("", marker.readAloudSpeechText())
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "第一段。\n$marker\n第二段。\n",
            pageStarts = listOf(0),
        )
        assertEquals(
            listOf("第一段。", "第二段。"),
            chapter.readAloudParagraphs(splitByPage = false).map { it.text },
        )
    }

    @Test
    fun chapterWithoutSpeakableUnitFallsBackToRawUnits() {
        // 整章都没有可念单元（空白页、纯符号页）时单元集合退回原始列表，
        // 由朗读服务的「读完本章」那一套收尾；这些段仍然不能去请求引擎，只能落静音占位。
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "　　\n……\n",
            pageStarts = listOf(0),
        )

        val raw = chapter.paragraphs(splitByPage = false).map { it.text }
        assertEquals(raw, chapter.readAloudParagraphs(splitByPage = false).map { it.text })
        assertEquals(listOf("", ""), raw.map { it.readAloudSpeechText() })
    }

    @Test
    fun explicitSymbolPolicyResplitsUnitsAtAbsolutePositions() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "甲乙，丙丁\n戊己\n",
            pageStarts = listOf(0),
        )
        val policy = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，"),
        )

        val units = chapter.paragraphs(splitByPage = false, policy = policy)

        assertEquals(listOf("甲乙，", "丙丁", "戊己"), units.map { it.text })
        assertEquals(listOf(0, 3, 6), units.map { it.chapterPosition })
        // 段内切出的子单元不是段末，只有每个原始段的最后一个单元是
        assertEquals(listOf(false, true, true), units.map { it.isParagraphEnd })
    }

    @Test
    fun symbolPolicyResplitsPageUnitsToo() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "甲乙，丙丁\n戊己\n",
            pageStarts = listOf(0, 3, 6),
        )
        val policy = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，"),
        )

        val units = chapter.paragraphs(splitByPage = true, policy = policy)

        assertEquals(listOf("甲乙，", "丙丁", "戊己"), units.map { it.text })
        assertEquals(listOf(0, 3, 6), units.map { it.chapterPosition })
    }
}
