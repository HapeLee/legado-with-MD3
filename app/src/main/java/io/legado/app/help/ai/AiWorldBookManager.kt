package io.legado.app.help.ai

import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiWorldBookBinding
import io.legado.app.ui.main.ai.AiWorldBookConfig
import io.legado.app.ui.main.ai.AiWorldBookEntry
import org.json.JSONArray
import org.json.JSONObject

data class AiWorldBookHit(
    val worldBook: AiWorldBookConfig,
    val entry: AiWorldBookEntry,
    val matchedKeys: List<String>,
    val binding: AiWorldBookBinding?,
    val score: Int
)

data class AiWorldBookInjection(
    val hit: AiWorldBookHit,
    val role: String,
    val position: String,
    val injectDepth: Int,
    val content: String
)

data class AiWorldBookContext(
    val injections: List<AiWorldBookInjection> = emptyList()
) {
    val isNotEmpty: Boolean
        get() = injections.isNotEmpty()

    val hits: List<AiWorldBookHit>
        get() = injections.map { it.hit }

    fun toTokenText(maxChars: Int = 4_800): String {
        if (injections.isEmpty()) return ""
        val builder = StringBuilder()
        injections.forEach { injection ->
            val hit = injection.hit
            val line = buildString {
                append("- [")
                append(hit.worldBook.name)
                append(" / ")
                append(hit.entry.title)
                append("]")
                if (hit.matchedKeys.isNotEmpty()) {
                    append(" keys=")
                    append(hit.matchedKeys.joinToString(","))
                }
                append(": ")
                append(injection.content.replace(Regex("\\s+"), " ").trim())
            }
            if (builder.length + line.length + 1 > maxChars) return@forEach
            builder.append(line.take(1_200)).append('\n')
        }
        return builder.toString().trim()
    }

    fun toTraceJson(): JSONObject {
        val items = JSONArray()
        hits.forEach { hit ->
            items.put(
                JSONObject()
                    .put("worldBookId", hit.worldBook.id)
                    .put("worldBookName", hit.worldBook.name)
                    .put("entryId", hit.entry.id)
                    .put("entryTitle", hit.entry.title)
                    .put("keys", JSONArray(hit.matchedKeys))
                    .put("bindingType", hit.binding?.targetType.orEmpty())
                    .put("bindingKey", hit.binding?.targetKey.orEmpty())
                    .put("position", hit.entry.position)
                    .put("role", hit.entry.role)
                    .put("injectDepth", hit.entry.injectDepth)
                    .put("score", hit.score)
            )
        }
        return JSONObject()
            .put("count", hits.size)
            .put("items", items)
    }
}

object AiWorldBookManager {

    fun retrieve(
        context: AiMemoryContext?,
        messages: List<AiChatMessage>,
        maxEntries: Int = 12
    ): AiWorldBookContext {
        val hits = AppConfig.aiWorldBookList
            .asSequence()
            .filter { it.enabled && it.entries.isNotEmpty() }
            .flatMap { worldBook ->
                val activeBinding = worldBook.activeBinding(context) ?: return@flatMap emptySequence()
                worldBook.entries.asSequence()
                    .filter { it.enabled }
                    .mapNotNull { entry ->
                        val scanText = messages.scanText(entry.scanDepth)
                        val matchedKeys = entry.matchedKeys(scanText)
                        val excluded = entry.isExcluded(scanText)
                        val active = if (entry.constantActive || entry.constant) {
                            !excluded
                        } else {
                            matchedKeys.isNotEmpty() &&
                                    !excluded &&
                                    entry.matchesSecondary(scanText)
                        }
                        if (!active) {
                            null
                        } else {
                            AiWorldBookHit(
                                worldBook = worldBook,
                                entry = entry,
                                matchedKeys = matchedKeys,
                                binding = activeBinding,
                                        score = entry.priority * 10 +
                                        matchedKeys.sumOf { it.length.coerceAtMost(30) } +
                                        if (entry.constantActive || entry.constant) 120 else 0
                            )
                        }
                    }
                    .sortedWith(compareByDescending<AiWorldBookHit> { it.score }.thenBy { it.entry.order })
                    .take(worldBook.maxEntries)
            }
            .sortedWith(compareByDescending<AiWorldBookHit> { it.score }.thenBy { it.entry.order })
            .take(maxEntries)
            .toList()
        return AiWorldBookContext(
            hits.map { hit ->
                AiWorldBookInjection(
                    hit = hit,
                    role = normalizeRole(hit.entry.role),
                    position = normalizePosition(hit.entry.position),
                    injectDepth = hit.entry.injectDepth.coerceIn(0, 64),
                    content = hit.entry.content
                )
            }
        )
    }

