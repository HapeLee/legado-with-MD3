package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.domain.usecase.SaveMarkingUseCase
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 划线/高亮笔记域：承载一次「选中 → 配置样式/备注 → 保存」的会话。
 *
 * 落库走 [SaveMarkingUseCase]（book_marks 表），与书签、AI 正文处理完全独立。
 * 再次选中同一段文字划线时，按锚点查到已有标记进入编辑模式（预填样式与备注）。
 */
class MarkingDelegate(
    private val scope: CoroutineScope,
    private val context: Context,
    private val highlightRuleRepository: HighlightRuleRepository,
    private val saveMarkingUseCase: SaveMarkingUseCase,
    private val host: Host,
) {

    interface Host {
        fun reloadCurrentChapter()
        fun dismissMarkingSheet()
        fun showToast(message: String)
    }

    private val _uiState = MutableStateFlow(MarkingUiState())
    val uiState = _uiState.asStateFlow()

    fun open(selection: Bookmark) {
        val book = ReadBook.book
        _uiState.update {
            it.copy(
                selection = selection,
                editing = null,
                highlightRules = persistentListOf(),
                loading = true,
            )
        }
        scope.launch(IO) {
            val rules = runCatching {
                highlightRuleRepository.load(ReadBookConfig.durConfig.name)
            }.getOrDefault(emptyList())
            val existing = if (book != null) {
                runCatching {
                    saveMarkingUseCase.find(
                        bookName = book.name,
                        bookAuthor = book.author,
                        chapterIndex = selection.chapterIndex,
                        chapterPosition = selection.chapterPos,
                        selectedText = selection.bookText,
                    )
                }.getOrNull()
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    highlightRules = rules.toImmutableList(),
                    editing = existing,
                    loading = false,
                )
            }
        }
    }

    /** 从目录 Sheet 点标记项进入编辑模式：按 id 取完整标记预填。 */
    fun openForEdit(markingId: String) {
        val book = ReadBook.book
        _uiState.update {
            it.copy(
                selection = null,
                editing = null,
                highlightRules = persistentListOf(),
                loading = true,
            )
        }
        scope.launch(IO) {
            val rules = runCatching {
                highlightRuleRepository.load(ReadBookConfig.durConfig.name)
            }.getOrDefault(emptyList())
            val marking = if (book != null) {
                runCatching { saveMarkingUseCase.findById(markingId) }.getOrNull()
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    highlightRules = rules.toImmutableList(),
                    editing = marking,
                    loading = false,
                )
            }
        }
    }

    fun save(style: TextProcessStyle, note: String) {
        val current = _uiState.value
        val book = ReadBook.book ?: return
        scope.launch(IO) {
            runCatching {
                if (current.editing != null) {
                    // 编辑模式：锚点与源指纹沿用已有标记（保留原源），只改 style/note
                    val mark = current.editing
                    val anchor = mark.anchor() ?: error("marking anchor missing")
                    saveMarkingUseCase.save(
                        bookName = book.name,
                        bookAuthor = book.author,
                        bookUrl = mark.bookUrl,
                        chapterIndex = mark.chapterIndex ?: anchor.chapterIndex,
                        chapterPosition = anchor.chapterPosition ?: 0,
                        selectedText = anchor.selectedText,
                        style = style,
                        contextBefore = anchor.contextBefore,
                        contextAfter = anchor.contextAfter,
                        chapterName = mark.chapterName,
                        note = note,
                    )
                } else {
                    val selection = current.selection ?: return@runCatching
                    val (contextBefore, contextAfter) = selectionContext(selection)
                    saveMarkingUseCase.save(
                        bookName = book.name,
                        bookAuthor = book.author,
                        bookUrl = book.bookUrl,
                        chapterIndex = selection.chapterIndex,
                        chapterPosition = selection.chapterPos,
                        selectedText = selection.bookText,
                        style = style,
                        contextBefore = contextBefore,
                        contextAfter = contextAfter,
                        chapterName = selection.chapterName,
                        note = note,
                    )
                }
            }.onSuccess {
                host.reloadCurrentChapter()
                host.dismissMarkingSheet()
            }.onFailure { error ->
                host.showToast(error.localizedMessage ?: context.getString(R.string.error))
            }
        }
    }

    /** 编辑模式下删除当前标记。 */
    fun deleteCurrent() {
        val editing = _uiState.value.editing ?: return
        scope.launch(IO) {
            runCatching {
                saveMarkingUseCase.delete(editing.id)
            }.onSuccess {
                host.reloadCurrentChapter()
                host.dismissMarkingSheet()
            }.onFailure { error ->
                host.showToast(error.localizedMessage ?: context.getString(R.string.error))
            }
        }
    }

    fun onSheetDismissed() {
        _uiState.value = MarkingUiState()
    }

    private fun BookMarking.anchor(): TextProcessAnchor? =
        GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()

    /**
     * 将选中文本附近的正文一并存入锚点，供换源后的文本匹配消歧。
     * 位置来自当前已排版章节；若选区位置和合成正文略有偏差，则在附近窗口寻找选中文本。
     */
    private fun selectionContext(selection: Bookmark): Pair<String, String> {
        val chapter = ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == selection.chapterIndex }
            ?: return "" to ""
        val content = chapter.getContent()
        val expectedStart = selection.chapterPos.coerceIn(0, content.length)
        val windowStart = max(0, expectedStart - CONTEXT_SEARCH_WINDOW)
        val nearStart = content.indexOf(selection.bookText, windowStart)
            .takeIf { it >= 0 && it <= min(content.length, expectedStart + CONTEXT_SEARCH_WINDOW) }
            ?: expectedStart
        val end = min(content.length, nearStart + selection.bookText.length)
        return content.substring(max(0, nearStart - MARKING_CONTEXT_CHARS), nearStart) to
                content.substring(end, min(content.length, end + MARKING_CONTEXT_CHARS))
    }

    private companion object {
        const val MARKING_CONTEXT_CHARS = 48
        const val CONTEXT_SEARCH_WINDOW = 256
    }
}
