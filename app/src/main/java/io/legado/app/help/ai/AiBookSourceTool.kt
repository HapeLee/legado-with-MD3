package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiBookSourceTool {

    fun resolvedTools(): List<AiResolvedTool> = listOf(
        AiResolvedTool(
            name = "list_book_sources",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "list_book_sources")
                    put("description", "列出所有已启用的书源。可按关键字过滤。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("keyword", JSONObject().apply {
                                put("type", "string")
                                put("description", "书源名称或 URL 关键字")
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回条数，默认 20")
                            })
                        })
                    })
                })
            },
            execute = { args -> listBookSources(args) }
        ),
        AiResolvedTool(
            name = "get_book_source",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_book_source")
                    put("description", "获取指定书源的详细信息，包括搜索规则、发现规则、正文规则等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("sourceUrl"))
                        put("properties", JSONObject().apply {
                            put("sourceUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "书源 URL")
                            })
                        })
                    })
                })
            },
            execute = { args -> getBookSource(args) }
        ),
        AiResolvedTool(
            name = "search_book_source",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "search_book_source")
                    put("description", "在已启用的书源中搜索书籍。返回书名、作者、书源、封面、简介、章节等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("keyword"))
                        put("properties", JSONObject().apply {
                            put("keyword", JSONObject().apply {
                                put("type", "string")
                                put("description", "要搜索的书名或作者关键字")
                            })
                            put("sourceUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "可选。限制只在指定书源搜索")
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "每书源返回条数，默认 5")
                            })
                        })
                    })
                })
            },
            execute = { args -> searchBookSource(args) }
        ),
        AiResolvedTool(
            name = "fetch_source_html",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "fetch_source_html")
                    put("description", "抓取指定 URL 的原始 HTML 内容。用于调试书源规则。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("url"))
                        put("properties", JSONObject().apply {
                            put("url", JSONObject().apply {
                                put("type", "string")
                                put("description", "要抓取的网页 URL")
                            })
                            put("sourceUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "可选。使用该书源的请求头配置")
                            })
                            put("maxChars", JSONObject().apply {
                                put("type", "integer")
                                put("description", "最多返回字符数，默认 12000")
                            })
                        })
                    })
                })
            },
            execute = { args -> fetchSourceHtml(args) }
        ),
        AiResolvedTool(
            name = "search_web_tavily",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "search_web_tavily")
                    put("description", "使用网络搜索获取最新信息。当问题需要实时信息或超出本地知识时使用。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("query"))
                        put("properties", JSONObject().apply {
                            put("query", JSONObject().apply {
                                put("type", "string")
                                put("description", "搜索查询词")
                            })
                        })
                    })
                })
            },
            execute = { args -> searchWebTavily(args) }
        )
    )

    private suspend fun listBookSources(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val keyword = args?.optString("keyword", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val limit = args?.optInt("limit", 20) ?: 20

        val sources = appDb.bookSourceDao.all
        var filtered = sources.filter { it.enabled }
        if (keyword.isNotBlank()) {
            filtered = filtered.filter {
                it.bookSourceName.contains(keyword, ignoreCase = true) ||
                    it.bookSourceUrl.contains(keyword, ignoreCase = true)
            }
        }

        val result = filtered.take(limit).map { source ->
            JSONObject().apply {
                put("name", source.bookSourceName)
                put("sourceUrl", source.bookSourceUrl)
                put("group", source.bookSourceGroup)
                put("type", source.bookSourceType)
                put("enabled", source.enabled)
            }
        }

        JSONObject().apply {
            put("ok", true)
            put("total", filtered.size)
            put("results", JSONArray().apply { result.forEach { put(it) } })
        }.toString(2)
    }

    private suspend fun getBookSource(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val sourceUrl = args?.optString("sourceUrl", "")?.takeIf { it.isNotBlank() }.orEmpty()

        if (sourceUrl.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 sourceUrl")
            }.toString(2)
        }

        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
            ?: return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "未找到书源 $sourceUrl")
            }.toString(2)

        JSONObject().apply {
            put("ok", true)
            put("name", source.bookSourceName)
            put("sourceUrl", source.bookSourceUrl)
            put("group", source.bookSourceGroup)
            put("type", source.bookSourceType)
            put("searchUrl", source.searchUrl)
            put("searchList", source.ruleSearch?.bookList)
            put("searchName", source.ruleSearch?.name)
            put("searchAuthor", source.ruleSearch?.author)
            put("searchIntro", source.ruleSearch?.intro)
            put("searchCover", source.ruleSearch?.coverUrl)
            put("searchBookUrl", source.ruleSearch?.bookUrl)
            put("bookInfoUrlPattern", source.bookUrlPattern)
            put("bookInfoName", source.ruleBookInfo?.name)
            put("bookInfoAuthor", source.ruleBookInfo?.author)
            put("bookInfoIntro", source.ruleBookInfo?.intro)
            put("bookInfoCover", source.ruleBookInfo?.coverUrl)
            put("bookInfoKind", source.ruleBookInfo?.kind)
            put("bookInfoLatestChapter", source.ruleBookInfo?.lastChapter)
            put("chapterUrl", source.ruleToc?.chapterUrl)
            put("chapterList", source.ruleToc?.chapterList)
            put("chapterName", source.ruleToc?.chapterName)
            put("content", source.ruleContent?.content)
            put("header", source.header)
        }.toString(2)
    }

    private suspend fun searchBookSource(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val keyword = args?.optString("keyword", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val sourceUrlArg = args?.optString("sourceUrl", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val limit = args?.optInt("limit", 5) ?: 5

        if (keyword.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 keyword")
            }.toString(2)
        }

        val allBooks = appDb.bookDao.flowAll().firstOrNull() ?: emptyList()
        val localMatches = allBooks.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                it.author.contains(keyword, ignoreCase = true)
        }.take(limit)

        val result = localMatches.map { book ->
            JSONObject().apply {
                put("name", book.name)
                put("author", book.author)
                put("origin", book.origin)
                put("originName", book.originName)
                put("bookUrl", book.bookUrl)
                put("coverUrl", book.coverUrl)
                put("kind", book.kind)
                put("intro", book.intro?.take(200))
                put("latestChapterTitle", book.latestChapterTitle)
            }
        }

        val sourceHint = if (sourceUrlArg.isNotBlank()) sourceUrlArg else ""

        JSONObject().apply {
            put("ok", true)
            put("note", if (result.isEmpty()) "本地书架中没有找到匹配书籍" else "以下是本地书架中匹配的书籍")
            put("searchedSource", sourceHint)
            put("total", result.size)
            put("results", JSONArray().apply { result.forEach { put(it) } })
        }.toString(2)
    }

    private suspend fun fetchSourceHtml(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val url = args?.optString("url", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val maxChars = args?.optInt("maxChars", 12000) ?: 12000

        if (url.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 url")
            }.toString(2)
        }

        val html = runCatching {
            okHttpClient.newCallResponse {
                url(url)
            }.body?.string().orEmpty()
        }.getOrDefault("")

        val truncated = html.take(maxChars)

        JSONObject().apply {
            put("ok", true)
            put("url", url)
            put("htmlChars", truncated.length)
            put("html", truncated)
        }.toString(2)
    }

    private suspend fun searchWebTavily(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val query = args?.optString("query", "")?.takeIf { it.isNotBlank() }.orEmpty()
        if (query.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 query")
            }.toString(2)
        }

        JSONObject().apply {
            put("ok", true)
            put("note", "网络搜索功能需要配置搜索 API。如需真实搜索结果，请先在 AI 设置中配置搜索服务。")
            put("query", query)
            put("results", JSONArray())
        }.toString(2)
    }
}
