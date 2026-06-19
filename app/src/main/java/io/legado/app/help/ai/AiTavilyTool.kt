package io.legado.app.help.ai

import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiTavilyTool {

    private const val TOOL_TAVILY_SEARCH = "search_web_tavily"

    fun resolvedTools(): List<AiResolvedTool> {
        if (!AppConfig.aiTavilyEnabled || AppConfig.aiTavilyApiKey.isBlank()) {
            return emptyList()
        }
        return listOf(
            AiResolvedTool(
                name = TOOL_TAVILY_SEARCH,
                definition = searchDefinition(),
                execute = { args -> search(args) }
            )
        )
    }

    private fun searchDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_TAVILY_SEARCH)
                put("description", "使用 Tavily 联网搜索实时网页信息，返回答案摘要、来源链接和内容片段。适合新闻、实时事件、最新产品信息和网页检索。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "要搜索的问题或关键词。")
                        })
                        put("topic", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("general", "news", "finance")))
                            put("description", "搜索主题，默认使用设置中的值。")
                        })
                        put("searchDepth", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("basic", "advanced", "ultra-fast")))
                            put("description", "搜索深度，默认使用设置中的值。")
                        })
                        put("maxResults", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 10)
                            put("description", "最多返回几条结果，默认使用设置中的值。")
                        })
                        put("includeDomains", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("excludeDomains", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("includeAnswer", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "是否返回 Tavily 的简短总结答案。默认 true。")
                        })
                    })
                    put("required", JSONArray(listOf("query")))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private suspend fun search(arguments: JSONObject?): String = withContext(IO) {
        val query = arguments?.optString("query")?.trim().orEmpty()
        if (query.isBlank()) {
            return@withContext errorJson("query 不能为空")
        }
        val requestBody = JSONObject().apply {
            put("query", query)
            put("topic", arguments?.optString("topic")?.takeIf { it.isNotBlank() } ?: AppConfig.aiTavilyTopic)
            put(
                "search_depth",
                arguments?.optString("searchDepth")?.takeIf { it.isNotBlank() } ?: AppConfig.aiTavilySearchDepth
            )
            put("max_results", (arguments?.optInt("maxResults", AppConfig.aiTavilyMaxResults)
                ?: AppConfig.aiTavilyMaxResults).coerceIn(1, 10))
            put("include_answer", arguments?.optBoolean("includeAnswer", true) ?: true)
            put("include_raw_content", false)
            appendStringArray(this, "include_domains", arguments?.optJSONArray("includeDomains"))
            appendStringArray(this, "exclude_domains", arguments?.optJSONArray("excludeDomains"))
        }
        val response = okHttpClient.newCallResponse {
            url(normalizeUrl(AppConfig.aiTavilyBaseUrl))
            addHeader("Accept", "application/json")
            addHeader("Authorization", "Bearer ${AppConfig.aiTavilyApiKey}")
            postJson(requestBody.toString())
        }
        response.use { raw ->
            val body = raw.body?.string().orEmpty()
            if (!raw.isSuccessful) {
                return@withContext errorJson(
                    extractError(body).ifBlank { "${raw.code} ${raw.message}" },
                    body
                )
            }
            val root = JSONObject(body)
            JSONObject().apply {
                put("ok", true)
                put("query", root.optString("query").ifBlank { query })
                put("answer", root.optString("answer"))
                put("responseTime", root.opt("response_time"))
                root.optJSONObject("usage")?.let { put("usage", it) }
                put("results", JSONArray().apply {
                    val results = root.optJSONArray("results") ?: JSONArray()
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        put(JSONObject().apply {
                            put("title", item.optString("title"))
                            put("url", item.optString("url"))
                            put("content", item.optString("content"))
                            put("score", item.opt("score"))
                            put("favicon", item.optString("favicon"))
                        })
                    }
                })
                val images = root.optJSONArray("images")
                if (images != null) {
                    put("images", images)
                }
            }.toString()
        }
    }

    private fun appendStringArray(target: JSONObject, key: String, source: JSONArray?) {
        if (source == null || source.length() == 0) return
        val array = JSONArray()
        for (index in 0 until source.length()) {
            source.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(array::put)
        }
        if (array.length() > 0) {
            target.put(key, array)
        }
    }

    private fun normalizeUrl(raw: String): String {
        val normalized = raw.trim().trimEnd('/')
        return if (normalized.endsWith("/search")) normalized else "$normalized/search"
    }

    private fun extractError(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
                ?: root.optString("detail")
        }.getOrDefault("")
    }

    private fun errorJson(message: String, raw: String? = null): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
            if (!raw.isNullOrBlank()) put("raw", raw.take(1000))
        }.toString()
    }
}
