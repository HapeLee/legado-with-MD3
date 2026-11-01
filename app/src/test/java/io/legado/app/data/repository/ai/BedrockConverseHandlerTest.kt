package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiToolCall
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Test
import okio.Buffer

class BedrockConverseHandlerTest {

    @Test
    fun systemMessagesAreDroppedAndSameRoleTurnsMerge() {
        val messages = listOf(
            AiMessage(AiMessageRole.SYSTEM, "be brief"),
            AiMessage(AiMessageRole.USER, "hi"),
            AiMessage(
                AiMessageRole.ASSISTANT,
                "",
                toolCalls = listOf(AiToolCall("call_1", "lookup", """{"q":"x"}""")),
            ),
            AiMessage(AiMessageRole.TOOL, "result 1", toolCallId = "call_1"),
            AiMessage(AiMessageRole.USER, "and then?"),
        ).toBedrockMessages()

        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"] })
        // The blank assistant text is skipped; only the toolUse block remains.
        assertEquals(1, (messages[1]["content"] as List<*>).size)
        // The tool result and the following user text share one user turn.
        assertEquals(2, (messages[2]["content"] as List<*>).size)
    }

    @Test
    fun parsesFoundationModelsAndInferenceProfiles() {
        val models = """
            {"modelSummaries":[
              {"modelId":"amazon.nova-lite-v1:0","modelName":"Nova Lite","providerName":"Amazon","outputModalities":["TEXT"]},
              {"modelId":"amazon.titan-image-generator-v2:0","modelName":"Titan Image","outputModalities":["IMAGE"]}
            ],
            "inferenceProfileSummaries":[
              {"inferenceProfileId":"us.anthropic.claude-sonnet-4-20250514-v1:0","inferenceProfileName":"US Claude Sonnet 4"}
            ]}
        """.trimIndent().toJsonObject().toBedrockModels()

        assertEquals(
            listOf("amazon.nova-lite-v1:0", "us.anthropic.claude-sonnet-4-20250514-v1:0"),
            models.map { it.id }
        )
        assertEquals("Amazon Nova Lite", models.first().name)
    }

    @Test
    fun decodesEventStreamFrames() {
        val source = Buffer()
            .write(frame("contentBlockDelta", """{"contentBlockIndex":0,"delta":{"text":"Hel"}}"""))
            .write(frame("contentBlockDelta", """{"contentBlockIndex":0,"delta":{"text":"lo"}}"""))
        val items = Channel<EventStreamItem>(Channel.UNLIMITED)

        pumpEventStream(source, items)

        val first = items.tryReceive().getOrThrow() as EventStreamItem.Message
        val second = items.tryReceive().getOrThrow() as EventStreamItem.Message
        assertEquals("event", first.headers[":message-type"])
        assertEquals("contentBlockDelta", first.headers[":event-type"])
        assertEquals("""{"contentBlockIndex":0,"delta":{"text":"lo"}}""", second.payload)
    }

    /** Builds one event stream frame with zeroed CRCs (the reader does not verify them). */
    private fun frame(eventType: String, payload: String): ByteArray {
        val headers = Buffer()
        listOf(":message-type" to "event", ":event-type" to eventType).forEach { (name, value) ->
            headers.writeByte(name.length).writeUtf8(name)
            headers.writeByte(7).writeShort(value.length).writeUtf8(value)
        }
        val headerBytes = headers.readByteArray()
        val payloadBytes = payload.toByteArray()
        return Buffer()
            .writeInt(12 + headerBytes.size + payloadBytes.size + 4)
            .writeInt(headerBytes.size)
            .writeInt(0)
            .write(headerBytes)
            .write(payloadBytes)
            .writeInt(0)
            .readByteArray()
    }
}
