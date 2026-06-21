package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.coroutines.coroutineContext

object AiBookSourceTool {

    private const val TOOL_CREATE_SOURCE = "create_book_source"
    private const val TOOL_GET_SOURCE = "get_book_source"
    private const val TOOL_UPDATE_SOURCE = "update_book_source"
    private const val TOOL_FETCH_HTML = "fetch_source_html"
    private const val TOOL_DEBUG_SOURCE = "debug_book_source"

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_CREATE_SOURCE,
                definition = createSourceDefinition(),
                execute = { args -> createBookSource(args) }
            ),
            AiResolvedTool(
                name = TOOL_GET_SOURCE,
                definition = getSourceDefinition(),
                execute = { args -> getBookSource(args) }
            ),
            AiResolvedTool(
                name = TOOL_UPDATE_SOURCE,
                definition = updateSourceDefinition(),
                execute = { args -> updateBookSource(args) }
            ),
            AiResolvedTool(
                name = TOOL_FETCH_HTML,
                definition = fetchHtmlDefinition(),
                execute = { args -> fetchSourceHtml(args) }
            ),
            AiResolvedTool(
                name = TOOL_DEBUG_SOURCE,
                definition = debugSourceDefinition(),
                execute = { args -> debugBookSource(args) }
            )
        )
    }

    private fun createSourceDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_CREATE_SOURCE)
                put(
                    "description",
                    "创建、规范化或预览 Legado 书源草稿。可传完整 sourceJson，也可传基础字段和规则字段。创建后必须继续调用 debug_book_source 调试；调试失败时应调用 update_book_source 修改草稿，再继续调试。只有用户明确要求保存时才 save=true 写入本地书源库。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("save", booleanProp("是否保存到本地书源库。默认 false，仅返回预览 JSON。"))
                        put("sourceJson", stringProp("完整 BookSource JSON。传入时优先按此解析。"))
                        put("bookSourceUrl", stringProp("书源唯一 URL，通常是站点根地址。"))
                        put("bookSourceName", stringProp("书源名称。"))
                        put("bookSourceGroup", stringProp("书源分组。"))
                        put("searchUrl", stringProp("搜索 URL 规则。"))
                        put("exploreUrl", stringProp("发现 URL 规则，可选。"))
                        put("bookUrlPattern", stringProp("详情页 URL 正则，可选。"))
                        put("ruleSearch", objectProp("搜索规则对象。"))
                        put("ruleBookInfo", objectProp("详情规则对象。"))
                        put("ruleToc", objectProp("目录规则对象。"))
                        put("ruleContent", objectProp("正文规则对象。"))
                        put("ruleExplore", objectProp("发现规则对象。"))
                        put("comment", stringProp("书源注释。"))
                    })
                    put("additionalProperties", true)
                })
            })
        }
    }

    private fun getSourceDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_GET_SOURCE)
                put(
                    "description",
                    "读取本地已保存的 Legado 书源。bookSourceUrl 精确读取；searchKey 可按名称、分组、URL、注释搜索。用于修改已有书源前获取当前完整 JSON。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookSourceUrl", stringProp("本地已保存书源的唯一 URL。"))
                        put("bookSourceUrls", JSONObject().apply {
                            put("type", "array")
                            put("description", "批量读取时传多个书源 URL。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("searchKey", stringProp("搜索关键词，可匹配书源名、分组、URL、注释。"))
                        put("searchKeys", JSONObject().apply {
                            put("type", "array")
                            put("description", "批量搜索时传多个关键词。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("description", "搜索返回数量，默认 10，最大 30。")
                            put("minimum", 1)
                            put("maximum", 30)
                        })
                    })
                    put("additionalProperties", true)
                })
            })
        }
    }

    private fun updateSourceDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_UPDATE_SOURCE)
                put(
                    "description",
                    "修改 Legado 书源草稿或本地已保存书源。可传 sourceJson 作为草稿基底，或传 bookSourceUrl 读取本地书源；推荐用 patch 做深度合并，也可直接传 bookSourceName/searchUrl/ruleToc/ruleContent 等顶层字段作为修改项。patch 字段值为 null 表示清空。若只传 sourceJson 和 save=true，则直接保存该完整书源。返回修改后的完整 sourceJson。调试失败后用本工具修正，再调用 debug_book_source。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookSourceUrl", stringProp("本地已保存书源 URL。没有 sourceJson 时用它读取基底；保存时也作为目标主键。"))
                        put("sourceJson", stringProp("当前草稿完整 BookSource JSON。传入时优先作为修改基底。"))
                        put("patch", objectProp("要合并到书源里的字段。支持嵌套对象，null 可清空字段。示例：{\"ruleToc\":{\"chapterList\":\".list dd\",\"chapterName\":\"a@text\",\"chapterUrl\":\"a@href\"}}"))
                        put("save", booleanProp("是否保存修改后的书源到本地书源库。默认 false。"))
                        put("bookSourceName", stringProp("可直接修改的书源名称。"))
                        put("bookSourceGroup", stringProp("可直接修改的书源分组。"))
                        put("searchUrl", stringProp("可直接修改的搜索 URL 规则。"))
                        put("exploreUrl", stringProp("可直接修改的发现 URL 规则。"))
                        put("bookUrlPattern", stringProp("可直接修改的详情页 URL 正则。"))
                        put("ruleSearch", objectProp("可直接替换或修改的搜索规则对象。"))
                        put("ruleBookInfo", objectProp("可直接替换或修改的详情规则对象。"))
                        put("ruleToc", objectProp("可直接替换或修改的目录规则对象。"))
                        put("ruleContent", objectProp("可直接替换或修改的正文规则对象。"))
                        put("ruleExplore", objectProp("可直接替换或修改的发现规则对象。"))
                        put("comment", stringProp("可直接修改的书源注释。"))
                    })
                    put("additionalProperties", true)
                })
            })
        }
    }

    private fun fetchHtmlDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_FETCH_HTML)
                put(
                    "description",
                    "按 Legado 的 AnalyzeUrl/书源配置真实获取网页 HTML，用于书源 agent 分析搜索页、详情页、目录页、正文页。可传 sourceJson 或 bookSourceUrl 复用书源 header/cookie/webView 配置；返回状态码、最终 URL、HTML 片段。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("url", stringProp("要获取的搜索页、详情页、目录页或正文页 URL。支持 Legado URL 规则。"))
                        put("bookSourceUrl", stringProp("可选，本地已保存书源 URL，用于复用 header/cookie 等配置。"))
                        put("sourceJson", stringProp("可选，临时书源完整 JSON，用于复用 header/cookie 等配置。"))
                        put("useWebView", booleanProp("是否允许使用书源 URL 规则中的 WebView 配置，默认 true。"))
                        put("js", stringProp("可选，页面加载后执行的 JS。"))
                        put("sourceRegex", stringProp("可选，WebView 抓取源码的匹配规则。"))
                        put("timeoutMs", JSONObject().apply {
                            put("type", "integer")
                            put("description", "请求超时毫秒，默认 45000，最大 90000。")
                            put("minimum", 10000)
                            put("maximum", 90000)
                        })
                        put("maxChars", JSONObject().apply {
                            put("type", "integer")
                            put("description", "返回 HTML 最大字符数，默认 20000，最大 80000。")
                            put("minimum", 1000)
                            put("maximum", 80000)
                        })
                    })
                    put("required", JSONArray().put("url"))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun debugSourceDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_DEBUG_SOURCE)
                put(
                    "description",
                    "使用 Legado 原生 Debug 流程调试书源，支持搜索、详情 URL、发现、目录和正文。返回调试日志。若 success=false，必须根据 logs 修改 sourceJson，然后再次调用 create_book_source(save=false) 和 debug_book_source，最多重试 3 轮。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookSourceUrl", stringProp("本地已保存书源的 URL。"))
                        put("sourceJson", stringProp("临时调试用完整 BookSource JSON。"))
                        put(
                            "key",
                            stringProp(
                                "调试入口：普通关键词为搜索；绝对 URL 为详情；title::url 为发现；++url 为目录；--url 为正文。默认使用规则里的 checkKeyWord 或“我的”。"
                            )
                        )
                        put("timeoutMs", JSONObject().apply {
                            put("type", "integer")
                            put("description", "调试超时毫秒，默认 45000，最大 90000。")
                            put("minimum", 10000)
                            put("maximum", 90000)
                        })
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun createBookSource(args: JSONObject?): String {
        val source = resolveSource(args, allowDbLookup = false) ?: return error("缺少 sourceJson 或 bookSourceUrl")
        val save = args?.optBoolean("save", false) == true
        if (save) {
            appDb.bookSourceDao.insert(source)
        }
        return ok().apply {
            put("saved", save)
            put("source", JSONObject(GSON.toJson(source)))
        }.toString()
    }

    private fun getBookSource(args: JSONObject?): String {
        val sourceUrls = linkedSetOf<String>().apply {
            args?.optString("bookSourceUrl").orEmpty().trim().takeIf { it.isNotBlank() }?.let(::add)
            args?.optJSONArray("bookSourceUrls")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (sourceUrls.isNotEmpty()) {
            val sources = sourceUrls.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
            if (sources.isEmpty()) return error("未找到指定书源")
            return ok().apply {
                if (sources.size == 1) {
                    put("source", JSONObject(GSON.toJson(sources.first())))
                }
                put("sources", JSONArray().apply {
                    sources.forEach { put(JSONObject(GSON.toJson(it))) }
                })
            }.toString()
        }
        val searchKeys = linkedSetOf<String>().apply {
            args?.optString("searchKey").orEmpty().trim().takeIf { it.isNotBlank() }?.let(::add)
            args?.optJSONArray("searchKeys")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (searchKeys.isEmpty()) {
            return error("缺少 bookSourceUrl 或 searchKey")
        }
        val limit = (args?.optInt("limit", 10) ?: 10).coerceIn(1, 30)
        val sources = appDb.bookSourceDao.all.filter { source ->
            searchKeys.any { key ->
                source.bookSourceName.contains(key, ignoreCase = true) ||
                    source.bookSourceUrl.contains(key, ignoreCase = true) ||
                    source.bookSourceGroup.orEmpty().contains(key, ignoreCase = true) ||
                    source.bookSourceComment.orEmpty().contains(key, ignoreCase = true)
            }
        }.take(limit)
        return ok().apply {
            put("count", sources.size)
            put("sources", JSONArray().apply {
                sources.forEach { source ->
                    put(JSONObject().apply {
                        put("bookSourceUrl", source.bookSourceUrl)
                        put("bookSourceName", source.bookSourceName)
                        put("bookSourceGroup", source.bookSourceGroup)
                        put("enabled", source.enabled)
                        put("enabledExplore", source.enabledExplore)
                        put("hasSearchUrl", !source.searchUrl.isNullOrBlank())
                        put("hasExploreUrl", !source.exploreUrl.isNullOrBlank())
                        put("comment", source.bookSourceComment)
                    })
                }
            })
        }.toString()
    }

    private fun updateBookSource(args: JSONObject?): String {
        args ?: return error("缺少参数")
        val source = resolveSource(args, allowDbLookup = true) ?: return error("缺少 sourceJson 或有效 bookSourceUrl")
        val merged = JSONObject(GSON.toJson(source))
        val patch = readPatch(args)
        if (patch.length() > 0) {
            mergeJson(merged, patch)
        } else if (!args.has("sourceJson")) {
            return error(
                "缺少修改内容。请传 patch，例如 {\"ruleToc\":{\"chapterList\":\".list dd\",\"chapterName\":\"a@text\",\"chapterUrl\":\"a@href\"}}，也可以直接传 ruleToc/ruleContent/searchUrl 等字段。"
            )
        }
        val updated = GSON.fromJsonObject<BookSource>(merged.toString()).getOrNull()
            ?: return error("修改后的书源 JSON 无法解析")
        val save = args.optBoolean("save", false)
        if (save) {
            appDb.bookSourceDao.insert(updated)
        }
        return ok().apply {
            put("saved", save)
            put("source", JSONObject(GSON.toJson(updated)))
        }.toString()
    }

    private suspend fun fetchSourceHtml(args: JSONObject?): String = coroutineScope {
        args ?: return@coroutineScope error("缺少参数")
        val url = args.optString("url").trim()
        if (url.isBlank()) {
            return@coroutineScope error("缺少 url")
        }
        val timeoutMs = (args.optLong("timeoutMs", 45_000L)).coerceIn(10_000L, 90_000L)
        val maxChars = (args.optInt("maxChars", 20_000)).coerceIn(1_000, 80_000)
        val source = resolveSource(args, allowDbLookup = true) ?: temporarySourceFor(url)
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
                put("htmlLength", body.length)
                put("truncated", body.length > maxChars)
                put("html", body.take(maxChars))
            }.toString()
        }.getOrElse { throwable ->
            error(throwable.localizedMessage ?: throwable.javaClass.simpleName)
        }
    }

    private suspend fun debugBookSource(args: JSONObject?): String = coroutineScope {
        val source = resolveSource(args, allowDbLookup = true) ?: return@coroutineScope error("未找到书源")
        val key = args?.optString("key").orEmpty()
            .ifBlank { source.ruleSearch?.checkKeyWord.orEmpty() }
            .ifBlank { "我的" }
        val timeoutMs = (args?.optLong("timeoutMs", 45_000L) ?: 45_000L)
            .coerceIn(10_000L, 90_000L)
        val logs = arrayListOf<String>()
        val finished = CompletableDeferred<Int>()
        Debug.callback = object : Debug.Callback {
            override fun printLog(state: Int, msg: String) {
                logs += msg
                if ((state == -1 || state == 1000) && !finished.isCompleted) {
                    finished.complete(state)
                }
            }
        }
        Debug.startDebug(this, source, key)
        val state = withTimeoutOrNull(timeoutMs) { finished.await() }
        Debug.cancelDebug(true)
        ok().apply {
            put("bookSourceUrl", source.bookSourceUrl)
            put("key", key)
            put("finished", state != null)
            put("success", state == 1000)
            put("logs", JSONArray(logs.takeLast(80)))
        }.toString()
    }

    private fun resolveSource(args: JSONObject?, allowDbLookup: Boolean): BookSource? {
        args ?: return null
        args.optString("sourceJson").takeIf { it.isNotBlank() }?.let { json ->
            GSON.fromJsonObject<BookSource>(json).getOrNull()?.let { return it }
        }
        val sourceUrl = args.optString("bookSourceUrl").trim()
        if (allowDbLookup && sourceUrl.isNotBlank()) {
            appDb.bookSourceDao.getBookSource(sourceUrl)?.let { return it }
        }
        if (sourceUrl.isBlank()) return null
        return BookSource(
            bookSourceUrl = sourceUrl,
            bookSourceName = args.optString("bookSourceName").ifBlank { sourceUrl },
            bookSourceGroup = args.optString("bookSourceGroup").takeIf { it.isNotBlank() },
            bookUrlPattern = args.optString("bookUrlPattern").takeIf { it.isNotBlank() },
            searchUrl = args.optString("searchUrl").takeIf { it.isNotBlank() },
            exploreUrl = args.optString("exploreUrl").takeIf { it.isNotBlank() },
            bookSourceComment = args.optString("comment").takeIf { it.isNotBlank() },
            ruleSearch = args.optJSONObject("ruleSearch")?.toRule(),
            ruleBookInfo = args.optJSONObject("ruleBookInfo")?.toRule(),
            ruleToc = args.optJSONObject("ruleToc")?.toRule(),
            ruleContent = args.optJSONObject("ruleContent")?.toRule(),
            ruleExplore = args.optJSONObject("ruleExplore")?.toRule()
        )
    }

    private fun mergeJson(target: JSONObject, patch: JSONObject) {
        patch.keys().forEach { key ->
            val value = patch.opt(key)
            if (value == JSONObject.NULL) {
                target.put(key, JSONObject.NULL)
                return@forEach
            }
            val oldValue = target.opt(key)
            if (oldValue is JSONObject && value is JSONObject) {
                mergeJson(oldValue, value)
            } else {
                target.put(key, value)
            }
        }
    }

    private fun readPatch(args: JSONObject): JSONObject {
        val directPatch = when (val value = args.opt("patch")) {
            is JSONObject -> value
            is String -> value.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            else -> null
        } ?: JSONObject()
        val generatedPatch = JSONObject()
        val reservedKeys = setOf("sourceJson", "bookSourceUrl", "patch", "save")
        args.keys().forEach { key ->
            if (key !in reservedKeys) {
                val targetKey = when (key) {
                    "comment" -> "bookSourceComment"
                    else -> key
                }
                generatedPatch.put(targetKey, args.opt(key))
            }
        }
        mergeJson(generatedPatch, directPatch)
        return generatedPatch
    }

    private fun temporarySourceFor(url: String): BookSource {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val origin = if (host.isNotBlank()) {
            runCatching {
                val uri = URI(url)
                "${uri.scheme}://${uri.host}"
            }.getOrDefault(url)
        } else {
            url
        }
        return BookSource(
            bookSourceUrl = origin,
            bookSourceName = host.ifBlank { origin }
        )
    }

    private inline fun <reified T> JSONObject.toRule(): T? {
        return GSON.fromJsonObject<T>(toString()).getOrNull()
    }

    private fun stringProp(description: String) = JSONObject().apply {
        put("type", "string")
        put("description", description)
    }

    private fun booleanProp(description: String) = JSONObject().apply {
        put("type", "boolean")
        put("description", description)
    }

    private fun objectProp(description: String) = JSONObject().apply {
        put("type", "object")
        put("description", description)
        put("additionalProperties", true)
    }

    private fun ok() = JSONObject().put("ok", true)

    private fun error(message: String) = JSONObject().apply {
        put("ok", false)
        put("error", message)
    }.toString()
}
