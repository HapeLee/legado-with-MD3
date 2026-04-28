package io.legado.app.domain.model

import io.legado.app.utils.splitNotBlank

data class BookSearchScope(val raw: String) {

    val items: List<String>
        get() = raw.splitNotBlank(",").toList()

    val isAll: Boolean
        get() = raw.isEmpty()

    val isSource: Boolean
        get() = items.isNotEmpty() && parseSourceItems().size == items.size

    val groupNames: List<String>
        get() = if (isSource) emptyList() else items

    val sourceUrls: List<String>
        get() = parseSourceItems().map { it.url }

    private fun parseSourceItems(): List<ScopeSourceItem> {
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

    private data class ScopeSourceItem(
        val name: String,
        val url: String,
    )
}
