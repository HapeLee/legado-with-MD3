package io.legado.app.help.ai

import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiLibraryTool {

    private const val TOOL_QUERY_READ_RECORDS = "query_read_records"
    private const val TOOL_LIST_BOOK_SOURCES = "list_book_sources"
    private const val TOOL_SEARCH_BOOK_SOURCE = "search_book_source"
    private const val DEFAULT_LIMIT = 8
    private const val MAX_LIMIT = 20
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_QUERY_READ_RECORDS,
                definition = queryReadRecordsDefinition(),
                execute = { args -> queryReadRecords(args) }
            ),
            AiResolvedTool(
                name = TOOL_LIST_BOOK_SOURCES,
                definition = listBookSourcesDefinition(),
                execute = { args -> listBookSources(args) }
            ),
            AiResolvedTool(
                name = TOOL_SEARCH_BOOK_SOURCE,
                definition = searchBookSourceDefinition(),
                execute = { args -> searchBookSource(args) }
            )
        )
    }

    private fun queryReadRecordsDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_QUERY_READ_RECORDS)
                put("description", "查询本地阅读时长、每日阅读记录和书籍阅读排行。可按书名或日期范围筛选。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("bookName", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，按书名模糊筛选阅读时长排行。")
                        })
                        put("bookNames", JSONObject().apply {
                            put("type", "array")
                            put("description", "可选，批量按多个书名筛选阅读记录。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("startDate", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，开始日期，格式 yyyy-MM-dd，用于每日阅读记录。")
                        })
                        put("endDate", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，结束日期，格式 yyyy-MM-dd，用于每日阅读记录。")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", MAX_LIMIT)
                        })
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun searchBookSourceDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_SEARCH_BOOK_SOURCE)
                put("description", "调用本地书源搜索书籍，返回可用于打开详情页或视频页的真实搜索结果。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "搜索关键词，通常是书名、作者或影视名。")
                        })
                        put("sourceUrl", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，指定单个书源 URL。")
                        })
                        put("sourceUrls", JSONObject().apply {
                            put("type", "array")
                            put("description", "可选，批量指定多个书源 URL。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("sourceName", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，指定书源名称。sourceUrl 优先。")
                        })
                        put("sourceNames", JSONObject().apply {
                            put("type", "array")
                            put("description", "可选，批量指定多个书源名称。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("sourceGroup", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，指定书源分组。sourceUrl 优先。")
                        })
                        put("sourceGroups", JSONObject().apply {
                            put("type", "array")
                            put("description", "可选，批量指定多个书源分组。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("mode", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("single", "batch")))
                            put("description", "single 返回聚合结果；batch 额外返回按书源拆分的结果。默认 single。")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", MAX_LIMIT)
                        })
                    })
                    put("required", JSONArray(listOf("keyword")))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun listBookSourcesDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_LIST_BOOK_SOURCES)
                put("description", "读取本地书源列表，用于查看可用书源、按名称/分组/类型筛选，并为 search_book_source 选择 sourceUrl 或 sourceName。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("keyword", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，按书源名称、URL 或分组模糊筛选。")
                        })
                        put("sourceGroup", JSONObject().apply {
                            put("type", "string")
                            put("description", "可选，按书源分组筛选。")
                        })
                        put("sourceGroups", JSONObject().apply {
                            put("type", "array")
                            put("description", "可选，按多个书源分组筛选。")
                            put("items", JSONObject().apply { put("type", "string") })
                        })
                        put("sourceType", JSONObject().apply {
                            put("type", "integer")
                            put("description", "可选，书源类型：0文本，1音频，2图片/漫画，3文件，4视频。")
                        })
                        put("enabledOnly", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "是否只返回启用书源，默认 true。")
                        })
                        put("limit", JSONObject().apply {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 100)
                        })
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private suspend fun queryReadRecords(arguments: JSONObject?): String = withContext(IO) {
        val bookNames = linkedSetOf<String>().apply {
            arguments?.optString("bookName")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            arguments?.optJSONArray("bookNames")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val startDate = arguments?.optString("startDate")?.trim().orEmpty()
        val endDate = arguments?.optString("endDate")?.trim().orEmpty()
        val limit = (arguments?.optInt("limit", DEFAULT_LIMIT) ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        val bookRecords = if (bookNames.isEmpty()) {
            appDb.readRecordDao.allShow
        } else {
            appDb.readRecordDao.allShow.filter { record ->
                bookNames.any { name -> record.bookName.contains(name, ignoreCase = true) }
            }
        }.sortedByDescending { it.readTime }.take(limit)
        val dailyRecords = appDb.readRecordDailyDao.allDesc.filter { record ->
            (startDate.isBlank() || record.date >= startDate) &&
                    (endDate.isBlank() || record.date <= endDate)
        }.take(limit)
        JSONObject().apply {
            put("ok", true)
            put("queryBookNames", JSONArray(bookNames.toList()))
            put("totalReadTimeMillis", appDb.readRecordDao.allTime)
            put("totalReadTimeText", formatDuration(appDb.readRecordDao.allTime))
            put("bookRecords", JSONArray().apply {
                bookRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("bookName", record.bookName)
                        put("readTimeMillis", record.readTime)
                        put("readTimeText", formatDuration(record.readTime))
                        put("lastRead", formatTime(record.lastRead))
                    })
                }
            })
            put("dailyRecords", JSONArray().apply {
                dailyRecords.forEach { record ->
                    put(JSONObject().apply {
                        put("date", record.date)
                        put("readTimeMillis", record.readTime)
                        put("readTimeText", formatDuration(record.readTime))
                        put("updatedAt", formatTime(record.updatedAt))
                    })
                }
            })
        }.toString()
    }

    private suspend fun searchBookSource(arguments: JSONObject?): String = withContext(IO) {
        val keyword = arguments?.optString("keyword")?.trim().orEmpty()
        if (keyword.isBlank()) {
            return@withContext errorJson("keyword 不能为空")
        }
        val limit = (arguments?.optInt("limit", DEFAULT_LIMIT) ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        val sources = resolveSources(arguments).filter { !it.searchUrl.isNullOrBlank() }
        if (sources.isEmpty()) {
            return@withContext errorJson("未找到可搜索书源")
        }
        val batchMode = arguments?.optString("mode")?.trim().orEmpty() == "batch"
        val results = arrayListOf<SearchBook>()
        val groupedResults = JSONArray()
        val errors = JSONArray()
        for (source in sources) {
            if (results.size >= limit) break
            runCatching {
                WebBook.searchBookAwait(
                    bookSource = source,
                    key = keyword,
                    page = 1,
                    shouldBreak = { size -> results.size + size >= limit }
                )
            }.onSuccess { books ->
                appDb.searchBookDao.insert(*books.toTypedArray())
                val limitedBooks = books.take(limit - results.size)
                results += limitedBooks
                if (batchMode) {
                    groupedResults.put(JSONObject().apply {
                        put("sourceUrl", source.bookSourceUrl)
                        put("sourceName", source.bookSourceName)
                        put("results", JSONArray().apply {
                            limitedBooks.distinctBy { it.bookUrl }.forEach { put(searchBookToJson(it)) }
                        })
                    })
                }
            }.onFailure { throwable ->
                errors.put(JSONObject().apply {
                    put("source", source.bookSourceName)
                    put("error", throwable.localizedMessage ?: throwable.javaClass.simpleName)
                })
            }
        }
        JSONObject().apply {
            put("ok", true)
            put("keyword", keyword)
            put("searchedSourceCount", sources.size)
            put("mode", if (batchMode) "batch" else "single")
            put("results", JSONArray().apply {
                results.distinctBy { it.bookUrl }.take(limit).forEach { put(searchBookToJson(it)) }
            })
            if (batchMode) {
                put("groupedResults", groupedResults)
            }
            put("errors", errors)
        }.toString()
    }

    private suspend fun listBookSources(arguments: JSONObject?): String = withContext(IO) {
        val keyword = arguments?.optString("keyword")?.trim().orEmpty()
        val sourceGroups = linkedSetOf<String>().apply {
            arguments?.optString("sourceGroup")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            arguments?.optJSONArray("sourceGroups")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val enabledOnly = arguments?.optBoolean("enabledOnly", true) ?: true
        val sourceType = arguments?.takeIf { it.has("sourceType") }?.optInt("sourceType")
        val limit = (arguments?.optInt("limit", 50) ?: 50).coerceIn(1, 100)
        val sources = (if (enabledOnly) appDb.bookSourceDao.allEnabled else appDb.bookSourceDao.all)
            .asSequence()
            .filter { sourceType == null || it.bookSourceType == sourceType }
            .filter {
                sourceGroups.isEmpty() ||
                    it.bookSourceGroup.orEmpty().split(',').any { group -> sourceGroups.contains(group.trim()) }
            }
            .filter {
                keyword.isBlank() ||
                        it.bookSourceName.contains(keyword, ignoreCase = true) ||
                        it.bookSourceUrl.contains(keyword, ignoreCase = true) ||
                        it.bookSourceGroup.orEmpty().contains(keyword, ignoreCase = true)
            }
            .take(limit)
            .toList()
        JSONObject().apply {
            put("ok", true)
            put("querySourceGroups", JSONArray(sourceGroups.toList()))
            put("count", sources.size)
            put("sources", JSONArray().apply {
                sources.forEach { source ->
                    put(JSONObject().apply {
                        put("name", source.bookSourceName)
                        put("url", source.bookSourceUrl)
                        put("group", source.bookSourceGroup.orEmpty())
                        put("type", source.bookSourceType)
                        put("typeName", sourceTypeName(source.bookSourceType))
                        put("enabled", source.enabled)
                        put("enabledExplore", source.enabledExplore)
                        put("hasSearch", !source.searchUrl.isNullOrBlank())
                    })
                }
            })
        }.toString()
    }

    private fun resolveSources(arguments: JSONObject?): List<BookSource> {
        val sourceUrls = linkedSetOf<String>().apply {
            arguments?.optString("sourceUrl")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            arguments?.optJSONArray("sourceUrls")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (sourceUrls.isNotEmpty()) {
            return sourceUrls.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
        }
        val sourceNames = linkedSetOf<String>().apply {
            arguments?.optString("sourceName")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            arguments?.optJSONArray("sourceNames")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (sourceNames.isNotEmpty()) {
            return appDb.bookSourceDao.allEnabled.filter { source ->
                sourceNames.any { name ->
                    source.bookSourceName == name || source.bookSourceName.contains(name, ignoreCase = true)
                }
            }
        }
        val sourceGroups = linkedSetOf<String>().apply {
            arguments?.optString("sourceGroup")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            arguments?.optJSONArray("sourceGroups")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        if (sourceGroups.isNotEmpty()) {
            return appDb.bookSourceDao.allEnabled.filter { source ->
                source.bookSourceGroup.orEmpty().split(',').any { group -> sourceGroups.contains(group.trim()) }
            }
        }
        return appDb.bookSourceDao.allEnabled
    }

    private fun searchBookToJson(book: SearchBook): JSONObject {
        val isVideo = appDb.bookSourceDao.getBookSource(book.origin)?.bookSourceType == BookSourceType.video
        return JSONObject().apply {
            put("name", book.name)
            put("author", book.author)
            put("bookUrl", book.bookUrl)
            put("origin", book.origin)
            put("originName", book.originName)
            put("kind", book.kind ?: "")
            put("coverUrl", book.coverUrl ?: "")
            put("intro", book.intro ?: "")
            put("latestChapterTitle", book.latestChapterTitle ?: "")
            put("target", if (isVideo) "video" else "bookInfo")
            put("openAction", JSONObject().apply {
                put("type", "open_search_book")
                put("target", if (isVideo) "video" else "bookInfo")
                put("name", book.name)
                put("author", book.author)
                put("bookUrl", book.bookUrl)
                put("origin", book.origin)
                put("originName", book.originName)
            })
        }
    }

    private fun sourceTypeName(type: Int): String {
        return when (type) {
            BookSourceType.audio -> "音频"
            BookSourceType.image -> "图片/漫画"
            BookSourceType.file -> "文件"
            BookSourceType.video -> "视频"
            else -> "文本"
        }
    }

    private fun errorJson(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }.toString()
    }

    private fun formatTime(time: Long): String {
        if (time <= 0L) return ""
        return timeFormat.format(Date(time))
    }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60000L
        val days = minutes / (60 * 24)
        val hours = minutes % (60 * 24) / 60
        val leftMinutes = minutes % 60
        return buildString {
            if (days > 0) append(days).append("天")
            if (hours > 0) append(hours).append("小时")
            append(leftMinutes).append("分钟")
        }
    }
}
