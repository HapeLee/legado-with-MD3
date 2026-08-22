package io.legado.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckBookContentQualityUseCaseTest {

    @Test
    fun parseChapterIndicesSupportsRangesAndIgnoresOutOfBounds() {
        assertEquals(
            listOf(1, 3, 4, 5),
            CheckBookContentQualityUseCase.parseChapterIndices("1, 3-5, 99", 5),
        )
    }

    @Test
    fun parseChapterIndicesSupportsChineseRangeSeparators() {
        assertEquals(
            listOf(2, 3, 4),
            CheckBookContentQualityUseCase.parseChapterIndices("第4至2章", 4),
        )
    }

    @Test
    fun parseChapterIndicesReturnsEmptyForEmptySpecOrZeroToc() {
        assertEquals(emptyList<Int>(), CheckBookContentQualityUseCase.parseChapterIndices("", 10))
        assertEquals(emptyList<Int>(), CheckBookContentQualityUseCase.parseChapterIndices("1-5", 0))
        assertEquals(emptyList<Int>(), CheckBookContentQualityUseCase.parseChapterIndices("abc", 10))
    }

    @Test
    fun cleanContentStripsHtmlCollapsesWhitespaceAndDropsHead() {
        val useCase = CheckBookContentQualityUseCase
        val content = "<p>第一章 起点</p>   正文开始\n主角登场"
        assertEquals("第一章 起点 正文开始 主角登场", useCase.cleanContent(content, 0))
        // skipHeadChars 掐掉头部
        assertEquals("主角登场", useCase.cleanContent(content, 12))
        // 负数按 0 处理
        assertEquals("第一章 起点 正文开始 主角登场", useCase.cleanContent(content, -5))
    }

    @Test
    fun countOccurrencesCountsNonOverlappingCaseInsensitive() {
        val useCase = CheckBookContentQualityUseCase
        assertEquals(2, useCase.countOccurrences("主角 与 主角 对话", "主角"))
        // 大小写不敏感
        assertEquals(2, useCase.countOccurrences("aAa A aA", "aa"))
        // 非重叠：连排关键词只计一次位置
        assertEquals(2, useCase.countOccurrences("aaaa", "aa"))
        // 空关键词
        assertEquals(0, useCase.countOccurrences("任意文本", ""))
        assertEquals(0, useCase.countOccurrences("任意文本", "  "))
    }
}
