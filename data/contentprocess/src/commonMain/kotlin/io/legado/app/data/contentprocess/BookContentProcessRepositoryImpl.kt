package io.legado.app.data.contentprocess

import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.domain.contentprocess.BookContentProcess
import io.legado.app.domain.contentprocess.BookContentProcessGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BookContentProcessGateway] 的实现（M4-8）：逐字照抄迁移前 `core:data` 的
 * `io.legado.app.data.repository.BookContentProcessRepository`，只改两处：
 *
 *  1. **每个方法都包 `withContext(Dispatchers.IO)`** —— 迁移前就是如此（与 M4-1 / M4-2 /
 *     M4-4 / M4-6 同侧，与「裸调 DAO」的 M4-3 / M4-7 相反）。照抄而不是向上一片看齐：
 *     多一跳或少一跳都不会让测试变红，只会悄悄改线程。
 *  2. 三个方法体内有**真实逻辑**，不是纯委派（`BookContentProcessRepositoryImplTest` 各一条：
 *     [nextOrder] 的 `maxOrder() + 1`、[delete] 走 DAO 的 `markDeleted` 软删、
 *     [getForChapter] 的 DAO 结果逐条映射）。
 *
 * ⚠️ 端口的 `flowForChapter` 已随片删除（零调用方）；**DAO 的 `flowForChapter` 保留**。
 */
class BookContentProcessRepositoryImpl(
    private val dao: BookContentProcessDao,
) : BookContentProcessGateway {

    override suspend fun getForChapter(
        bookUrl: String,
        chapterIndex: Int?,
    ): List<BookContentProcess> = withContext(Dispatchers.IO) {
        dao.getForChapter(bookUrl, chapterIndex).toDomainList()
    }

    override suspend fun nextOrder(bookUrl: String): Int = withContext(Dispatchers.IO) {
        dao.maxOrder(bookUrl) + 1
    }

    override suspend fun upsert(process: BookContentProcess) = withContext(Dispatchers.IO) {
        dao.upsert(process.toEntity())
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    /** 软删：DAO 侧置 `status = STATUS_DELETED`，不物理删除。 */
    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.markDeleted(id)
    }
}
