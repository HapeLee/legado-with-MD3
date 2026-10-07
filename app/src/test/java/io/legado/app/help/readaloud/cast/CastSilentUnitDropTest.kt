package io.legado.app.help.readaloud.cast

import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「念不出东西」的朗读单元不进队列。
 *
 * 这种单元送进引擎只会得到一段无声音频（`res/raw/silent_sound.mp3` 长 1224 毫秒），
 * 听感就是段与段之间、以及正则命中前凭空顿一下。这里锁三件事：空白段被丢掉、
 * 它身上挂的音效不丢（并给下一个单元）、章末那种并回最后一个。
 */
class CastSilentUnitDropTest {

    private fun item(text: String, sound: String = "") = SpeechPlanItem(
        segment = ChapterSpeechSegment(
            id = "seg-$text",
            analysisId = "an",
            bookUrl = "book",
            chapterIndex = 0,
            paragraphIndex = 0,
            start = 0,
            end = text.length,
            chapterPosition = 0,
            text = text,
            roleType = SpeechRoleType.Narrator,
            source = SpeechResolutionSource.Rule,
        ),
        voice = null,
        fallbackVoices = emptyList(),
        soundEffect = sound,
    )

    @Test
    fun `只剩空白的单元被丢掉`() {
        val (kept, dropped) = CastSpeechOverlay.dropSilentUnits(
            listOf(item("　　"), item("正文一句"), item(" "))
        )
        // 全角空格与半角空格都念不出东西，两个都要掉
        assertEquals(2, dropped)
        assertEquals(listOf("正文一句"), kept.map { it.segment.text })
    }

    @Test
    fun `空白单元身上的音效并给下一个单元`() {
        val (kept, dropped) = CastSpeechOverlay.dropSilentUnits(
            listOf(item("　　", "/boom.mp3"), item("正文一句"))
        )
        assertEquals(1, dropped)
        assertEquals("/boom.mp3", kept.single().soundEffect)
    }

    @Test
    fun `章末只剩空白加音效时并回最后一个单元`() {
        val (kept, dropped) = CastSpeechOverlay.dropSilentUnits(
            listOf(item("正文一句"), item("　　", "/boom.mp3"))
        )
        assertEquals(1, dropped)
        assertEquals(1, kept.size)
        assertEquals("/boom.mp3", kept.single().soundEffect)
    }

    @Test
    fun `两个单元都有音效时各留各的`() {
        val (kept, dropped) = CastSpeechOverlay.dropSilentUnits(
            listOf(item("第一句", "/a.mp3"), item("第二句", "/b.mp3"))
        )
        assertEquals(0, dropped)
        assertEquals(listOf("/a.mp3", "/b.mp3"), kept.map { it.soundEffect })
    }
}
