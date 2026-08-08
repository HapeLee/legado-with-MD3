package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 正文处理域（R2.2 续批）。
 *
 * 自持 [ContentProcessConfigUiState]，不再让每次加载/开关/删除都 copy 整个
 * [ReadBookUiState]。章节重载是本域和 AI 域共用的动作，故走 [Host]。
 *
 * 只承载 AI 改写（净化/重写）等「修改正文」的记录；用户划线/高亮笔记独立存于
 * book_marks 表，查看在目录 Sheet，不混进本表。
 */
class ReadContentProcessDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val bookContentProcessGateway: BookContentProcessGateway,
) {

    interface Host {
        fun showToast(message: String)
    }

    private val _uiState = MutableStateFlow(ContentProcessConfigUiState())
    val uiState = _uiState.asStateFlow()

    fun load() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(IO) {
            runCatching {
                bookContentProcessGateway.getForChapter(book.bookUrl, chapterIndex)
                    .mapNotNull { it.toContentProcessItemUi() }
                    .toImmutableList()
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(isLoading = false, items = items, errorMessage = null)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                            ?: context.getString(R.string.error),
                    )
                }
            }
        }
    }

    fun toggle(id: String, enabled: Boolean) {
        scope.launch(IO) {
            runCatching {
                bookContentProcessGateway.setEnabled(id, enabled)
            }.onSuccess {
                reloadCurrentChapter()
                load()
            }.onFailure { error ->
                host.showToast(error.localizedMessage ?: context.getString(R.string.error))
            }
        }
    }

    fun requestDelete(item: ContentProcessItemUi) {
        _uiState.update { it.copy(deleteItem = item) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteItem = null) }
    }

    fun confirmDelete() {
        val item = _uiState.value.deleteItem ?: return
        scope.launch(IO) {
            runCatching {
                bookContentProcessGateway.delete(item.id)
            }.onSuccess {
                _uiState.update { it.copy(deleteItem = null) }
                reloadCurrentChapter()
                load()
            }.onFailure { error ->
                host.showToast(error.localizedMessage ?: context.getString(R.string.error))
            }
        }
    }

    /** 弹层关闭时清掉中间态。 */
    fun onSheetDismissed() {
        _uiState.value = ContentProcessConfigUiState()
    }

    /**
     * 正文处理项变化后重载当前章。AI 域改写落库后也走这里（经 VM 的 Host 转发），
     * 故带上 bookUrl / chapterIndex 的守卫：期间用户可能已经翻走。
     */
    fun reloadCurrentChapter(
        bookUrl: String? = null,
        chapterIndex: Int = ReadBook.durChapterIndex,
    ) {
        val book = ReadBook.book ?: return
        if (bookUrl != null && book.bookUrl != bookUrl) return
        if (ReadBook.durChapterIndex != chapterIndex) return
        ReadBook.clearTextChapter()
        for (index in chapterIndex - 1..chapterIndex + 1) {
            ReadBook.removeLoading(index)
        }
        ReadBook.loadContent(resetPageOffset = false)
    }

    private fun BookContentProcess.toContentProcessItemUi(): ContentProcessItemUi? {
        val anchor = GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
            ?: return null
        val action = GSON.fromJsonObject<TextProcessAction>(actionJson).getOrNull()
            ?: return null
        return ContentProcessItemUi(
            id = id,
            kind = kind,
            actionType = action.type,
            enabled = enabled && status == BookContentProcess.STATUS_ACTIVE,
            chapterIndex = chapterIndex ?: anchor.chapterIndex,
            selectedText = anchor.selectedText,
            replacementText = action.replacement ?: action.text.orEmpty(),
            createdAt = createdAt,
        )
    }
}
