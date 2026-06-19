package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.http.HttpCaptureHelper
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.coroutineContext

object AiReadingNetworkTool {

    private const val TOOL_READING_AJAX = "reading_ajax"
    private const val TOOL_READING_WEBVIEW = "reading_webview"
    private const val TOOL_CAPTURE_WEB_REQUESTS = "capture_web_requests"

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_READING_AJAX,
                definition = ajaxDefinition(),
                execute = { args -> readingAjax(args) }
            ),
            AiResolvedTool(
                name = TOOL_READING_WEBVIEW,
                definition = webViewDefinition(),
                execute = { args -> readingWebView(args) }
            ),
            AiResolvedTool(
                name = TOOL_CAPTURE_WEB_REQUESTS,
                definition = captureDefinition(),
                execute = { args -> captureWebRequests(args) }
            )
        )
    }

    private fun ajaxDefinition() = functionDefinition(
        name = TOOL_READING_AJAX,
        description = "调用阅读 AnalyzeUrl 网络链路获取网页或接口内容。可复用书源 header/cookie/URL 规则，返回最终 URL、状态码、耗时和截断后的正文。",
        properties = commonSourceProperties().apply {
            put("url", stringProp("要请求的 http/https URL，支持 Legado URL option。"))
            put("useWebView", booleanProp("是否允许使用 URL 规则中的 WebView 配置，默认 true。"))
            put("js", stringProp("可选，WebView 页面加载后执行的 JS。"))
            put("sourceRegex", stringProp("可选，WebView 资源匹配正则。"))
            put("timeoutMs", intProp("请求超时毫秒，默认 30000，最大 90000。", 10_000, 90_000))
            put("maxChars", intProp("返回正文最大字符数，默认 20000，最大 80000。", 1_000, 80_000))
        },
        required = JSONArray().put("url")
    )

    private fun webViewDefinition() = functionDefinition(
        name = TOOL_READING_WEBVIEW,
        description = "调用阅读后台 WebView 加载 URL 或 HTML，并执行 JS 返回结果。用于分析需要浏览器渲染的页面。",
        properties = commonSourceProperties().apply {
            put("url", stringProp("页面 URL。html 不为空时作为 baseUrl。"))
            put("html", stringProp("可选，直接加载的 HTML。"))
            put("js", stringProp("可选，页面加载后执行的 JS；为空则返回页面源码相关结果。"))
            put("sourceRegex", stringProp("可选，要嗅探的资源 URL 正则。"))
            put("overrideUrlRegex", stringProp("可选，要拦截的跳转 URL 正则。"))
            put("cacheFirst", booleanProp("是否优先使用 WebView 缓存，默认 false。"))
            put("delayMs", intProp("页面完成后额外等待毫秒，默认 900，最大 30000。", 0, 30_000))
            put("timeoutMs", intProp("请求超时毫秒，默认 30000，最大 90000。", 10_000, 90_000))
            put("maxChars", intProp("返回结果最大字符数，默认 20000，最大 80000。", 1_000, 80_000))
        }
    )

    private fun captureDefinition() = functionDefinition(
        name = TOOL_CAPTURE_WEB_REQUESTS,
        description = "用后台 WebView 打开网页并记录网络请求，可按需复放请求获取响应体。用于分析页面接口和抓包数据。",
        properties = commonSourceProperties().apply {
            put("url", stringProp("要打开抓包的 http/https 页面。"))
            put("waitMs", intProp("页面完成后继续抓包等待毫秒，默认 5000，最大 30000。", 500, 30_000))
            put("timeoutMs", intProp("总超时毫秒，默认 30000，最大 90000。", 10_000, 90_000))
            put("includeRegex", stringProp("只记录匹配该正则的请求。"))
            put("excludeRegex", stringProp("排除匹配该正则的请求。"))
            put("maxRequests", intProp("最多记录请求数，默认 50，最大 200。", 1, 200))
            put("maxBodyChars", intProp("单个响应体最大返回字符数，默认 20000，最大 80000。", 0, 80_000))
            put("replayResponse", booleanProp("是否复放 GET/HEAD 请求获取响应体，默认 true。"))
            put("replayTimeoutMs", intProp("单个复放请求超时毫秒，默认 8000，最大 30000。", 2_000, 30_000))
            put("maxReplayRequests", intProp("最大复放请求数，默认 5，最大 20。", 1, 20))
            put("replayOnlyMatched", booleanProp("是否只复放命中 includeRegex 或疑似接口的请求，默认 true。"))
            put("js", stringProp("可选，页面完成后执行的 JS，用于触发接口请求。"))
        },
        required = JSONArray().put("url")
    )

    private suspend fun readingAjax(args: JSONObject?): String = coroutineScope {
        args ?: return@coroutineScope error("缺少参数")
        val url = args.optString("url").trim()
        if (!isHttpUrl(url)) return@coroutineScope error("仅支持 http/https URL")
        val timeoutMs = args.optLong("timeoutMs", 30_000L).coerceIn(10_000L, 90_000L)
        val maxChars = args.optInt("maxChars", 20_000).coerceIn(1_000, 80_000)
        val source = resolveSource(args) ?: temporarySourceFor(url)
        runCatching {
            val response = AnalyzeUrl(
                mUrl = url,
                source = source,
                callTimeout = timeoutMs,
                coroutineContext = coroutineContext
            ).getStrResponseAwait(
                jsStr = args.optString("js").takeIf { it.isNotBlank() },
                sourceRegex = args.optString("sourceRegex").takeIf { it.isNotBlank() },
                useWebView = args.optBoolean("useWebView", true),
                isTest = true
            )
            val body = response.body.orEmpty()
            ok().apply {
                put("url", url)
                put("finalUrl", response.url)
                put("statusCode", response.code())
                put("message", response.message())
                put("callTime", response.callTime)
                put("bodyLength", body.length)
                put("truncated", body.length > maxChars)
                put("body", body.take(maxChars))
            }.toString()
        }.getOrElse { error(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    private suspend fun readingWebView(args: JSONObject?): String = coroutineScope {
        args ?: return@coroutineScope error("缺少参数")
        val url = args.optString("url").trim().takeIf { it.isNotBlank() }
        val html = args.optString("html").takeIf { it.isNotBlank() }
        if (url == null && html == null) return@coroutineScope error("缺少 url 或 html")
        if (url != null && !isHttpUrl(url)) return@coroutineScope error("仅支持 http/https URL")
        val timeoutMs = args.optLong("timeoutMs", 30_000L).coerceIn(10_000L, 90_000L)
        val maxChars = args.optInt("maxChars", 20_000).coerceIn(1_000, 80_000)
        val source = resolveSource(args) ?: url?.let(::temporarySourceFor)
        runCatching {
            val response = BackstageWebView(
                url = url,
                html = html,
                tag = source?.getKey(),
                headerMap = source?.getHeaderMap(true),
                sourceRegex = args.optString("sourceRegex").takeIf { it.isNotBlank() },
                overrideUrlRegex = args.optString("overrideUrlRegex").takeIf { it.isNotBlank() },
                javaScript = args.optString("js").takeIf { it.isNotBlank() },
                delayTime = args.optLong("delayMs", 900L).coerceIn(0L, 30_000L),
                cacheFirst = args.optBoolean("cacheFirst", false),
                timeout = timeoutMs
            ).getStrResponse()
            val body = response.body.orEmpty()
            ok().apply {
                put("url", url.orEmpty())
                put("finalUrl", response.url)
                put("statusCode", response.code())
                put("message", response.message())
                put("bodyLength", body.length)
                put("truncated", body.length > maxChars)
                put("body", body.take(maxChars))
            }.toString()
        }.getOrElse { error(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    private suspend fun captureWebRequests(args: JSONObject?): String = coroutineScope {
        args ?: return@coroutineScope error("缺少参数")
        val url = args.optString("url").trim()
        if (!isHttpUrl(url)) return@coroutineScope error("仅支持 http/https URL")
        val source = resolveSource(args) ?: temporarySourceFor(url)
        runCatching {
            HttpCaptureHelper.capture(
                HttpCaptureHelper.Config(
                    url = url,
                    source = source,
                    waitMs = args.optLong("waitMs", 5_000L).coerceIn(500L, 30_000L),
                    timeoutMs = args.optLong("timeoutMs", 30_000L).coerceIn(10_000L, 90_000L),
                    includeRegex = args.optString("includeRegex").takeIf { it.isNotBlank() },
                    excludeRegex = args.optString("excludeRegex").takeIf { it.isNotBlank() },
                    maxRequests = args.optInt("maxRequests", 50).coerceIn(1, 200),
                    maxBodyChars = args.optInt("maxBodyChars", 20_000).coerceIn(0, 80_000),
                    replayResponse = args.optBoolean("replayResponse", true),
                    replayTimeoutMs = args.optLong("replayTimeoutMs", 8_000L).coerceIn(2_000L, 30_000L),
                    maxReplayRequests = args.optInt("maxReplayRequests", 5).coerceIn(1, 20),
                    replayOnlyMatched = args.optBoolean("replayOnlyMatched", true),
                    js = args.optString("js").takeIf { it.isNotBlank() },
                    coroutineContext = coroutineContext
                )
            ).toString()
        }.getOrElse { error(it.localizedMessage ?: it.javaClass.simpleName) }
    }

    private fun resolveSource(args: JSONObject?): BookSource? {
        args ?: return null
        args.optString("sourceJson").takeIf { it.isNotBlank() }?.let { json ->
            GSON.fromJsonObject<BookSource>(json).getOrNull()?.let { return it }
        }
        val sourceUrl = args.optString("bookSourceUrl").trim()
        if (sourceUrl.isNotBlank()) {
            appDb.bookSourceDao.getBookSource(sourceUrl)?.let { return it }
        }
        return null
    }

    private fun temporarySourceFor(url: String): BookSource {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val origin = runCatching {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        }.getOrDefault(url)
        return BookSource(
            bookSourceUrl = origin,
            bookSourceName = host.ifBlank { origin }
        )
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun commonSourceProperties() = JSONObject().apply {
        put("bookSourceUrl", stringProp("可选，本地书源 URL，用于复用 header/cookie。"))
        put("sourceJson", stringProp("可选，临时书源完整 JSON，用于复用 header/cookie。"))
    }

    private fun functionDefinition(
        name: String,
        description: String,
        properties: JSONObject,
        required: JSONArray = JSONArray()
    ) = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                if (required.length() > 0) put("required", required)
                put("additionalProperties", false)
            })
        })
    }

    private fun stringProp(description: String) = JSONObject().apply {
        put("type", "string")
        put("description", description)
    }

    private fun booleanProp(description: String) = JSONObject().apply {
        put("type", "boolean")
        put("description", description)
    }

    private fun intProp(description: String, minimum: Int, maximum: Int) = JSONObject().apply {
        put("type", "integer")
        put("description", description)
        put("minimum", minimum)
        put("maximum", maximum)
    }

    private fun ok() = JSONObject().put("ok", true)

    private fun error(message: String) = JSONObject().apply {
        put("ok", false)
        put("error", message)
    }.toString()
}
