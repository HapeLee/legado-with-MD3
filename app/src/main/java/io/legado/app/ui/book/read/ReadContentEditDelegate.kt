package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.feature.reader.core.navigation.ReaderPageContext
import io.legado.app.feature.reader.core.source.ReaderBodyOnlyFilter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 阅读页正文编辑域：打开编辑弹层、载入当前章正文与章名、保存、还原。
 *
 * 自持 [ContentEditUiState]；章节读取走 [Host]（理由同 [ReadAiDelegate]——
 * 不让 DAO 直连从 `legacyDaoInjectionBaseline` 洗进宽松的 `legacyUiDaoAccessBaseline`）。
 *
 * `execute {}` 是 `BaseViewModel` 的成员，这里换成它的实现体
 * `Coroutine.async(scope, Dispatchers.IO)`，默认参数一致，语义不变。
 *
 * 正文有两条文本线：
 *
 * - [rawText]：ContentProcessor 处理后的完整正文（含 `<img>`、`<usehtml>`、`[newpage]`），
 *   写回缓存的就是这一份。
 * - [ContentEditUiState.text]：编辑器里显示的那一份。「仅显示正文」打开时由
 *   [ReaderBodyOnlyFilter] 把非正文片段折叠成占位标记，保存时再按标记原样插回，
 *   因此折叠与否都不会改动图片标签、富文本源码、分页标记和其它字符。
 */
