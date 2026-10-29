package io.legado.app.data.repository

import android.net.Uri
import androidx.media3.common.MediaItem
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.getDanmaku
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File

/**
 * 视频正文解析出的可播放媒体。
 *
 * @param mediaItem 给播放器用（headers 已按 ExoPlayer 的约定编码进 uri）
 * @param playUrl 解析后的真实地址，用于复制播放地址和调用外部播放器
 * @param danmaku 弹幕文本，来自章节的 danmaku 变量
 */
data class VideoMedia(
    val mediaItem: MediaItem,
    val playUrl: String,
    val danmaku: String? = null,
)

/** 视频书源的规则、网络与缓存操作；WebBook 的 Await API 本身不会切换线程。 */
open class VideoContentRepository {

    open suspend fun loadBookInfo(source: BookSource, book: Book) {
        withContext(Dispatchers.IO) {
            WebBook.getBookInfoAwait(source, book)
        }
    }

    open suspend fun loadChapters(source: BookSource, book: Book): List<BookChapter> =
        withContext(Dispatchers.IO) {
            WebBook.getChapterListAwait(source, book).getOrThrow()
        }

    open suspend fun loadMedia(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
    ): VideoMedia? =
        withContext(Dispatchers.IO) {
            val mediaUrl = resolveMediaUrl(WebBook.getContentAwait(source, book, chapter))
                ?: return@withContext null
            val analyzeUrl = AnalyzeUrl(
                mediaUrl,
                source = source,
                ruleData = book,
                chapter = chapter,
                coroutineContext = coroutineContext,
            )
            VideoMedia(
                mediaItem = analyzeUrl.getMediaItem(),
                playUrl = analyzeUrl.url,
                danmaku = chapter.getDanmaku(),
            )
        }

    /** 只解析出地址，供「下一集预解析」预热缓存，不构造 MediaItem。 */
    suspend fun resolvePlayUrl(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
    ): String? = withContext(Dispatchers.IO) {
        val mediaUrl = resolveMediaUrl(WebBook.getContentAwait(source, book, chapter))
            ?: return@withContext null
        AnalyzeUrl(
            mediaUrl,
            source = source,
            ruleData = book,
            chapter = chapter,
            coroutineContext = coroutineContext,
        ).url
    }

    /** 会话结束时清理 DASH/MPD 临时文件，它们只在本次播放期间有意义。 */
    suspend fun clearTempFiles() {
        withContext(Dispatchers.IO) {
            FileUtils.delete(videoTempDir)
        }
    }

    /**
     * 正文规则可能直接返回 DASH/MPD 清单文本：这类内容没有可识别的后缀，
     * 必须落成临时 .mpd 文件再交给播放器，否则无法按清单建流。
     */
    private fun resolveMediaUrl(content: String): String? {
        val text = content.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("<")) return text
        val file = FileUtils.createFileIfNotExist(
            videoTempDir,
            "${MD5Utils.md5Encode(text)}$MPD_SUFFIX",
        )
        file.writeText(text)
        return Uri.fromFile(file).toString()
    }

    private val videoTempDir: File
        get() = FileUtils.createFolderIfNotExist(File(FileUtils.getCachePath(), VIDEO_TEMP_PATH))

    companion object {
        private const val VIDEO_TEMP_PATH = "video_temp"
        private const val MPD_SUFFIX = ".mpd"
    }
}
