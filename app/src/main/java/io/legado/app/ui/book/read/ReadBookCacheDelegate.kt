package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.data.repository.BookRepository
import io.legado.app.constant.AppLog
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读页「离线缓存」域：范围预览与派发前的章节筛选。
 *
 * 两件事都必须在 IO 上做：
 * - 预览要遍历全书章节做 `hasContent`（只看正文文件是否存在，宽松判定）；
 * - 派发要按 `CacheBookModel` 出队时的同一跳过条件（卷、无正文、显式图片已完整）过滤，
 *   否则已缓存章节会白占队列条目、白刷一遍状态广播。
 *
 * 严格图片校验（`hasExplicitImageContent`）只在派发路径用；预览用宽松判定，
 * 避免打开弹窗就扫全书图片索引。
 */
class ReadBookCacheDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val bookRepository: BookRepository,
    private val emit: (ReadBookEffect) -> Unit,
    private val onPreview: (CacheRangePreview) -> Unit,
) {

    /** 统计范围预览：全书已缓存章数 + 范围内仍缺正文的章数。 */
    fun previewRange(startIndex: Int, endIndex: Int) {
        val book = ReadBook.book ?: return
        scope.launch {
            val preview = try {
                withContext(Dispatchers.IO) {
                    val chapters = bookRepository.getChapters(book.bookUrl)
                    var cached = 0
                    var pending = 0
                    for (chapter in chapters) {
                        val hasContent = chapter.isVolume || BookHelp.hasContent(book, chapter)
                        if (hasContent) {
                            cached++
                        } else if (chapter.index in startIndex..endIndex) {
                            pending++
                        }
                    }
                    CacheRangePreview(
                        bookUrl = book.bookUrl,
                        start = startIndex,
                        end = endIndex,
                        totalChapters = book.totalChapterNum,
                        cachedChapters = cached,
                        pendingChapters = pending,
                    )
                }
            } catch (e: Exception) {
                AppLog.put("统计离线缓存范围失败", e)
                return@launch
            }
            onPreview(preview)
        }
    }

    /** 把范围内真正需要下载的章节交给缓存队列；一章都不用下时直接告诉用户。 */
    fun downloadRange(startIndex: Int, endIndex: Int) {
        val book = ReadBook.book ?: return
        if (endIndex < startIndex) return
        scope.launch {
            val indices = try {
                withContext(Dispatchers.IO) {
                    bookRepository.getChapters(book.bookUrl, startIndex, endIndex)
                        .asSequence()
                        .filterNot { it.isVolume }
                        .filterNot { BookHelp.hasExplicitImageContent(book, it) }
                        .map { it.index }
                        .toList()
                }
            } catch (e: Exception) {
                AppLog.put("筛选待缓存章节失败", e)
                emit(ReadBookEffect.ShowToast(context.getString(R.string.error_load_msg, e.localizedMessage)))
                return@launch
            }
            if (indices.isEmpty()) {
                emit(ReadBookEffect.ShowToast(context.getString(R.string.all_chapters_cached)))
                return@launch
            }
            emit(ReadBookEffect.DownloadChapters(indices))
            emit(
                ReadBookEffect.ShowToast(
                    context.getString(R.string.start_downloading_chapters, indices.size)
                )
            )
        }
    }
}
