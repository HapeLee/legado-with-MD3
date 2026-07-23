package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PartialTranslationAssemblerTest {

    private val chunks = listOf(
        TextChunk(0, "original zero", emptyList()),
        TextChunk(1, "original one", emptyList()),
        TextChunk(2, "original two", emptyList()),
    )

    @Test
    fun `partial chunk replaces the whole current chunk`() {
        val result = PartialTranslationAssembler.assemble(
            originalChunks = chunks,
            translatedMap = mapOf(0 to "translated zero"),
            partialChunk = PartialTranslationAssembler.PartialChunkTranslation(1, "partial one"),
        )

        assertEquals("translated zero\n\npartial one\n\noriginal two", result)
    }

    @Test
    fun `empty partial keeps original chunk`() {
        val result = PartialTranslationAssembler.assemble(
            originalChunks = chunks,
            translatedMap = emptyMap(),
            partialChunk = PartialTranslationAssembler.PartialChunkTranslation(1, ""),
        )

        assertEquals("original zero\n\noriginal one\n\noriginal two", result)
    }
}
