package io.legado.app.constant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BookType] 的 characterization test。
 *
 * 这些常量是**位掩码**：一本书可以同时具备多个类型（如「本地 + 压缩包」），
 * 且数值被写进数据库（`Book.type`），所以取值与互斥性都不能漂移。
 */
class BookTypeTest {

    @Test
    fun `each type flag is a distinct power of two`() {
        val flags = listOf(
            BookType.video,
            BookType.text,
            BookType.updateError,
            BookType.audio,
            BookType.image,
            BookType.webFile,
            BookType.local,
            BookType.archive,
            BookType.notShelf,
        )
        assertEquals(listOf(4, 8, 16, 32, 64, 128, 256, 512, 1024), flags)
        flags.forEach { flag ->
            assertTrue(flag > 0, "$flag 必须是正数")
            assertEquals(0, flag and (flag - 1), "$flag 必须是 2 的幂，否则位掩码会互相污染")
        }
    }

    @Test
    fun `composite masks are the union of their members`() {
        assertEquals(232, BookType.allBookType)
        assertEquals(488, BookType.allBookTypeLocal)

        // allBookType 覆盖可从书源转换的四种类型
        listOf(BookType.text, BookType.image, BookType.audio, BookType.webFile).forEach { flag ->
            assertTrue(flag and BookType.allBookType > 0, "$flag 应属于 allBookType")
        }
        // 不含本地/压缩包/视频/更新失败
        listOf(BookType.local, BookType.archive, BookType.notShelf, BookType.video).forEach { flag ->
            assertFalse(flag and BookType.allBookType > 0, "$flag 不应属于 allBookType")
        }

        // allBookTypeLocal 只比 allBookType 多一个 local
        assertEquals(BookType.allBookType or BookType.local, BookType.allBookTypeLocal)
        assertTrue(BookType.local and BookType.allBookTypeLocal > 0)
        assertFalse(BookType.archive and BookType.allBookTypeLocal > 0)
    }

    @Test
    fun `composite type can be tested and narrowed with bit operations`() {
        // 模拟 Book.setType / addType / removeType 的位运算语义
        var type = BookType.local or BookType.archive
        assertTrue(type and BookType.local > 0)
        assertTrue(type and BookType.archive > 0)
        assertFalse(type and BookType.text > 0)

        type = type or BookType.text
        assertTrue(type and BookType.text > 0)

        type = type and BookType.text.inv()
        assertFalse(type and BookType.text > 0)
        assertTrue(type and BookType.local > 0)
    }

    @Test
    fun `tag constants keep their wire format`() {
        assertEquals("loc_book", BookType.localTag)
        assertEquals("webDav::", BookType.webDavTag)
    }
}
