package io.legado.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class AiImageModelsTest {
    @Test fun `image names are not mistaken for similarly named text models`() {
        listOf("gpt-image-2.5-sunburst", "gemini-3.1-flash-image", "gemini-nano-banana-2.1", "doubao-seedream-4-5-251128", "qwen-image-edit-plus", "grok-imagine-image-2.0").forEach {
            val capabilities = AiModelRegistry.inferCapabilities(it)
            assertTrue(it, AiCapability.IMAGE_EDITING in capabilities)
            assertFalse(it, AiCapability.TOOLS in capabilities)
        }
        assertFalse(AiCapability.IMAGE_EDITING in AiModelRegistry.inferCapabilities("gemini-3.1-pro"))
        assertFalse(AiCapability.IMAGE_EDITING in AiModelRegistry.inferCapabilities("gpt-5"))
        assertFalse(AiCapability.IMAGE_EDITING in AiModelRegistry.inferCapabilities("dall-e-3"))
    }
    @Test fun `old incorrectly inferred profiles are upgraded without changing text profiles`() {
        assertTrue(AiCapability.IMAGE_EDITING in AiImageModels.capabilities("gpt-image-1", "tools,vision,streaming"))
        assertEquals(setOf("tools", "vision"), AiImageModels.capabilities("custom-text", "tools,vision"))
        assertFalse(AiImageModels.isTextModel(AiProtocol.OPENAI_IMAGES, "custom-name", ""))
        assertFalse(AiImageModels.isTextModel(AiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-image-1", "vision"))
        assertTrue(AiImageModels.isTextModel(AiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-5", "vision"))
        assertTrue(AiImageModels.capabilities("gpt-image-1", AiImageModels.EXPLICIT_CAPABILITIES).isEmpty())
        assertFalse(AiImageModels.isTextModel(AiProtocol.OPENAI_CHAT_COMPLETIONS, "gpt-image-1", AiImageModels.EXPLICIT_CAPABILITIES))
    }
}
