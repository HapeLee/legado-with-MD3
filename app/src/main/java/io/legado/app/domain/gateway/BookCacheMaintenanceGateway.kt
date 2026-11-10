package io.legado.app.domain.gateway

import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.DeletionProgress
import io.legado.app.domain.model.OrphanDeleteRequest
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget

interface BookCacheMaintenanceGateway {

    /**
     * 扫描孤立章节缓存。只读，不做任何删除。
     * 跳过正在下载或正在阅读的书，因为这些书的缓存目录可能被并发写入。
     */
    suspend fun scanOrphanedChapters(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> Unit },
    ): List<OrphanedChapterGroup>

    /**
     * 批量删除指定的孤立缓存条目。
     *
     * 执行前会按**当前**章节列表重新判定一次，只删此刻仍然是孤儿的条目；
     * 扫描与执行之间可能隔很久，目录随时可能变，而删除不可逆。
     * 批量接口让章节表只查一次，而不是逐本重复查。
     * 正在下载/阅读的书整本跳过，体现在 [DeletedChapterCache.skippedBooks] 上。
     *
     * [onProgress] 每处理完一本书回报一次累计结果，取消时调用方仍握有已提交的部分。
     */
    suspend fun deleteOrphanedChapters(
        requests: List<OrphanDeleteRequest>,
        onProgress: (DeletionProgress) -> Unit = {},
    ): DeletedChapterCache

    /** 扫描可以从缓存补齐字数的目标书籍。只读。 */
    suspend fun scanRecountTargets(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> Unit },
    ): List<WordCountRecountTarget>

    suspend fun recountWordCounts(
        bookUrls: Set<String>,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> Unit },
    ): RecountOutcome
}
