package io.legado.app.data.marking

import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.data.entities.BookMarking as BookMarkingEntity
import io.legado.app.domain.marking.BookMarking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * [BookMarkingRepositoryImpl] 的行为测试（M4-6）。
 *
 * 为什么不能只靠 `BookMarkingMapperTest`：本域的端口有一个 **`Flow` 方法**（`flowByBook`），
 * 映射发生在**流内、每次发射都跑**。把 `.map { it.toDomainList() }` 换成
 * `.map { it as List<BookMarking> }` **能编译**（只是一条 unchecked cast 告警），
 * 而且全部 mapper 用例照样绿 —— 真机每次发射才 `ClassCastException`（M4-3 的判据）。
 * 所以这里用假 DAO 驱动**多次发射**并全部收集。
 *
 * 其余用例钉住参数透传与 `getById` 的空值路径：这些分支 mapper 测试一个都碰不到。
 */
class BookMarkingRepositoryImplTest {

    /** 手写假 DAO：纯 Kotlin 实现 Room 的 `@Dao` interface，不碰 Room 运行时。 */
    private class FakeDao : BookMarkingDao {
        var getByBookArgs: List<Any?> = emptyList()
        var getForChapterArgs: List<Any?> = emptyList()
        var lastUpserted: BookMarkingEntity? = null
        var deletedIds = mutableListOf<String>()
        var byBookResult: List<BookMarkingEntity> = emptyList()
        var forChapterResult: List<BookMarkingEntity> = emptyList()
        var byIdResult: BookMarkingEntity? = null
        var emissions: List<List<BookMarkingEntity>> = emptyList()

        override suspend fun getForChapterSync(
            bookUrl: String,
            chapterIndex: Int?,
        ): List<BookMarkingEntity> {
            getForChapterArgs = listOf(bookUrl, chapterIndex)
            return forChapterResult
        }

        override suspend fun getByBook(
            bookName: String,
            bookAuthor: String,
            chapterIndex: Int?,
        ): List<BookMarkingEntity> {
            getByBookArgs = listOf(bookName, bookAuthor, chapterIndex)
            return byBookResult
        }

        override fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookMarkingEntity>> {
            getByBookArgs = listOf(bookName, bookAuthor)
            return flow { emissions.forEach { emit(it) } }
        }

        override suspend fun getById(id: String): BookMarkingEntity? {
            return if (byIdResult?.id == id) byIdResult else null
        }

        override suspend fun upsert(bookMarking: BookMarkingEntity) {
            lastUpserted = bookMarking
        }

        override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) = Unit

        override suspend fun delete(id: String) {
            deletedIds.add(id)
        }
    }

    private fun entity(id: String, note: String = "") = BookMarkingEntity(
        id = id,
        bookUrl = "https://example.com/book",
        bookName = "书名",
        bookAuthor = "作者",
        chapterIndex = 3,
        anchorJson = """{"chapterIndex":3}""",
        styleJson = null,
        note = note,
        chapterName = "第三章",
        enabled = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    /**
     * 关键用例：流内映射必须**每次发射都跑**，不能只跑第一次、更不能退化成 cast。
     * 三条发射给三个不同的 id，任一条没映射就会 `ClassCastException` 或 id 不对。
     */
    @Test
    fun flowByBookMapsEveryEmission() = runBlocking {
        val dao = FakeDao()
        dao.emissions = listOf(
            listOf(entity("e1")),
            listOf(entity("e1"), entity("e2")),
            emptyList(),
        )
        val impl = BookMarkingRepositoryImpl(dao)

        val collected = impl.flowByBook("书名", "作者").toList()

        assertEquals(3, collected.size)
        assertEquals(listOf("e1"), collected[0].map { it.id })
        assertEquals(listOf("e1", "e2"), collected[1].map { it.id })
        assertTrue(collected[2].isEmpty())
        // 取一条**非主键**字段：若流内没做映射（退化成 unchecked cast），这里访问的就是
        // Room 实体的字段 ⇒ `ClassCastException`，而不是「断言失败」这种温和的结果。
        assertEquals("第三章", collected[1].last().chapterName)
    }

    @Test
    fun getForChapterPassesArgsAndMaps() = runBlocking {
        val dao = FakeDao()
        dao.forChapterResult = listOf(entity("f1", note = "n1"))
        val impl = BookMarkingRepositoryImpl(dao)

        val actual = impl.getForChapter("https://example.com/book", 9)

        assertEquals(listOf("https://example.com/book", 9), dao.getForChapterArgs)
        assertEquals(1, actual.size)
        assertEquals("f1", actual[0].id)
        assertEquals("n1", actual[0].note)
    }

    @Test
    fun getByBookPassesArgsAndMaps() = runBlocking {
        val dao = FakeDao()
        dao.byBookResult = listOf(entity("b1"), entity("b2"))
        val impl = BookMarkingRepositoryImpl(dao)

        val actual = impl.getByBook("书名", "作者", null)

        assertEquals(listOf("书名", "作者", null), dao.getByBookArgs)
        assertEquals(listOf("b1", "b2"), actual.map { it.id })
    }

    @Test
    fun getByIdMapsHitAndReturnsNullOnMiss() = runBlocking {
        val dao = FakeDao()
        dao.byIdResult = entity("hit")
        val impl = BookMarkingRepositoryImpl(dao)

        assertEquals("hit", impl.getById("hit")?.id)
        assertNull(impl.getById("miss"))
    }

    @Test
    fun upsertMapsDomainBackToEntity() = runBlocking {
        val dao = FakeDao()
        val impl = BookMarkingRepositoryImpl(dao)

        impl.upsert(
            BookMarking(
                id = "u1",
                bookUrl = "https://example.com/book",
                chapterIndex = 5,
                anchorJson = "{}",
                styleJson = """{"underlineMode":2}""",
                note = "备注",
            )
        )

        val saved = dao.lastUpserted
        assertEquals("u1", saved?.id)
        assertEquals("""{"underlineMode":2}""", saved?.styleJson)
        assertEquals(5, saved?.chapterIndex)
        assertEquals("备注", saved?.note)
    }

    @Test
    fun deletePassesIdThrough() = runBlocking {
        val dao = FakeDao()
        val impl = BookMarkingRepositoryImpl(dao)

        impl.delete("d1")

        assertEquals(listOf("d1"), dao.deletedIds)
    }
}
