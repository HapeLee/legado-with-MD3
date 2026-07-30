package io.legado.app.help.readaloud.resolve

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.SpeakerCharacter
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCharacterSpeakerResolverTest {

    @Test
    fun `resolves explicit speaker before quote`() {
        val paragraph = paragraph("张三说：“你好！”")
        val result = resolve(
            paragraph = paragraph,
            segment = dialogue(paragraph, "“你好！”"),
            characters = listOf(character("zhang", "张三")),
        )

        assertEquals("zhang", result.characterId)
        assertEquals("张三", result.characterName)
        assertEquals(SpeechResolutionSource.Local, result.source)
    }

    @Test
    fun `resolves alias after quote`() {
        val paragraph = paragraph("“别动！”老李喝道。")
        val result = resolve(
            paragraph = paragraph,
            segment = dialogue(paragraph, "“别动！”"),
            characters = listOf(character("li", "李四", listOf("老李"))),
        )

        assertEquals("li", result.characterId)
    }

    @Test
    fun `keeps ambiguous alias unresolved`() {
        val paragraph = paragraph("阿青说：“走吧！”")
        val result = resolve(
            paragraph = paragraph,
            segment = dialogue(paragraph, "“走吧！”"),
            characters = listOf(
                character("a", "张青", listOf("阿青")),
                character("b", "李青", listOf("阿青")),
            ),
        )

        assertNull(result.characterId)
    }

    @Test
    fun `does not match alias embedded in longer name`() {
        val paragraph = paragraph("王小明说：“你好！”")
        val result = resolve(
            paragraph = paragraph,
            segment = dialogue(paragraph, "“你好！”"),
            characters = listOf(character("ming", "小明")),
        )

        assertNull(result.characterId)
    }

    private fun resolve(
        paragraph: CanonicalSpeechParagraph,
        segment: ChapterSpeechSegment,
        characters: List<SpeakerCharacter>,
    ): ChapterSpeechSegment = LocalCharacterSpeakerResolver.resolve(
        paragraphs = listOf(paragraph),
        segments = listOf(segment),
        characters = characters,
    ).single()

    private fun paragraph(text: String) = CanonicalSpeechParagraph(0, text, 0)

    private fun dialogue(
        paragraph: CanonicalSpeechParagraph,
        text: String,
    ): ChapterSpeechSegment {
        val start = paragraph.text.indexOf(text)
        return ChapterSpeechSegment(
            id = "segment",
            analysisId = "analysis",
            bookUrl = "book",
            chapterIndex = 0,
            paragraphIndex = paragraph.index,
            start = start,
            end = start + text.length,
            chapterPosition = start,
            text = text,
            roleType = SpeechRoleType.Character,
            source = SpeechResolutionSource.Rule,
        )
    }

    private fun character(
        id: String,
        name: String,
        aliases: List<String> = emptyList(),
    ) = SpeakerCharacter(id = id, name = name, aliases = aliases)
}
