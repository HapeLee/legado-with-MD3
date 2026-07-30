package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationResultPreviewParserTest {

    @Test
    fun `marker and text can span deltas`() {
        val parser = TranslationResultPreviewParser()
        val firstBatch = "甲".repeat(1_024)

        assertEquals(emptyList<String>(), parser.feed("[dictionary]\nJack -> 杰克\n[res"))
        assertEquals(emptyList<String>(), parser.feed("ult]${firstBatch.dropLast(1)}"))
        assertEquals(listOf(firstBatch), parser.feed("甲\n第二行"))
    }

    @Test
    fun `preview waits for the next newline after batch threshold`() {
        val parser = TranslationResultPreviewParser()

        assertEquals(emptyList<String>(), parser.feed("[result]${"一".repeat(1_024)}"))
        assertEquals(emptyList<String>(), parser.feed("二"))
        assertEquals(
            listOf("${"一".repeat(1_024)}二"),
            parser.feed("\n"),
        )
    }

    @Test
    fun `one delta can publish multiple cumulative batches`() {
        val parser = TranslationResultPreviewParser()
        val firstBatch = "一".repeat(1_024)
        val secondBatch = "二".repeat(1_024)

        assertEquals(
            listOf(firstBatch, "$firstBatch\n$secondBatch"),
            parser.feed("[RESULT]$firstBatch\r\n$secondBatch\n"),
        )
    }

    @Test
    fun `finish publishes final line without newline`() {
        val parser = TranslationResultPreviewParser()
        parser.feed("[result]\n最后一行")
        assertEquals(listOf("最后一行"), parser.finish())
    }
}
