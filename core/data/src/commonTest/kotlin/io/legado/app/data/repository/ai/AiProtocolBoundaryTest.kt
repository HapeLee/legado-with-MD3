package io.legado.app.data.repository.ai

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiProviderConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AiProtocolBoundaryTest {

    @Test
    fun registryResolvesEveryProtocolToItsRegisteredHandler() {
        val openAi = testHandler("openai", "openai-responses")
        val anthropic = testHandler("anthropic")
        val registry = AiProviderRegistry(listOf(openAi, anthropic))

        assertSame(openAi, registry.handlerFor("openai"))
        assertSame(openAi, registry.handlerFor("openai-responses"))
        assertSame(anthropic, registry.handlerFor("anthropic"))
    }

    @Test
    fun registryFailsExplicitlyForAnUnsupportedProtocol() {
        val error = assertFailsWith<IllegalStateException> {
            AiProviderRegistry(emptyList()).handlerFor("unknown")
        }

        assertEquals("Unsupported AI protocol: unknown", error.message)
    }

    @Test
    fun retryableFailureRotatesKeyAndRetries() = runTest {
        val rotator = KeyRotator("first, second")
        var attempts = 0
        val observedKeys = mutableListOf<String>()

        val value = retryWithBackoff(
            maxAttempts = 2,
            baseDelayMs = 0,
            maxDelayMs = 0,
            keyRotator = rotator,
            onRetry = { _, _, _ -> observedKeys += rotator.currentKey },
        ) {
            attempts += 1
            if (attempts == 1) throw Exception("HTTP 429")
            rotator.currentKey
        }

        assertEquals(2, attempts)
        assertEquals(listOf("second"), observedKeys)
        assertEquals("second", value)
    }

    @Test
    fun nonRetryableFailureDoesNotInvokeTheBlockAgain() = runTest {
        var attempts = 0

        val error = try {
            retryWithBackoff(maxAttempts = 3, baseDelayMs = 0, maxDelayMs = 0) {
                attempts += 1
                throw Exception("HTTP 400")
            }
        } catch (error: Exception) {
            error
        }

        assertEquals("HTTP 400", error.message)
        assertEquals(1, attempts)
    }

    private fun testHandler(vararg protocols: String): AiProtocolHandler = object : AiProtocolHandler {
        override val protocols: Set<String> = protocols.toSet()

        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
            error("Not used by registry tests")

        override suspend fun stream(
            request: AiGenerateRequest,
            emitEvent: suspend (AiStreamEvent) -> Unit,
        ) = error("Not used by registry tests")

        override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
            error("Not used by registry tests")
    }
}
