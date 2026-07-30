package io.legado.app.ui.book.read

import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.searchContent.SearchResult

/**
 * 把一条 [SearchResult] 定位到当前章排版结果里的 (页, 行, 列)。
 *
 * 这段是纯函数——只吃 (TextChapter, SearchResult, query)，不碰任何 UI 状态，
 * 所以从 `ReadBookViewModel` 摘出来独立成对象，[findMatch] 可以直接写单测覆盖。
 * `ReadBookViewModel.searchResultPositions` 保留为转发入口，供 `ReadBookController` 调用。
 */
internal object ReadSearchResultLocator {

    /** @return `[pageIndex, lineIndex, charIndex, pageSpan, endLineIndex, endCharIndex]`；定位失败首位为 -1。 */
    fun positions(
        textChapter: TextChapter,
        searchResult: SearchResult,
        query: String,
    ): Array<Int> {
        val pages = textChapter.pages
        val content = textChapter.getContent()
        if (pages.isEmpty()) return NOT_FOUND

        val match = findMatch(content, searchResult, query) ?: return NOT_FOUND
        val contentPosition = match.first
        val queryLength = match.second
        if (contentPosition < 0 || queryLength <= 0) {
            return NOT_FOUND
        }

        val start = findTextPoint(pages, contentPosition, preferPreviousLine = false)
            ?: return NOT_FOUND
        val end = findTextPoint(
            pages,
            contentPosition + queryLength - 1,
            preferPreviousLine = true
        ) ?: start
        return arrayOf(
            start.pageIndex,
            start.lineIndex,
            start.charIndex,
            end.pageIndex - start.pageIndex,
            end.lineIndex,
            end.charIndex
        )
    }

    /**
     * 在章节正文里找到这条结果对应的 `位置 to 长度`。
     *
     * 先信 [SearchResult.queryIndexInChapter] 这个直达下标（校验后才用），校验不过再退回
     * 「数到第 [SearchResult.resultCountWithinChapter] 次出现」的扫描。
     */
    internal fun findMatch(
        content: String,
        searchResult: SearchResult,
        query: String,
    ): Pair<Int, Int>? {
        if (query.isEmpty()) return null
        val directLength = if (searchResult.matchLength > 0) searchResult.matchLength else query.length
        val directIndex = searchResult.queryIndexInChapter
        if (directIndex >= 0 && directIndex + directLength <= content.length) {
            val directMatch = if (searchResult.isRegex) {
                runCatching {
                    Regex(query).matches(content.substring(directIndex, directIndex + directLength))
                }.getOrDefault(false)
            } else {
                content.regionMatches(
                    directIndex,
                    query,
                    0,
                    query.length,
                    ignoreCase = false
                )
            }
            if (directMatch) {
                return directIndex to directLength
            }
        }
        if (searchResult.isRegex) {
            return runCatching {
                Regex(query).findAll(content)
                    .drop(searchResult.resultCountWithinChapter)
                    .firstOrNull()
                    ?.let { it.range.first to it.value.length }
            }.getOrNull()
        }

        var count = 0
        var index = content.indexOf(query)
        while (count != searchResult.resultCountWithinChapter && index >= 0) {
            index = content.indexOf(query, index + query.length)
            count += 1
        }
        return index.takeIf { it >= 0 }?.let { it to query.length }
    }

    private fun findTextPoint(
        pages: List<TextPage>,
        contentPosition: Int,
        preferPreviousLine: Boolean,
    ): SearchTextPoint? {
        var fallback: SearchTextPoint? = null
        pages.forEachIndexed { pageIndex, page ->
            page.lines.forEachIndexed { lineIndex, line ->
                if (line.columns.isEmpty()) return@forEachIndexed
                val lineStart = line.chapterPosition
                val lineEndExclusive = lineStart + line.charSize
                if (contentPosition in lineStart until lineEndExclusive) {
                    return SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = (contentPosition - lineStart).coerceIn(0, line.columns.lastIndex)
                    )
                }
                if (preferPreviousLine && line.isParagraphEnd && contentPosition == lineEndExclusive) {
                    return SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = line.columns.lastIndex
                    )
                }
                if (contentPosition >= lineEndExclusive) {
                    fallback = SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = line.columns.lastIndex
                    )
                }
            }
        }
        return fallback
    }

    private val NOT_FOUND get() = arrayOf(-1, 0, 0, 0, 0, 0)

    private data class SearchTextPoint(
        val pageIndex: Int,
        val lineIndex: Int,
        val charIndex: Int,
    )
}
