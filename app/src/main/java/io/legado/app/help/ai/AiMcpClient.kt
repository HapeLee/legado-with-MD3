package io.legado.app.help.ai

import io.legado.app.BuildConfig
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.ui.main.ai.AiMcpServerConfig
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

object AiMcpClient {

    private const val PROTOCOL_VERSION = "2025-06-18"
    private const val HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"
    private const val HEADER_SESSION_ID = "Mcp-Session-Id"
    private const val TOOL_CACHE_TTL_MS = 60_000L

    private data class SessionState(
        val sessionId: String?,
        val protocolVersion: String,
        val configFingerprint: String
    )

    private data class CachedTools(
        val fingerprint: String,
        val createdAt: Long,
        val tools: List<AiResolvedTool>
    )

    private data class McpToolDescriptor(
        val name: String,
        val title: String,
        val description: String,
        val inputSchema: JSONObject
    )

    private val sessionMap = mutableMapOf<String, SessionState>()
    private val toolCache = mutableMapOf<String, CachedTools>()

    suspend fun resolveTools(servers: List<AiMcpServerConfig>): List<AiResolvedTool> {
        val result = mutableListOf<AiResolvedTool>()
        val usedNames = mutableSetOf<String>()
        servers.filter { it.enabled }.forEach { server ->
            val fingerprint = server.fingerprint()
            val cached = toolCache[server.id]
            if (cached != null
                && cached.fingerprint == fingerprint
                && System.currentTimeMillis() - cached.createdAt < TOOL_CACHE_TTL_MS
            ) {
                cached.tools.forEach {
                    if (usedNames.add(it.name)) result += it
                }
                return@forEach
            }
            val tools = runCatching {
                val localNames = usedNames.toMutableSet()
                listTools(server).mapIndexed { index, descriptor ->
                    val alias = buildToolAlias(server, descriptor.name, index, localNames)
                    localNames += alias
                    buildResolvedTool(server, alias, descriptor)
                }
            }.getOrElse {
                sessionMap.remove(server.id)
                emptyList()
            }
            toolCache[server.id] = CachedTools(
                fingerprint = fingerprint,
                createdAt = System.currentTimeMillis(),
                tools = tools
            )
            tools.forEach {
                if (usedNames.add(it.name)) result += it
            }
        }
        return result
    }

    private suspend fun listTools(server: AiMcpServerConfig): List<McpToolDescriptor> {
        val session = ensureSession(server)
        val tools = mutableListOf<McpToolDescriptor>()
        var cursor: String? = null
        do {
            val requestId = nextRequestId()
            val params = JSONObject()
            cursor?.let { params.put("cursor", it) }
            val response = postJsonRpc(
                server = server,
                session = session,
                body = jsonRpcRequest("tools/list", params, requestId),
                requestId = requestId
            )
            val result = response.optJSONObject("result") ?: JSONObject()
            val toolArray = result.optJSONArray("tools") ?: JSONArray()
            for (index in 0 until toolArray.length()) {
                val tool = toolArray.optJSONObject(index) ?: continue
                tools += McpToolDescriptor(
                    name = tool.optString("name"),
                    title = tool.optString("title"),
                    description = tool.optString("description"),
                    inputSchema = tool.optJSONObject("inputSchema") ?: emptyObjectSchema()
                )
            }
            cursor = result.optString("nextCursor").takeIf { it.isNotBlank() }
        } while (cursor != null)
        return tools.filter { it.name.isNotBlank() }
    }

    private fun buildResolvedTool(
        server: AiMcpServerConfig,
        alias: String,
        descriptor: McpToolDescriptor
    ): AiResolvedTool {
        val description = buildString {
            append("MCP ")
            append(server.name)
            if (descriptor.title.isNotBlank() && descriptor.title != descriptor.name) {
                append(" - ")
                append(descriptor.title)
            }
            if (descriptor.description.isNotBlank()) {
                append(": ")
                append(descriptor.description)
            }
        }
        return AiResolvedTool(
            name = alias,
            definition = JSONObject().apply {
                put("type", "function")
                put(
                    "function",
                    JSONObject().apply {
                        put("name", alias)
                        put("description", description)
                        put("parameters", sanitizeSchema(descriptor.inputSchema))
                    }
                )
            },
            execute = { args ->
                callTool(server, descriptor.name, args)
            }
        )
    }

