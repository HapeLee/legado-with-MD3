package io.legado.app.data.marking

import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.domain.marking.BookMarking
import io.legado.app.domain.marking.BookMarkingGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [BookMarkingGateway] 的实现（M4-6）：逐字照抄迁移前 `core:data` 的
 * `io.legado.app.data.repository.BookMarkingRepository`，只改三处：
 *
 *  1. **每个方法都包 `withContext(Dispatchers.IO)`** —— 迁移前就是如此（与 M4-1 / M4-2 /
 *     M4-4 同侧，与 M4-3「迁前就是裸调 DAO」相反）。照抄而不是向上一片看齐：改变调度
 *     行为不会让任何测试变红，只会悄悄移动线程。
 *  2. [flowByBook] **在流内映射**（`dao.flowByBook(...).map { it.toDomainList() }`），
 *     不是先 `first()` 再映射：映射必须**每次发射都跑**。mapper 测试碰不到这条路径
 *     ⇒ 由 `BookMarkingRepositoryImplTest` 驱动多次发射来钉住（M4-3 立的判据）。
 *     `flowOn(Dispatchers.IO)` 的位置也照抄：它在 `map` **之后**，所以查询在 IO 上、
 *     映射在收集者的调度器上——迁移前就是这样。
 *  3. [getForChapter] 是新增的（原本只有 `:app` 直连 DAO），语义见端口的 KDoc。
 *
 * ⚠️ `setEnabled` 随片删除（零调用方），但 **DAO 方法保留**。
 */
class BookMarkingRepositoryImpl(
    private val dao: BookMarkingDao,
) : BookMarkingGateway {

    override suspend fun getByBook(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int?,
    ): List<BookMarking> = withContext(Dispatchers.IO) {
        dao.getByBook(bookName, bookAuthor, chapterIndex).toDomainList()
    }

    override fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookMarking>> =
        dao.flowByBook(bookName, bookAuthor)
            .map { it.toDomainList() }
            .flowOn(Dispatchers.IO)

    override suspend fun getForChapter(
        bookUrl: String,
        chapterIndex: Int?,
    ): List<BookMarking> = withContext(Dispatchers.IO) {
        dao.getForChapterSync(bookUrl, chapterIndex).toDomainList()
    }

    override suspend fun getById(id: String): BookMarking? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun upsert(bookMarking: BookMarking) = withContext(Dispatchers.IO) {
        dao.upsert(bookMarking.toEntity())
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
