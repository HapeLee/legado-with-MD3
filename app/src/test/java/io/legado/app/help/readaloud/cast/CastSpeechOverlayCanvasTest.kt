package io.legado.app.help.readaloud.cast

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
 * 划分方式（整句、按符号）会把一句台词切成多个朗读单元：按单元比的话 `［…］` 这种中间带句号
 * 的内容首尾落在两块里，永远凑不齐 → 整章 0 命中（短字面量整块落在同一句里不受影响，
 * 所以看着像「只有正则不生效」）。[CastSpeechOverlay.unitMatches] 按单元顺序把文字接起来比，
 * 只依赖先后，不依赖 segment.chapterPosition 那份绝对坐标。
 */
class CastSpeechOverlayCanvasTest {

    private val effects = listOf(
        RegexCastEffect("系统", Regex("［([^］]*)］"), voiceId = "v1"),
    )

    /** [at] 故意全部传同一个值：单元顺序才是这套匹配的依据，绝对坐标错了也不该影响命中。 */
    private fun item(at: Int, text: String) = SpeechPlanItem(
        segment = ChapterSpeechSegment(
            id = "seg-$text",
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
    fun `a bracket span split over two units is found and voiced in both`() {
        val first = "　　［咯咯……说不定"
        val second = "有一天又会有叫的机会。］"
        val plan = listOf(item(0, first), item(0, second))

        // 旧口径：只在本单元里比，两块都凑不齐首尾
        assertTrue(
            "按单元比居然命中了",
            RegexCastSplitter.matchesIn(CastMarkers.blank(first), effects).isEmpty() &&
                RegexCastSplitter.matchesIn(CastMarkers.blank(second), effects).isEmpty(),
        )

        val perUnit = CastSpeechOverlay.unitMatches(plan, effects)
        // 一次命中裁进它经过的两块
        assertEquals(2, perUnit.sumOf { it.size })

        val voiced = perUnit.mapIndexed { index, own ->
            RegexCastSplitter.split(
                0,
                plan[index].segment.text,
                own,
                effects,
            ).parts.mapNotNull { it.voiceId }
        }

        assertEquals(listOf(listOf("v1"), listOf("v1")), voiced)
    }

    @Test
    fun `每块拿到的下标是它自己文字里的偏移`() {
        val first = "旁白。［系统］提示"
        val plan = listOf(item(7, first))

        val own = CastSpeechOverlay.unitMatches(plan, effects).single()

        assertEquals(1, own.size)
        assertEquals(
            "［系统］",
            first.substring(own.single().start, own.single().end),
        )
    }

    @Test
    fun `没有规则或没有单元时不产生命中`() {
        // 每个单元一格，没有规则时那一格是空的
        assertEquals(
            listOf<List<RegexCastSplitter.Match>>(emptyList()),
            CastSpeechOverlay.unitMatches(listOf(item(0, "正文")), emptyList()),
        )
        assertEquals(
            listOf<List<RegexCastSplitter.Match>>(),
            CastSpeechOverlay.unitMatches(emptyList(), effects),
        )
    }
}
