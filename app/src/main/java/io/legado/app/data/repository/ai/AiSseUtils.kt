package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.utils.GSON
import okhttp3.Response

/**
 * Shared SSE parsing utilities used by all protocol handlers.
 */
internal suspend fun Response.readSseData(onData: suspend (String) -> Unit) {
    val source = body.source()
    val dataLines = mutableListOf<String>()
    try {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n").trim()
                        dataLines.clear()
                        if (data == "[DONE]") break
                        if (data.isNotEmpty()) onData(data)
                    }
                }
                line.startsWith("data:") -> {
                    dataLines += line.removePrefix("data:").trimStart()
                }
            }
        }
        if (dataLines.isNotEmpty()) {
            val data = dataLines.joinToString("\n").trim()
            if (data != "[DONE]" && data.isNotEmpty()) onData(data)
        }
    } finally {
        source.close()
    }
}

internal fun String.toJsonObject(): JsonObject? {
    return runCatching {
        GSON.fromJson(this, JsonObject::class.java)
    }.getOrNull()
}

internal fun JsonObject.getString(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.extractApiErrorMessage(): String? {
    val error = get("error")?.asJsonObjectOrNull() ?: return null
    return error.getString("message") ?: error.getString("code") ?: "AI provider returned an error"
}

internal fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

internal fun JsonElement.asJsonArrayOrNull() = if (isJsonArray) asJsonArray else null

/**
 * Check if the model supports reasoning capability.
 */
internal fun hasReasoningCapability(capabilities: Set<String>): Boolean {
    return AiCapability.REASONING in capabilities
}

/**
 * Map AiReasoningLevel to OpenAI-compatible reasoning_effort value.
 * OpenAI doesn't accept "none" — remap to "low".
 */
internal fun AiReasoningLevel.toOpenAiEffort(): String {
    return if (effort == "none") "low" else effort
}
