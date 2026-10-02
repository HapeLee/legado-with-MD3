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
 * 正则角色的匹配范围。
 *
 * 划分方式（整句、按符号）会把一句台词切成多个朗读单元，而送进 [CastSpeechOverlay.apply] 的
 * 「段落列表」那时已经是切完的那一份：按单元或按这一份比，`［…］` 这种中间带句号的内容
 * 首尾落在两个单元里，永远凑不齐 → 整章 0 命中（短字面量不受影响，所以看着像「只有正则坏了」）。
 * 画布把单元按章内绝对坐标铺回去，命中才能跨单元裁进它经过的每一块。
 */
class CastSpeechOverlayCanvasTest {

    private val effects = listOf(
        RegexCastEffect("系统", Regex("［([^］]*)］"), voiceId = "v1"),
    )

    private fun item(at: Int, text: String) = SpeechPlanItem(
        segment = ChapterSpeechSegment(
            id = "seg-$at",
            analysisId = "a",
            bookUrl = "book",
            chapterIndex = 3,
            paragraphIndex = 0,
            start = 0,
            end = text.length,
            chapterPosition = at,
            text = text,
            roleType = SpeechRoleType.Narrator,
            source = SpeechResolutionSource.Rule,
        ),
        voice = null,
        fallbackVoices = emptyList(),
    )

    @Test
    fun `a bracket span split over two units is found on the canvas and voiced in both`() {
        val first = "　　［咯咯……说不定"
        val second = "有一天又会有叫的机会。］"
        val secondAt = first.length + 1
        val plan = listOf(item(0, first), item(secondAt, second))

        // 旧口径：只在本单元里比，两块都凑不齐首尾
        assertTrue(
            "按单元比居然命中了",
            RegexCastSplitter.matchesIn(CastMarkers.blank(first), effects).isEmpty() &&
                RegexCastSplitter.matchesIn(CastMarkers.blank(second), effects).isEmpty(),
        )

        val matches = RegexCastSplitter.matchesIn(CastSpeechOverlay.chapterCanvas(plan), effects)
        assertEquals(1, matches.size)

        val voiced = plan.map { unit ->
            RegexCastSplitter.split(
                unit.segment.chapterPosition,
                unit.segment.text,
                matches.mapNotNull {
                    it.ofPiece(unit.segment.chapterPosition, unit.segment.text.length)
                },
                effects,
            ).parts.mapNotNull { it.voiceId }
        }

        assertEquals(listOf(listOf("v1"), listOf("v1")), voiced)
    }

    @Test
    fun `canvas keeps every unit at its own chapter position`() {
        val first = "甲段"
        val second = "乙段"
        val chapter = listOf(
            CanonicalSpeechParagraph(0, first, 0),
            CanonicalSpeechParagraph(1, second, first.length + 1),
        )
        val canvas = CastSpeechOverlay.chapterCanvas(chapter.map { item(it.chapterPosition, it.text) })

        assertEquals("${first} ${second}", canvas)
    }

    @Test
    fun `an empty plan has no canvas to match on`() {
        assertEquals("", CastSpeechOverlay.chapterCanvas(emptyList()))
    }
}