    fun listWorldBooks(arguments: JSONObject?): String {
        val includeEntries = arguments?.optBoolean("includeEntries", false) == true
        val includeBindings = arguments?.optBoolean("includeBindings", true) != false
        val items = JSONArray()
        AppConfig.aiWorldBookList.forEach { worldBook ->
            items.put(worldBook.toJson(includeEntries, includeBindings))
        }
        return JSONObject()
            .put("ok", true)
            .put("count", items.length())
            .put("items", items)
            .toString()
    }

    fun upsertWorldBook(arguments: JSONObject?): String {
        if (arguments == null) return jsonError("missing arguments")
        val id = arguments.optString("id").trim()
        val old = AppConfig.aiWorldBookList.firstOrNull { it.id == id }
        val name = arguments.optString("name", old?.name.orEmpty()).trim()
        if (name.isBlank()) return jsonError("name is required")
        val updated = (old ?: AiWorldBookConfig(name = name)).copy(
            name = name,
            description = arguments.optString("description", old?.description.orEmpty()).trim(),
            version = arguments.optInt("version", old?.version ?: 1).takeIf { it > 0 } ?: 1,
            type = arguments.optString("type", old?.type ?: AiWorldBookConfig.TYPE_LOREBOOK)
                .takeIf { it == AiWorldBookConfig.TYPE_LOREBOOK }
                ?: AiWorldBookConfig.TYPE_LOREBOOK,
            scope = arguments.optString("scope", old?.scope ?: AiWorldBookConfig.SCOPE_GLOBAL)
                .takeIf {
                    it == AiWorldBookConfig.SCOPE_GLOBAL ||
                            it == AiWorldBookConfig.SCOPE_BOOK ||
                            it == AiWorldBookConfig.SCOPE_SESSION
                }
                ?: AiWorldBookConfig.SCOPE_GLOBAL,
            bookKey = arguments.optString("bookKey", old?.bookKey.orEmpty()).trim(),
            enabled = arguments.optBoolean("enabled", old?.enabled ?: true),
            bindingVersion = 1,
            maxEntries = arguments.optInt("maxEntries", old?.maxEntries ?: 12).coerceIn(1, 40),
            bindings = arguments.optBindingArray("bindings", old?.bindings ?: legacyBindingsFromArguments(arguments)),
            order = arguments.optInt("order", old?.order ?: AppConfig.aiWorldBookList.size),
            entries = old?.entries ?: emptyList()
        )
        AppConfig.aiWorldBookList = AppConfig.aiWorldBookList
            .filterNot { it.id == updated.id }
            .plus(updated)
        return JSONObject()
            .put("ok", true)
            .put("item", updated.toJson(includeEntries = true, includeBindings = true))
            .toString()
    }

    fun deleteWorldBook(arguments: JSONObject?): String {
        val id = arguments?.optString("id")?.trim().orEmpty()
        if (id.isBlank()) return jsonError("id is required")
        val before = AppConfig.aiWorldBookList
        AppConfig.aiWorldBookList = before.filterNot { it.id == id }
        return JSONObject()
            .put("ok", before.any { it.id == id })
            .put("id", id)
            .toString()
    }

