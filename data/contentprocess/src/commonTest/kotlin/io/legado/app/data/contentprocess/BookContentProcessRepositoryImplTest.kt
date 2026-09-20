package io.legado.app.data.contentprocess

import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.data.entities.BookContentProcess as BookContentProcessEntity
import io.legado.app.domain.contentprocess.BookContentProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/**
 * [BookContentProcessRepositoryImpl] 的行为测试（M4-8）。
 *
 * 本片端口**没有 `Flow` 方法**（`flowForChapter` 因零调用方随片删除），所以"流内映射"那条
 * 判据不适用。这里要钉的是另外两条**方法体里有真实逻辑**的地方 —— mapper 测试碰不到：
 *  - [BookContentProcessRepositoryImpl.nextOrder]：返回 `maxOrder() **+ 1**`，DAO 只给最大值；
 *  - [BookContentProcessRepositoryImpl.delete]：走 DAO 的 `markDeleted`（**软删**）而不是物理删。
 *
 * 用假 DAO（纯 Kotlin 实现 Room 的 `@Dao` interface，不碰 Room 运行时）+ `runBlocking`。
 */
class BookContentProcessRepositoryImplTest {

    private class FakeDao : BookContentProcessDao {
        var maxOrderValue = 0
        var markedDeleted: String? = null
        var enabledCall: Pair<String, Boolean>? = null
        var lastUpserted: BookContentProcessEntity? = null
        var forChapterResult: List<BookContentProcessEntity> = emptyList()
        var forChapterArgs: Pair<String, Int?>? = null

        override suspend fun getForChapter(
            bookUrl: String,
            chapterIndex: Int?,
        ): List<BookContentProcessEntity> {
            forChapterArgs = bookUrl to chapterIndex
            return forChapterResult
        }

        override fun flowForChapter(
            bookUrl: String,
            chapterIndex: Int?,
        ): kotlinx.coroutines.flow.Flow<List<BookContentProcessEntity>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun maxOrder(bookUrl: String): Int = maxOrderValue

        override suspend fun upsert(process: BookContentProcessEntity) {
            lastUpserted = process
        }

        override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
            enabledCall = id to enabled
        }

        override suspend fun markDeleted(id: String, updatedAt: Long) {
            markedDeleted = id
        }
    }

    private fun entity(id: String) = BookContentProcessEntity(
        id = id,
        bookUrl = "book",
        chapterIndex = 0,
        kind = BookContentProcess.KIND_AI_CLEAN,
        anchorJson = "{}",
        actionJson = "{}",
    )

    @Test
    fun getForChapterPassesArgsAndMaps() = runBlocking {
        val dao = FakeDao().apply { forChapterResult = listOf(entity("p1"), entity("p2")) }
        val impl = BookContentProcessRepositoryImpl(dao)

        val actual = impl.getForChapter("book", 7)

        assertEquals("book" to 7, dao.forChapterArgs)
        assertEquals(listOf("p1", "p2"), actual.map { it.id })
        // 取一条非主键字段：若实现漏了映射，这里拿到的就是 Room 实体的字段
        assertEquals(BookContentProcess.KIND_AI_CLEAN, actual.first().kind)
    }

    /** `nextOrder` 是 `maxOrder() + 1`，不是 `maxOrder()`。 */
    @Test
    fun nextOrderIsMaxOrderPlusOne() = runBlocking {
        val dao = FakeDao().apply { maxOrderValue = 4 }
        val impl = BookContentProcessRepositoryImpl(dao)

        assertEquals(5, impl.nextOrder("book"))
    }

    @Test
    fun nextOrderOnEmptyBookStartsAtOne() = runBlocking {
        val dao = FakeDao().apply { maxOrderValue = 0 }
        val impl = BookContentProcessRepositoryImpl(dao)

        assertEquals(1, impl.nextOrder("book"))
    }

    /** `delete` 是**软删**（`markDeleted`），必须证明它没有走物理删除路径。 */
    @Test
    fun deleteMarksDeletedInsteadOfRemoving() = runBlocking {
        val dao = FakeDao()
        val impl = BookContentProcessRepositoryImpl(dao)

        impl.delete("p9")

        assertEquals("p9", dao.markedDeleted)
    }

    @Test
    fun upsertMapsDomainBackToEntity() = runBlocking {
        val dao = FakeDao()
        val impl = BookContentProcessRepositoryImpl(dao)

        impl.upsert(
            BookContentProcess(
                id = "p1",
                bookUrl = "book",
                kind = BookContentProcess.KIND_AI_REWRITE,
                anchorJson = "{}",
                actionJson = "{}",
                styleJson = """{"underlineMode":2}""",
                status = BookContentProcess.STATUS_DRAFT,
            )
        )

        assertEquals("p1", dao.lastUpserted?.id)
        assertEquals(BookContentProcess.KIND_AI_REWRITE, dao.lastUpserted?.kind)
        assertEquals(BookContentProcess.STATUS_DRAFT, dao.lastUpserted?.status)
        assertEquals("""{"underlineMode":2}""", dao.lastUpserted?.styleJson)
    }

    @Test
    fun setEnabledPassesThrough() = runBlocking {
        val dao = FakeDao()
        val impl = BookContentProcessRepositoryImpl(dao)

        impl.setEnabled("p1", false)

        assertEquals("p1" to false, dao.enabledCall)
    }
}
