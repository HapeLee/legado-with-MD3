package io.legado.app.domain.model

import io.legado.app.utils.splitNotBlank
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class BookSearchScope(val raw: String) {

    private val parsed: ParsedSearchScope by lazy {
        parse(raw)
    }

    val items: List<String>
        get() = when {
            isSource -> sourceUrls
            else -> groupNames
        }

    val isAll: Boolean
        get() = parsed.isAll

    val isSource: Boolean
        get() = parsed.sources.isNotEmpty()

    val groupNames: List<String>
        get() = parsed.groups

    val sourceUrls: List<String>
        get() = parsed.sources.map { it.url }

    val sourceNames: List<String>
        get() = parsed.sources.map { it.name }

    private data class ParsedSearchScope(
        val groups: List<String> = emptyList(),
        val sources: List<ScopeSourceItem> = emptyList(),
    ) {
        val isAll: Boolean
            get() = groups.isEmpty() && sources.isEmpty()
    }

    data class ScopeSourceItem(
        val name: String,
        val url: String,
    )

    companion object {

        fun encodeGroups(groups: List<String>): String {
            val selected = groups.filter { it.isNotBlank() }
            return if (selected.isEmpty()) {
                ""
            } else {
                buildJsonObject {
                    put("type", JsonPrimitive(TYPE_GROUP))
                    put("groups", buildJsonArray {
                        selected.forEach { add(JsonPrimitive(it)) }
                    })
                }.toString()
            }
        }

        fun encodeSources(sources: List<ScopeSourceItem>): String {
            val selected = sources.filter { it.url.isNotBlank() }
            return if (selected.isEmpty()) {
                ""
            } else {
                buildJsonObject {
                    put("type", JsonPrimitive(TYPE_SOURCE))
                    put("sources", buildJsonArray {
                        selected.forEach { source ->
                            add(buildJsonObject {
                                put("name", JsonPrimitive(source.name))
                                put("url", JsonPrimitive(source.url))
                            })
                        }
                    })
                }.toString()
            }
        }

        private fun parse(raw: String): ParsedSearchScope {
            if (raw.isEmpty()) return ParsedSearchScope()

            parseJson(raw)?.let {
                return it
            }

            return parseLegacy(raw)
        }

        private fun parseJson(raw: String): ParsedSearchScope? {
            val json = raw.trim()
            if (!json.startsWith("{") || !json.endsWith("}")) return null

            return runCatching {
                scopeJson.parseToJsonElement(json).jsonObject
            }.getOrNull()?.let { scope ->
                when (scope["type"]?.jsonPrimitive?.contentOrNull) {
                    TYPE_SOURCE -> ParsedSearchScope(
                        sources = scope["sources"]
                            ?.safeJsonArray()
                            ?.mapNotNull { it.toSourceItemOrNull() }
                            .orEmpty()
                    )

                    TYPE_GROUP -> ParsedSearchScope(
                        groups = scope["groups"]
                            ?.safeJsonArray()
                            ?.mapNotNull { it.toStringOrNull() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty()
                    )

                    else -> null
                }
            }
        }

        private fun parseLegacy(raw: String): ParsedSearchScope {
            val rawItems = raw.split(",").filter { it.isNotBlank() }
            val sourceItems = parseLegacySourceItems(rawItems)
            if (rawItems.isNotEmpty() && sourceItems.size == rawItems.size) {
                return ParsedSearchScope(sources = sourceItems)
            }
            return ParsedSearchScope(groups = raw.splitNotBlank(",").toList())
        }

        private fun parseLegacySourceItems(items: List<String>): List<ScopeSourceItem> {
            return items.mapNotNull { item ->
                val splitIndex = item.indexOf("::")
                if (splitIndex <= 0 || splitIndex >= item.lastIndex) {
                    null
                } else {
                    ScopeSourceItem(
                        name = item.substring(0, splitIndex),
                        url = item.substring(splitIndex + 2)
                    )
                }
            }
        }

        private fun JsonElement.safeJsonArray() = runCatching {
            jsonArray
        }.getOrNull()

        private fun JsonElement.toStringOrNull() = runCatching {
            jsonPrimitive.contentOrNull
        }.getOrNull()

        private fun JsonElement.toSourceItemOrNull(): ScopeSourceItem? {
            return runCatching {
                val item = jsonObject
                val url = item["url"]?.jsonPrimitive?.contentOrNull
                if (url.isNullOrBlank()) {
                    null
                } else {
                    ScopeSourceItem(
                        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = url
                    )
                }
            }.getOrNull()
        }

        private const val TYPE_GROUP = "group"
        private const val TYPE_SOURCE = "source"
        private val scopeJson = Json
    }

}
