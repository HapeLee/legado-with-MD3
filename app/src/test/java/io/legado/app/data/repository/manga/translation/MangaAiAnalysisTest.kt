package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.gateway.*
import io.legado.app.domain.model.*
import io.legado.app.domain.model.manga.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaAiAnalysisTest {
    private val request = AiGenerateRequest(
        model = AiModelConfig("m", AiProviderConfig("p", "Test", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://example.invalid", "unused"), "Model", "model"),
        messages = emptyList(), params = AiGenerationParams(),
    )
    private fun gateway(events: Flow<AiStreamEvent>) = object : AiTextGateway {
        override fun generateStream(request: AiGenerateRequest) = events
        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> = error("No non-streaming fallback")
        override suspend fun fetchModels(provider: AiProviderConfig) = Result.success(emptyList<AiAvailableModel>())
    }
    private val phase = MangaTranslationProgress(MangaTranslationStage.ANALYZING, 1, 4)

    @Test fun `reasoning and JSON content remain separate with final progress and no synthetic reasoning`() = runTest {
        val updates = mutableListOf<MangaTranslationProgress>()
        val result = mangaAiAnalysis(gateway(flowOf(AiStreamEvent.Reasoning("thinking"), AiStreamEvent.Content("{\"regions\":"), AiStreamEvent.Content("[]}"))),
            request, phase, updates::add) { "message:$it" }
        assertEquals("{\"regions\":[]}", result)
        assertTrue(updates.all { it.stage == phase.stage && it.completed == 1 && it.total == 4 })
        assertEquals("thinking", updates.last().ai!!.reasoning)
        assertEquals(result.length, updates.last().ai!!.outputCharacters)
        assertFalse(updates.last().ai!!.running)
        assertTrue(updates.any { it.ai!!.thinking })
        val plain = mutableListOf<MangaTranslationProgress>()
        mangaAiAnalysis(gateway(flowOf(AiStreamEvent.Content("{}"))), request, phase, plain::add) { "message:$it" }
        assertTrue(plain.all { it.ai!!.reasoning.isEmpty() })
    }

    @Test fun `reasoning display is bounded while full JSON result is retained`() = runTest {
        val updates = mutableListOf<MangaTranslationProgress>()
        val content = "a".repeat(100_000)
        val result = mangaAiAnalysis(gateway(flowOf(AiStreamEvent.Reasoning("x".repeat(100_000)), AiStreamEvent.Reasoning("last"), AiStreamEvent.Content(content))),
            request, phase, updates::add) { "message:$it" }
        val trace = updates.last().ai!!
        assertEquals(64 * 1024, trace.reasoning.length)
        assertTrue(trace.reasoning.endsWith("last")); assertTrue(trace.reasoningTruncated)
        assertEquals(content, result)
    }

    @Test fun `reasoning-only and failed streams retain diagnostics and stop without fallback requests`() = runTest {
        for (events in listOf(flowOf(AiStreamEvent.Reasoning("only thinking")), flow {
            emit(AiStreamEvent.Reasoning("only thinking")); throw IllegalStateException("network failure")
        })) {
            val updates = mutableListOf<MangaTranslationProgress>()
            try { mangaAiAnalysis(gateway(events), request, phase, updates::add) { "message:$it" }; fail("Expected failure") }
            catch (_: IllegalStateException) { }
            catch (_: IllegalArgumentException) { }
            assertEquals("only thinking", updates.last().ai!!.reasoning)
            assertFalse(updates.last().ai!!.running)
        }
    }

    @Test fun `cancellation closes request state and propagates`() = runTest {
        val updates = mutableListOf<MangaTranslationProgress>()
        var closed = false
        val job = launch {
            mangaAiAnalysis(gateway(flow {
                try { emit(AiStreamEvent.Reasoning("pending")); awaitCancellation() }
                finally { closed = true }
            }), request, phase, updates::add) { "message:$it" }
        }
        runCurrent(); job.cancelAndJoin()
        assertTrue(job.isCancelled); assertTrue(closed)
        assertFalse(updates.last().ai!!.running)
    }

    @Test fun `stalled analysis respects its overall deadline`() = runTest {
        val updates = mutableListOf<MangaTranslationProgress>()
        var timedOut = false
        val job = launch {
            try { mangaAiAnalysis(gateway(flow { awaitCancellation() }), request, phase, updates::add) { "message:$it" } }
            catch (_: TimeoutCancellationException) { timedOut = true }
        }
        runCurrent(); advanceTimeBy(180_001); runCurrent(); job.join()
        assertTrue(timedOut); assertFalse(updates.last().ai!!.running)
    }

    @Test fun `oversized output and tool calls fail without publishing or retrying`() = runTest {
        for (event in listOf(AiStreamEvent.Content("x".repeat(4 * 1024 * 1024 + 1)),
            AiStreamEvent.ToolCallDelta("tool", 0, "unexpected", "{}", "function"))) {
            val updates = mutableListOf<MangaTranslationProgress>()
            var failed = false
            try { mangaAiAnalysis(gateway(flowOf(event)), request, phase, updates::add) { "message:$it" } }
            catch (_: IllegalStateException) { failed = true }
            catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed); assertFalse(updates.last().ai!!.running)
            assertEquals(0, updates.last().ai!!.outputCharacters)
        }
    }

    @Test fun `DeepSeek diagnostics show the same native switch even without metadata and expose an ignored disable request`() = runTest {
        val updates = mutableListOf<MangaTranslationProgress>()
        val deepSeek = request.copy(model = request.model.copy(modelId = "deepseek-flash"),
            params = AiGenerationParams(reasoningLevel = AiReasoningLevel.OFF))
        mangaAiAnalysis(gateway(flowOf(AiStreamEvent.Reasoning("server ignored disable"), AiStreamEvent.Content("{}"))),
            deepSeek, phase, updates::add) { "message:$it" }
        val trace = updates.last().ai!!
        val parameters = com.google.gson.JsonParser.parseString(trace.reasoningParameters).asJsonObject
        assertEquals(setOf("thinking"), parameters.keySet())
        assertEquals("disabled", parameters.getAsJsonObject("thinking").get("type").asString)
        assertTrue(trace.reasoningDisabledRequested)
        assertFalse(trace.reasoningCapabilityDeclared)
        assertEquals("server ignored disable", trace.reasoning)
        assertFalse(trace.reasoningParameters!!.contains(deepSeek.model.provider.apiKey))
    }
}