    suspend fun callTool(
        server: AiMcpServerConfig,
        toolName: String,
        arguments: JSONObject?
    ): String {
        val session = ensureSession(server)
        val requestId = nextRequestId()
        val response = postJsonRpc(
            server = server,
            session = session,
            body = jsonRpcRequest(
                method = "tools/call",
                params = JSONObject().apply {
                    put("name", toolName)
                    put("arguments", arguments ?: JSONObject())
                },
                id = requestId
            ),
            requestId = requestId
        )
        return (response.optJSONObject("result") ?: JSONObject()).toString()
    }

    private suspend fun ensureSession(server: AiMcpServerConfig): SessionState {
        val fingerprint = server.fingerprint()
        val current = sessionMap[server.id]
        if (current != null && current.configFingerprint == fingerprint) {
            return current
        }
        val requestId = nextRequestId()
        val initializeBody = jsonRpcRequest(
            method = "initialize",
            params = JSONObject().apply {
                put("protocolVersion", PROTOCOL_VERSION)
                put(
                    "clientInfo",
                    JSONObject().apply {
                        put("name", "Legado")
                        put("version", BuildConfig.VERSION_NAME)
                    }
                )
                put("capabilities", JSONObject())
            },
            id = requestId
        )
        val response = okHttpClient.newCallResponse {
            url(server.endpoint)
            addHeader("Accept", "application/json, text/event-stream")
            addHeader("Content-Type", "application/json")
            server.apiKey.trim().takeIf { it.isNotBlank() }?.let {
                addHeader("Authorization", "Bearer $it")
            }
            postJson(initializeBody.toString())
        }
        response.use { rawResponse ->
            val rpcResponse = readJsonRpcResponse(rawResponse, requestId)
            val result = rpcResponse.optJSONObject("result") ?: JSONObject()
            val session = SessionState(
                sessionId = rawResponse.header(HEADER_SESSION_ID),
                protocolVersion = result.optString("protocolVersion").ifBlank { PROTOCOL_VERSION },
                configFingerprint = fingerprint
            )
            sessionMap[server.id] = session
            toolCache.remove(server.id)
            sendInitializedNotification(server, session)
            return session
        }
    }

