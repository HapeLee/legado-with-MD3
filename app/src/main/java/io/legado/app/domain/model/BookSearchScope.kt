package io.legado.app.domain.model

import com.google.gson.GsonBuilder
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.splitNotBlank

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

    private data class SerializedSearchScope(
        val type: String = "",
        val groups: List<String> = emptyList(),
        val sources: List<ScopeSourceItem> = emptyList(),
    )

    companion object {

        fun encodeGroups(groups: List<String>): String {
            val selected = groups.filter { it.isNotBlank() }
            return if (selected.isEmpty()) {
                ""
            } else {
                scopeGson.toJson(SerializedSearchScope(type = TYPE_GROUP, groups = selected))
            }
        }

        fun encodeSources(sources: List<ScopeSourceItem>): String {
            val selected = sources.filter { it.url.isNotBlank() }
            return if (selected.isEmpty()) {
                ""
            } else {
                scopeGson.toJson(SerializedSearchScope(type = TYPE_SOURCE, sources = selected))
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

            return scopeGson.fromJsonObject<SerializedSearchScope>(json).getOrNull()?.let { scope ->
                when (scope.type) {
                    TYPE_SOURCE -> ParsedSearchScope(
                        sources = scope.sources.filter { it.url.isNotBlank() }
                    )

                    TYPE_GROUP -> ParsedSearchScope(
                        groups = scope.groups.filter { it.isNotBlank() }
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

        private const val TYPE_GROUP = "group"
        private const val TYPE_SOURCE = "source"
        private val scopeGson = GsonBuilder().disableHtmlEscaping().create()
    }

}
