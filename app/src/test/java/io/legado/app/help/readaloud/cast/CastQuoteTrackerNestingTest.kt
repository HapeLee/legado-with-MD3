package io.legado.app.help.readaloud.cast

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.feature.reader.core.cast.CastMarkers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 引号配对的三条硬账，全都直接决定正文里胶囊的个数与位置：
 * 1) 一句话里的嵌套引号不是新的说话人 —— 不发胶囊，也不占序号；
 * 2) 嵌套引号仍要入栈，否则它那句的闭引号会跑去关外层的引号；
 * 3) 内层引号等不到自己的闭号时（台词里混进的英文撇号）必须由外层闭号收口，
 *    否则章级共享的栈永久满着，整章只剩下第一条胶囊。
 */
class CastQuoteTrackerNestingTest {

    private fun anchorsOf(vararg paragraphs: String): List<Int> {
        val tracker = CastMarkers.CastQuoteTracker()
        val hits = ArrayList<Int>()
        paragraphs.forEach { paragraph ->
            paragraph.forEach { ch -> if (tracker.feed(ch)) hits += tracker.lastCastOrdinal }
        }
        return hits
    }

    @Test
    fun `一句话里的嵌套引号既不发胶囊也不占序号`() {
        val speech = "“你好，我是‘处刑人’，怎么说呢，你可真是让我“心潮澎湃”啊！”"

        assertEquals(listOf(0), anchorsOf(speech))
        assertEquals(listOf(0, 1), anchorsOf(speech, "“第二句。”"))
    }

    @Test
    fun `嵌套引号自己那句的闭号不会误关外层`() {
        val speech = "“他说「别人讲过」之后就走。”"
        val tracker = CastMarkers.CastQuoteTracker()

        speech.forEach { ch -> tracker.feed(ch) }

        // 收口的必须是整句那条锚点，区间覆盖到句末闭引号
        assertEquals(0, tracker.lastClosedSpan?.ordinal)
        assertEquals(speech.length, tracker.lastClosedSpan?.endExclusive)
    }

    @Test
    fun `台词里的英文撇号不再把整章钉死`() {
        val withEnglish = "“It's me. A.K.A.杀手小王。”"
        val next = "“下一句台词。”"

        assertEquals(listOf(0, 1), anchorsOf(withEnglish, next))
    }

    @Test
    fun `标记只落在一级开引号后，台词范围在自己的闭引号处收口`() {
        val labels = mapOf(
            0 to CastMarkers.markerText("小王", "男青年")!!,
            1 to CastMarkers.markerText("小李", "女青年")!!,
        )
        val tracker = CastMarkers.CastQuoteTracker()
        var position = 0
        val chapter = listOf("“It's me. A.K.A.杀手小王。”", "“下一句台词。”")
            .mapIndexed { index, raw ->
                val text = CastMarkers.injectParagraph(raw, tracker, labels)
                CanonicalSpeechParagraph(index, text, position)
                    .also { position += text.length + 1 }
            }
        val spans = CastSpeechOverlay.spansOf(chapter).sortedBy { it.start }

        assertEquals(2, chapter.flatMap { CastMarkers.findMarkers(it.text) }.size)
        assertEquals(listOf("小王", "小李"), spans.map { it.name })
        // 每句都在自己的闭引号处结束：钉死的栈会让范围一路吃到章末，把旁白也吞进台词
        assertEquals(chapter[0].chapterPosition + chapter[0].text.length, spans.first().end)
        assertEquals(chapter[1].chapterPosition + chapter[1].text.length, spans.last().end)
    }
}
