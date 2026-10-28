package io.legado.app.ui.book.cache.manage

import io.legado.app.domain.model.CacheMaintenanceRules.findOrphanSubjects
import io.legado.app.domain.model.CacheMaintenanceRules.parseStoredWordCount
import io.legado.app.domain.model.CacheMaintenanceRules.sumWordCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 缓存维护最容易出错的地方在口径，不在编排：
 * 什么算孤儿、存的字数能不能还原、大书求和会不会溢出。这里把这些钉死。
 */
class CacheMaintenanceRulesTest {

    // formatBytes 走 Locale.getDefault()，不同机器上小数分隔符可能不一致，这里固定住。
    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `findOrphanSubjects - 目录与 nb 文件主体不在章节列表时算孤儿`() {
        val legal = setOf("00001-aaa", "00002-bbb")

        val found = findOrphanSubjects(
            cachedEntryNames = listOf("00001-aaa", "00002-bbb", "00003-ccc", "00004-ddd.nb"),
            legalSubjects = legal,
            bookImageFolderName = "images",
        )

        assertEquals(listOf("00003-ccc", "00004-ddd.nb"), found)
    }

    @Test
    fun `findOrphanSubjects - 书级图片目录永不算孤儿`() {
        val found = findOrphanSubjects(
            cachedEntryNames = listOf("images", "00009-zzz"),
            legalSubjects = setOf("00001-aaa"),
            bookImageFolderName = "images",
        )

        assertEquals(listOf("00009-zzz"), found)
    }

    @Test
    fun `findOrphanSubjects - 章节列表为空时不产出孤儿`() {
        // 书刚入库、还没拉到目录时，磁盘上的缓存不能因为查无此章被判要删。
        val found = findOrphanSubjects(
            cachedEntryNames = listOf("00001-aaa", "00002-bbb.nb"),
            legalSubjects = emptySet(),
            bookImageFolderName = "images",
        )

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `findOrphanSubjects - legacy 平铺 nb 与章节目录同一口径`() {
        val found = findOrphanSubjects(
            cachedEntryNames = listOf("00007-ghost.nb"),
            legalSubjects = setOf("00007-ghost"),
            bookImageFolderName = "images",
        )

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `parseStoredWordCount - 识别字与万字两种写法`() {
        assertEquals(1234, parseStoredWordCount("1234字"))
        assertEquals(123_000, parseStoredWordCount("12.3万字"))
        assertEquals(56, parseStoredWordCount("56"))
    }

    @Test
    fun `parseStoredWordCount - 无法识别的书源文案返回 null 而不是猜一个数`() {
        assertNull(parseStoredWordCount("待统计"))
        assertNull(parseStoredWordCount("约100万字"))
        assertNull(parseStoredWordCount(""))
        assertNull(parseStoredWordCount(null))
    }

    @Test
    fun `sumWordCounts - 溢出时截断而不是变成负数`() {
        assertEquals(Int.MAX_VALUE, sumWordCounts(listOf(Int.MAX_VALUE, 1)))
        assertEquals(300, sumWordCounts(listOf(100, 200)))
    }

    @Test
    fun `formatBytes - 不足 1GB 用 MB`() {
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("0.0 MB", formatBytes(0L))
    }
}
