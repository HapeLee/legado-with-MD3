package io.legado.app.data.repository

import com.google.gson.JsonObject
import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiToolRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val readRecordDao: ReadRecordDao,
    private val aiArtifactDao: AiArtifactDao,
    private val aiMemoryGateway: AiMemoryGateway
) : AiToolGateway {

    override fun availableTools(): List<AiToolDefinition> = tools

    override fun requiresConfirmation(toolName: String): Boolean {
        return toolName in confirmationRequiredTools
    }

    override suspend fun execute(call: AiToolCall): AiToolResult = withContext(Dispatchers.IO) {
        val args = call.arguments.toJsonObject()
        val content = when (call.name) {
            TOOL_SEARCH_BOOKS -> searchBooks(args)
            TOOL_GET_BOOK_DETAIL -> getBookDetail(args)
            TOOL_LIST_BOOK_CHAPTERS -> listBookChapters(args)
            TOOL_GET_CHAPTER_CONTENT -> getChapterContent(args)
            TOOL_SEARCH_BOOKMARKS -> searchBookmarks(args)
            TOOL_GET_READING_STATS -> getReadingStats(args)
            TOOL_GET_AI_ARTIFACTS -> getAiArtifacts(args)
            TOOL_SAVE_AI_ARTIFACT -> saveAiArtifact(args)
            TOOL_SAVE_MEMORY -> saveMemory(args)
            TOOL_RECALL_MEMORY -> recallMemory(args)
            TOOL_DELETE_MEMORY -> deleteMemory(args)
            else -> """{"error":"Unknown tool: ${call.name}"}"""
        }
        AiToolResult(
            callId = call.id,
            name = call.name,
            content = content
        )
    }

    private fun searchBooks(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 8).coerceIn(1, 20)
        val books = bookDao.all
            .asSequence()
            .filter { book ->
                query.isBlank() ||
                    book.name.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true) ||
                    book.originName.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.durChapterTime }
            .take(limit)
            .map { it.toSummaryMap() }
            .toList()
        return GSON.toJson(mapOf("books" to books))
    }

    private fun getBookDetail(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        return GSON.toJson(
            book.toSummaryMap() + mapOf(
                "bookUrl" to book.bookUrl,
                "kind" to book.kind,
                "intro" to book.getDisplayIntro(),
                "remark" to book.remark,
                "latestChapterTitle" to book.latestChapterTitle,
                "lastCheckCount" to book.lastCheckCount,
                "canUpdate" to book.canUpdate
            )
        )
    }

    private fun listBookChapters(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        val start = args.int("start", 0).coerceAtLeast(0)
        val limit = args.int("limit", 20).coerceIn(1, 80)
        val chapters = if (query.isBlank()) {
            bookChapterDao.getChapterList(book.bookUrl, start, start + limit - 1)
        } else {
            bookChapterDao.search(book.bookUrl, query).drop(start).take(limit)
        }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "chapters" to chapters.map {
                    mapOf(
                        "index" to it.index,
                        "title" to it.title,
                        "isVolume" to it.isVolume,
                        "wordCount" to it.wordCount,
                        "tag" to it.tag
                    )
                }
            )
        )
    }

    private fun getChapterContent(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val chapterIndex = args.int("chapterIndex", book.durChapterIndex).coerceAtLeast(0)
        val maxChars = args.int("maxChars", 6000).coerceIn(500, 12000)
        val chapter = bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            ?: return """{"error":"Chapter not found"}"""
        val rawContent = BookHelp.getContent(book, chapter)
            ?: return """{"error":"Chapter content is not cached locally"}"""
        val content = ContentProcessor.get(book.name, book.origin)
            .getContent(book, chapter, rawContent, includeTitle = false)
            .toString()
            .take(maxChars)
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "chapter" to mapOf("index" to chapter.index, "title" to chapter.title),
                "truncated" to (content.length < rawContent.length),
                "content" to content
            )
        )
    }

    private fun searchBookmarks(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 10).coerceIn(1, 30)
        val bookName = args.string("bookName")?.trim().orEmpty()
        val bookAuthor = args.string("bookAuthor")?.trim().orEmpty()
        val bookmarks = bookmarkDao.all
            .asSequence()
            .filter { bookmark ->
                (bookName.isBlank() || bookmark.bookName.equals(bookName, ignoreCase = true)) &&
                    (bookAuthor.isBlank() || bookmark.bookAuthor.equals(bookAuthor, ignoreCase = true)) &&
                    (query.isBlank() ||
                        bookmark.bookName.contains(query, ignoreCase = true) ||
                        bookmark.bookAuthor.contains(query, ignoreCase = true) ||
                        bookmark.chapterName.contains(query, ignoreCase = true) ||
                        bookmark.content.contains(query, ignoreCase = true) ||
                        bookmark.bookText.contains(query, ignoreCase = true))
            }
            .sortedWith(compareBy({ it.bookName }, { it.chapterIndex }, { it.chapterPos }))
            .take(limit)
            .map {
                mapOf(
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "chapterIndex" to it.chapterIndex,
                    "chapterName" to it.chapterName,
                    "chapterPos" to it.chapterPos,
                    "note" to it.content,
                    "text" to it.bookText.take(500),
                    "time" to it.time
                )
            }
            .toList()
        return GSON.toJson(mapOf("bookmarks" to bookmarks))
    }

    private fun getReadingStats(args: JsonObject): String {
        val query = args.string("query").orEmpty().trim()
        val date = args.string("date")?.trim().orEmpty()
        val limit = args.int("limit", 10).coerceIn(1, 30)
        val records = readRecordDao.all
            .asSequence()
            .filter {
                query.isBlank() ||
                    it.bookName.contains(query, ignoreCase = true) ||
                    it.bookAuthor.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.lastRead }
            .take(limit)
            .map {
                mapOf(
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "readTimeMillis" to it.readTime,
                    "lastReadTime" to it.lastRead
                )
            }
            .toList()
        val dailyDetails = readRecordDao.allDetail
            .asSequence()
            .filter {
                (date.isBlank() || it.date == date) &&
                    (query.isBlank() ||
                        it.bookName.contains(query, ignoreCase = true) ||
                        it.bookAuthor.contains(query, ignoreCase = true))
            }
            .sortedWith(compareByDescending<io.legado.app.data.entities.readRecord.ReadRecordDetail> { it.date }
                .thenByDescending { it.lastReadTime })
            .take(limit)
            .map {
                mapOf(
                    "date" to it.date,
                    "bookName" to it.bookName,
                    "bookAuthor" to it.bookAuthor,
                    "readTimeMillis" to it.readTime,
                    "readWords" to it.readWords,
                    "firstReadTime" to it.firstReadTime,
                    "lastReadTime" to it.lastReadTime
                )
            }
            .toList()
        return GSON.toJson(
            mapOf(
                "totalReadTimeMillis" to readRecordDao.all.sumOf { it.readTime },
                "recentRecords" to records,
                "dailyDetails" to dailyDetails
            )
        )
    }

    private suspend fun getAiArtifacts(args: JsonObject): String {
        val book = resolveBook(args)
        val taskType = args.string("taskType")?.trim()?.takeIf { it.isNotBlank() }
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val limit = args.int("limit", 8).coerceIn(1, 30)
        val artifacts = aiArtifactDao.queryArtifacts(
            bookUrl = book?.bookUrl,
            taskType = taskType,
            chapterIndex = chapterIndex,
            limit = limit
        ).map {
            mapOf(
                "id" to it.id,
                "bookUrl" to it.bookUrl,
                "chapterIndex" to it.chapterIndex,
                "taskType" to it.taskType,
                "status" to it.status,
                "modelProfileId" to it.modelProfileId,
                "updatedAt" to it.updatedAt,
                "output" to it.output.orEmpty().take(4000),
                "errorMessage" to it.errorMessage,
                "truncated" to (it.output.orEmpty().length > 4000)
            )
        }
        return GSON.toJson(
            mapOf(
                "book" to book?.toIdentityMap(),
                "artifacts" to artifacts
            )
        )
    }

    private suspend fun saveAiArtifact(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val output = args.string("output")?.trim().orEmpty()
        if (output.isBlank()) return """{"error":"output is required"}"""
        val taskType = args.string("taskType")?.trim()?.takeIf { it.isNotBlank() } ?: "ai_note"
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val now = System.currentTimeMillis()
        val contentHash = MD5Utils.md5Encode(output)
        val promptHash = MD5Utils.md5Encode("tool:$TOOL_SAVE_AI_ARTIFACT:$taskType")
        val artifact = AiArtifact(
            id = "tool_${book.bookUrl}_${chapterIndex ?: "book"}_${taskType}_${contentHash}",
            taskType = taskType,
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            contentHash = contentHash,
            promptHash = promptHash,
            modelProfileId = "tool",
            status = AiArtifact.STATUS_SUCCESS,
            output = output,
            createdAt = now,
            updatedAt = now
        )
        aiArtifactDao.upsert(artifact)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "artifactId" to artifact.id,
                "book" to book.toIdentityMap(),
                "taskType" to taskType,
                "chapterIndex" to chapterIndex,
                "updatedAt" to now
            )
        )
    }

    private suspend fun saveMemory(args: JsonObject): String {
        val key = args.string("key")?.trim().orEmpty()
        if (key.isBlank()) return """{"error":"key is required"}"""
        val value = args.string("value")?.trim().orEmpty()
        if (value.isBlank()) return """{"error":"value is required"}"""
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        aiMemoryGateway.upsert(
            AiMemory(
                conversationId = conversationId,
                key = key,
                value = value
            )
        )
        return GSON.toJson(mapOf("saved" to true, "key" to key, "scope" to if (conversationId.isBlank()) "global" else "conversation"))
    }

    private suspend fun recallMemory(args: JsonObject): String {
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        val memories = aiMemoryGateway.getForPrompt(conversationId)
        return GSON.toJson(
            mapOf(
                "memories" to memories.map { mapOf("key" to it.key, "value" to it.value, "scope" to if (it.conversationId.isBlank()) "global" else "conversation") }
            )
        )
    }

    private suspend fun deleteMemory(args: JsonObject): String {
        val key = args.string("key")?.trim().orEmpty()
        if (key.isBlank()) return """{"error":"key is required"}"""
        val conversationId = args.string("conversationId")?.trim().orEmpty()
        aiMemoryGateway.delete(conversationId, key)
        return GSON.toJson(mapOf("deleted" to true, "key" to key))
    }

    private fun resolveBook(args: JsonObject): Book? {
        args.string("bookUrl")?.takeIf { it.isNotBlank() }?.let { url ->
            bookDao.getBook(url)?.let { return it }
        }
        val name = args.string("bookName")?.trim().orEmpty()
        val author = args.string("bookAuthor")?.trim().orEmpty()
        if (name.isNotBlank() && author.isNotBlank()) {
            bookDao.getBook(name, author)?.let { return it }
        }
        if (name.isNotBlank()) {
            return bookDao.findByName(name).firstOrNull()
        }
        return bookDao.lastReadBook
    }

    private fun Book.toIdentityMap(): Map<String, Any?> {
        return mapOf(
            "bookUrl" to bookUrl,
            "name" to name,
            "author" to author
        )
    }

    private fun Book.toSummaryMap(): Map<String, Any?> {
        return toIdentityMap() + mapOf(
            "originName" to originName,
            "currentChapterIndex" to durChapterIndex,
            "currentChapterTitle" to durChapterTitle,
            "totalChapterNum" to totalChapterNum,
            "wordCount" to wordCount,
            "lastReadTime" to durChapterTime
        )
    }

    private fun JsonObject.string(name: String): String? {
        return get(name)?.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.int(name: String, defaultValue: Int): Int {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue
    }

    private fun String.toJsonObject(): JsonObject {
        return runCatching { GSON.fromJson(this, JsonObject::class.java) }.getOrNull() ?: JsonObject()
    }

    companion object {
        const val TOOL_SEARCH_BOOKS = "search_books"
        const val TOOL_GET_BOOK_DETAIL = "get_book_detail"
        const val TOOL_LIST_BOOK_CHAPTERS = "list_book_chapters"
        const val TOOL_GET_CHAPTER_CONTENT = "get_chapter_content"
        const val TOOL_SEARCH_BOOKMARKS = "search_bookmarks"
        const val TOOL_GET_READING_STATS = "get_reading_stats"
        const val TOOL_GET_AI_ARTIFACTS = "get_ai_artifacts"
        const val TOOL_SAVE_AI_ARTIFACT = "save_ai_artifact"
        const val TOOL_SAVE_MEMORY = "save_memory"
        const val TOOL_RECALL_MEMORY = "recall_memory"
        const val TOOL_DELETE_MEMORY = "delete_memory"

        private val confirmationRequiredTools = setOf(TOOL_SAVE_AI_ARTIFACT, TOOL_SAVE_MEMORY, TOOL_DELETE_MEMORY)

        private val tools = listOf(
            AiToolDefinition(
                name = TOOL_SEARCH_BOOKS,
                description = "Search the local bookshelf by book title, author, or source name.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Search keyword. Leave empty to list recently read books."),
                    "limit" to intSchema("Maximum number of books to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_BOOK_DETAIL,
                description = "Get metadata and reading progress for one book. If no identifier is given, use the last read book.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title."),
                    "bookAuthor" to stringSchema("Book author.")
                )
            ),
            AiToolDefinition(
                name = TOOL_LIST_BOOK_CHAPTERS,
                description = "List chapter metadata for a local bookshelf book.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Optional chapter title keyword."),
                    "start" to intSchema("Zero-based offset for returned chapters."),
                    "limit" to intSchema("Maximum number of chapters to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_CHAPTER_CONTENT,
                description = "Read cached text content for a chapter. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Zero-based chapter index. Defaults to current reading chapter."),
                    "maxChars" to intSchema("Maximum characters to return, capped by the app.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_BOOKMARKS,
                description = "Search local bookmarks and notes across the bookshelf or within one book.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Keyword for bookmark text, note, book, or chapter."),
                    "bookName" to stringSchema("Optional book title filter."),
                    "bookAuthor" to stringSchema("Optional book author filter."),
                    "limit" to intSchema("Maximum number of bookmarks to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_READING_STATS,
                description = "Get local reading statistics, recent read books, and daily read records.",
                inputSchema = objectSchema(
                    "query" to stringSchema("Optional book title or author filter."),
                    "date" to stringSchema("Optional date filter for daily records, format YYYY-MM-DD."),
                    "limit" to intSchema("Maximum number of records to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_AI_ARTIFACTS,
                description = "Read existing AI artifacts such as chapter summaries for a book or chapter.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Optional zero-based chapter index."),
                    "taskType" to stringSchema("Optional artifact task type, such as summarize_chapter."),
                    "limit" to intSchema("Maximum number of artifacts to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_AI_ARTIFACT,
                description = "Save a user-approved AI note or summary into local AI artifacts. Use only when the user explicitly asks to save content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Optional zero-based chapter index."),
                    "taskType" to stringSchema("Artifact task type, such as ai_note or summarize_chapter."),
                    "output" to stringSchema("The note or summary content to save.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_MEMORY,
                description = "Save a fact or preference about the user to long-term memory. Use when the user shares a preference, fact, or instruction you should remember for future conversations.",
                inputSchema = objectSchema(
                    "key" to stringSchema("Short label for the memory, e.g. 'favorite_genre', 'reading_goal'."),
                    "value" to stringSchema("The fact or preference to remember."),
                    "conversationId" to stringSchema("Leave empty for global memory across all conversations, or pass current conversation id for scoped memory.")
                )
            ),
            AiToolDefinition(
                name = TOOL_RECALL_MEMORY,
                description = "Recall saved memories about the user.",
                inputSchema = objectSchema(
                    "conversationId" to stringSchema("Leave empty to recall global memories, or pass current conversation id for scoped memories.")
                )
            ),
            AiToolDefinition(
                name = TOOL_DELETE_MEMORY,
                description = "Delete a saved memory entry.",
                inputSchema = objectSchema(
                    "key" to stringSchema("The memory key to delete."),
                    "conversationId" to stringSchema("Leave empty for global scope, or pass conversation id for scoped memory.")
                )
            )
        )

        private fun objectSchema(vararg properties: Pair<String, Map<String, Any?>>): Map<String, Any?> {
            return mapOf(
                "type" to "object",
                "properties" to properties.toMap(),
                "additionalProperties" to false
            )
        }

        private fun stringSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "string", "description" to description)
        }

        private fun intSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "integer", "description" to description)
        }
    }
}
