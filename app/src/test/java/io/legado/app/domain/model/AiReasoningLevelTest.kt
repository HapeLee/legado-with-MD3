package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiReasoningLevelTest {

    @Test
    fun deepSeekUsesTheStandardMediumEffort() {
        val provider = AiProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.deepseek.com",
            apiKey = "test"
        )

        assertEquals("medium", AiReasoningLevel.MEDIUM.effortFor(provider))
    }

    @Test
    fun storageValueRoundTripsForEveryLevel() {
        AiReasoningLevel.entries.forEach { level ->
            assertEquals(level, AiReasoningLevel.fromStorage(level.storageValue))
        }
    }

    @Test
    fun unknownStorageValuesFallBackToTheGivenDefault() {
        assertEquals(AiReasoningLevel.OFF, AiReasoningLevel.fromStorage("", AiReasoningLevel.OFF))
        assertEquals(
            AiReasoningLevel.OFF,
            AiReasoningLevel.fromStorage("medium ", AiReasoningLevel.OFF),
        )
    }
}
