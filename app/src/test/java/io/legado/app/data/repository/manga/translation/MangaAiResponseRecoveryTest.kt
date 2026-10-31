package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test

class MangaAiResponseRecoveryTest {
    private val a = MangaTranslatedRegion("a0", "source", "Hello world", MangaTextBox(10f, 10f, 50f, 50f),
        typesetting = MangaAiTypesetting(listOf("Hello", "world"), 12f))
    private val b = a.copy(id = "a1", original = "second", translation = "你好", typesetting = MangaAiTypesetting(listOf("你好"), 12f))
    private val partial = """{"regions":[{"id":"a0","original":"source","translation":"Hello world"}]}"""

    @Test fun `text only correction locks already completed translations`() {
        val response = """{"translations":[{"id":"a0","translation":"Hello world"}]}"""
        validateMangaAiResponseCorrection(response, MangaTranslationPlan(listOf(a, b)), setOf("a0", "a1"))
        assertThrows(IllegalArgumentException::class.java) {
            validateMangaAiResponseCorrection(response, MangaTranslationPlan(listOf(a.copy(translation = "Bye", typesetting = null), b)), setOf("a0", "a1"))
        }
    }

    @Test fun `completing missing style or candidate retains all completed text`() {
        validateMangaAiResponseCorrection(partial, MangaTranslationPlan(listOf(a, b)), setOf("a0", "a1"))
        for (changed in listOf(a.copy(original = "rewritten"), a.copy(translation = "Bye", typesetting = null),
            a.copy(typesetting = a.typesetting!!.copy(lines = listOf("Hel", "lo world"))))) {
            assertThrows(IllegalArgumentException::class.java) {
                validateMangaAiResponseCorrection(partial, MangaTranslationPlan(listOf(changed, b)), setOf("a0", "a1"))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { validateMangaAiResponseCorrection(partial, MangaTranslationPlan(listOf(b))) }
    }

    @Test fun `model can repair an unknown ID only with one unambiguous unchanged source match`() {
        val unknown = partial.replace("a0", "r0")
        validateMangaAiResponseCorrection(unknown, MangaTranslationPlan(listOf(a, b)), setOf("a0", "a1"))
        assertThrows(IllegalArgumentException::class.java) {
            validateMangaAiResponseCorrection(unknown, MangaTranslationPlan(listOf(a, a.copy(id = "a2"))), setOf("a0", "a2"))
        }
    }

    @Test fun `repair cannot reverse observed source order or change a no text result`() {
        val before = """{"regions":[{"id":"a0","original":"source"},{"id":"a1","original":"second"}]}"""
        assertThrows(IllegalArgumentException::class.java) { validateMangaAiResponseCorrection(before, MangaTranslationPlan(listOf(b, a))) }
        assertThrows(IllegalArgumentException::class.java) {
            validateMangaAiResponseCorrection("""{"regions":[],"noText":true}""", MangaTranslationPlan(listOf(a)))
        }
    }

    @Test fun `line breaks can change without changing source words or translated characters`() {
        val before = """{"regions":[{"id":"a0","original":"source\nwords","typesetting":{"lines":"Hello\r\nworld"}}]}"""
        validateMangaAiResponseCorrection(before, MangaTranslationPlan(listOf(a.copy(original = "source words"))))
    }
}
