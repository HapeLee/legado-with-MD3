package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiMemoryFragment
import io.legado.app.data.entities.AiMemoryItem
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.utils.MD5Utils
import org.json.JSONObject

data class AiMemoryContext(
    val scope: String = AiMemoryItem.SCOPE_GLOBAL,
    val bookKey: String = "",
    val sessionId: String = "",
    val companionId: String = "",
    val title: String = ""
)

data class AiRetrievedMemory(
    val items: List<AiMemoryItem> = emptyList(),
    val fragments: List<AiMemoryFragment> = emptyList()
) {
    val isNotEmpty: Boolean
        get() = items.isNotEmpty() || fragments.isNotEmpty()

    fun toSystemPrompt(maxChars: Int = 2_800): String {
        if (!isNotEmpty) return ""
        val builder = StringBuilder("Relevant long-term memories:\n")
        items.forEach { item ->
            val line = buildString {
                append("- ")
                if (item.subject.isNotBlank()) append(item.subject).append(": ")
                append(item.content.ifBlank {
                    listOf(item.subject, item.predicate, item.objectValue)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                })
            }.trim()
            if (line.length > 2 && builder.length + line.length < maxChars) {
                builder.append(line.take(360)).append('\n')
            }
        }
        fragments.forEach { fragment ->
            val line = "- ${fragment.title.ifBlank { fragment.sourceType }}: ${fragment.content.replace(Regex("\\s+"), " ").trim()}"
            if (builder.length + line.length < maxChars) {
                builder.append(line.take(520)).append('\n')
            }
        }
        return builder.toString().trim()
    }
}

object AiMemoryStore {

    fun bookKey(bookName: String, author: String): String {
        return listOf(bookName.trim(), author.trim())
            .filter { it.isNotBlank() }
            .joinToString("::")
    }

    fun upsertItem(item: AiMemoryItem) {
        val saving = item.withFingerprint()
        appDb.aiMemoryDao.upsertItem(saving)
        appDb.aiMemoryDao.deleteItemFts(saving.memoryId)
        appDb.aiMemoryDao.upsertItemFts(
            memoryId = saving.memoryId,
            subject = saving.subject,
            predicate = saving.predicate,
            objectValue = saving.objectValue,
            content = saving.content
        )
    }

    fun upsertFragment(fragment: AiMemoryFragment) {
        val saving = fragment.withContentHash()
        appDb.aiMemoryDao.upsertFragment(saving)
        appDb.aiMemoryDao.deleteFragmentFts(saving.fragmentId)
        appDb.aiMemoryDao.upsertFragmentFts(
            fragmentId = saving.fragmentId,
            title = saving.title,
            content = saving.content,
            chapterTitle = saving.chapterTitle
        )
    }

    private fun AiMemoryItem.withFingerprint(): AiMemoryItem {
        if (fingerprint.isNotBlank()) return this
        val raw = listOf(scope, bookKey, sessionId, type, subject, predicate, objectValue, content)
            .joinToString("|")
        return copy(fingerprint = MD5Utils.md5Encode(raw))
    }

    private fun AiMemoryFragment.withContentHash(): AiMemoryFragment {
        if (contentHash.isNotBlank()) return this
        val raw = listOf(scope, bookKey, sessionId, sourceType, title, content, chapterIndex.toString())
            .joinToString("|")
        return copy(contentHash = MD5Utils.md5Encode(raw))
    }
}

object AiMemoryRetriever {

