package io.legado.app.feature.reader.core.selection

data class ReaderSearchRequest(
    val directIndex: Int,
    val directLength: Int,
    val occurrence: Int,
    val isRegex: Boolean,
)

data class ReaderSearchMatch(val start: Int, val length: Int)

/** Resolves a search result against the latest processed chapter text, without page coordinates. */
object ReaderSearchMatcher {
    fun find(content: String, query: String, request: ReaderSearchRequest): ReaderSearchMatch? {
        if (query.isEmpty()) return null
        val directLength = request.directLength.takeIf { it > 0 } ?: query.length
        val directIndex = request.directIndex
        if (directIndex >= 0 && directIndex + directLength <= content.length) {
            val matches = if (request.isRegex) {
                runCatching {
                    Regex(query).matches(content.substring(directIndex, directIndex + directLength))
                }.getOrDefault(false)
            } else {
                content.regionMatches(directIndex, query, 0, query.length, ignoreCase = false)
            }
            if (matches) return ReaderSearchMatch(directIndex, directLength)
        }
        if (request.isRegex) {
            return runCatching {
                Regex(query).findAll(content)
                    .drop(request.occurrence.coerceAtLeast(0))
                    .firstOrNull()
                    ?.let { ReaderSearchMatch(it.range.first, it.value.length) }
            }.getOrNull()
        }
        var occurrence = 0
        var index = content.indexOf(query)
        while (occurrence != request.occurrence.coerceAtLeast(0) && index >= 0) {
            index = content.indexOf(query, index + query.length)
            occurrence += 1
        }
        return index.takeIf { it >= 0 }?.let { ReaderSearchMatch(it, query.length) }
    }
}