    fun upsertWorldBookEntry(arguments: JSONObject?): String {
        if (arguments == null) return jsonError("missing arguments")
        val worldBookId = arguments.optString("worldBookId").trim()
        if (worldBookId.isBlank()) return jsonError("worldBookId is required")
        val worldBooks = AppConfig.aiWorldBookList.toMutableList()
        val worldBookIndex = worldBooks.indexOfFirst { it.id == worldBookId }
        if (worldBookIndex < 0) return jsonError("world book not found")
        val worldBook = worldBooks[worldBookIndex]
        val entryJson = arguments.optJSONObject("entry") ?: arguments
        val entryId = entryJson.optString("id").trim()
        val old = worldBook.entries.firstOrNull { it.id == entryId }
        val title = entryJson.optString("name")
            .ifBlank { entryJson.optString("title", old?.title.orEmpty()) }
            .trim()
        val content = entryJson.optString("content", old?.content.orEmpty()).trim()
        if (title.isBlank()) return jsonError("entry title is required")
        if (content.isBlank()) return jsonError("entry content is required")
        val keywords = entryJson.optStringArray("keywords", old?.keywords?.takeIf { it.isNotEmpty() } ?: old?.keys ?: emptyList())
            .ifEmpty { entryJson.optStringArray("keys", old?.keys ?: emptyList()) }
        val useRegex = entryJson.optBoolean("useRegex", old?.useRegex ?: old?.regexEnabled ?: false) ||
                entryJson.optBoolean("regexEnabled", false)
        val constantActive = entryJson.optBoolean("constantActive", old?.constantActive ?: old?.constant ?: false) ||
                entryJson.optBoolean("constant", false)
        val updated = (old ?: AiWorldBookEntry(title = title, content = content)).copy(
            title = title,
            name = title,
            content = content.take(8_000),
            keys = keywords,
            keywords = keywords,
            secondaryKeys = entryJson.optStringArray("secondaryKeys", old?.secondaryKeys ?: emptyList()),
            excludeKeys = entryJson.optStringArray("excludeKeys", old?.excludeKeys ?: emptyList()),
            regexEnabled = useRegex,
            useRegex = useRegex,
            caseSensitive = entryJson.optBoolean("caseSensitive", old?.caseSensitive ?: false),
            enabled = entryJson.optBoolean("enabled", old?.enabled ?: true),
            constant = constantActive,
            constantActive = constantActive,
            priority = entryJson.optInt("priority", old?.priority ?: 50).coerceIn(-9999, 9999),
            position = normalizePosition(entryJson.optString("position", old?.position ?: AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT)),
            injectDepth = entryJson.optInt("injectDepth", old?.injectDepth ?: 4).coerceIn(0, 64),
            role = normalizeRole(entryJson.optString("role", old?.role ?: AiWorldBookEntry.ROLE_USER)),
            scanDepth = entryJson.optInt("scanDepth", old?.scanDepth ?: 8).coerceIn(1, 64),
            maxMatches = entryJson.optInt("maxMatches", old?.maxMatches ?: 1).coerceIn(1, 20),
            order = entryJson.optInt("order", old?.order ?: worldBook.entries.size)
        )
        worldBooks[worldBookIndex] = worldBook.copy(
            entries = worldBook.entries
                .filterNot { it.id == updated.id }
                .plus(updated)
        )
        AppConfig.aiWorldBookList = worldBooks
        return JSONObject()
            .put("ok", true)
            .put("worldBookId", worldBook.id)
            .put("entry", updated.toJson())
            .toString()
    }

    fun deleteWorldBookEntry(arguments: JSONObject?): String {
        val worldBookId = arguments?.optString("worldBookId")?.trim().orEmpty()
        val entryId = arguments?.optString("entryId")?.trim().orEmpty()
        if (worldBookId.isBlank() || entryId.isBlank()) return jsonError("worldBookId and entryId are required")
        var deleted = false
        AppConfig.aiWorldBookList = AppConfig.aiWorldBookList.map { worldBook ->
            if (worldBook.id != worldBookId) return@map worldBook
            val entries = worldBook.entries.filterNot { entry ->
                val hit = entry.id == entryId
                if (hit) deleted = true
                hit
            }
            worldBook.copy(entries = entries)
        }
        return JSONObject()
            .put("ok", deleted)
            .put("worldBookId", worldBookId)
            .put("entryId", entryId)
            .toString()
    }

