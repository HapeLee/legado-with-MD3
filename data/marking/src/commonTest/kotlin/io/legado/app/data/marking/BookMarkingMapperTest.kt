package io.legado.app.data.marking

import io.legado.app.data.entities.BookMarking as BookMarkingEntity
import io.legado.app.domain.marking.BookMarking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `BookMarking` 实体 ↔ 领域模型的映射契约（M4-6）。
 *
 * ⚠️ **本片在「整对象断言成立」的一侧**：实体与领域模型都没有重写 `equals` / `hashCode`
 * （全字段判等），所以 `assertEquals(wholeObject)` 是真的在比全部字段 —— 与 M3-6
 * `TagGroupRule`（id-only 判等，整对象断言会漏字段）相反。**别向上一片看齐。**
 *
 * 即便如此仍保留 [copyWithDifferentNoteIsNotEqual]：万一后来者给领域模型补一个
 * 「按 id 判等」的 `equals`，整对象断言会静默失效，只有这条反向用例会红。
 */
class BookMarkingMapperTest {

    private fun entity(
        id: String = "m1",
        bookUrl: String = "https://example.com/book",
        bookName: String = "书名",
        bookAuthor: String = "作者",
        chapterIndex: Int? = 7,
        anchorJson: String = """{"chapterIndex":7,"chapterPosition":12,"selectedText":"原文"}""",
        styleJson: String? = """{"underlineMode":1}""",
        note: String = "我的备注",
        chapterName: String = "第七章",
        enabled: Boolean = true,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_000_123L,
    ) = BookMarkingEntity(
        id = id,
        bookUrl = bookUrl,
        bookName = bookName,
        bookAuthor = bookAuthor,
        chapterIndex = chapterIndex,
        anchorJson = anchorJson,
        styleJson = styleJson,
        note = note,
        chapterName = chapterName,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun toDomainCopiesEveryField() {
        val source = entity()
        val actual = source.toDomain()
        assertEquals(source.id, actual.id)
        assertEquals(source.bookUrl, actual.bookUrl)
        assertEquals(source.bookName, actual.bookName)
        assertEquals(source.bookAuthor, actual.bookAuthor)
        assertEquals(source.chapterIndex, actual.chapterIndex)
        assertEquals(source.anchorJson, actual.anchorJson)
        assertEquals(source.styleJson, actual.styleJson)
        assertEquals(source.note, actual.note)
        assertEquals(source.chapterName, actual.chapterName)
        assertEquals(source.enabled, actual.enabled)
        assertEquals(source.createdAt, actual.createdAt)
        assertEquals(source.updatedAt, actual.updatedAt)
    }

    @Test
    fun toEntityCopiesEveryField() {
        val source = entity().toDomain()
        val actual = source.toEntity()
        assertEquals(source.id, actual.id)
        assertEquals(source.bookUrl, actual.bookUrl)
        assertEquals(source.bookName, actual.bookName)
        assertEquals(source.bookAuthor, actual.bookAuthor)
        assertEquals(source.chapterIndex, actual.chapterIndex)
        assertEquals(source.anchorJson, actual.anchorJson)
        assertEquals(source.styleJson, actual.styleJson)
        assertEquals(source.note, actual.note)
        assertEquals(source.chapterName, actual.chapterName)
        assertEquals(source.enabled, actual.enabled)
        assertEquals(source.createdAt, actual.createdAt)
        assertEquals(source.updatedAt, actual.updatedAt)
    }

    /**
     * 两个可空字段（`chapterIndex` / `styleJson`）的 `null` 必须原样搬运：
     * `styleJson = null` 表示「无样式」，归一成空串会让渲染桥把「无样式」当成「空样式」去解析。
     */
    @Test
    fun nullablesPassThroughUnchanged() {
        val source = entity(chapterIndex = null, styleJson = null)
        val domain = source.toDomain()
        assertNull(domain.chapterIndex)
        assertNull(domain.styleJson)
        val back = domain.toEntity()
        assertNull(back.chapterIndex)
        assertNull(back.styleJson)
    }

    /** 空串是 bookName / bookAuthor 的默认值也是合法值（跨源关联键），映射不做 trim。 */
    @Test
    fun blankAssociationKeysAreNotNormalized() {
        val source = entity(bookName = "  ", bookAuthor = "")
        val domain = source.toDomain()
        assertEquals("  ", domain.bookName)
        assertEquals("", domain.bookAuthor)
        assertEquals("  ", domain.toEntity().bookName)
    }

    /** `anchorJson` 是不透明串：首尾空白与不可规范化形状都要原样过去（M4-4 的教训）。 */
    @Test
    fun anchorJsonIsCopiedVerbatim() {
        val raw = "  not-a-json-at-all { unbalanced  "
        val domain = entity(anchorJson = raw).toDomain()
        assertEquals(raw, domain.anchorJson)
        assertEquals(raw, domain.toEntity().anchorJson)
    }

    @Test
    fun roundTripIsLossless() {
        val source = entity()
        assertEquals(source, source.toDomain().toEntity())
    }

    /** 默认时间戳由 `systemTimeMillis()` 生成：只断言它是个正数，不钉固定值（它本来就是当前时间）。 */
    @Test
    fun defaultTimestampsArePopulatedByPlatformClock() {
        val domain = BookMarking(
            id = "m2",
            bookUrl = "https://example.com/book",
            anchorJson = "{}",
        )
        assertTrue(domain.createdAt > 0)
        assertTrue(domain.updatedAt > 0)
        assertEquals("", domain.bookName)
        assertEquals("", domain.bookAuthor)
        assertNull(domain.chapterIndex)
        assertNull(domain.styleJson)
        assertEquals("", domain.note)
        assertEquals("", domain.chapterName)
        assertTrue(domain.enabled)
    }

    /**
     * 反向用例：全字段判等必须对一个「非主键」字段敏感。
     * 若后来者把判等改成 id-only，这条会红（而所有正向用例仍绿）。
     */
    @Test
    fun copyWithDifferentNoteIsNotEqual() {
        val a = entity().toDomain()
        val b = entity(note = "另一条备注").toDomain()
        assertNotEquals(a, b)
        assertFalse(a == b)
    }

    /** `toDomainList` 保持顺序与数量（目录 Sheet 按 DAO 的 `order by` 展示，不能重排）。 */
    @Test
    fun toDomainListPreservesOrderAndSize() {
        val list = listOf(
            entity(id = "c"),
            entity(id = "a"),
            entity(id = "b"),
        )
        val actual = list.toDomainList()
        assertEquals(3, actual.size)
        assertEquals(listOf("c", "a", "b"), actual.map { it.id })
    }
}
