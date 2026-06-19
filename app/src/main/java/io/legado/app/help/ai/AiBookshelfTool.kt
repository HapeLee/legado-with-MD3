package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.model.ReadBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiBookshelfTool {

    fun resolvedTools(): List<AiResolvedTool> = listOf(
        AiResolvedTool(
            name = "query_bookshelf",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "query_bookshelf")
                    put("description", "查询本地书架中的书籍。支持按关键字、分组、标签、是否在读、最近阅读等筛选。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("keyword", JSONObject().apply {
                                put("type", "string")
                                put("description", "书名或作者关键字")
                            })
                            put("group", JSONObject().apply {
                                put("type", "string")
                                put("description", "分组名")
                            })
                            put("tag", JSONObject().apply {
                                put("type", "string")
                                put("description", "标签名")
                            })
                            put("onlyReading", JSONObject().apply {
                                put("type", "boolean")
                                put("description", "只返回正在读的书")
                            })
                            put("onlyRecent", JSONObject().apply {
                                put("type", "boolean")
                                put("description", "只返回最近读过的书")
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回条数，默认20")
                            })
                        })
                    })
                })
            },
            execute = { args -> queryBookshelf(args) }
        ),
        AiResolvedTool(
            name = "get_bookshelf_book_info",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_bookshelf_book_info")
                    put("description", "根据书的名称或 URL 获取某本书的详细信息，包括简介、最近章节、阅读进度。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("bookName", JSONObject().apply {
                                put("type", "string")
                                put("description", "书名（模糊匹配，优先）")
                            })
                            put("bookUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "书的唯一 URL（精确匹配）")
                            })
                        })
                    })
                })
            },
            execute = { args -> getBookInfo(args) }
        ),
        AiResolvedTool(
            name = "list_book_chapters",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "list_book_chapters")
                    put("description", "列出某本书的章节目录。返回章节标题、索引、是否已读等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("bookUrl"))
                        put("properties", JSONObject().apply {
                            put("bookUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "书的 URL")
                            })
                            put("offset", JSONObject().apply {
                                put("type", "integer")
                                put("description", "起始章节索引，默认 0")
                            })
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回条数，默认 50")
                            })
                        })
                    })
                })
            },
            execute = { args -> listBookChapters(args) }
        ),
        AiResolvedTool(
            name = "read_book_chapter_content",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "read_book_chapter_content")
                    put("description", "读取指定章节的正文内容。用于内容总结、情节分析、人物关系、翻译等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("bookUrl").put("chapterIndex"))
                        put("properties", JSONObject().apply {
                            put("bookUrl", JSONObject().apply {
                                put("type", "string")
                                put("description", "书的 URL")
                            })
                            put("chapterIndex", JSONObject().apply {
                                put("type", "integer")
                                put("description", "章节索引（从 0 开始）")
                            })
                            put("maxChars", JSONObject().apply {
                                put("type", "integer")
                                put("description", "最多返回字符数，默认 8000")
                            })
                        })
                    })
                })
            },
            execute = { args -> readChapterContent(args) }
        ),
        AiResolvedTool(
            name = "query_read_records",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "query_read_records")
                    put("description", "查询阅读记录，返回最近阅读的书、阅读时长、最后阅读时间等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("limit", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回条数，默认 10")
                            })
                        })
                    })
                })
            },
            execute = { args -> queryReadRecords(args) }
        )
    )

    private suspend fun queryBookshelf(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val keyword = args?.optString("keyword", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val group = args?.optString("group", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val tag = args?.optString("tag", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val onlyReading = args?.optBoolean("onlyReading", false) ?: false
        val onlyRecent = args?.optBoolean("onlyRecent", false) ?: false
        val limit = args?.optInt("limit", 20) ?: 20

        val allBooks = appDb.bookDao.flowAll().firstOrNull() ?: emptyList()
        var filtered = allBooks

        if (keyword.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(keyword, ignoreCase = true) ||
                    it.author.contains(keyword, ignoreCase = true)
            }
        }
        if (group.isNotBlank()) {
            filtered = filtered.filter { it.group == group }
        }
        if (tag.isNotBlank()) {
            filtered = filtered.filter {
                it.bookTag?.split(",")?.any { t -> t.trim() == tag } == true
            }
        }
        if (onlyReading) {
            filtered = filtered.filter { it.durChapterTitle != null }
        }
        if (onlyRecent) {
            filtered = filtered.sortedByDescending { it.lastCheckCount }.take(limit)
        }

        val result = filtered.take(limit).map { book ->
            JSONObject().apply {
                put("name", book.name)
                put("author", book.author)
                put("bookUrl", book.bookUrl)
                put("origin", book.origin)
                put("kind", book.kind)
                put("group", book.group)
                put("intro", book.intro.take(200))
                put("lastChapter", book.latestChapterTitle)
                put("currentChapter", book.durChapterTitle)
                put("lastReadTime", book.lastCheckTime)
            }
        }

        JSONObject().apply {
            put("ok", true)
            put("total", filtered.size)
            put("results", JSONArray().apply { result.forEach { put(it) } })
        }.toString(2)
    }

    private suspend fun getBookInfo(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val bookName = args?.optString("bookName", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val bookUrl = args?.optString("bookUrl", "")?.takeIf { it.isNotBlank() }.orEmpty()

        val allBooks = appDb.bookDao.flowAll().firstOrNull() ?: emptyList()
        val book: Book? = when {
            bookUrl.isNotBlank() -> allBooks.firstOrNull { it.bookUrl == bookUrl }
            bookName.isNotBlank() -> allBooks.firstOrNull {
                it.name.contains(bookName, ignoreCase = true)
            }
            else -> null
        }

        if (book == null) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "未找到匹配的书")
            }.toString(2)
        }

        JSONObject().apply {
            put("ok", true)
            put("name", book.name)
            put("author", book.author)
            put("bookUrl", book.bookUrl)
            put("origin", book.origin)
            put("originName", book.originName)
            put("kind", book.kind)
            put("group", book.group)
            put("intro", book.intro)
            put("coverUrl", book.coverUrl)
            put("latestChapterTitle", book.latestChapterTitle)
            put("currentChapterTitle", book.durChapterTitle)
            put("currentChapterIndex", book.durChapterIndex)
            put("totalChapters", book.totalChapterNum)
            put("lastReadTime", book.lastCheckTime)
            put("wordCount", book.lastCheckCount)
        }.toString(2)
    }

    private suspend fun listBookChapters(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val bookUrl = args?.optString("bookUrl", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val offset = args?.optInt("offset", 0) ?: 0
        val limit = args?.optInt("limit", 50) ?: 50

        if (bookUrl.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 bookUrl")
            }.toString(2)
        }

        val chapters = appDb.bookChapterDao.getChapterByBook(bookUrl)
            .drop(offset)
            .take(limit)
            .map { chapter ->
                JSONObject().apply {
                    put("index", chapter.index)
                    put("title", chapter.title)
                    put("url", chapter.url)
                    put("isRead", chapter.isRead)
                }
            }

        JSONObject().apply {
            put("ok", true)
            put("total", appDb.bookChapterDao.getChapterByBook(bookUrl).size)
            put("offset", offset)
            put("results", JSONArray().apply { chapters.forEach { put(it) } })
        }.toString(2)
    }

    private suspend fun readChapterContent(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val bookUrl = args?.optString("bookUrl", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val chapterIndex = args?.optInt("chapterIndex", -1) ?: -1
        val maxChars = args?.optInt("maxChars", 8000) ?: 8000

        if (bookUrl.isEmpty() || chapterIndex < 0) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 bookUrl 和 chapterIndex")
            }.toString(2)
        }

        val chapter = appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
        if (chapter == null) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "未找到章节 $chapterIndex")
            }.toString(2)
        }

        val content = if (chapter.isRead) {
            appDb.bookSourceDao.getBookSource(chapter.origin)?.let { source ->
                runCatching {
                    io.legado.app.model.analyzeRule.AnalyzeRule(source).getContent(chapter.url, bookUrl)
                }.getOrDefault("")
            } ?: ""
        } else {
            ""
        }

        val truncated = content.take(maxChars)

        JSONObject().apply {
            put("ok", true)
            put("bookUrl", bookUrl)
            put("chapterIndex", chapterIndex)
            put("chapterTitle", chapter.title)
            put("contentChars", truncated.length)
            put("content", truncated)
        }.toString(2)
    }

    private suspend fun queryReadRecords(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val limit = args?.optInt("limit", 10) ?: 10

        val allBooks = appDb.bookDao.flowAll().firstOrNull() ?: emptyList()
        val records = allBooks
            .filter { it.lastCheckTime > 0 }
            .sortedByDescending { it.lastCheckTime }
            .take(limit)
            .map { book ->
                JSONObject().apply {
                    put("name", book.name)
                    put("author", book.author)
                    put("bookUrl", book.bookUrl)
                    put("currentChapter", book.durChapterTitle)
                    put("readCount", book.lastCheckCount)
                    put("lastReadTime", book.lastCheckTime)
                }
            }

        JSONObject().apply {
            put("ok", true)
            put("results", JSONArray().apply { records.forEach { put(it) } })
        }.toString(2)
    }
}
