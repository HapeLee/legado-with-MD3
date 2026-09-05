package io.legado.app.feature.reader.core.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSearchMatcherTest {
    private fun request(
        directIndex: Int = -1,
        occurrence: Int = 0,
        directLength: Int = 0,
        isRegex: Boolean = false,
    ) = ReaderSearchRequest(directIndex, directLength, occurrence, isRegex)

    @Test
    fun `直达下标命中时直接采用`() {
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(7, 2),
            ReaderSearchMatcher.find(content, "灯塔", request(directIndex = 7, directLength = 2)),
        )
    }

    @Test
    fun `直达下标失效时退回按出现次数扫描`() {
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(7, 2),
            ReaderSearchMatcher.find(content, "灯塔", request(directIndex = 99, occurrence = 1)),
        )
    }

    @Test
    fun `直达下标指向其他文字时回退扫描`() {
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(4, 2),
            ReaderSearchMatcher.find(content, "灯塔", request(directIndex = 0, directLength = 2)),
        )
    }

    @Test
    fun `空查询或正文无结果返回 null`() {
        assertNull(ReaderSearchMatcher.find("海边有座灯塔。", "", request(directIndex = 0)))
        assertNull(ReaderSearchMatcher.find("海边有座灯塔。", "潜水艇", request()))
    }

    @Test
    fun `正则回退返回实际匹配长度`() {
        assertEquals(
            ReaderSearchMatch(7, 4),
            ReaderSearchMatcher.find(
                "第1章 起点\n第22章 终点",
                """第\d+章""",
                request(occurrence = 1, isRegex = true),
            ),
        )
    }

    @Test
    fun `非法正则不抛异常`() {
        assertNull(ReaderSearchMatcher.find("海边有座灯塔。", "[未闭合", request(isRegex = true)))
    }

    @Test
    fun `零直达长度回退查询长度`() {
        assertEquals(
            ReaderSearchMatch(4, 2),
            ReaderSearchMatcher.find("海边有座灯塔。", "灯塔", request(directIndex = 4)),
        )
    }
}
