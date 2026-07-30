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
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookOutlineNode
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolDefinition
import io.legado.app.domain.model.AiToolResult
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AiToolRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val readRecordDao: ReadRecordDao,
    private val aiArtifactDao: AiArtifactDao,
    private val aiMemoryGateway: AiMemoryGateway,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
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
            TOOL_GET_CHAPTER_WINDOW -> getChapterWindow(args)
            TOOL_SEARCH_CHAPTER_CONTENT -> searchChapterContent(args)
            TOOL_SEARCH_BOOKMARKS -> searchBookmarks(args)
            TOOL_GET_READING_STATS -> getReadingStats(args)
            TOOL_GET_AI_ARTIFACTS -> getAiArtifacts(args)
            TOOL_SEARCH_BOOK_CHARACTERS -> searchBookCharacters(args)
            TOOL_GET_CHARACTER_PROFILE -> getCharacterProfile(args)
            TOOL_SEARCH_BOOK_KNOWLEDGE -> searchBookKnowledge(args)
            TOOL_GET_BOOK_OUTLINE -> getBookOutline(args)
            TOOL_SAVE_AI_ARTIFACT -> saveAiArtifact(args)
            TOOL_SAVE_BOOK_CHARACTER_PROFILE -> saveBookCharacterProfile(args)
            TOOL_SAVE_BOOK_CHARACTER_EVENT -> saveBookCharacterEvent(args)
            TOOL_SAVE_BOOK_CHARACTER_RELATION -> saveBookCharacterRelation(args)
            TOOL_SAVE_BOOK_KNOWLEDGE_ENTRY -> saveBookKnowledgeEntry(args)
            TOOL_SAVE_BOOK_OUTLINE_NODE -> saveBookOutlineNode(args)
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

    private fun getChapterWindow(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val centerChapterIndex = args.int("chapterIndex", book.durChapterIndex).coerceAtLeast(0)
        val before = args.int("before", 1).coerceIn(0, 5)
        val after = args.int("after", 1).coerceIn(0, 5)
        val maxCharsPerChapter = args.int("maxCharsPerChapter", 2500).coerceIn(300, 6000)
        val start = (centerChapterIndex - before).coerceAtLeast(0)
        val end = centerChapterIndex + after
        val chapters = bookChapterDao.getChapterList(book.bookUrl, start, end)
            .map { chapter ->
                val rawContent = BookHelp.getContent(book, chapter)
                val processedContent = rawContent?.let {
                    ContentProcessor.get(book.name, book.origin)
                        .getContent(book, chapter, it, includeTitle = false)
                        .toString()
                }
                mapOf(
                    "index" to chapter.index,
                    "title" to chapter.title,
                    "isCenter" to (chapter.index == centerChapterIndex),
                    "isCached" to (processedContent != null),
                    "truncated" to ((processedContent?.length ?: 0) > maxCharsPerChapter),
                    "content" to processedContent.orEmpty().take(maxCharsPerChapter)
                )
            }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "centerChapterIndex" to centerChapterIndex,
                "chapters" to chapters
            )
        )
    }

    private fun searchChapterContent(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        if (query.isBlank()) return """{"error":"query is required"}"""
        val aroundChapterIndex = args.int("aroundChapterIndex", book.durChapterIndex)
        val limit = args.int("limit", 6).coerceIn(1, 20)
        val maxChars = args.int("maxChars", 800).coerceIn(200, 2000)
        val chapters = bookChapterDao.getChapterList(book.bookUrl)
            .sortedWith(
                compareBy<io.legado.app.data.entities.BookChapter> {
                    kotlin.math.abs(it.index - aroundChapterIndex)
                }.thenBy { it.index }
            )
        val matches = mutableListOf<Map<String, Any?>>()
        for (chapter in chapters) {
            if (matches.size >= limit) break
            val rawContent = BookHelp.getContent(book, chapter) ?: continue
            val content = ContentProcessor.get(book.name, book.origin)
                .getContent(book, chapter, rawContent, includeTitle = false)
                .toString()
            val titleMatch = chapter.title.contains(query, ignoreCase = true)
            val contentIndex = content.indexOf(query, ignoreCase = true)
            if (!titleMatch && contentIndex < 0) continue
            val excerpt = if (contentIndex >= 0) {
                content.excerptAround(contentIndex, maxChars)
            } else {
                content.take(maxChars)
            }
            matches += mapOf(
                "index" to chapter.index,
                "title" to chapter.title,
                "matchedTitle" to titleMatch,
                "excerpt" to excerpt,
                "truncated" to (excerpt.length < content.length)
            )
        }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "query" to query,
                "aroundChapterIndex" to aroundChapterIndex,
                "matches" to matches
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

    private suspend fun searchBookCharacters(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        val limit = args.int("limit", 8).coerceIn(1, 30)
        val profiles = bookKnowledgeGateway.searchCharacterProfiles(
            bookUrl = book.bookUrl,
            query = query,
            limit = limit,
        ).map { it.toToolMap() }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "query" to query,
                "characters" to profiles,
            )
        )
    }

    private suspend fun getCharacterProfile(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val idOrName = args.string("characterId")
            ?: args.string("characterName")
            ?: return """{"error":"characterId or characterName is required"}"""
        val profile = bookKnowledgeGateway.getCharacterProfile(book.bookUrl, idOrName.trim())
            ?: return """{"error":"Character not found"}"""
        val maxChapterIndex = args.string("maxChapterIndex")?.toIntOrNull()
            ?: args.string("chapterIndex")?.toIntOrNull()
        val limit = args.int("limit", 20).coerceIn(1, 60)
        val events = bookKnowledgeGateway.getCharacterEvents(
            bookUrl = book.bookUrl,
            characterId = profile.id,
            maxChapterIndex = maxChapterIndex,
            limit = limit,
        ).map { it.toToolMap() }
        val relations = bookKnowledgeGateway.getCharacterRelations(
            bookUrl = book.bookUrl,
            characterId = profile.id,
            limit = limit,
        ).map { it.toToolMap() }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "character" to profile.toToolMap(),
                "maxChapterIndex" to maxChapterIndex,
                "events" to events,
                "relations" to relations,
            )
        )
    }

    private suspend fun searchBookKnowledge(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val query = args.string("query").orEmpty().trim()
        val type = args.string("type")?.trim()?.takeIf { it.isNotBlank() }
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val limit = args.int("limit", 12).coerceIn(1, 50)
        val entries = bookKnowledgeGateway.searchKnowledgeEntries(
            bookUrl = book.bookUrl,
            query = query,
            type = type,
            chapterIndex = chapterIndex,
            limit = limit,
        ).map { it.toToolMap() }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "query" to query,
                "type" to type,
                "chapterIndex" to chapterIndex,
                "entries" to entries,
            )
        )
    }

    private suspend fun getBookOutline(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val chapterIndex = args.string("chapterIndex")?.toIntOrNull()
        val nodeType = args.string("nodeType")?.trim()?.takeIf { it.isNotBlank() }
        val limit = args.int("limit", 20).coerceIn(1, 80)
        val nodes = bookKnowledgeGateway.getOutlineNodes(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            nodeType = nodeType,
            limit = limit,
        ).map { it.toToolMap() }
        return GSON.toJson(
            mapOf(
                "book" to book.toIdentityMap(),
                "chapterIndex" to chapterIndex,
                "nodeType" to nodeType,
                "outline" to nodes,
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

    private suspend fun saveBookCharacterProfile(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val name = args.string("name")?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"name is required"}"""
        val now = System.currentTimeMillis()
        val existing = bookKnowledgeGateway.getCharacterProfile(book.bookUrl, name)
        val profile = BookCharacterProfile(
            id = args.string("id")?.trim()?.takeIf { it.isNotBlank() }
                ?: existing?.id
                ?: "character_${MD5Utils.md5Encode("${book.bookUrl}:$name")}",
            bookUrl = book.bookUrl,
            name = name,
            aliasesJson = args.string("aliasesJson") ?: existing?.aliasesJson ?: "[]",
            avatarUri = args.string("avatarUri") ?: existing?.avatarUri,
            tagsJson = args.string("tagsJson") ?: existing?.tagsJson ?: "[]",
            role = args.string("role") ?: existing?.role.orEmpty(),
            voiceGender = args.string("voiceGender") ?: existing?.voiceGender
            ?: BookCharacterProfile.VOICE_GENDER_UNKNOWN,
            voiceAgeBand = args.string("voiceAgeBand") ?: existing?.voiceAgeBand
            ?: BookCharacterProfile.VOICE_AGE_UNKNOWN,
            personality = args.string("personality") ?: existing?.personality.orEmpty(),
            summary = args.string("summary") ?: existing?.summary.orEmpty(),
            status = args.int("status", existing?.status ?: BookCharacterProfile.STATUS_ACTIVE),
            source = args.string("source") ?: BookCharacterProfile.SOURCE_AI,
            confidence = args.float("confidence", existing?.confidence ?: 0.8f),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertCharacterProfile(profile)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "character" to profile.toToolMap()
            )
        )
    }

    private suspend fun saveBookCharacterEvent(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val character = resolveCharacterForKnowledgeTool(book, args)
            ?: return """{"error":"Character not found"}"""
        val content = args.string("content")?.trim().orEmpty()
        if (content.isBlank()) return """{"error":"content is required"}"""
        val now = System.currentTimeMillis()
        val event = BookCharacterEvent(
            id = args.string("id")?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
                .toString(),
            bookUrl = book.bookUrl,
            characterId = character.id,
            chapterIndex = args.string("chapterIndex")?.toIntOrNull(),
            chapterTitle = args.string("chapterTitle").orEmpty(),
            eventTimeText = args.string("eventTimeText").orEmpty(),
            content = content,
            importance = args.int("importance", 0),
            sourceTextHash = args.string("sourceTextHash"),
            evidenceJson = args.string("evidenceJson") ?: "[]",
            source = args.string("source") ?: BookCharacterProfile.SOURCE_AI,
            confidence = args.float("confidence", 0.8f),
            createdAt = now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertCharacterEvent(event)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "event" to event.toToolMap()
            )
        )
    }

    private suspend fun saveBookCharacterRelation(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val from = args.string("fromCharacterId")?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
        } ?: args.string("fromCharacterName")?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
        } ?: return """{"error":"fromCharacterId or fromCharacterName is required"}"""
        val to = args.string("toCharacterId")?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
        } ?: args.string("toCharacterName")?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
        } ?: return """{"error":"toCharacterId or toCharacterName is required"}"""
        val relationType = args.string("relationType")?.trim().orEmpty()
        if (relationType.isBlank()) return """{"error":"relationType is required"}"""
        val now = System.currentTimeMillis()
        val relation = BookCharacterRelation(
            id = args.string("id")?.trim()?.takeIf { it.isNotBlank() }
                ?: "relation_${MD5Utils.md5Encode("${book.bookUrl}:${from.id}:${to.id}")}",
            bookUrl = book.bookUrl,
            fromCharacterId = from.id,
            toCharacterId = to.id,
            relationType = relationType,
            summary = args.string("summary").orEmpty(),
            attitude = args.string("attitude").orEmpty(),
            evidenceJson = args.string("evidenceJson") ?: "[]",
            chapterIndex = args.string("chapterIndex")?.toIntOrNull(),
            status = args.int("status", BookCharacterProfile.STATUS_ACTIVE),
            source = args.string("source") ?: BookCharacterProfile.SOURCE_AI,
            confidence = args.float("confidence", 0.8f),
            createdAt = now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertCharacterRelation(relation)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "relation" to relation.toToolMap()
            )
        )
    }

    private suspend fun saveBookKnowledgeEntry(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val type = args.string("type")?.trim().orEmpty()
        val title = args.string("title")?.trim().orEmpty()
        val content = args.string("content")?.trim().orEmpty()
        if (type.isBlank()) return """{"error":"type is required"}"""
        if (title.isBlank()) return """{"error":"title is required"}"""
        if (content.isBlank()) return """{"error":"content is required"}"""
        val now = System.currentTimeMillis()
        val entry = BookKnowledgeEntry(
            id = args.string("id")?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
                .toString(),
            bookUrl = book.bookUrl,
            type = type,
            title = title,
            keywordsJson = args.string("keywordsJson") ?: "[]",
            content = content,
            scopeStartChapter = args.string("scopeStartChapter")?.toIntOrNull(),
            scopeEndChapter = args.string("scopeEndChapter")?.toIntOrNull(),
            priority = args.int("priority", 0),
            source = args.string("source") ?: BookCharacterProfile.SOURCE_AI,
            confidence = args.float("confidence", 0.8f),
            evidenceJson = args.string("evidenceJson") ?: "[]",
            createdAt = now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertKnowledgeEntry(entry)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "entry" to entry.toToolMap()
            )
        )
    }

    private suspend fun saveBookOutlineNode(args: JsonObject): String {
        val book = resolveBook(args) ?: return """{"error":"Book not found"}"""
        val nodeType = args.string("nodeType")?.trim().orEmpty()
        val title = args.string("title")?.trim().orEmpty()
        val summary = args.string("summary")?.trim().orEmpty()
        if (nodeType.isBlank()) return """{"error":"nodeType is required"}"""
        if (title.isBlank()) return """{"error":"title is required"}"""
        if (summary.isBlank()) return """{"error":"summary is required"}"""
        val now = System.currentTimeMillis()
        val node = BookOutlineNode(
            id = args.string("id")?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID()
                .toString(),
            bookUrl = book.bookUrl,
            parentId = args.string("parentId")?.trim()?.takeIf { it.isNotBlank() },
            nodeType = nodeType,
            title = title,
            summary = summary,
            startChapterIndex = args.string("startChapterIndex")?.toIntOrNull(),
            endChapterIndex = args.string("endChapterIndex")?.toIntOrNull(),
            order = args.int("order", 0),
            source = args.string("source") ?: BookCharacterProfile.SOURCE_AI,
            confidence = args.float("confidence", 0.8f),
            createdAt = now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertOutlineNode(node)
        return GSON.toJson(
            mapOf(
                "saved" to true,
                "book" to book.toIdentityMap(),
                "outlineNode" to node.toToolMap()
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

    private suspend fun resolveCharacterForKnowledgeTool(
        book: Book,
        args: JsonObject,
    ): BookCharacterProfile? {
        args.string("characterId")?.trim()?.takeIf { it.isNotBlank() }?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
                ?.let { profile -> return profile }
        }
        args.string("characterName")?.trim()?.takeIf { it.isNotBlank() }?.let {
            bookKnowledgeGateway.getCharacterProfile(book.bookUrl, it)
                ?.let { profile -> return profile }
        }
        return null
    }

    private fun BookCharacterProfile.toToolMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "bookUrl" to bookUrl,
            "name" to name,
            "aliasesJson" to aliasesJson,
            "avatarUri" to avatarUri,
            "tagsJson" to tagsJson,
            "role" to role,
            "voiceGender" to voiceGender,
            "voiceAgeBand" to voiceAgeBand,
            "personality" to personality,
            "summary" to summary,
            "status" to status,
            "source" to source,
            "confidence" to confidence,
            "updatedAt" to updatedAt,
        )
    }

    private fun BookCharacterEvent.toToolMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "characterId" to characterId,
            "chapterIndex" to chapterIndex,
            "chapterTitle" to chapterTitle,
            "eventTimeText" to eventTimeText,
            "content" to content,
            "importance" to importance,
            "sourceTextHash" to sourceTextHash,
            "evidenceJson" to evidenceJson,
            "source" to source,
            "confidence" to confidence,
            "updatedAt" to updatedAt,
        )
    }

    private fun BookCharacterRelation.toToolMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "fromCharacterId" to fromCharacterId,
            "toCharacterId" to toCharacterId,
            "relationType" to relationType,
            "summary" to summary,
            "attitude" to attitude,
            "evidenceJson" to evidenceJson,
            "chapterIndex" to chapterIndex,
            "status" to status,
            "source" to source,
            "confidence" to confidence,
            "updatedAt" to updatedAt,
        )
    }

    private fun BookKnowledgeEntry.toToolMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "type" to type,
            "title" to title,
            "keywordsJson" to keywordsJson,
            "content" to content,
            "scopeStartChapter" to scopeStartChapter,
            "scopeEndChapter" to scopeEndChapter,
            "priority" to priority,
            "source" to source,
            "confidence" to confidence,
            "evidenceJson" to evidenceJson,
            "updatedAt" to updatedAt,
        )
    }

    private fun BookOutlineNode.toToolMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "parentId" to parentId,
            "nodeType" to nodeType,
            "title" to title,
            "summary" to summary,
            "startChapterIndex" to startChapterIndex,
            "endChapterIndex" to endChapterIndex,
            "order" to order,
            "source" to source,
            "confidence" to confidence,
            "updatedAt" to updatedAt,
        )
    }

    private fun JsonObject.string(name: String): String? {
        return get(name)?.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.int(name: String, defaultValue: Int): Int {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: defaultValue
    }

    private fun JsonObject.float(name: String, defaultValue: Float): Float {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asFloat }.getOrNull()
            ?: defaultValue
    }

    private fun JsonObject.bool(name: String, defaultValue: Boolean): Boolean {
        return runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
            ?: defaultValue
    }

    private fun String.toJsonObject(): JsonObject {
        return runCatching { GSON.fromJson(this, JsonObject::class.java) }.getOrNull() ?: JsonObject()
    }

    companion object {
        const val TOOL_SEARCH_BOOKS = "search_books"
        const val TOOL_GET_BOOK_DETAIL = "get_book_detail"
        const val TOOL_LIST_BOOK_CHAPTERS = "list_book_chapters"
        const val TOOL_GET_CHAPTER_CONTENT = "get_chapter_content"
        const val TOOL_GET_CHAPTER_WINDOW = "get_chapter_window"
        const val TOOL_SEARCH_CHAPTER_CONTENT = "search_chapter_content"
        const val TOOL_SEARCH_BOOKMARKS = "search_bookmarks"
        const val TOOL_GET_READING_STATS = "get_reading_stats"
        const val TOOL_GET_AI_ARTIFACTS = "get_ai_artifacts"
        const val TOOL_SEARCH_BOOK_CHARACTERS = "search_book_characters"
        const val TOOL_GET_CHARACTER_PROFILE = "get_character_profile"
        const val TOOL_SEARCH_BOOK_KNOWLEDGE = "search_book_knowledge"
        const val TOOL_GET_BOOK_OUTLINE = "get_book_outline"
        const val TOOL_SAVE_AI_ARTIFACT = "save_ai_artifact"
        const val TOOL_SAVE_BOOK_CHARACTER_PROFILE = "save_book_character_profile"
        const val TOOL_SAVE_BOOK_CHARACTER_EVENT = "save_book_character_event"
        const val TOOL_SAVE_BOOK_CHARACTER_RELATION = "save_book_character_relation"
        const val TOOL_SAVE_BOOK_KNOWLEDGE_ENTRY = "save_book_knowledge_entry"
        const val TOOL_SAVE_BOOK_OUTLINE_NODE = "save_book_outline_node"
        const val TOOL_SAVE_MEMORY = "save_memory"
        const val TOOL_RECALL_MEMORY = "recall_memory"
        const val TOOL_DELETE_MEMORY = "delete_memory"

        private val confirmationRequiredTools = setOf(
            TOOL_SAVE_AI_ARTIFACT,
            TOOL_SAVE_BOOK_CHARACTER_PROFILE,
            TOOL_SAVE_BOOK_CHARACTER_EVENT,
            TOOL_SAVE_BOOK_CHARACTER_RELATION,
            TOOL_SAVE_BOOK_KNOWLEDGE_ENTRY,
            TOOL_SAVE_BOOK_OUTLINE_NODE,
            TOOL_SAVE_MEMORY,
            TOOL_DELETE_MEMORY,
        )

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
                name = TOOL_GET_CHAPTER_WINDOW,
                description = "Read cached text for a chapter and its neighboring chapters in one call. Useful for continuity checks before rewriting or summarizing. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Zero-based center chapter index. Defaults to current reading chapter."),
                    "before" to intSchema("How many previous chapters to include, capped by the app."),
                    "after" to intSchema("How many following chapters to include, capped by the app."),
                    "maxCharsPerChapter" to intSchema("Maximum characters to return per chapter, capped by the app.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_CHAPTER_CONTENT,
                description = "Search cached chapter text in a local bookshelf book by character name, plot keyword, place, or phrase. This never downloads network content.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Character name, plot keyword, place, or phrase to search in cached chapter text."),
                    "aroundChapterIndex" to intSchema("Prefer matches closest to this zero-based chapter index. Defaults to current reading chapter."),
                    "limit" to intSchema("Maximum number of matching chapters to return."),
                    "maxChars" to intSchema("Maximum excerpt characters per match, capped by the app.")
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
                name = TOOL_SEARCH_BOOK_CHARACTERS,
                description = "Search saved character profiles for a local book. Use this to find character names, aliases, personality, and profile ids.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Character name, alias, or profile keyword. Leave empty to list recently updated profiles."),
                    "limit" to intSchema("Maximum number of character profiles to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_CHARACTER_PROFILE,
                description = "Get one saved character profile with events and relationship notes. Events can be limited to chapters up to a specified index to avoid future spoilers.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "characterId" to stringSchema("Character profile id."),
                    "characterName" to stringSchema("Character name or alias when id is unavailable."),
                    "maxChapterIndex" to intSchema("Only include events at or before this zero-based chapter index."),
                    "chapterIndex" to intSchema("Alias for maxChapterIndex."),
                    "limit" to intSchema("Maximum number of events and relations to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SEARCH_BOOK_KNOWLEDGE,
                description = "Search saved world-book knowledge entries such as world rules, locations, factions, objects, terminology, timeline, style, or theme.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "query" to stringSchema("Keyword for title, keywords, or content. Leave empty to list high-priority entries."),
                    "type" to stringSchema("Optional entry type, e.g. world_rule, location, faction, object, terminology, timeline, style, theme."),
                    "chapterIndex" to intSchema("Only include entries whose chapter scope contains this zero-based chapter index."),
                    "limit" to intSchema("Maximum number of entries to return.")
                )
            ),
            AiToolDefinition(
                name = TOOL_GET_BOOK_OUTLINE,
                description = "Read saved outline nodes for a book, volume, arc, chapter, or scene. Can be scoped to the current chapter.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "chapterIndex" to intSchema("Optional zero-based chapter index; returns outline nodes covering this chapter."),
                    "nodeType" to stringSchema("Optional node type, e.g. book, volume, arc, chapter, scene."),
                    "limit" to intSchema("Maximum number of outline nodes to return.")
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
                name = TOOL_SAVE_BOOK_CHARACTER_PROFILE,
                description = "Save or update a user-approved character profile for a local book. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "id" to stringSchema("Optional existing character id."),
                    "name" to stringSchema("Character name."),
                    "aliasesJson" to stringSchema("JSON string representing an array of aliases, e.g. \"[\\\"别名1\\\",\\\"别名2\\\"]\". Must be a string, not an array."),
                    "avatarUri" to stringSchema("Optional avatar uri or local path."),
                    "tagsJson" to stringSchema("JSON string representing an array of simple tags, e.g. \"[\\\"修仙者\\\",\\\"白衣\\\",\\\"年轻\\\"]\". Must be a string, not an array."),
                    "role" to stringSchema("Character role: one of \"male_lead\", \"female_lead\", \"male_supporting\", \"female_supporting\", or empty."),
                    "personality" to stringSchema("Personality description."),
                    "summary" to stringSchema("Concise character summary."),
                    "confidence" to floatSchema("Confidence from 0 to 1.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_CHARACTER_EVENT,
                description = "Save a user-approved character event or timeline item for a local book. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "characterId" to stringSchema("Character profile id."),
                    "characterName" to stringSchema("Character name when id is unavailable."),
                    "chapterIndex" to intSchema("Zero-based chapter index where the event appears."),
                    "chapterTitle" to stringSchema("Chapter title."),
                    "eventTimeText" to stringSchema("In-story time label if known."),
                    "content" to stringSchema("Event content."),
                    "importance" to intSchema("Importance score."),
                    "sourceTextHash" to stringSchema("Optional hash of source excerpt."),
                    "evidenceJson" to stringSchema("JSON array of evidence excerpts or references."),
                    "confidence" to floatSchema("Confidence from 0 to 1.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_CHARACTER_RELATION,
                description = "Save a user-approved directed relation between two character profiles. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "fromCharacterId" to stringSchema("Source character id."),
                    "fromCharacterName" to stringSchema("Source character name when id is unavailable."),
                    "toCharacterId" to stringSchema("Target character id."),
                    "toCharacterName" to stringSchema("Target character name when id is unavailable."),
                    "relationType" to stringSchema("Relation type. Use a concise Chinese label describing the relationship, e.g. 恋人, 夫妻, 师徒, 同门, 兄弟, 姐妹, 主仆, 盟友, 敌对, 上下级, 知己, 情敌, 仇人, 亲属, 朋友."),
                    "summary" to stringSchema("Relation summary."),
                    "attitude" to stringSchema("Source character's attitude toward target, e.g. 信任, 敌视, 尊敬, 嫉妒, 爱慕, 怀戒备, 利用."),
                    "chapterIndex" to intSchema("Chapter index where the relation is established or updated."),
                    "evidenceJson" to stringSchema("JSON array of evidence excerpts or references."),
                    "confidence" to floatSchema("Confidence from 0 to 1.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_KNOWLEDGE_ENTRY,
                description = "Save a user-approved world-book knowledge entry for a local book. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "type" to stringSchema("Entry type, e.g. world_rule, location, faction, object, terminology, timeline, style, theme."),
                    "title" to stringSchema("Entry title."),
                    "keywordsJson" to stringSchema("JSON array of trigger keywords."),
                    "content" to stringSchema("Entry content."),
                    "scopeStartChapter" to intSchema("Optional first chapter index where this entry applies."),
                    "scopeEndChapter" to intSchema("Optional last chapter index where this entry applies."),
                    "priority" to intSchema("Higher priority entries are returned first."),
                    "evidenceJson" to stringSchema("JSON array of evidence excerpts or references."),
                    "confidence" to floatSchema("Confidence from 0 to 1.")
                )
            ),
            AiToolDefinition(
                name = TOOL_SAVE_BOOK_OUTLINE_NODE,
                description = "Save a user-approved outline node for a book, volume, arc, chapter, or scene. Requires user confirmation.",
                inputSchema = objectSchema(
                    "bookUrl" to stringSchema("Exact book URL/id from search_books."),
                    "bookName" to stringSchema("Book title, used when bookUrl is unavailable."),
                    "bookAuthor" to stringSchema("Book author."),
                    "id" to stringSchema("Optional existing outline node id."),
                    "parentId" to stringSchema("Optional parent outline node id."),
                    "nodeType" to stringSchema("Node type, e.g. book, volume, arc, chapter, scene."),
                    "title" to stringSchema("Outline node title."),
                    "summary" to stringSchema("Outline node summary."),
                    "startChapterIndex" to intSchema("Optional first chapter index covered by this node."),
                    "endChapterIndex" to intSchema("Optional last chapter index covered by this node."),
                    "order" to intSchema("Sort order among siblings."),
                    "confidence" to floatSchema("Confidence from 0 to 1.")
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

        private fun floatSchema(description: String): Map<String, Any?> {
            return mapOf("type" to "number", "description" to description)
        }
    }
}

private fun String.excerptAround(index: Int, maxChars: Int): String {
    if (length <= maxChars) return trim()
    val start = (index - maxChars / 2).coerceAtLeast(0)
    val end = (start + maxChars).coerceAtMost(length)
    return substring(start, end).trim()
}
