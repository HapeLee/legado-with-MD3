package io.legado.app.domain.gateway

import io.legado.app.domain.model.DeletedChapterCache
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

    suspend fun deleteOrphanedChapters(
        bookUrl: String,
        entryNames: Set<String>,
    ): DeletedChapterCache

    /** 扫描可以从缓存补齐字数的目标书籍。只读。 */
    suspend fun scanRecountTargets(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> Unit },
    ): List<WordCountRecountTarget>

    suspend fun recountWordCounts(bookUrls: Set<String>): RecountOutcome
}
