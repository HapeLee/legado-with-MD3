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
    ) = ReaderSearchRequest(
        directIndex,
        directLength,
        occurrence,
        isRegex,
    )

    @Test
    fun `按出现序号定位章节内第 N 条命中`() {
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(7, 2),
            ReaderSearchMatcher.find(
                content,
                "灯塔",
                request(directIndex = 7, occurrence = 1, directLength = 2),
            ),
        )
    }

    @Test
    fun `序号越界时退回文本已核对的直达下标`() {
        // 章节在搜索后被改短（序号不够用），但该偏移处确实是关键词。
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(7, 2),
            ReaderSearchMatcher.find(
                content,
                "灯塔",
                request(directIndex = 7, occurrence = 5, directLength = 2),
            ),
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
    fun `直达下标指向其他文字时不影响按出现次数定位`() {
        val content = "海边有座灯塔，灯塔很亮。"
        assertEquals(
            ReaderSearchMatch(4, 2),
            ReaderSearchMatcher.find(content, "灯塔", request(directIndex = 0, directLength = 2)),
        )
    }

    @Test
    fun `位移后的直达下标落到另一个同词时按出现序号定位`() {
        // 检索文档与渲染文档不同源（替换净化、图片占位）时偏移会整体位移，
        // 位移后的下标可能正好压在另一个同词上；出现序号才是稳定锚点。
        val content = "灯塔在前，灯塔在后。"
        assertEquals(
            ReaderSearchMatch(0, 2),
            ReaderSearchMatcher.find(
                content,
                "灯塔",
                request(directIndex = 5, occurrence = 0, directLength = 2),
            ),
        )
    }

    @Test
    fun `标题前缀后的直达下标映射到正文坐标`() {
        // 检索层的章节文本是“标题\\n正文”，Canvas 只以正文建立坐标。
        // 若不先扣标题长度，原下标可能碰巧指向正文中的另一个同词。
        val content = "灯塔在前，灯塔在后。"
        assertEquals(
            ReaderSearchMatch(0, 2),
            ReaderSearchMatcher.find(
                content,
                "灯塔",
                request(
                    directIndex = 3,
                    occurrence = 0,
                ),
                title = "标题",
            ),
        )
    }

    @Test
    fun `按出现次数定位时保留检索标题中的匹配`() {
        val content = "灯塔在前，灯塔在后。"
        assertEquals(
            ReaderSearchMatch(5, 2),
            ReaderSearchMatcher.find(
                content = content,
                query = "灯塔",
                request = request(directIndex = 99, occurrence = 2),
                title = "灯塔章节",
            ),
        )
    }

    @Test
    fun `标题命中使用标题坐标而不误标正文`() {
        assertEquals(
            ReaderSearchMatch(0, 2, isTitle = true),
            ReaderSearchMatcher.find(
                content = "正文也有灯塔。",
                query = "灯塔",
                request = request(directIndex = 0, directLength = 2),
                title = "灯塔章节",
            ),
        )
    }

    @Test
    fun `空查询或正文无结果返回 null`() {
        assertNull(ReaderSearchMatcher.find("海边有座灯塔。", "", request(directIndex = 0)))
        assertNull(ReaderSearchMatcher.find("海边有座灯塔。", "潜水艇", request()))
    }

    @Test
    fun `大小写不同的同词不算命中`() {
        // 检索层是大小写敏感的 indexOf，定位与回退都必须一致。
        assertEquals(
            ReaderSearchMatch(4, 3),
            ReaderSearchMatcher.find("Foo foo", "foo", request(occurrence = 0, directLength = 3)),
        )
        assertNull(
            ReaderSearchMatcher.find(
                "Foo foo",
                "foo",
                request(directIndex = 0, occurrence = 5, directLength = 3),
            )
        )
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
    fun `零宽正则不会产生空选区`() {
        assertNull(ReaderSearchMatcher.find("abc", "(?=b)", request(isRegex = true)))
        assertEquals(
            ReaderSearchMatch(0, 1),
            ReaderSearchMatcher.find("abc", "a*", request(isRegex = true)),
        )
    }

    @Test
    fun `零直达长度回退查询长度`() {
        assertEquals(
            ReaderSearchMatch(4, 2),
            ReaderSearchMatcher.find("海边有座灯塔。", "灯塔", request(directIndex = 4)),
        )
    }
}
