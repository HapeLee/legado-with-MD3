package io.legado.app.data.bigdata

import io.legado.app.core.platform.RuleDataStorage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RuleDataFileStore] 的真文件系统验证（desktop target）。
 *
 * 两个重点：
 * 1. **路径布局与迁移前逐字节一致**。迁移前是 `:app` 的 `help.RuleBigDataHelp`，
 *    既有用户 `<externalFiles>/ruleData` 下的数据必须能被直接读回，否则等于静默丢数据。
 *    因此这里用**已知 MD5 向量**（`md5("abc") = 900150983cd24fb0d6963f7d28e17f72`）
 *    硬编码期望路径，而不是拿被测函数自己的 md5 去拼期望值——后者会自证。
 * 2. 清理原语（`listBookOwners` 等）能被 host 的清理任务正确使用。
 */
class RuleDataFileStoreDesktopTest {

    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = File.createTempFile("legado-ruledata", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        RuleDataStorage.rootDir = root.absolutePath
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `md5 是标准小写十六进制`() {
        // 标准 MD5 已知向量；小写十六进制，UTF-8 编码，无盐。
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RuleDataStorage.md5("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", RuleDataStorage.md5(""))
    }

    @Test
    fun `书籍变量落在与迁移前一致的路径上`() {
        RuleDataFileStore.putBookVariable("abc", "abc", "长文本")

        // 期望路径全部硬编码（<root>/book/<md5(bookUrl)>/<md5(key)>.txt）
        val valueFile = File(root, "book/900150983cd24fb0d6963f7d28e17f72/900150983cd24fb0d6963f7d28e17f72.txt")
        assertTrue(valueFile.isFile, "期望文件存在：${valueFile.path}")
        assertEquals("长文本", valueFile.readText())

        // 标记文件保存原始标识，供清理任务反查，且不参与 MD5
        val marker = File(root, "book/900150983cd24fb0d6963f7d28e17f72/bookUrl.txt")
        assertTrue(marker.isFile)
        assertEquals("abc", marker.readText())

        assertEquals("长文本", RuleDataFileStore.getBookVariable("abc", "abc"))
        assertTrue(RuleDataFileStore.hasBookVariable("abc", "abc"))
    }

    @Test
    fun `章节变量落在书籍分区下的二级 MD5 目录`() {
        RuleDataFileStore.putChapterVariable("abc", "abc", "abc", "章节值")

        val valueFile = File(
            root,
            "book/900150983cd24fb0d6963f7d28e17f72/900150983cd24fb0d6963f7d28e17f72" +
                "/900150983cd24fb0d6963f7d28e17f72.txt",
        )
        assertTrue(valueFile.isFile, "期望文件存在：${valueFile.path}")
        assertEquals("章节值", valueFile.readText())
        assertEquals("章节值", RuleDataFileStore.getChapterVariable("abc", "abc", "abc"))
        assertNull(RuleDataFileStore.getChapterVariable("abc", "abc", "missing"))
    }

    @Test
    fun `RSS 变量落在独立分区并写两级标记`() {
        RuleDataFileStore.putRssVariable("abc", "abc", "abc", "RSS 值")

        val valueFile = File(
            root,
            "rss/900150983cd24fb0d6963f7d28e17f72/900150983cd24fb0d6963f7d28e17f72" +
                "/900150983cd24fb0d6963f7d28e17f72.txt",
        )
        assertTrue(valueFile.isFile, "期望文件存在：${valueFile.path}")
        assertEquals("RSS 值", valueFile.readText())

        assertEquals("abc", File(root, "rss/900150983cd24fb0d6963f7d28e17f72/origin.txt").readText())
        assertEquals(
            "abc",
            File(root, "rss/900150983cd24fb0d6963f7d28e17f72/900150983cd24fb0d6963f7d28e17f72/origin.txt").readText(),
        )
        assertEquals("RSS 值", RuleDataFileStore.getRssVariable("abc", "abc", "abc"))
    }

    @Test
    fun `写null表示删除且三种分区互不串号`() {
        RuleDataFileStore.putBookVariable("abc", "abc", "书")
        RuleDataFileStore.putChapterVariable("abc", "abc", "abc", "章")
        RuleDataFileStore.putRssVariable("abc", "abc", "abc", "RSS")

        assertEquals("书", RuleDataFileStore.getBookVariable("abc", "abc"))
        assertEquals("章", RuleDataFileStore.getChapterVariable("abc", "abc", "abc"))
        assertEquals("RSS", RuleDataFileStore.getRssVariable("abc", "abc", "abc"))

        RuleDataFileStore.putBookVariable("abc", "abc", null)
        assertNull(RuleDataFileStore.getBookVariable("abc", "abc"))
        assertFalse(RuleDataFileStore.hasBookVariable("abc", "abc"))
        // 删书籍级不影响章节级与 RSS
        assertEquals("章", RuleDataFileStore.getChapterVariable("abc", "abc", "abc"))
        assertEquals("RSS", RuleDataFileStore.getRssVariable("abc", "abc", "abc"))
    }

    @Test
    fun `清理原语列出条目并可按条目删除`() {
        RuleDataFileStore.putBookVariable("abc", "k", "值")
        RuleDataFileStore.putBookVariable("https://b/book", "k", "值")
        // 迁移前的清理逻辑会删掉这类遗留散落文件
        File(root, "book/loose.txt").writeText("x")

        val owners = RuleDataFileStore.listBookOwners()
        assertEquals(3, owners.size)

        val loose = owners.single { !it.isDirectory }
        assertEquals("loose.txt", loose.name)
        assertNull(loose.id)

        val bookOwners = owners.filter { it.isDirectory }
        assertEquals(2, bookOwners.size)
        assertEquals(setOf("abc", "https://b/book"), bookOwners.mapNotNull { it.id }.toSet())

        RuleDataFileStore.deleteBookEntry(loose.name)
        assertEquals(2, RuleDataFileStore.listBookOwners().size)

        RuleDataFileStore.deleteBookEntry(bookOwners.single { it.id == "abc" }.name)
        assertEquals(1, RuleDataFileStore.listBookOwners().size)
    }
}
