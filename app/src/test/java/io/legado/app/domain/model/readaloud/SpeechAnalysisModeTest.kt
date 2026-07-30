package io.legado.app.domain.model.readaloud

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechAnalysisModeTest {
    @Test
    fun `unknown persisted value safely falls back to rules`() {
        assertEquals(SpeechAnalysisMode.Rule, SpeechAnalysisMode.fromStorage("future_mode"))
    }

    @Test
    fun `all modes round trip through storage value`() {
        SpeechAnalysisMode.entries.forEach { mode ->
            assertEquals(mode, SpeechAnalysisMode.fromStorage(mode.storageValue))
        }
    }
}
