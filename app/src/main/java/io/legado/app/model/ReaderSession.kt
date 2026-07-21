package io.legado.app.model

import kotlinx.coroutines.flow.StateFlow

/**
 * 阅读会话面向所有者的 API。
 *
 * 首个实现 [LegacyReaderSession] 委托给全局单例 [ReadBook]：
 * - [state] 直接投影 `ReadBook.snapshot`，**不维护竞争副本**；
 * - 所有 mutation 都经由 ReadBook 的受控 mutator（`private set` + 语义化命令）；
 * - 调用方只拿到只读快照 [LegacyReaderSnapshot]，拿不到可变领域对象（Book/TextChapter）。
 *
 * 这是迁移期的桥接层：待 Track A 后续把所有权彻底从 ReadBook 收回后，可替换为真正的会话实现。
 */
interface ReaderSession {

    /** 权威会话快照流。 */
    val state: StateFlow<LegacyReaderSnapshot>

    /** 当前会话是否正指向该 URL 的书。 */
    fun isCurrentBook(bookUrl: String): Boolean

    /** 跳转到指定章节与章内位置。 */
    fun moveToChapter(index: Int, position: Int = 0)

    /** 下一章。 */
    fun nextChapter()

    /** 上一章。 */
    fun previousChapter()

    /** 更新当前章节内的阅读位置。 */
    fun updateViewport(position: Int)
}

/**
 * 遗留桥接实现：把 [ReaderSession] 全部委托给全局单例 [ReadBook]。
 * 命令一一映射到 ReadBook 既有的受控 mutator，不引入并行状态。
 */
class LegacyReaderSession : ReaderSession {

    override val state: StateFlow<LegacyReaderSnapshot>
        get() = ReadBook.snapshot

    override fun isCurrentBook(bookUrl: String): Boolean = ReadBook.isCurrentBook(bookUrl)

    override fun moveToChapter(index: Int, position: Int) {
        ReadBook.openChapter(index, position)
    }

    override fun nextChapter() {
        ReadBook.moveToNextChapter(upContent = true)
    }

    override fun previousChapter() {
        ReadBook.moveToPrevChapter(upContent = true)
    }

    override fun updateViewport(position: Int) {
        ReadBook.updateReadingPosition(position)
    }
}
