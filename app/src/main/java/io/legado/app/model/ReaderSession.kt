package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话侧一次性事件（R2.3）。
 *
 * 遗留的 [ReadBook.CallBack] 四个回调原来由 `ReadBookViewModel` 直接实现，
 * 于是 `ReadBook.callBack` 这个全局槽位指向 ViewModel。现在改由 [LegacyReaderSession]
 * 接住并转成事件流，ViewModel 只是订阅者。
 */
sealed interface ReaderSessionEvent {

    /** 会话派生状态（菜单/进度等）需要重新同步。对应 `upMenuView`。 */
    data object StateInvalidated : ReaderSessionEvent

    /** [ReadBook] 请求所有者去加载目录。对应 `loadChapterList`。 */
    data class ChapterListRequested(val book: Book) : ReaderSessionEvent

    /** 当前会话的书被换掉。对应 `notifyBookChanged`。 */
    data object BookChanged : ReaderSessionEvent

    /** 云端进度比本地新，需所有者确认。对应 `sureNewProgress`。 */
    data class NewProgressAvailable(val progress: BookProgress) : ReaderSessionEvent
}

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

    /**
     * 会话事件流。
     *
     * **订阅必须早于 [attach]**：事件用 `tryEmit` 投递，没有订阅者时会被丢弃。
     * 会话是每个所有者一份（Koin `factory`），订阅者恒为 1。
     */
    val events: SharedFlow<ReaderSessionEvent>

    /** 把本会话接到 [ReadBook] 上，成为当前的状态回调持有者。 */
    fun attach()

    /** 从 [ReadBook] 摘下本会话。 */
    fun detach()

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
 *
 * 同时实现 [ReadBook.CallBack]——`ReadBook.callBack` 的身份既是「阅读页已挂载」信号
 * （`prefetchForOpen` / `upData` 靠 `callBack != null` 判断），也在 `register` 时给
 * **上一个**持有者发 `notifyBookChanged`。因此本类必须**每个所有者一份**，
 * attach/detach 与 register/unregister 一一对应，身份语义与迁移前完全一致。
 */
class LegacyReaderSession : ReaderSession, ReadBook.CallBack {

    private val _events = MutableSharedFlow<ReaderSessionEvent>(extraBufferCapacity = 16)

    override val events: SharedFlow<ReaderSessionEvent> = _events.asSharedFlow()

    override val state: StateFlow<LegacyReaderSnapshot>
        get() = ReadBook.snapshot

    override fun attach() {
        ReadBook.register(this)
    }

    override fun detach() {
        ReadBook.unregister(this)
    }

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

    // --- ReadBook.CallBack → 事件流 ---

    override fun upMenuView() {
        _events.tryEmit(ReaderSessionEvent.StateInvalidated)
    }

    override fun loadChapterList(book: Book) {
        _events.tryEmit(ReaderSessionEvent.ChapterListRequested(book))
    }

    override fun notifyBookChanged() {
        _events.tryEmit(ReaderSessionEvent.BookChanged)
    }

    override fun sureNewProgress(progress: BookProgress) {
        _events.tryEmit(ReaderSessionEvent.NewProgressAvailable(progress))
    }
}
