package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.help.http.StrResponse
import okhttp3.Response

/**
 * Some gateways require a client-supplied session header to route/cache a conversation.
 * OpenCode Go (`https://opencode.ai/zen/go/v1`) returns HTTP 400 when it is missing.
 */
internal const val AI_SESSION_HEADER = "x-opencode-session"

private const val MAX_ERROR_BODY_LENGTH = 600

/**
 * Whether this provider routes requests by a client session header. Matched by profile identity so
 * a manually configured profile that shares the gateway base URL also benefits.
 */
internal fun AiProviderConfig.requiresSessionHeader(): Boolean {
    val identity = "$id $name $baseUrl".lowercase()
    return "opencode" in identity
}

/**
 * Session header for this provider. Uses the per-conversation [sessionId] when available, and falls
 * back to the stable provider id for non-conversation requests (model listing, background tasks).
 */
internal fun AiProviderConfig.sessionHeaders(sessionId: String?): Map<String, String> {
    if (!requiresSessionHeader()) return emptyMap()
    val value = sessionId?.takeIf { it.isNotBlank() }
        ?: id.takeIf { it.isNotBlank() }
        ?: return emptyMap()
    return mapOf(AI_SESSION_HEADER to value)
}

/**
 * Merges provider headers in the order handlers must send them: provider defaults, generated session
 * header, user custom headers (override), then protocol headers (override).
 */
internal fun AiProviderConfig.aiRequestHeaders(
    sessionId: String?,
    protocolHeaders: Map<String, String>
): Map<String, String> =
    headers + sessionHeaders(sessionId) + customHeaders + protocolHeaders

/**
 * Builds an error message that keeps the response body. OkHttp reports an empty message over HTTP/2,
 * so "HTTP 400:" alone hides actionable gateway errors such as OpenCode's `MissingSessionID`.
 */
internal fun StrResponse.httpErrorMessage(): String =
    formatHttpErrorMessage(code(), message(), body)

internal fun Response.httpErrorMessage(): String =
    formatHttpErrorMessage(code, message, runCatching { body.string() }.getOrNull())

private fun formatHttpErrorMessage(code: Int, message: String, body: String?): String {
    val detail = body?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { if (it.length > MAX_ERROR_BODY_LENGTH) it.take(MAX_ERROR_BODY_LENGTH) + "…" else it }
        ?: message.trim().takeIf { it.isNotEmpty() }
    return if (detail == null) "HTTP $code" else "HTTP $code: $detail"
}