    fun retrieve(
        context: AiMemoryContext?,
        messages: List<AiChatMessage>,
        limit: Int = 8
    ): AiRetrievedMemory {
        if (context == null) return AiRetrievedMemory()
        val queryText = messages
            .takeLast(6)
            .joinToString("\n") { it.content }
            .take(4_000)
        if (queryText.isBlank()) return AiRetrievedMemory()
        val ftsQuery = buildFtsQuery(queryText)
        val ftsItems = if (ftsQuery.isBlank()) {
            emptyList()
        } else {
            runCatching {
                appDb.aiMemoryDao.searchItems(ftsQuery, context.scope, context.bookKey, context.sessionId, limit)
            }.getOrDefault(emptyList())
        }
        val ftsFragments = if (ftsQuery.isBlank()) {
            emptyList()
        } else {
            runCatching {
                appDb.aiMemoryDao.searchFragments(ftsQuery, context.scope, context.bookKey, context.sessionId, limit)
            }.getOrDefault(emptyList())
        }
        val keywords = keywords(queryText)
        val rankedItems = (ftsItems + appDb.aiMemoryDao.candidateItems(context.scope, context.bookKey, context.sessionId))
            .distinctBy { it.memoryId }
            .sortedByDescending { score("${it.subject} ${it.predicate} ${it.objectValue} ${it.content}", keywords) + it.importance }
            .take(limit)
        val rankedFragments = (ftsFragments + appDb.aiMemoryDao.candidateFragments(context.scope, context.bookKey, context.sessionId))
            .distinctBy { it.fragmentId }
            .sortedByDescending { score("${it.title} ${it.chapterTitle} ${it.content}", keywords) + it.importance }
            .take(limit)
        if (rankedItems.isNotEmpty()) {
            appDb.aiMemoryDao.markItemsUsed(rankedItems.map { it.memoryId })
        }
        if (rankedFragments.isNotEmpty()) {
            appDb.aiMemoryDao.markFragmentsUsed(rankedFragments.map { it.fragmentId })
        }
        return AiRetrievedMemory(rankedItems, rankedFragments)
    }

    private fun buildFtsQuery(text: String): String {
        return Regex("[A-Za-z0-9_]{2,}")
            .findAll(text)
            .map { it.value.lowercase() }
            .distinct()
            .take(8)
            .joinToString(" OR ")
    }

    private fun keywords(text: String): List<String> {
        val compact = text.replace(Regex("\\s+"), "")
        val words = Regex("[\\p{L}\\p{N}_]{2,}")
            .findAll(text)
            .map { it.value }
            .toMutableList()
        if (compact.length >= 2) {
            compact.windowed(2, 2, partialWindows = false)
                .take(24)
                .forEach(words::add)
        }
        return words.distinct().take(48)
    }

    private fun score(text: String, keywords: List<String>): Int {
        if (keywords.isEmpty()) return 0
        return keywords.count { keyword -> text.contains(keyword, ignoreCase = true) } * 10
    }
}

object AiMemoryExtractor {

    fun recordConversation(
        context: AiMemoryContext?,
        requestMessages: List<AiChatMessage>,
        assistantContent: String
    ) {
        if (context == null || assistantContent.isBlank()) return
        val userContent = requestMessages.lastOrNull { it.role == AiChatMessage.Role.USER }
            ?.content
            ?.trim()
            .orEmpty()
        if (userContent.isBlank()) return
        val now = System.currentTimeMillis()
        val fragmentContent = buildString {
            append("User: ").append(userContent.take(2_000))
            append("\nAssistant: ").append(assistantContent.trim().take(2_000))
        }
        if (fragmentContent.length >= 40) {
            AiMemoryStore.upsertFragment(
                AiMemoryFragment(
                    scope = context.scope,
                    bookKey = context.bookKey,
                    sessionId = context.sessionId,
                    sourceType = if (context.scope == AiMemoryItem.SCOPE_BOOK) {
                        AiMemoryFragment.SOURCE_READ_AI
                    } else {
                        AiMemoryFragment.SOURCE_CHAT
                    },
                    title = context.title.ifBlank { "AI 对话" },
                    content = fragmentContent,
                    importance = 40,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        extractPreference(userContent, context, now)?.let(AiMemoryStore::upsertItem)
    }

    private fun extractPreference(content: String, context: AiMemoryContext, now: Long): AiMemoryItem? {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        val hit = listOf("我希望", "我喜欢", "我不希望", "我不喜欢", "以后", "记住")
            .firstOrNull { normalized.contains(it) }
            ?: return null
        return AiMemoryItem(
            scope = AiMemoryItem.SCOPE_GLOBAL,
            sessionId = context.sessionId,
            type = AiMemoryItem.TYPE_USER_PREFERENCE,
            subject = "用户偏好",
            predicate = hit,
            objectValue = normalized.take(240),
            content = normalized.take(500),
            confidence = 70,
            importance = 75,
            sourceIds = JSONObject().put("sessionId", context.sessionId).toString(),
            createdAt = now,
            updatedAt = now
        )
    }
}
