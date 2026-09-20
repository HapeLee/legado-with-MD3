package io.legado.app.domain.contentprocess

/**
 * 正文处理项的领域端口（M4-8）。
 *
 * **既有契约搬家**，不是新造：原 `io.legado.app.domain.gateway.BookContentProcessGateway`
 * 早就在 `:core:data` 里，本片只把收发类型从 Room 实体换成 [BookContentProcess]
 * （名字一字不改，理由同 M4-6/M4-7）。
 *
 * ## 方法集：删掉零外部调用方的 `flowForChapter`
 *
 * 原端口有一个 `fun flowForChapter(...): Flow<List<BookContentProcess>>`，全仓**零调用方**
 * （唯一出现是它自己的实现）⇒ 随片删除。**DAO 方法保留**（`BookContentProcessDao` 的
 * `flowForChapter` 与 `@Query` 留给 `data:database`）。
 *
 * 副作用是本模块不再需要 `kotlinx-coroutines-core`（剩下的方法全是 `suspend`）。
 * 删掉这个依赖是**结果**而不是目的 —— 不要为了"保持对称"再把它加回来。
 *
 * ## 两个方法名与 DAO 不同，别当成笔误
 *
 * [delete] 的实现调 DAO 的 `markDeleted`（软删：置 `status = STATUS_DELETED`），
 * [nextOrder] 的实现调 DAO 的 `maxOrder()` **再 `+ 1`**（这两个是真实逻辑，
 * `BookContentProcessRepositoryImplTest` 各有一条用例）。
 */
interface BookContentProcessGateway {

    /** 取本章生效的处理项（DAO 侧已排除 `STATUS_DELETED`，按 `sortOrder, createdAt` 排序）。 */
    suspend fun getForChapter(bookUrl: String, chapterIndex: Int?): List<BookContentProcess>

    /** 下一个排序号 = 该书当前最大 `sortOrder` **+ 1**。 */
    suspend fun nextOrder(bookUrl: String): Int

    suspend fun upsert(process: BookContentProcess)

    suspend fun setEnabled(id: String, enabled: Boolean)

    /** **软删**：置 `status = STATUS_DELETED` 并刷新 `updatedAt`，不物理删除。 */
    suspend fun delete(id: String)
}
