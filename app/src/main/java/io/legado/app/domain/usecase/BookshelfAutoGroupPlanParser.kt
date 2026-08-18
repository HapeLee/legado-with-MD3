package io.legado.app.domain.usecase

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import kotlin.uuid.Uuid

internal class BookshelfAutoGroupPlanParser {

    fun parse(
        response: String,
        booksByPromptId: Map<String, BookshelfAutoGroupBook>,
        existingGroupNames: Set<String>,
    ): BookshelfAutoGroupPlan {
        val root = extractFirstPlanObject(response)
        val assignedIds = linkedSetOf<String>()
        val groups = mutableListOf<BookshelfAutoGroupPlanGroup>()

        root.arrayOrNull("groups")?.forEach { groupElement ->
            val groupObject = groupElement.objectOrNull() ?: return@forEach
            val name = groupObject.stringOrNull("name")
                ?.trim()
                ?.take(MAX_GROUP_NAME_CHARS)
                ?.takeIf(String::isNotBlank)
                ?: return@forEach
            val books = groupObject.arrayOrNull("books")
                ?.mapNotNull { bookElement ->
                    val bookObject = bookElement.objectOrNull() ?: return@mapNotNull null
                    val promptId = bookObject.stringOrNull("id") ?: return@mapNotNull null
                    val book = booksByPromptId[promptId] ?: return@mapNotNull null
                    if (!assignedIds.add(promptId)) return@mapNotNull null
                    BookshelfAutoGroupPlanBook(
                        bookUrl = book.bookUrl,
                        name = book.name,
                        author = book.author,
                        currentGroupNames = book.currentGroupNames,
                        reason = bookObject.stringOrNull("reason").toShortReason(),
                    )
                }
                .orEmpty()
            if (books.isNotEmpty()) {
                groups += BookshelfAutoGroupPlanGroup(
                    key = Uuid.random().toString(),
                    name = name,
                    description = groupObject.stringOrNull("description")?.trim().orEmpty()
                        .take(MAX_DESCRIPTION_CHARS),
                    reuseExisting = name in existingGroupNames,
                    books = books,
                )
            }
        }

        val ignored = mutableListOf<BookshelfAutoGroupIgnoredBook>()
        root.arrayOrNull("ignoredBooks")?.forEach { ignoredElement ->
            val ignoredObject = ignoredElement.objectOrNull() ?: return@forEach
            val promptId = ignoredObject.stringOrNull("id") ?: return@forEach
            val book = booksByPromptId[promptId] ?: return@forEach
            if (assignedIds.add(promptId)) {
                ignored += BookshelfAutoGroupIgnoredBook(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    reason = ignoredObject.stringOrNull("reason").toShortReason(),
                )
            }
        }

        booksByPromptId.forEach { (promptId, book) ->
            if (assignedIds.add(promptId)) {
                ignored += BookshelfAutoGroupIgnoredBook(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    reason = "",
                )
            }
        }
        return BookshelfAutoGroupPlan(
            groups = mergeGroups(groups, existingGroupNames),
            ignoredBooks = ignored,
        )
    }

    private fun extractFirstPlanObject(text: String): JsonObject {
        text.indices.filter { text[it] == '{' }.forEach { start ->
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until text.length) {
                val char = text[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = text.substring(start, index + 1)
                            val parsed = runCatching { JsonParser.parseString(candidate) }.getOrNull()
                            if (parsed?.isJsonObject == true) {
                                val root = parsed.asJsonObject
                                if (root.hasPlanShape()) return root
                            }
                            break
                        }
                    }
                }
            }
        }
        throw BookshelfAutoGroupException(BookshelfAutoGroupErrorReason.InvalidResponse)
    }

    private fun JsonObject.hasPlanShape(): Boolean {
        val groups = get("groups")
        val ignoredBooks = get("ignoredBooks")
        val hasKnownField = groups != null || ignoredBooks != null
        return hasKnownField &&
            (groups == null || groups.isJsonArray) &&
            (ignoredBooks == null || ignoredBooks.isJsonArray)
    }

    private fun mergeGroups(
        groups: List<BookshelfAutoGroupPlanGroup>,
        existingNames: Set<String>,
    ): List<BookshelfAutoGroupPlanGroup> {
        val groupsByName = linkedMapOf<String, BookshelfAutoGroupPlanGroup>()
        groups.forEach { group ->
            val existing = groupsByName[group.name]
            groupsByName[group.name] = if (existing == null) {
                group.copy(reuseExisting = group.name in existingNames)
            } else {
                existing.copy(books = existing.books + group.books)
            }
        }
        return groupsByName.values.toList()
    }

    private fun JsonObject.arrayOrNull(name: String) = get(name)
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray

    private fun JsonElement.objectOrNull() = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringOrNull(name: String): String? {
        val element = get(name) ?: return null
        return element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    private fun String?.toShortReason(): String {
        val reason = this?.trim().orEmpty()
        val sentenceEnd = reason.indexOfAny(SENTENCE_ENDINGS)
        return reason.take(if (sentenceEnd >= 0) sentenceEnd + 1 else MAX_REASON_CHARS)
            .take(MAX_REASON_CHARS)
    }

    private companion object {
        const val MAX_GROUP_NAME_CHARS = 24
        const val MAX_DESCRIPTION_CHARS = 120
        const val MAX_REASON_CHARS = 60
        val SENTENCE_ENDINGS = charArrayOf('。', '！', '？', '.', '!', '?')
    }
}