class ReadContentEditDelegate(
    private val scope: CoroutineScope,
    private val host: Host,
    private val readSettingsRepository: ReadSettingsRepository,
) {

    interface Host {
        val currentCanvasPage: ReaderPageContext?

        fun setActiveSheet(sheet: ReadBookSheet?)

        suspend fun findChapter(bookUrl: String, chapterIndex: Int): BookChapter?
    }

    private val _uiState = MutableStateFlow(ContentEditUiState())
    val uiState = _uiState.asStateFlow()

    private var pendingCursorOffset: Int? = null
    private var pendingAnchor: String? = null

    /** 完整正文（写回缓存的那一份）。 */
    private var rawText: String = ""

    /** [rawText] 里的非正文片段，按出现顺序；保存时按标记插回。 */
    private var hiddenSpans: List<ReaderBodyOnlyFilter.HiddenSpan> = emptyList()

    /** [ReadBook.durChapterPos] 一类原文坐标的光标位置；折叠后按标记长度换算。 */
    private var rawCursorOffset: Int = 0

    fun open() {
        pendingCursorOffset = currentOffset()
        pendingAnchor = currentAnchor()
        host.setActiveSheet(ReadBookSheet.ContentEdit)
    }

    /**
     * 关闭弹层时清空正文缓冲，避免下次开弹层闪上一章内容。
     *
     * 保存路径不受这里影响：[save] 在调用线程就把折叠文本换算成了完整正文，
     * 协程里不再读这些字段。
     */
    fun onSheetDismissed() {
        rawText = ""
        hiddenSpans = emptyList()
        rawCursorOffset = 0
        _uiState.update {
            it.copy(
                text = "",
                chapterTitle = "",
                cursorOffset = 0,
                loading = false,
                saveToSource = false,
            )
        }
    }

    /**
     * 编辑器正文变化；[text] 是当前显示文本（「仅显示正文」打开时已折叠非正文）。
     */
    fun setText(text: String) {
        rawText = toRawText(text)
        _uiState.update { it.copy(text = text) }
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(chapterTitle = title) }
    }

    /**
     * 切换「仅显示正文」。打开时按当前 [rawText] 重新扫描非正文片段
     * （用户可能在未折叠状态下手写过 `<img>`），保证显示与插回用的是同一套片段。
     */
    fun setBodyOnly(enabled: Boolean) {
        if (enabled == _uiState.value.bodyOnly) return
        if (enabled) {
            hiddenSpans = scanNonBody(rawText)
        }
        _uiState.update { it.copy(bodyOnly = enabled) }
        publishRaw()
    }

    fun setSaveToSource(value: Boolean) {
        _uiState.update { it.copy(saveToSource = value) }
    }

    fun load() {
        _uiState.update { it.copy(loading = true, text = "") }
        Coroutine.async(scope, Dispatchers.IO) {
            val book = ReadBook.book ?: return@async
            val chapter = host.findChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val rawContent = BookHelp.getContent(book, chapter) ?: return@async
            val text = contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
                .toString()
            _uiState.update {
                it.copy(
                    chapterTitle = chapter.title,
                    isLocalTxt = book.isLocalTxt,
                )
            }
            adopt(text)
        }.onFinally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun save(content: String, saveToSource: Boolean, chapterTitle: String) {
        // 「折叠显示 → 完整正文」的换算必须在**调用线程当场**做完：点保存后弹层立刻 dismiss，
        // [onSheetDismissed] 会同步清空 rawText/hiddenSpans，而 `Coroutine.async` 的 body 是
        // 派发到 IO 才跑的。放到协程里换算就会读到空片段表，把带 `〔图片1〕` 的显示文本
        // 直接写回缓存（图片被占位符顶掉）。
        val text = toRawText(content)
        Coroutine.async(scope, Dispatchers.IO) {
            val book = ReadBook.book ?: return@async
            val chapter = host.findChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            val title = chapterTitle.trim()
            if (title.isNotEmpty() && title != chapter.title) {
                // 章名先写：本地 TXT 的写回（saveText → saveToLocalTxt）用 chapter.title 当段首标题
                BookHelp.saveChapterTitle(chapter, title)
            }
            BookHelp.saveText(book, chapter, text, saveToSource)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    fun reset() {
        _uiState.update { it.copy(loading = true) }
        Coroutine.async(scope, Dispatchers.IO) {
            val book = ReadBook.book ?: return@async
            val chapter = host.findChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.delContent(book, chapter)
            if (!book.isLocal) {
                ReadBook.bookSource?.let { bookSource ->
                    WebBook.getContentAwait(bookSource, book, chapter)
                }
            }
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val rawContent = BookHelp.getContent(book, chapter)
            val text = if (rawContent != null) {
                contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
                    .toString()
            } else {
                ""
            }
            adopt(text)
            _uiState.update { it.copy(loading = false) }
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }.onError {
            _uiState.update { it.copy(loading = false) }
        }
    }

    /**
     * 采纳一份新正文：重扫非正文片段、定位光标，再按当前开关发布显示文本。
     * 光标坐标来自 [ReaderPageContext]，与 [rawText] 同一坐标系，折叠后再换算。
     */
    private fun adopt(text: String) {
        rawText = text
        hiddenSpans = scanNonBody(text)
        rawCursorOffset = resolveCursorOffset(text)
        publishRaw()
    }

    /** 非正文判据与渲染器同源：`adaptSpecialStyle` 关闭时 `[newpage]`/`<usehtml>` 是可见文字。 */
    private fun scanNonBody(text: String): List<ReaderBodyOnlyFilter.HiddenSpan> =
        ReaderBodyOnlyFilter.scan(text, readSettingsRepository.currentSettings.adaptSpecialStyle)

    private fun publishRaw() {
        val bodyOnly = _uiState.value.bodyOnly
        val text = if (bodyOnly) {
            ReaderBodyOnlyFilter.toBodyOnly(rawText, hiddenSpans)
        } else {
            rawText
        }
        val cursorOffset = if (bodyOnly) {
            ReaderBodyOnlyFilter.toBodyOnlyOffset(rawCursorOffset, hiddenSpans)
        } else {
            rawCursorOffset
        }
        _uiState.update { it.copy(text = text, cursorOffset = cursorOffset) }
    }

    /**
     * 编辑器文本 → 完整正文。折叠态下按占位标记把非正文片段原样插回；若文本里还带着某个片段的
     * 原始源码（切换开关瞬间编辑器回吐旧值），说明它不是折叠文本，按原文收下——否则
     * [ReaderBodyOnlyFilter.reassemble] 找不到标记会把这些片段再插一遍。
     */
    private fun toRawText(text: String): String {
        if (!_uiState.value.bodyOnly) return text
        if (hiddenSpans.any { it.raw.isNotEmpty() && text.contains(it.raw) }) return text
        return ReaderBodyOnlyFilter.reassemble(text, hiddenSpans)
    }

    // --- 光标定位：优先用打开弹层那一刻的可见首行，其次用锚点文本 ---

    private fun currentPage(): ReaderPageContext? = host.currentCanvasPage
        ?.takeIf { it.chapterIndex == ReadBook.durChapterIndex }

    private fun currentOffset(): Int = currentPage()?.contentStartPosition ?: ReadBook.durChapterPos

    private fun currentAnchor(): String? = currentPage()?.anchorText

    private fun resolveCursorOffset(text: String): Int {
        if (text.isEmpty()) {
            clearPendingLocation()
            return 0
        }
        val preferred = (pendingCursorOffset ?: currentOffset())
            .coerceIn(0, text.length)
        val anchor = pendingAnchor ?: currentAnchor()
        clearPendingLocation()
        if (anchor.isNullOrBlank()) {
            return preferred
        }
        val startIndex = (preferred - 200).coerceAtLeast(0)
        val nearIndex = text.indexOf(anchor, startIndex = startIndex)
        if (nearIndex >= 0) {
            return nearIndex
        }
        val anyIndex = text.indexOf(anchor)
        return if (anyIndex >= 0) anyIndex else preferred
    }

    private fun clearPendingLocation() {
        pendingCursorOffset = null
        pendingAnchor = null
    }
}
