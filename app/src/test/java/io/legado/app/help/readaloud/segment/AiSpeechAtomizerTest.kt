package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSpeechAtomizerTest {
    @Test
    fun `atoms preserve the complete paragraph without overlap`() {
        val text = "张三说：“你好！”李四点头。"
        val atoms = AiSpeechAtomizer.atomize(CanonicalSpeechParagraph(3, text, 120))

        assertEquals(text, atoms.joinToString("") { it.text })
        assertEquals(0, atoms.first().start)
        assertEquals(text.length, atoms.last().end)
        assertTrue(atoms.zipWithNext().all { (first, second) -> first.end == second.start })
        assertTrue(atoms.all { it.id.startsWith("p3:") })
    }

    @Test
    fun `opening quote creates a boundary for attribution and dialogue`() {
        val atoms = AiSpeechAtomizer.atomize(
            CanonicalSpeechParagraph(0, "张三低声说：“别出声。”", 0)
        )

        assertEquals("张三低声说：", atoms.first().text)
        assertEquals("“别出声。”", atoms.drop(1).joinToString("") { it.text })
    }

    @Test
    fun `straight quotes split dialogue but apostrophes inside words do not`() {
        val text = "Dean said: \"Don't move!\""
        val atoms = AiSpeechAtomizer.atomize(CanonicalSpeechParagraph(1, text, 0))

        assertEquals(text, atoms.joinToString("") { it.text })
        assertTrue(atoms.none { it.text == "'" })
        assertEquals("Dean said: ", atoms.first().text)
    }
}
