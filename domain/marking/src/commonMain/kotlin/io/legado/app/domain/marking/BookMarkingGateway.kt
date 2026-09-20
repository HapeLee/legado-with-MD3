package io.legado.app.domain.marking

import kotlinx.coroutines.flow.Flow

/**
 * 用户划线/高亮笔记（book_marks）的领域端口（M4-6）。
 *
 * 这是**既有契约搬家**，不是新造端口：`io.legado.app.domain.gateway.BookMarkingGateway`
 * 早在下沉前就存在，本片只把收发类型从 Room 实体换成 [BookMarking]。按 M3/M4 模板
 * 「keep the existing contract name」，名字一字不改（本仓有 60+ 个同形态 `XxxGateway`）；
 * 落点从 `:core:data` 的 `domain/gateway` 挪到 `:domain:marking`（与 `domain/ai` 的
 * `AiXxxGateway` 同律）——包名变了，消费方改一行 import。
 *
 * ## 方法集：只留有调用方的
 *
 * - `setEnabled(id, enabled)` **零调用方**（全仓只出现在声明、实现与测试假实现里）
 *   ⇒ 随片删除；**底层 DAO 方法保留**（留给 `data:database`）。
 * - [getForChapter] 是**新增**而非搬移：`:app` 的 `ContentProcessor` 此前直连
 *   `appDb.bookMarkingDao.getForChapterSync(...)`（渲染要先取当前源的标记再合成
 *   `BookContentProcess`）。按模板「A port method may be *added* — when a caller still
 *   holds the DAO」，把它收进端口，`ContentProcessor` 从「持有 DAO」变成「持有端口」。
 *   构造注入的 DAO **不落 G4 的 `appDb|...` 模式**（只有 `appDb.` 锚定的访问才算），
 *   但 `ContentProcessor` 用的是 `appDb.` 直连 ⇒ 这一扩同时消掉一条 legacy 计数。
 *
 * ## 两条查询的语义差异（别合并）
 *
 * - [getForChapter] 按 **bookUrl（当前源）** 查：渲染只画当前源能对上正文的标记。
 * - [getByBook] / [flowByBook] 按 **书名+作者** 查：管理与保存去重，跨源全部列出。
 */
interface BookMarkingGateway {

    /** 按「书名+作者」查（含跨源全部标记），供保存去重/定位；`chapterIndex` 可空。 */
    suspend fun getByBook(
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int?
    ): List<BookMarking>

    /** 按「书名+作者」流式订阅全部章节的标记，供目录 Sheet 笔记页跨源展示。 */
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<BookMarking>>

    /**
     * 按**当前源**（`bookUrl`）查本章的标记，供渲染管线合成仅供渲染的
     * `BookContentProcess`。`chapterIndex` 为 `null` 时不限章节。
     *
     * ⚠️ 与 [getByBook] 不是同一个查询：换源后旧源的标记**不会**出现在这里，
     * 但它们仍会出现在 [flowByBook] 里（可管理、只是当前源正文对不上就不画）。
     */
    suspend fun getForChapter(bookUrl: String, chapterIndex: Int?): List<BookMarking>

    suspend fun getById(id: String): BookMarking?

    suspend fun upsert(bookMarking: BookMarking)

    suspend fun delete(id: String)
}