    private suspend fun sendInitializedNotification(
        server: AiMcpServerConfig,
        session: SessionState
    ) {
        runCatching {
            okHttpClient.newCallResponse {
                url(server.endpoint)
                addHeader("Accept", "application/json, text/event-stream")
                addHeader("Content-Type", "application/json")
                addHeader(HEADER_PROTOCOL_VERSION, session.protocolVersion)
                session.sessionId?.let { addHeader(HEADER_SESSION_ID, it) }
                server.apiKey.trim().takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
                postJson(
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "notifications/initialized")
                    }.toString()
                )
            }.close()
        }
    }

    private suspend fun postJsonRpc(
        server: AiMcpServerConfig,
        session: SessionState,
        body: JSONObject,
        requestId: String
    ): JSONObject {
        return runCatching {
            okHttpClient.newCallResponse {
                url(server.endpoint)
                addHeader("Accept", "application/json, text/event-stream")
                addHeader("Content-Type", "application/json")
                addHeader(HEADER_PROTOCOL_VERSION, session.protocolVersion)
                session.sessionId?.let { addHeader(HEADER_SESSION_ID, it) }
                server.apiKey.trim().takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
                postJson(body.toString())
            }.use { rawResponse ->
                readJsonRpcResponse(rawResponse, requestId)
            }
        }.recoverCatching {
            sessionMap.remove(server.id)
            toolCache.remove(server.id)
            val freshSession = ensureSession(server)
            okHttpClient.newCallResponse {
                url(server.endpoint)
                addHeader("Accept", "application/json, text/event-stream")
                addHeader("Content-Type", "application/json")
                addHeader(HEADER_PROTOCOL_VERSION, freshSession.protocolVersion)
                freshSession.sessionId?.let { addHeader(HEADER_SESSION_ID, it) }
                server.apiKey.trim().takeIf { it.isNotBlank() }?.let {
                    addHeader("Authorization", "Bearer $it")
                }
                postJson(body.toString())
            }.use { rawResponse ->
                readJsonRpcResponse(rawResponse, requestId)
            }
        }.getOrThrow()
    }

    private fun readJsonRpcResponse(response: Response, requestId: String): JSONObject {
        val body = response.body ?: throw IllegalStateException("MCP empty response body")
        if (!response.isSuccessful) {
            val payload = body.string()
            throw IllegalStateException(
                "MCP ${response.code} ${response.message}: ${extractJsonRpcError(payload)}"
            )
        }
        val payload = body.string()
        if (payload.isBlank()) {
            return JSONObject()
        }
        val isSse = response.header("Content-Type").orEmpty().contains("text/event-stream")
        val rpcResponse = if (isSse) {
            parseSsePayload(payload, requestId)
        } else {
            JSONObject(payload)
        }
        rpcResponse.optJSONObject("error")?.let { error ->
            throw IllegalStateException(
                error.optString("message").ifBlank { error.toString() }
            )
        }
        return rpcResponse
    }

    private fun parseSsePayload(payload: String, requestId: String): JSONObject {
        val eventData = StringBuilder()
        payload.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.startsWith("data:") -> {
                    eventData.append(line.removePrefix("data:").trim()).append('\n')
                }

                line.isBlank() -> {
                    if (eventData.isNotEmpty()) {
                        val json = JSONObject(eventData.toString().trim())
                        if (json.opt("id")?.toString() == requestId) {
                            return json
                        }
                        eventData.clear()
                    }
                }
            }
        }
        if (eventData.isNotEmpty()) {
            val json = JSONObject(eventData.toString().trim())
            if (json.opt("id")?.toString() == requestId) {
                return json
            }
        }
        throw IllegalStateException("MCP SSE response missing matching id")
    }

    private fun sanitizeSchema(schema: JSONObject?): JSONObject {
        val result = schema?.let { JSONObject(it.toString()) } ?: JSONObject()
        if (result.optString("type").isBlank()) {
            result.put("type", "object")
        }
        if (!result.has("properties")) {
            result.put("properties", JSONObject())
        }
        return result
    }

    private fun emptyObjectSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject())
            put("additionalProperties", true)
        }
    }

    private fun buildToolAlias(
        server: AiMcpServerConfig,
        toolName: String,
        index: Int,
        usedNames: Collection<String>
    ): String {
        val serverSlug = slug(server.name).take(16).ifBlank { "server" }
        val toolSlug = slug(toolName).take(32).ifBlank { "tool" }
        val base = "mcp_${serverSlug}_${toolSlug}"
        var candidate = base.take(64)
        if (candidate !in usedNames) return candidate
        val suffix = "_${server.id.filter { it.isLetterOrDigit() }.takeLast(6)}_${index + 1}"
        candidate = (base.take(64 - suffix.length) + suffix).take(64)
        return candidate
    }

    private fun slug(value: String): String {
        return value.lowercase(Locale.getDefault())
            .map { ch ->
                when {
                    ch.isLetterOrDigit() -> ch
                    else -> '_'
                }
            }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private fun AiMcpServerConfig.fingerprint(): String {
        return listOf(id, name.trim(), endpoint.trim(), apiKey.trim(), enabled).joinToString("|")
    }

    private fun jsonRpcRequest(method: String, params: JSONObject, id: String): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
    }

    private fun nextRequestId(): String = UUID.randomUUID().toString()

    private fun extractJsonRpcError(payload: String): String {
        return runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty().ifBlank { payload }
    }
}