    fun listWorldBookBindings(arguments: JSONObject?): String {
        val worldBookId = arguments?.optString("worldBookId")?.trim().orEmpty()
        val targetType = arguments?.optString("targetType")?.trim().orEmpty()
        val targetKey = arguments?.optString("targetKey")?.trim().orEmpty()
        val items = JSONArray()
        AppConfig.aiWorldBookList.forEach { worldBook ->
            if (worldBookId.isNotBlank() && worldBook.id != worldBookId) return@forEach
            worldBook.bindings
                .filter { targetType.isBlank() || it.targetType == targetType }
                .filter { targetKey.isBlank() || it.targetKey == targetKey }
                .forEach { binding ->
                    items.put(binding.toJson().put("worldBookId", worldBook.id).put("worldBookName", worldBook.name))
                }
        }
        return JSONObject()
            .put("ok", true)
            .put("count", items.length())
            .put("items", items)
            .toString()
    }

    fun upsertWorldBookBinding(arguments: JSONObject?): String {
        if (arguments == null) return jsonError("missing arguments")
        val worldBookId = arguments.optString("worldBookId").trim()
        if (worldBookId.isBlank()) return jsonError("worldBookId is required")
        val targetType = normalizeTargetType(arguments.optString("targetType").trim())
        if (targetType.isBlank()) return jsonError("invalid targetType")
        val targetKey = arguments.optString("targetKey").trim()
        val requiresKey = targetType == AiWorldBookBinding.TARGET_COMPANION
        if (requiresKey && targetKey.isBlank()) return jsonError("targetKey is required")
        var updatedBinding: AiWorldBookBinding? = null
        var found = false
        AppConfig.aiWorldBookList = AppConfig.aiWorldBookList.map { worldBook ->
            if (worldBook.id != worldBookId) return@map worldBook
            found = true
            val old = worldBook.bindings.firstOrNull {
                it.id == arguments.optString("bindingId").trim() ||
                        (it.targetType == targetType && it.targetKey == targetKey)
            }
            val updated = (old ?: AiWorldBookBinding(
                targetType = targetType,
                targetKey = targetKey,
                order = worldBook.bindings.size
            )).copy(
                targetType = targetType,
                targetKey = targetKey,
                enabled = arguments.optBoolean("enabled", old?.enabled ?: true),
                order = arguments.optInt("order", old?.order ?: worldBook.bindings.size)
            )
            updatedBinding = updated
            worldBook.copy(
                bindingVersion = 1,
                bindings = worldBook.bindings
                    .filterNot { it.id == updated.id || (it.targetType == targetType && it.targetKey == targetKey) }
                    .plus(updated)
            )
        }
        if (!found) return jsonError("world book not found")
        return JSONObject()
            .put("ok", true)
            .put("worldBookId", worldBookId)
            .put("binding", updatedBinding?.toJson())
            .toString()
    }

    fun deleteWorldBookBinding(arguments: JSONObject?): String {
        val worldBookId = arguments?.optString("worldBookId")?.trim().orEmpty()
        if (worldBookId.isBlank()) return jsonError("worldBookId is required")
        val bindingId = arguments?.optString("bindingId")?.trim().orEmpty()
        val targetType = arguments?.optString("targetType")?.trim().orEmpty()
        val targetKey = arguments?.optString("targetKey")?.trim().orEmpty()
        var deleted = false
        AppConfig.aiWorldBookList = AppConfig.aiWorldBookList.map { worldBook ->
            if (worldBook.id != worldBookId) return@map worldBook
            val bindings = worldBook.bindings.filterNot { binding ->
                val hit = if (bindingId.isNotBlank()) {
                    binding.id == bindingId
                } else {
                    binding.targetType == targetType && binding.targetKey == targetKey
                }
                if (hit) deleted = true
                hit
            }
            worldBook.copy(bindingVersion = 1, bindings = bindings)
        }
        return JSONObject()
            .put("ok", deleted)
            .put("worldBookId", worldBookId)
            .put("bindingId", bindingId)
            .toString()
    }

