package io.legado.app.model.localBook

/**
 * 批量导入的分块执行骨架（[LocalBook.importFiles] 使用，抽出为纯 JVM 可测函数）。
 *
 * 块事务内不吞异常：Android SQLite 的嵌套事务失败会把外层标为失败，若在块内逐本
 * runCatching，异常被吞掉后外层 endTransaction 可能已静默回滚整块，而调用方只见到
 * 「个别失败」的错误计数——计数与最终落库不一致。因此任一本抛错即让异常越过块事务
 * 边界（整块回滚有保证），该块退回逐本无事务路径重放：单本失败按本隔离计入错误数，
 * 其余本正常落库，错误计数与落库结果严格一致。
 */
internal fun <T> importInChunks(
    items: List<T>,
    chunkSize: Int,
    importOne: (T) -> Unit,
    runChunkInTransaction: (() -> Unit) -> Unit,
    onImportError: (item: T, error: Throwable) -> Unit,
) {
    items.chunked(chunkSize).forEach { chunk ->
        val chunkFailed = kotlin.runCatching {
            runChunkInTransaction { chunk.forEach(importOne) }
        }.isFailure
        if (chunkFailed) {
            chunk.forEach { item ->
                kotlin.runCatching { importOne(item) }.onFailure { onImportError(item, it) }
            }
        }
    }
}
