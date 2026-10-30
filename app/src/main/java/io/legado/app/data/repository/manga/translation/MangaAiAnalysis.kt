package io.legado.app.data.repository.manga.translation

import io.legado.app.R
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.data.repository.ai.deepSeekReasoningParameters
import io.legado.app.utils.GSON
import io.legado.app.domain.model.manga.MangaTranslationAiProgress
import io.legado.app.domain.model.manga.MangaTranslationProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Collects one streamed JSON analysis, keeping bounded, throttled diagnostics out of the result. */
internal suspend fun mangaAiAnalysis(
    gateway: AiTextGateway,
    request: AiGenerateRequest,
    phase: MangaTranslationProgress,
    progress: (MangaTranslationProgress) -> Unit,
    message: (Int) -> String,
): String {
    val requestId = UUID.randomUUID().toString()
    val started = System.nanoTime()
    var lastUpdate = 0L
    var thinkingStarted: Long? = null
    var thinkingNanos = 0L
    val reasoning = StringBuilder()
    val content = StringBuilder()
    var truncated = false
    val control = request.deepSeekReasoningParameters()
    val controlJson = control?.let { GSON.toJson(it) }
    fun emit(running: Boolean, force: Boolean = false) {
        val now = System.nanoTime()
        if (!force && now - lastUpdate < 100_000_000L) return
        lastUpdate = now
        progress(phase.copy(ai = MangaTranslationAiProgress(
            requestId = requestId, modelName = request.model.displayName,
            reasoningCapabilityDeclared = AiCapability.REASONING in request.model.capabilities,
            reasoningParameters = controlJson,
            reasoningDisabledRequested = control != null && request.params.reasoningLevel == AiReasoningLevel.OFF,
            reasoning = reasoning.toString(), reasoningTruncated = truncated,
            outputCharacters = content.length, elapsedMillis = (now - started) / 1_000_000,
            thinkingMillis = (thinkingNanos + (thinkingStarted?.let { now - it } ?: 0L)) / 1_000_000,
            running = running, thinking = running && thinkingStarted != null,
        )))
    }
    emit(running = true, force = true)
    try {
        withTimeout(180_000L) {
            gateway.generateStream(request).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is AiStreamEvent.Reasoning -> {
                        if (event.text.isNotEmpty()) {
                            val first = thinkingStarted == null
                            if (first) thinkingStarted = System.nanoTime()
                            // Bound retained text before appending even a single oversized delta.
                            val tail = event.text.takeLast(64 * 1024)
                            truncated = truncated || tail.length != event.text.length
                            reasoning.append(tail)
                            if (reasoning.length > 64 * 1024) {
                                reasoning.delete(0, reasoning.length - 64 * 1024)
                                truncated = true
                            }
                            emit(running = true, force = first)
                        }
                    }
                    is AiStreamEvent.Content -> {
                        val endedThinking = thinkingStarted != null
                        thinkingStarted?.let { thinkingNanos += System.nanoTime() - it }
                        thinkingStarted = null
                        require(event.text.length <= 4 * 1024 * 1024 - content.length) { message(R.string.manga_translation_ai_output_too_large) }
                        content.append(event.text)
                        emit(running = true, force = endedThinking)
                    }
                    is AiStreamEvent.ToolCallDelta -> error(message(R.string.manga_translation_ai_unexpected_tool))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (content.isBlank()) throw MangaAiEmptyResponseException(message(R.string.manga_translation_ai_no_output))
        return content.toString()
    } finally {
        thinkingStarted?.let { thinkingNanos += System.nanoTime() - it }
        thinkingStarted = null
        emit(running = false, force = true)
    }
}
