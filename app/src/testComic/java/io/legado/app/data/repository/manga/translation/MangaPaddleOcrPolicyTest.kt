package io.legado.app.data.repository.manga.translation

import org.junit.Assert.*
import io.legado.app.domain.model.manga.MangaLocalOcrEngine as Engine
import org.junit.Test

class MangaPaddleOcrPolicyTest {
    @Test fun `specialized model dictionaries and language capabilities are not assumed universal`() {
        assertEquals(listOf("П", "р", "и"), mangaPaddleDictionary(listOf("  character_dict:", "  - П", "  - р", "  - и")))
        assertTrue(runCatching { mangaPaddleDictionary(listOf("  character_dict:")) }.isFailure)
        assertTrue(MangaPaddleModelCatalog.supports(Engine.PADDLE_V5_KOREAN, "ko"))
        assertFalse(MangaPaddleModelCatalog.supports(Engine.PADDLE_V5_KOREAN, "ja"))
        assertTrue(MangaPaddleModelCatalog.supports(Engine.PADDLE_V5_ESLAV, "uk"))
        assertFalse(MangaPaddleModelCatalog.supports(Engine.PADDLE_V6_TINY, "ja"))
    }
    @Test fun `ctc collapses repetitions keeps blanks and decodes the extra space class`() {
        val keys = listOf("你", "好")
        fun step(index: Int) = FloatArray(4) { if (it == index) .95f else .01f }
        assertEquals("你你好 好", mangaPaddleCtc(arrayOf(1, 1, 0, 1, 2, 3, 2).map(::step).toTypedArray(), keys))
        assertEquals("", mangaPaddleCtc(arrayOf(floatArrayOf(.2f, .3f, .2f, .3f)), keys))
        assertTrue(runCatching { mangaPaddleCtc(arrayOf(FloatArray(5)), keys) }.isFailure)
    }

    @Test fun `pinned scalar dictionary preserves punctuation spaces and emoji`() {
        val keys = mangaPaddleDictionary(listOf("PostProcess:", "  character_dict:", "  - ''''", "  - '\\'", "  - ' '", "  - 🕧") +
            (1..1001).map { "  - 字$it" })
        assertEquals(listOf("'", "\\", " ", "🕧"), keys.take(4))
    }
}