    fun importWorldBookJson(arguments: JSONObject?): String {
        val raw = arguments?.optString("json")?.trim().orEmpty()
        if (raw.isBlank()) return jsonError("json is required")
        return runCatching {
            val imported = parseStandardWorldBook(raw, AppConfig.aiWorldBookList.size)
            val copyOnConflict = arguments?.optBoolean("copyOnConflict", true) != false
            val existingIds = AppConfig.aiWorldBookList.map { it.id }.toSet()
            val saving = if (copyOnConflict && imported.id in existingIds) {
                imported.copy(id = AiWorldBookConfig(name = imported.name).id, name = "${imported.name} 副本")
            } else {
                imported
            }
            AppConfig.aiWorldBookList = AppConfig.aiWorldBookList
                .filterNot { !copyOnConflict && it.id == saving.id }
                .plus(saving)
            JSONObject()
                .put("ok", true)
                .put("item", saving.toJson(includeEntries = true, includeBindings = true))
                .toString()
        }.getOrElse { throwable ->
            jsonError(throwable.localizedMessage ?: throwable.javaClass.simpleName)
        }
    }

    fun exportWorldBookJson(arguments: JSONObject?): String {
        val id = arguments?.optString("id")?.trim().orEmpty()
        val worldBook = AppConfig.aiWorldBookList.firstOrNull { it.id == id }
            ?: return jsonError("world book not found")
        return JSONObject()
            .put("ok", true)
            .put("json", exportStandardWorldBook(worldBook).toString())
            .put("item", worldBook.toJson(includeEntries = true, includeBindings = true))
            .toString()
    }

