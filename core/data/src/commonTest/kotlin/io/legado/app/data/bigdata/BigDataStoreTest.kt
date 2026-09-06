package io.legado.app.data.bigdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BigDataStoreTest {

    @Test
    fun 未安装时显式失败而不是静默返回空() {
        BigDataStoreProvider.uninstall()
        assertFalse(BigDataStoreProvider.isInstalled)
        val error = assertFailsWith<IllegalStateException> {
            BigDataStoreProvider.current
        }
        assertTrue(error.message!!.contains("BigDataStore 未安装"))
    }

    @Test
    fun 安装后可读回同一实现() {
        val store = InMemoryBigDataStore()
        BigDataStoreProvider.install(store)
        assertTrue(BigDataStoreProvider.isInstalled)
        assertEquals(store, BigDataStoreProvider.current)
        BigDataStoreProvider.uninstall()
        assertFalse(BigDataStoreProvider.isInstalled)
    }

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
