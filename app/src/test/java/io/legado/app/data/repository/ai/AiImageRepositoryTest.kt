package io.legado.app.data.repository.ai

import com.google.gson.JsonParser
import io.legado.app.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class AiImageRepositoryTest {
    private val input = AiImageInput("image/png", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
    private fun request(protocol: String, edit: Boolean = true) = AiImageRequest(
        AiModelConfig("model", AiProviderConfig("provider", "Test", protocol, "https://provider.invalid/v1", "first,second"),
            "Image", "custom-image", capabilities = AiImageModels.outputCapabilities), "translate", if (edit) input else null)
    private fun body(request: okhttp3.Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test fun `native edit protocols use different endpoints payloads and authentication`() {
        val openAi = imageWireRequest(request(AiProtocol.OPENAI_IMAGES))
        assertTrue(openAi.body!!.contentType().toString().startsWith("multipart/form-data"))
        assertTrue(body(openAi).contains("name=\"image\""))
        assertEquals("Bearer first", openAi.header("Authorization"))
        val gemini = imageWireRequest(request(AiProtocol.GEMINI_IMAGES))
        assertTrue(gemini.url.encodedPath.endsWith("/interactions"))
        assertEquals("first", gemini.header("x-goog-api-key")); assertNull(gemini.header("Authorization"))
        val geminiJson = JsonParser.parseString(body(gemini)).asJsonObject
        assertFalse(geminiJson["store"].asBoolean)
        assertEquals(input.base64Data, geminiJson.getAsJsonArray("input")[1].asJsonObject["data"].asString)
        val legacy = imageWireRequest(request(AiProtocol.GEMINI_IMAGES_LEGACY))
        assertTrue(legacy.url.encodedPath.endsWith(":generateContent"))
        assertTrue(body(legacy).contains("inlineData"))
        val seedream = imageWireRequest(request(AiProtocol.SEEDREAM_IMAGES))
        assertTrue(seedream.url.encodedPath.endsWith("/images/generations"))
        assertEquals(input.dataUrl, JsonParser.parseString(body(seedream)).asJsonObject["image"].asString)
        val qwen = imageWireRequest(request(AiProtocol.DASHSCOPE_IMAGES))
        assertTrue(qwen.url.encodedPath.endsWith("/multimodal-generation/generation"))
        assertFalse(JsonParser.parseString(body(qwen)).asJsonObject.getAsJsonObject("parameters")["prompt_extend"].asBoolean)
        val xai = imageWireRequest(request(AiProtocol.XAI_IMAGES))
        assertEquals("application/json; charset=utf-8", xai.body!!.contentType().toString())
        assertEquals(input.dataUrl, JsonParser.parseString(body(xai)).asJsonObject.getAsJsonObject("image")["url"].asString)
        val silicon = imageWireRequest(request(AiProtocol.SILICONFLOW_IMAGES))
        val siliconJson = JsonParser.parseString(body(silicon)).asJsonObject
        assertEquals(input.dataUrl, siliconJson["image"].asString)
        assertFalse(siliconJson.has("image_size"))
        assertNull(silicon.header("X-Enable-Watermark"))
    }

    @Test fun `generation does not send a source image and uses generation capabilities`() {
        AiProtocol.imageProtocols.forEach { protocol ->
            val wire = imageWireRequest(request(protocol, edit = false))
            assertFalse(body(wire).contains(input.base64Data))
            assertFalse(wire.url.encodedPath.endsWith("/images/edits"))
        }
    }

    @Test fun `vision alone never authorizes image output and unsafe paths are rejected`() {
        val base = request(AiProtocol.OPENAI_IMAGES)
        assertThrows(IllegalArgumentException::class.java) { imageWireRequest(base.copy(model = base.model.copy(capabilities = setOf(AiCapability.VISION)))) }
        assertThrows(IllegalArgumentException::class.java) { imageWireRequest(base.copy(endpointPath = "//other.invalid/images")) }
        assertThrows(IllegalArgumentException::class.java) { imageWireRequest(base.copy(endpointPath = "/../images")) }
    }

    @Test fun `final Gemini image is selected without returning thought images or text`() {
        val interactions = JsonParser.parseString("""{"status":"completed","steps":[{"type":"thought","content":[{"type":"image","data":"wrong"}]},{"type":"model_output","content":[{"type":"text","text":"done"},{"type":"image","mime_type":"image/png","data":"AQID"}]}]}""").asJsonObject
        assertEquals("AQID", imagePayload(interactions, AiProtocol.GEMINI_IMAGES).base64)
        val legacy = JsonParser.parseString("""{"candidates":[{"content":{"parts":[{"thought":true,"inlineData":{"mimeType":"image/png","data":"wrong"}},{"inlineData":{"mimeType":"image/png","data":"AQID"}}]}}]}""").asJsonObject
        assertEquals("AQID", imagePayload(legacy, AiProtocol.GEMINI_IMAGES_LEGACY).base64)
        assertThrows(IllegalStateException::class.java) { imagePayload(JsonParser.parseString("""{"status":"failed"}""").asJsonObject, AiProtocol.GEMINI_IMAGES) }
    }

    @Test fun `DashScope parses nested image content`() {
        val root = JsonParser.parseString("""{"output":{"choices":[{"message":{"content":[{"image":"https://generated.invalid/page.png"}]}}]}}""").asJsonObject
        assertEquals("https://generated.invalid/page.png", imagePayload(root, AiProtocol.DASHSCOPE_IMAGES).url)
        val silicon = JsonParser.parseString("""{"images":[{"url":"https://generated.invalid/page.png"}]}""").asJsonObject
        assertEquals("https://generated.invalid/page.png", imagePayload(silicon, AiProtocol.SILICONFLOW_IMAGES).url)
    }

    @Test fun `generated image download never receives provider credentials or custom headers`() = runBlocking {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val result = if (count.getAndIncrement() == 0) {
                assertEquals("Bearer first", request.header("Authorization"))
                assertEquals("private", request.header("x-private"))
                """{"data":[{"url":"https://generated.invalid/result.png"}]}""".toByteArray()
            } else {
                assertEquals("generated.invalid", request.url.host)
                assertNull(request.header("Authorization")); assertNull(request.header("x-private"))
                byteArrayOf(1, 2, 3)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(result.toResponseBody()).build()
        }.build()
        try {
            val source = request(AiProtocol.XAI_IMAGES)
            val response = AiImageRepository(client).generate(source.copy(model = source.model.copy(provider = source.model.provider.copy(customHeaders = mapOf("x-private" to "private")))))
            assertArrayEquals(byteArrayOf(1, 2, 3), response.bytes)
            assertEquals(2, count.get())
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun `failed paid request is attempted only once`() = runBlocking {
        val count = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("Limited").body("{}".toResponseBody()).build()
        }.build()
        try {
            try { AiImageRepository(client).generate(request(AiProtocol.OPENAI_IMAGES)); fail("Expected failure") }
            catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("429")) }
            assertEquals(1, count.get())
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
}