    fun parseStandardWorldBook(raw: String, order: Int = 0): AiWorldBookConfig {
        val root = JSONObject(raw)
        val data = root.optJSONObject("data") ?: root
        val type = root.optString("type", data.optString("type", AiWorldBookConfig.TYPE_LOREBOOK))
        require(type == AiWorldBookConfig.TYPE_LOREBOOK) { "unsupported world book type: $type" }
        val entriesArray = data.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until entriesArray.length()) {
                val item = entriesArray.optJSONObject(index) ?: continue
                val name = item.optString("name")
                    .ifBlank { item.optString("title") }
                    .trim()
                val content = item.optString("content").trim()
                if (name.isBlank() || content.isBlank()) continue
                val keywords = item.optStringArray("keywords", item.optStringArray("keys", emptyList()))
                val useRegex = item.optBoolean("useRegex", item.optBoolean("regexEnabled", false))
                val constantActive = item.optBoolean("constantActive", item.optBoolean("constant", false))
                add(
                    AiWorldBookEntry(
                        id = item.optString("id").trim().ifBlank { AiWorldBookEntry(title = name, content = content).id },
                        title = name,
                        name = name,
                        content = content.take(8_000),
                        keys = keywords,
                        keywords = keywords,
                        secondaryKeys = item.optStringArray("secondaryKeys", emptyList()),
                        excludeKeys = item.optStringArray("excludeKeys", emptyList()),
                        regexEnabled = useRegex,
                        useRegex = useRegex,
                        caseSensitive = item.optBoolean("caseSensitive", false),
                        enabled = item.optBoolean("enabled", true),
                        constant = constantActive,
                        constantActive = constantActive,
                        priority = item.optInt("priority", 0).coerceIn(-9999, 9999),
                        position = normalizePosition(item.optString("position", AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT)),
                        injectDepth = item.optInt("injectDepth", 4).coerceIn(0, 64),
                        role = normalizeRole(item.optString("role", AiWorldBookEntry.ROLE_USER)),
                        scanDepth = item.optInt("scanDepth", 4).coerceIn(1, 64),
                        maxMatches = item.optInt("maxMatches", 1).coerceIn(1, 20),
                        order = item.optInt("order", index)
                    )
                )
            }
        }
        val name = data.optString("name").trim()
        require(name.isNotBlank()) { "world book name is required" }
        return AiWorldBookConfig(
            id = data.optString("id").trim().ifBlank { AiWorldBookConfig(name = name).id },
            name = name,
            description = data.optString("description").trim(),
            version = root.optInt("version", data.optInt("version", 1)).takeIf { it > 0 } ?: 1,
            type = AiWorldBookConfig.TYPE_LOREBOOK,
            enabled = data.optBoolean("enabled", true),
            bindingVersion = 1,
            maxEntries = data.optInt("maxEntries", 12).coerceIn(1, 40),
            bindings = emptyList(),
            order = order,
            entries = entries
        )
    }

    fun exportStandardWorldBook(worldBook: AiWorldBookConfig): JSONObject {
        return JSONObject()
            .put("version", worldBook.version.takeIf { it > 0 } ?: 1)
            .put("type", AiWorldBookConfig.TYPE_LOREBOOK)
            .put(
                "data",
                JSONObject()
                    .put("id", worldBook.id)
                    .put("name", worldBook.name)
                    .put("description", worldBook.description)
                    .put("enabled", worldBook.enabled)
                    .put("entries", JSONArray().also { array ->
                        worldBook.entries.forEach { entry ->
                            array.put(entry.toStandardJson())
                        }
                    })
            )
    }

    private fun AiWorldBookConfig.activeBinding(context: AiMemoryContext?): AiWorldBookBinding? {
        val companionId = context?.companionId?.trim().orEmpty()
        if (companionId.isNotBlank()) {
            val companion = AppConfig.aiChatCompanionList.firstOrNull {
                it.id == companionId && it.enabled
            }
            if (companion?.worldBookIds?.contains(id) == true) {
                return AiWorldBookBinding(
                    targetType = AiWorldBookBinding.TARGET_COMPANION,
                    targetKey = companionId
                )
            }
        }
        return bindings
            .filter { it.enabled }
            .firstOrNull { it.matches(context) }
    }

    private fun AiWorldBookBinding.matches(context: AiMemoryContext?): Boolean {
        return when (targetType) {
            AiWorldBookBinding.TARGET_GLOBAL -> true
            AiWorldBookBinding.TARGET_COMPANION -> context?.companionId?.isNotBlank() == true &&
                    targetKey.isNotBlank() &&
                    context.companionId == targetKey
            else -> false
        }
    }

    private fun List<AiChatMessage>.scanText(scanDepth: Int): String {
        return takeLast(scanDepth.coerceAtLeast(1))
            .joinToString("\n") { it.content }
            .take(12_000)
    }

    private fun AiWorldBookEntry.matchedKeys(text: String): List<String> {
        val activeKeywords = keywords.ifEmpty { keys }
        if (activeKeywords.isEmpty()) return emptyList()
        return activeKeywords.filter { key -> text.matchesWorldBookKey(key, useRegex || regexEnabled, caseSensitive) }
            .take(maxMatches)
    }

    private fun AiWorldBookEntry.matchesSecondary(text: String): Boolean {
        if (secondaryKeys.isEmpty()) return true
        return secondaryKeys.any { text.matchesWorldBookKey(it, useRegex || regexEnabled, caseSensitive) }
    }

    private fun AiWorldBookEntry.isExcluded(text: String): Boolean {
        return excludeKeys.any { text.matchesWorldBookKey(it, useRegex || regexEnabled, caseSensitive) }
    }

    private fun String.matchesWorldBookKey(key: String, regexEnabled: Boolean, caseSensitive: Boolean): Boolean {
        if (isBlank() || key.isBlank()) return false
        return if (regexEnabled) {
            runCatching {
                val options = if (caseSensitive) {
                    setOf(RegexOption.MULTILINE)
                } else {
                    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
                }
                Regex(key, options).containsMatchIn(this)
            }.getOrDefault(false)
        } else {
            contains(key, ignoreCase = !caseSensitive)
        }
    }

    private fun AiWorldBookConfig.toJson(
        includeEntries: Boolean,
        includeBindings: Boolean
    ): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("description", description)
            .put("version", version)
            .put("type", type)
            .put("scope", scope)
            .put("bookKey", bookKey)
            .put("enabled", enabled)
            .put("bindingVersion", bindingVersion)
            .put("maxEntries", maxEntries)
            .put("order", order)
            .put("bindingCount", bindings.size)
            .put("entryCount", entries.size)
            .apply {
                if (includeBindings) {
                    put("bindings", JSONArray().also { array ->
                        bindings.forEach { array.put(it.toJson()) }
                    })
                }
                if (includeEntries) {
                    put("entries", JSONArray().also { array ->
                        entries.forEach { array.put(it.toJson()) }
                    })
                }
            }
    }

    private fun AiWorldBookEntry.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("name", name.ifBlank { title })
            .put("content", content)
            .put("keys", JSONArray(keys))
            .put("keywords", JSONArray(keywords.ifEmpty { keys }))
            .put("secondaryKeys", JSONArray(secondaryKeys))
            .put("excludeKeys", JSONArray(excludeKeys))
            .put("regexEnabled", regexEnabled)
            .put("useRegex", useRegex || regexEnabled)
            .put("caseSensitive", caseSensitive)
            .put("enabled", enabled)
            .put("constant", constant)
            .put("constantActive", constantActive || constant)
            .put("priority", priority)
            .put("position", position)
            .put("injectDepth", injectDepth)
            .put("role", role)
            .put("scanDepth", scanDepth)
            .put("maxMatches", maxMatches)
            .put("order", order)
    }

    private fun AiWorldBookEntry.toStandardJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name.ifBlank { title })
            .put("enabled", enabled)
            .put("priority", priority)
            .put("position", normalizePosition(position))
            .put("content", content)
            .put("injectDepth", injectDepth)
            .put("role", normalizeRole(role))
            .put("keywords", JSONArray(keywords.ifEmpty { keys }))
            .put("useRegex", useRegex || regexEnabled)
            .put("caseSensitive", caseSensitive)
            .put("scanDepth", scanDepth)
            .put("constantActive", constantActive || constant)
    }

    private fun AiWorldBookBinding.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("targetType", targetType)
            .put("targetKey", targetKey)
            .put("enabled", enabled)
            .put("order", order)
    }

    private fun JSONObject.optStringArray(name: String, fallback: List<String>): List<String> {
        val array = optJSONArray(name) ?: return fallback
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct().take(40)
    }

    private fun JSONObject.optBindingArray(
        name: String,
        fallback: List<AiWorldBookBinding>
    ): List<AiWorldBookBinding> {
        val array = optJSONArray(name) ?: return fallback
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val targetType = normalizeTargetType(item.optString("targetType").trim())
                if (targetType.isBlank()) continue
                add(
                    AiWorldBookBinding(
                        id = item.optString("id").trim().ifBlank { AiWorldBookBinding().id },
                        targetType = targetType,
                        targetKey = item.optString("targetKey").trim(),
                        enabled = item.optBoolean("enabled", true),
                        order = item.optInt("order", size)
                    )
                )
            }
        }
    }

    private fun legacyBindingsFromArguments(arguments: JSONObject): List<AiWorldBookBinding> {
        val scope = arguments.optString("scope").trim()
        return when (scope) {
            AiWorldBookConfig.SCOPE_GLOBAL -> listOf(AiWorldBookBinding(targetType = AiWorldBookBinding.TARGET_GLOBAL))
            else -> emptyList()
        }
    }

    private fun normalizeTargetType(targetType: String): String {
        return when (targetType) {
            AiWorldBookBinding.TARGET_GLOBAL,
            AiWorldBookBinding.TARGET_COMPANION -> targetType
            else -> ""
        }
    }

    private fun normalizePosition(position: String): String {
        return when (position) {
            AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT,
            AiWorldBookEntry.POSITION_BEFORE_PROMPT,
            AiWorldBookEntry.POSITION_INJECT_DEPTH,
            AiWorldBookEntry.POSITION_BEFORE_LAST_USER -> position
            else -> AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT
        }
    }

    private fun normalizeRole(role: String): String {
        return when (role) {
            AiWorldBookEntry.ROLE_SYSTEM,
            AiWorldBookEntry.ROLE_USER,
            AiWorldBookEntry.ROLE_ASSISTANT -> role
            else -> AiWorldBookEntry.ROLE_USER
        }
    }

    private fun jsonError(message: String): String {
        return JSONObject()
            .put("ok", false)
            .put("error", message)
            .toString()
    }
}
