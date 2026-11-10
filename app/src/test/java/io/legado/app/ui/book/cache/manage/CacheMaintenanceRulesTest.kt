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
 * 什么算孤儿（含历史命名残留）、存的字数能不能还原、大书求和会不会溢出。
 */
class CacheMaintenanceRulesTest {

    // formatBytes 走 Locale.getDefault()，不同机器上小数分隔符可能不一致，这里固定住。
    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `findOrphanSubjects - 目录与 nb 文件主体不在章节列表时算孤儿`() {
        val legal = setOf("00001-aaaaaaaaaaaaaaaa", "00002-bbbbbbbbbbbbbbbb")

        val found = findOrphanSubjects(
            cachedEntryNames = listOf(
                "00001-aaaaaaaaaaaaaaaa",
                "00002-bbbbbbbbbbbbbbbb",
                "00003-cccccccccccccccc",
                "00004-dddddddddddddddd.nb",
            ),
            legalSubjects = legal,
            bookImageFolderName = "images",
        )

        assertEquals(
            listOf("00003-cccccccccccccccc", "00004-dddddddddddddddd.nb"),
            found,
        )
    }

    @Test
    fun `findOrphanSubjects - 书级图片目录永不算孤儿`() {
        val found = findOrphanSubjects(
            cachedEntryNames = listOf("images", "00009-9999999999999999"),
            legalSubjects = setOf("00001-aaaaaaaaaaaaaaaa"),
            bookImageFolderName = "images",
        )

        assertEquals(listOf("00009-9999999999999999"), found)
    }

    @Test
    fun `findOrphanSubjects - 章节列表为空时不产出孤儿`() {
        // 书刚入库、还没拉到目录时，磁盘上的缓存不能因为查无此章被判要删。
        val found = findOrphanSubjects(
            cachedEntryNames = listOf(
                "00001-aaaaaaaaaaaaaaaa",
                "00002-bbbbbbbbbbbbbbbb.nb",
            ),
            legalSubjects = emptySet(),
            bookImageFolderName = "images",
        )

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `findOrphanSubjects - legacy 平铺 nb 与章节目录同一口径`() {
        val found = findOrphanSubjects(
            cachedEntryNames = listOf("00007-abcdef0123456789.nb"),
            legalSubjects = setOf("00007-abcdef0123456789"),
            bookImageFolderName = "images",
        )

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `历史命名的残留同样会被列为孤儿`() {
        // 早期版本 / 异 Locale 写入的缓存名与当前 getFileName() 规则对不上，
        // 它们在章节表里查无此章，就是孤儿，不该因为格式不认识而被跳过。
        val legacy = listOf("deadbeef.nb", "legacy-chapter-1", "00001-ABCDEF0123456789")
        assertEquals(
            legacy,
            findOrphanSubjects(
                cachedEntryNames = legacy,
                legalSubjects = setOf("00001-aaaaaaaaaaaaaaaa"),
                bookImageFolderName = "images",
            ),
        )
    }

    @Test
    fun `混合命名时合法缓存受保护而历史残留被列出`() {
        val found = findOrphanSubjects(
            cachedEntryNames = listOf(
                "00001-aaaaaaaaaaaaaaaa",
                "00003-cccccccccccccccc",
                "legacy-chapter-9",
                "deadbeef.nb",
            ),
            legalSubjects = setOf("00001-aaaaaaaaaaaaaaaa"),
            bookImageFolderName = "images",
        )
        assertEquals(
            listOf("00003-cccccccccccccccc", "legacy-chapter-9", "deadbeef.nb"),
            found,
        )
    }

    @Test
    fun `一本书的缓存全是孤儿时仍能被正常识别`() {
        val entries = listOf(
            "00007-cccccccccccccccc",
            "00008-dddddddddddddddd.nb",
        )
        assertEquals(
            entries,
            findOrphanSubjects(
                cachedEntryNames = entries,
                legalSubjects = setOf("00001-aaaaaaaaaaaaaaaa"),
                bookImageFolderName = "images",
            ),
        )
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
