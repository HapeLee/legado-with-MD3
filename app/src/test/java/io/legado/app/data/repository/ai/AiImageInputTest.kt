package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiImageInput
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import org.junit.Assert.*
import org.junit.Test

class AiImageInputTest {
    private val image = AiImageInput("image/png", "aGVsbG8=")
    private val messages = listOf(AiMessage(AiMessageRole.USER, "Translate", images = listOf(image)))

    @Test fun `text-only request serialization remains unchanged`() {
        val plain = listOf(AiMessage(AiMessageRole.USER, "hello"))
        assertEquals("hello", plain.toOpenAiChatMessages().single()["content"])
        assertEquals("hello", plain.toOpenAiResponsesInput().single()["content"])
        assertEquals("hello", plain.toAnthropicMessages().single()["content"])
    }

    @Test fun `chat and responses encode inline image blocks with their own schema`() {
        val chat = messages.toOpenAiChatMessages().single()["content"] as List<*>
        assertEquals("text", (chat[0] as Map<*, *>)["type"])
        assertEquals(image.dataUrl, ((chat[1] as Map<*, *>)["image_url"] as Map<*, *>)["url"])
        val responses = messages.toOpenAiResponsesInput().single()["content"] as List<*>
        assertEquals("input_image", (responses[1] as Map<*, *>)["type"])
        assertEquals(image.dataUrl, (responses[1] as Map<*, *>)["image_url"])
    }

    @Test fun `anthropic encodes a media type and base64 source without a data-url prefix`() {
        val content = messages.toAnthropicMessages().single()["content"] as List<*>
        val source = (content[1] as Map<*, *>)["source"] as Map<*, *>
        assertEquals("image/png", source["media_type"])
        assertEquals(image.base64Data, source["data"])
        assertFalse(image.toString().contains(image.base64Data))
    }
}
