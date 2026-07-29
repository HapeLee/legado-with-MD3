package io.legado.app.ui.book.read

import io.legado.app.ui.book.searchContent.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R2.2 —— [ReadSearchResultLocator.findMatch] 的行为覆盖。
 *
 * 这段逻辑原来埋在 `ReadBookViewModel` 里没法测。它有两条路径：
 * 先信 `queryIndexInChapter` 直达下标，校验不过再退回「数到第 N 次出现」的扫描。
 * 直达下标来自搜索时那一版正文，正文被替换规则/AI 净化改写后就会失效——
 * 校验分支失效的话，搜索跳转会静默跳到错的位置。
 */
class ReadSearchResultLocatorTest {

    private fun result(
        indexInChapter: Int = -1,
        countWithinChapter: Int = 0,
        matchLength: Int = 0,
        isRegex: Boolean = false,
    ) = SearchResult(
        query = "",
        queryIndexInChapter = indexInChapter,
        resultCountWithinChapter = countWithinChapter,
        matchLength = matchLength,
        isRegex = isRegex,
    )

    @Test
    fun `直达下标命中时直接采用`() {
        val content = "海边有座灯塔，灯塔很亮。"
        val match = ReadSearchResultLocator.findMatch(
            content = content,
            searchResult = result(indexInChapter = content.indexOf("灯塔", 7), matchLength = 2),
            query = "灯塔",
        )
        assertEquals(7 to 2, match)
    }

    @Test
    fun `直达下标失效时退回按出现次数扫描`() {
        val content = "海边有座灯塔，灯塔很亮。"
        // 正文被改写过，旧下标 99 已越界，必须退回扫描第 1 次出现（0-based）
        val match = ReadSearchResultLocator.findMatch(
            content = content,
            searchResult = result(indexInChapter = 99, countWithinChapter = 1),
            query = "灯塔",
        )
        assertEquals(7 to 2, match)
    }

    @Test
    fun `直达下标指向了别的字时不被采用`() {
        val content = "海边有座灯塔，灯塔很亮。"
        // 下标 0 处是「海边」不是「灯塔」，regionMatches 校验应失败并退回扫描第 0 次出现
        val match = ReadSearchResultLocator.findMatch(
            content = content,
            searchResult = result(indexInChapter = 0, matchLength = 2, countWithinChapter = 0),
            query = "灯塔",
        )
        assertEquals(4 to 2, match)
    }

    @Test
    fun `空查询返回 null`() {
        assertNull(
            ReadSearchResultLocator.findMatch(
                content = "海边有座灯塔。",
                searchResult = result(indexInChapter = 0),
                query = "",
            )
        )
    }

    @Test
    fun `正文里找不到时返回 null`() {
        assertNull(
            ReadSearchResultLocator.findMatch(
                content = "海边有座灯塔。",
                searchResult = result(indexInChapter = -1),
                query = "潜水艇",
            )
        )
    }

    @Test
    fun `正则匹配返回实际匹配长度而非查询串长度`() {
        val match = ReadSearchResultLocator.findMatch(
            content = "第1章 起点\n第22章 终点",
            searchResult = result(indexInChapter = -1, countWithinChapter = 1, isRegex = true),
            query = """第\d+章""",
        )
        // 第 1 次（0-based）出现是「第22章」，长度 4 而不是 pattern 的长度
        assertEquals(7 to 4, match)
    }

    @Test
    fun `非法正则不抛异常而是返回 null`() {
        assertNull(
            ReadSearchResultLocator.findMatch(
                content = "海边有座灯塔。",
                searchResult = result(indexInChapter = -1, isRegex = true),
                query = "[未闭合",
            )
        )
    }

    @Test
    fun `matchLength 为 0 时回退成查询串长度`() {
        val content = "海边有座灯塔。"
        val match = ReadSearchResultLocator.findMatch(
            content = content,
            searchResult = result(indexInChapter = 4, matchLength = 0),
            query = "灯塔",
        )
        assertEquals(4 to 2, match)
    }
}
