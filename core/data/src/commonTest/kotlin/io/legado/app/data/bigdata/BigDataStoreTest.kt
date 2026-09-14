package io.legado.app.data.bigdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [InMemoryBigDataStore] 的语义测试：读写删、按实体标识隔离、空 key 行为。
 *
 * 生产实现 [RuleDataFileStore] 走真文件系统（需要 `core:platform` 的 actual），
 * 其验证在 `desktopTest` 的 `RuleDataFileStoreDesktopTest` —— 那里额外覆盖
 * 「路径布局与迁移前逐字节一致」，那是既有用户数据能否读回的关键。
 */
class BigDataStoreTest {

    @Test
    fun 书籍变量支持写入读回删除() {
        val store = InMemoryBigDataStore()
        assertNull(store.getBookVariable("https://a/book", "k"))
        assertFalse(store.hasBookVariable("https://a/book", "k"))

        store.putBookVariable("https://a/book", "k", "长文本")
        assertEquals("长文本", store.getBookVariable("https://a/book", "k"))
        assertTrue(store.hasBookVariable("https://a/book", "k"))

        store.putBookVariable("https://a/book", "k", null)
        assertNull(store.getBookVariable("https://a/book", "k"))
        assertFalse(store.hasBookVariable("https://a/book", "k"))
    }

    @Test
    fun 变量按实体标识隔离不串号() {
        val store = InMemoryBigDataStore()
        store.putBookVariable("bookA", "k", "A 的值")
        store.putBookVariable("bookB", "k", "B 的值")
        assertEquals("A 的值", store.getBookVariable("bookA", "k"))
        assertEquals("B 的值", store.getBookVariable("bookB", "k"))

        // 章节级与书籍级同 key 互不影响
        store.putChapterVariable("bookA", "chapter1", "k", "章节值")
        assertEquals("章节值", store.getChapterVariable("bookA", "chapter1", "k"))
        assertEquals("A 的值", store.getBookVariable("bookA", "k"))

        // RSS 与书籍同名 key 互不影响
        store.putRssVariable("bookA", "link1", "k", "RSS 值")
        assertEquals("RSS 值", store.getRssVariable("bookA", "link1", "k"))
        assertEquals("A 的值", store.getBookVariable("bookA", "k"))
    }

    @Test
    fun 书籍变量空key返回空且不抛异常() {
        val store = InMemoryBigDataStore()
        store.putBookVariable("bookA", "k", "值")
        assertNull(store.getBookVariable("bookA", null))
    }
}
