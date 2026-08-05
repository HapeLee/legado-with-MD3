package io.legado.app.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupSource
import io.legado.app.help.book.isNotShelf
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class GenerateBookshelfAutoGroupPlanUseCase(
    private val database: AppDatabase,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) {

    suspend fun loadSource(): BookshelfAutoGroupSource = withContext(Dispatchers.IO) {
        val books = database.bookDao.getAll()
            .filterNot { it.isNotShelf }
            .sortedWith(compareBy<Book> { it.name }.thenBy { it.author })
        val existingGroups = database.bookGroupDao.all
            .filter { it.groupId > 0 }
            .map { it.groupName }
            .filter { it.isNotBlank() }
        BookshelfAutoGroupSource(
            books = books.map { book ->
                BookshelfAutoGroupBook(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    intro = book.groupingIntro(),
                    kind = book.customTag ?: book.kind.orEmpty(),
                    currentGroupNames = database.bookGroupDao.getGroupNames(book.group),
                )
            },
            existingGroupNames = existingGroups,
        )
    }

    suspend fun generate(
        source: BookshelfAutoGroupSource,
        groupingInstruction: String,
    ): BookshelfAutoGroupPlan {
        require(source.books.isNotEmpty()) { "书架没有可分析的书籍" }
        val preset = resolvePreset() ?: error("请先配置默认 AI 模型")
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, buildSystemPrompt()),
                    AiMessage(AiMessageRole.USER, buildGeneratePrompt(source, groupingInstruction)),
                ),
                params = preset.params,
            )
        ).getOrThrow().text
        return parseAndSanitizePlan(response, source)
    }

    suspend fun revise(
        source: BookshelfAutoGroupSource,
        currentPlan: BookshelfAutoGroupPlan,
        instruction: String,
    ): BookshelfAutoGroupPlan {
        require(instruction.isNotBlank()) { "调整要求不能为空" }
        val preset = resolvePreset() ?: error("请先配置默认 AI 模型")
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, buildSystemPrompt()),
                    AiMessage(AiMessageRole.USER, buildRevisePrompt(source, currentPlan, instruction)),
                ),
                params = preset.params,
            )
        ).getOrThrow().text
        return parseAndSanitizePlan(response, source)
    }

    private suspend fun resolvePreset(): AiTaskPresetConfig? {
        return aiProfileGateway.getTaskPreset(AiTaskType.BOOKSHELF_AUTO_GROUP)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TEXT_FACTORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_BOOK)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
    }

    private fun buildSystemPrompt(): String {
        return """
            你是阅读 App 的书架整理助手。
            你的任务是根据书名、作者、分类、简介和当前分组，生成可由用户确认的书架分组方案。
            只输出 JSON，不要 Markdown，不要代码块，不要解释。
            不要虚构 bookUrl，只能使用用户输入中存在的 bookUrl。
            分组名称使用简短中文，避免“其他”“综合”等过泛名称，除非确实无法判断。
            可以复用已有分组名称；也可以提出新分组。
            每本书最多放入一个建议分组。无法判断的书放入 ignoredBooks。
        """.trimIndent()
    }

    private fun buildGeneratePrompt(
        source: BookshelfAutoGroupSource,
        groupingInstruction: String,
    ): String {
        return buildString {
            append("请为以下全部书籍生成分组方案。\n")
            append("已有用户分组：")
            append(source.existingGroupNames.joinToString("、").ifBlank { "无" })
            groupingInstruction.trim().takeIf { it.isNotBlank() }?.let { instruction ->
                append("\n\n用户补充分组要求：\n")
                append(instruction)
            }
            append("\n\n返回 JSON 格式：\n")
            append(OUTPUT_SCHEMA)
            append("\n\n书籍数据：\n")
            append(GSON.toJson(source.books.map { it.toPromptMap() }))
        }
    }

    private fun buildRevisePrompt(
        source: BookshelfAutoGroupSource,
        currentPlan: BookshelfAutoGroupPlan,
        instruction: String,
    ): String {
        return buildString {
            append("请根据用户调整要求，重新输出完整分组方案。\n")
            append("用户调整要求：")
            append(instruction)
            append("\n\n已有用户分组：")
            append(source.existingGroupNames.joinToString("、").ifBlank { "无" })
            append("\n\n当前方案：\n")
            append(currentPlan.toPromptJson())
            append("\n\n书籍数据：\n")
            append(GSON.toJson(source.books.map { it.toPromptMap() }))
            append("\n\n返回 JSON 格式：\n")
            append(OUTPUT_SCHEMA)
        }
    }

    private fun parseAndSanitizePlan(
        response: String,
        source: BookshelfAutoGroupSource,
    ): BookshelfAutoGroupPlan {
        val root = extractJsonObject(response)
        val booksByUrl = source.books.associateBy { it.bookUrl }
        val existingNames = source.existingGroupNames.toSet()
        val assignedBookUrls = linkedSetOf<String>()
        val groups = mutableListOf<BookshelfAutoGroupPlanGroup>()

        root.getAsJsonArrayOrNull("groups")?.forEach { groupElement ->
            val groupObject = groupElement.asJsonObjectOrNull() ?: return@forEach
            val name = groupObject.string("name")
                ?.trim()
                ?.take(24)
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            val description = groupObject.string("description")?.trim().orEmpty().take(120)
            val books = groupObject.getAsJsonArrayOrNull("books")
                ?.mapNotNull { bookElement ->
                    val bookObject = bookElement.asJsonObjectOrNull() ?: return@mapNotNull null
                    val bookUrl = bookObject.string("bookUrl") ?: return@mapNotNull null
                    val book = booksByUrl[bookUrl] ?: return@mapNotNull null
                    if (!assignedBookUrls.add(bookUrl)) return@mapNotNull null
                    BookshelfAutoGroupPlanBook(
                        bookUrl = book.bookUrl,
                        name = book.name,
                        author = book.author,
                        currentGroupNames = book.currentGroupNames,
                        reason = bookObject.string("reason")?.trim().orEmpty().take(120),
                    )
                }
                .orEmpty()
            if (books.isNotEmpty()) {
                groups += BookshelfAutoGroupPlanGroup(
                    key = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                    reuseExisting = name in existingNames,
                    books = books,
                )
            }
        }

        val ignored = mutableListOf<BookshelfAutoGroupIgnoredBook>()
        root.getAsJsonArrayOrNull("ignoredBooks")?.forEach { ignoredElement ->
            val ignoredObject = ignoredElement.asJsonObjectOrNull() ?: return@forEach
            val bookUrl = ignoredObject.string("bookUrl") ?: return@forEach
            val book = booksByUrl[bookUrl] ?: return@forEach
            if (book.bookUrl !in assignedBookUrls) {
                ignored += BookshelfAutoGroupIgnoredBook(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    reason = ignoredObject.string("reason")?.trim().orEmpty().take(120),
                )
                assignedBookUrls += book.bookUrl
            }
        }

        val missingIgnored = source.books
            .filterNot { it.bookUrl in assignedBookUrls }
            .map {
                BookshelfAutoGroupIgnoredBook(
                    bookUrl = it.bookUrl,
                    name = it.name,
                    author = it.author,
                    reason = "AI 未给出明确分组",
                )
            }

        return BookshelfAutoGroupPlan(
            groups = groups.mergeSameName(existingNames),
            ignoredBooks = ignored + missingIgnored,
        )
    }

    private fun extractJsonObject(text: String): JsonObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 未返回可解析的分组方案" }
        return JsonParser.parseString(text.substring(start, end + 1)).asJsonObject
    }

    private fun List<BookshelfAutoGroupPlanGroup>.mergeSameName(
        existingNames: Set<String>,
    ): List<BookshelfAutoGroupPlanGroup> {
        return groupBy { it.name }.map { (name, groups) ->
            val first = groups.first()
            first.copy(
                name = name,
                reuseExisting = name in existingNames,
                books = groups.flatMap { it.books },
            )
        }
    }

    private fun Book.groupingIntro(): String {
        return (customIntro ?: intro ?: listIntro).orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(MAX_INTRO_CHARS)
    }

    private fun BookshelfAutoGroupBook.toPromptMap(): Map<String, Any?> {
        return mapOf(
            "bookUrl" to bookUrl,
            "name" to name,
            "author" to author,
            "kind" to kind,
            "intro" to intro,
            "currentGroups" to currentGroupNames,
        )
    }

    private fun BookshelfAutoGroupPlan.toPromptJson(): String {
        return GSON.toJson(
            mapOf(
                "groups" to groups.map { group ->
                    mapOf(
                        "name" to group.name,
                        "description" to group.description,
                        "books" to group.books.map { book ->
                            mapOf(
                                "bookUrl" to book.bookUrl,
                                "reason" to book.reason,
                            )
                        },
                    )
                },
                "ignoredBooks" to ignoredBooks.map { book ->
                    mapOf(
                        "bookUrl" to book.bookUrl,
                        "reason" to book.reason,
                    )
                },
            )
        )
    }

    private fun JsonObject.string(name: String): String? {
        return get(name)?.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
        get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
    }.getOrNull()

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
        return takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
    }

    private companion object {
        const val MAX_INTRO_CHARS = 320

        val OUTPUT_SCHEMA = """
            {
              "groups": [
                {
                  "name": "分组名称",
                  "description": "分组依据",
                  "books": [
                    {"bookUrl": "输入中的 bookUrl", "reason": "为什么放入该分组"}
                  ]
                }
              ],
              "ignoredBooks": [
                {"bookUrl": "输入中的 bookUrl", "reason": "无法判断原因"}
              ]
            }
        """.trimIndent()
    }
}
