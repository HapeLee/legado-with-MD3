package io.legado.app.feature.reader.core.selection

import io.legado.app.utils.RegexDeadlineSequence

private const val REGEX_MATCH_TIMEOUT_MILLIS = 500L

data class ReaderSearchRequest(
    val directIndex: Int,
    val directLength: Int,
    val occurrence: Int,
    val isRegex: Boolean,
)

data class ReaderSearchMatch(val start: Int, val length: Int, val isTitle: Boolean = false)

/** Resolves a search result against the latest processed chapter text, without page coordinates. */
object ReaderSearchMatcher {
    /**
     * [title] is the exact title prefix used by full-text search. It stays outside the
     * renderer's body coordinate system, but is included while resolving occurrences.
     */
    fun find(
        content: String,
        query: String,
        request: ReaderSearchRequest,
        title: String? = null,
    ): ReaderSearchMatch? {
        if (query.isEmpty()) return null
        val titlePrefix = title?.let { "$it\n" }.orEmpty()
        val searchContent = titlePrefix + content
        val occurrence = request.occurrence.coerceAtLeast(0)
        // 定位锚点只用"本章第几条命中"，与老 View 实现（TextChapter 内按序号重扫）一致。
        // 检索层按同一份正文顺序编号：替换净化、图片占位、简繁转换只会让绝对偏移整体位移，
        // 序号仍然稳定，所以不能用 queryIndexInChapter 直接猜位置。
        val byOccurrence = resolveByOccurrence(searchContent, query, request.isRegex, occurrence)
        if (byOccurrence != null) {
            return matchAt(byOccurrence.first, byOccurrence.second, titlePrefix.length)
        }
        // 序号越界说明章节在搜索后被改短了，退回"文本已核对"的直达下标。
        return resolveByDirectIndex(searchContent, query, request, titlePrefix.length)
    }

    private fun resolveByOccurrence(
        content: String,
        query: String,
        isRegex: Boolean,
        occurrence: Int,
    ): Pair<Int, Int>? {
        if (isRegex) {
            // 定位在主线程执行，给正则一个短预算：病态表达式超时后退回直达下标，
            // 不能像检索流程那样冻结界面。
            return runCatching {
                Regex(query).findAll(RegexDeadlineSequence(content, REGEX_MATCH_TIMEOUT_MILLIS))
                    .filter { match -> match.range.last >= match.range.first }
                    .drop(occurrence)
                    .firstOrNull()
                    ?.let { it.range.first to (it.range.last + 1 - it.range.first) }
            }.getOrNull()
        }
        var index = content.indexOf(query)
        var remaining = occurrence
        while (index >= 0 && remaining > 0) {
            index = content.indexOf(query, index + query.length)
            remaining -= 1
        }
        return index.takeIf { it >= 0 }?.let { it to query.length }
    }

    private fun resolveByDirectIndex(
        searchContent: String,
        query: String,
        request: ReaderSearchRequest,
        titlePrefixLength: Int,
    ): ReaderSearchMatch? {
        val directLength = request.directLength.takeIf { it > 0 } ?: query.length
        val directIndex = request.directIndex.takeIf { it in 0..searchContent.length }
            ?: return null
        if (directIndex + directLength > searchContent.length) return null
        val sameText = if (request.isRegex) {
            val matchedText = searchContent.substring(directIndex, directIndex + directLength)
            runCatching {
                Regex(query).matches(
                    RegexDeadlineSequence(matchedText, REGEX_MATCH_TIMEOUT_MILLIS)
                )
            }.getOrDefault(false)
        } else {
            // 检索层用大小写敏感的 indexOf，这里必须一致，否则会把不同大小写的同词当成命中。
            searchContent.regionMatches(directIndex, query, 0, query.length, ignoreCase = false)
        }
        if (!sameText) return null
        return matchAt(directIndex, directLength, titlePrefixLength)
    }

    private fun matchAt(index: Int, length: Int, titlePrefixLength: Int): ReaderSearchMatch? =
        when {
            titlePrefixLength == 0 -> ReaderSearchMatch(index, length)
            index + length <= titlePrefixLength - 1 -> ReaderSearchMatch(
                index,
                length,
                isTitle = true
            )

            index >= titlePrefixLength -> ReaderSearchMatch(index - titlePrefixLength, length)
            // A regular expression may straddle the title/body separator. It cannot be painted as
            // one Canvas range, so never substitute an unrelated visible match.
            else -> null
        }
}
