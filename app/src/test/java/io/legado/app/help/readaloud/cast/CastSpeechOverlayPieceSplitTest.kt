package io.legado.app.help.readaloud.cast

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.feature.reader.core.cast.CastMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「整段/整页」划分下角色音的落地验证：一个朗读单元就是一整段，旁白和已分配台词混在
 * 里面，归属必须按台词边界切开。段落形状取真实章节里那种「台词—旁白—台词」同段。
 */
class CastSpeechOverlayPieceSplitTest {

    private val rawParagraphs = listOf(
        "　　本家建筑内最大的会合厅。",
        "　　“家主代理，这次事态可不能轻轻揭过。”",
        "　　比星菲更先开口的是李振富。",
        "　　“李振富元老，对于比武途中发生的不幸事故，我深表哀悼。”",
        "　　砰！",
        "　　“事故！你说事故？！”李振富元老猛地拍桌而起，“区区一个解决师出身，砍下了我儿子的脑袋。”",
    )

    /** ordinal → 角色标签，与 chapter_role_assignments 的形状一致。 */
    private val labels = mapOf(
        0 to CastMarkers.markerText("星菲", "少女音")!!,
        1 to CastMarkers.markerText("陆言", "男主音")!!,
        2 to CastMarkers.markerText("李振富", "老人音")!!,
        3 to CastMarkers.markerText("李振富", "老人音")!!,
    )

    /** 注入标记后的章内单元序列 + 量出来的台词范围，与朗读服务拿到的完全同源。 */
    private fun chapter(): List<CanonicalSpeechParagraph> {
        val tracker = CastMarkers.CastQuoteTracker()
        var position = 0
        return rawParagraphs.mapIndexed { index, raw ->
            val text = CastMarkers.injectParagraph(raw, tracker, labels)
            CanonicalSpeechParagraph(index, text, position)
                .also { position += text.length + 1 }
        }
    }

    private fun piecesOf(
        chapter: List<CanonicalSpeechParagraph>,
        unit: CanonicalSpeechParagraph,
    ): List<CastSpeechOverlay.Piece> {
        val spans = CastSpeechOverlay.spansOf(chapter)
        val item = SpeechPlanItem(
            segment = ChapterSpeechSegment(
                id = "seg-${unit.chapterPosition}",
                analysisId = "a",
                bookUrl = "book",
                chapterIndex = 3,
                paragraphIndex = unit.index,
                start = 0,
                end = unit.text.length,
                chapterPosition = unit.chapterPosition,
                text = unit.text,
                roleType = SpeechRoleType.Narrator,
                source = SpeechResolutionSource.Rule,
            ),
            voice = null,
            fallbackVoices = emptyList(),
        )
        return CastSpeechOverlay.piecesOf(item, spans)
    }

    @Test
    fun `同段台词加旁白在台词边界切开并标好说话人`() {
        val chapter = chapter()
        val unit = chapter.last()

        val pieces = piecesOf(chapter, unit)

        assertEquals(
            listOf("李振富", "", "李振富"),
            pieces.map { it.span?.name ?: "" },
        )
        // 只有段首那两个全角空格因空白被丢掉，其余一个字都不许多、不少、不乱序
        assertEquals(unit.text.substring(2), pieces.joinToString(separator = "") { it.text })
        assertEquals(unit.chapterPosition + unit.text.indexOf('“'), pieces.first().start)
        assertEquals(
            unit.chapterPosition + unit.text.length,
            pieces.last().let { it.start + it.text.length },
        )
    }

    @Test
    fun `整段里没有台词就不切`() {
        val chapter = chapter()
        val unit = chapter[2]

        val pieces = piecesOf(chapter, unit)

        assertEquals(1, pieces.size)
        assertEquals(null, pieces.single().span)
        assertEquals(unit.text, pieces.single().text)
    }

    @Test
    fun `台词整段自成一单元时仍是一块`() {
        val chapter = chapter()
        val unit = chapter[1]

        val pieces = piecesOf(chapter, unit)

        assertEquals(1, pieces.size)
        assertEquals("星菲", pieces.single().span?.name)
        assertEquals(unit.text.substring(2), pieces.single().text)
    }

    /**
     * 切分这一层不许吃掉标记：偏移按含标记的正文算，去掉标记是送进引擎前的最后一步。
     */
    @Test
    fun `切分保留标记`() {
        val chapter = chapter()
        val pieces = chapter.flatMap { piecesOf(chapter, it) }

        assertTrue(pieces.any { it.text.contains("<<") })
        chapter.forEach { unit ->
            val joined = piecesOf(chapter, unit).joinToString(separator = "") { it.text }
            // 纯空白的块会被丢掉（只在台词与旁白交界处），段首空白两边一起忽略
            assertEquals(
                CastMarkers.blank(unit.text).trimStart('　'),
                CastMarkers.blank(joined).trimStart('　'),
            )
        }
    }

    /**
     * 开「多角色朗读」时送给引擎的那一句必须是干净正文：不留 `<<…>>`，也不留等长空格
     * （等长空格只用于锚点对齐，送进引擎就成了正文里的怪空白）。
     */
    @Test
    fun `送进引擎前标记整段去掉`() {
        val marker = CastMarkers.markerText("李星菲", "女青年")!!
        val marked = "“" + marker + "你好我是某某某”"

        assertEquals("“你好我是某某某”", CastMarkers.strip(marked))
        assertEquals(marker.length, CastMarkers.blank(marked).length - "“你好我是某某某”".length)
    }

    /**
     * 段级变声器存在分配表里，靠章内开引号序号认领：给第 1、3 句设预设，只有那两句带得到。
     */
    @Test
    fun `段级变声器按引号序号认领`() {
        val chapter = chapter()
        val spans = CastSpeechOverlay.spansOf(chapter, mapOf(1 to "魔王", 3 to "机器人"))

        assertEquals(
            listOf("", "魔王", "", "机器人"),
            spans.sortedBy { it.ordinal }.map { it.effect },
        )
    }

    /**
     * 引擎回传的下标是「去掉标记之后」的那一份，必须能换回正文（含标记）的绝对下标，
     * 否则朗读进度、翻页判定和「从半句接着念」全都错位。
     */
    @Test
    fun `去标记后的下标换回正文下标`() {
        val unit = chapter()[5]
        val base = unit.chapterPosition
        val spoken = CastMarkers.stripKeepingOffsets(unit.text, base)

        assertEquals(unit.text.replace(Regex("<<[^>>]*>>"), ""), spoken.text)
        assertEquals(base, spoken.storedOf(0))
        assertEquals(base + unit.text.length, spoken.storedOf(spoken.text.length))
        // 逐字对照：spoken 的第 i 个字必须就是正文里 storedOf(i) 那个字
        spoken.text.forEachIndexed { index, ch ->
            assertEquals(ch, unit.text[spoken.storedOf(index) - base])
        }
    }
}
