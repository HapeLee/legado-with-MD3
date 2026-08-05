package io.legado.app.ui.book.toc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TocTitleCacheTest {

    @Before
    @After
    fun clearCache() {
        TocTitleCache.clear()
    }

    @Test
    fun `completed titles are reused by a new directory instance`() {
        val key = cacheKey()
        val titles = mutableMapOf(0 to "处理后的标题")

        TocTitleCache.put(key, titles)
        titles[0] = "外部修改"

        val cached = TocTitleCache.get(key)
        assertEquals("处理后的标题", cached?.get(0))
        assertNotSame(titles, cached)
    }

    @Test
    fun `changed chapter fingerprint does not reuse stale titles`() {
        TocTitleCache.put(cacheKey(chaptersFingerprint = 1L), mapOf(0 to "旧标题"))

        assertNull(TocTitleCache.get(cacheKey(chaptersFingerprint = 2L)))
    }

    private fun cacheKey(chaptersFingerprint: Long = 1L) = TitleCacheKey(
        bookUrl = "book-url",
        useReplace = true,
        rulesFingerprint = 1,
        chineseConverterType = 0,
        chapterCount = 1,
        chaptersFingerprint = chaptersFingerprint
    )
}
