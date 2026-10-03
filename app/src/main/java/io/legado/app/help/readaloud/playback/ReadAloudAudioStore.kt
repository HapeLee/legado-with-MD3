package io.legado.app.help.readaloud.playback

import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadAloudAudioDownload
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import splitties.init.appCtx
import java.io.File

/**
 * 听书音频的下载区。
 *
 * 文件按书放在 `filesDir/readAloudAudio/<bookUrl 的 md5>/`，文件名沿用朗读缓存那一套
 * （引擎 + 语速 + 句子文本的 md5，`.mp3`）。正因为名字一致，朗读侧只要在这两个目录里
 * 找到同名文件就直接播，不用再联网——「下载过的章节点朗读就是播下载好的」。
 *
 * 为什么不共用 `externalCacheDir/httpTTS`：那一个是**缓存**，退出朗读会按设置清、
 * 系统存储紧张也会回收，下载的东西不能被清掉；而且删除要能精确到章，所以文件名清单
 * 记在 `read_aloud_audio_downloads` 表里（文件名是内容哈希，反推不出属于哪一章）。
 */
object ReadAloudAudioStore {

    /**
     * 无声占位音频的大小：合成失败时落的那份 `R.raw.silent_sound`，字节数和资源完全一致。
     *
     * 认不出它就会被它骗：一句合成失败留下 2160 字节的空壳，之后朗读在缓存或下载区
     * 认到同名文件，当成「已经有音频了」不再重新合成，这一句永远只有 0.1 秒空白。
     */
    private const val SILENT_SOUND_BYTES = 2160L

    /** 这份文件是不是无声占位（不是真音频）。 */
    fun isSilentPlaceholder(file: File?): Boolean =
        file != null && file.length() == SILENT_SOUND_BYTES

    /** 下载进度：章节和句子两条，界面要同时显示。 */
    data class Progress(
        val running: Boolean = false,
        val bookUrl: String = "",
        val chapterTotal: Int = 0,
        val chapterDone: Int = 0,
        val currentChapter: String = "",
        val sentenceTotal: Int = 0,
        val sentenceDone: Int = 0,
        val failed: Int = 0,
    ) {
        val chapterFraction: Float
            get() = if (chapterTotal <= 0) 0f else chapterDone.toFloat() / chapterTotal
        val sentenceFraction: Float
            get() = if (sentenceTotal <= 0) 0f else sentenceDone.toFloat() / sentenceTotal
    }

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    fun updateProgress(value: Progress) {
        _progress.value = value
    }

    fun finishProgress() {
        _progress.value = _progress.value.copy(running = false)
    }

    private fun dirOf(bookUrl: String): File = File(
        appCtx.filesDir,
        "readAloudAudio".plus(File.separator).plus(MD5Utils.md5Encode16(bookUrl)),
    )

    /** 这一句已经下载好的音频；没有、半截文件、无声占位都返回 null。 */
    fun downloadedFile(bookUrl: String?, fileName: String): File? {
        if (bookUrl.isNullOrEmpty()) return null
        val file = File(dirOf(bookUrl), "$fileName.mp3")
        return file.takeIf { it.isFile && it.length() > 0L && !isSilentPlaceholder(it) }
    }

    /** 把合成在缓存目录里的那份复制进下载区（缓存会被清，下载区不会）。 */
    fun saveFrom(bookUrl: String, fileName: String, source: File): File? {
        val dir = dirOf(bookUrl)
        dir.mkdirs()
        val target = File(dir, "$fileName.mp3")
        if (isSilentPlaceholder(target)) {
            // 下载失败留下的空壳：让新合成出来的那份覆盖它
            FileUtils.delete(target.absolutePath)
        } else if (target.isFile && target.length() > 0L) {
            // 合成缓存已清、下载区仍留有完整文件：视为命中，不重复合成
            return target
        }
        if (!source.isFile || source.length() <= 0L) return null
        return runCatching {
            source.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }

    suspend fun list(bookUrl: String): List<ReadAloudAudioDownload> =
        appDb.readAloudAudioDownloadDao.getForBook(bookUrl)

    suspend fun downloadedChapters(bookUrl: String): Set<Int> =
        appDb.readAloudAudioDownloadDao.getDownloadedChapters(bookUrl).toSet()

    /** 订阅：目录页的听书下载图标要跟着下载进度亮起来。 */
    fun flowDownloadedChapters(bookUrl: String): Flow<Set<Int>> =
        appDb.readAloudAudioDownloadDao.flowDownloadedChapters(bookUrl).map { it.toSet() }

    suspend fun record(bookUrl: String, chapterIndex: Int, title: String, files: List<String>) {
        appDb.readAloudAudioDownloadDao.upsert(
            ReadAloudAudioDownload(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                title = title,
                files = files.joinToString(","),
                createTime = System.currentTimeMillis(),
            )
        )
    }

    /** 删掉一章的下载音频与记录，返回删掉的句子数。 */
    suspend fun delete(bookUrl: String, chapterIndex: Int): Int {
        val row = appDb.readAloudAudioDownloadDao.getOne(bookUrl, chapterIndex) ?: return 0
        val dir = dirOf(bookUrl)
        row.fileNames.forEach { File(dir, "$it.mp3").delete() }
        appDb.readAloudAudioDownloadDao.delete(bookUrl, chapterIndex)
        return row.sentenceCount
    }

    /** 删掉若干章的下载音频，返回删掉的句子数（目录页长按菜单用）。 */
    suspend fun deleteChapters(bookUrl: String, chapterIndices: Collection<Int>): Int =
        chapterIndices.sumOf { delete(bookUrl, it) }

    /** 整本书的下载音频全清（书架/缓存管理里用）。 */
    suspend fun deleteBook(bookUrl: String): Int {
        val rows = appDb.readAloudAudioDownloadDao.getForBook(bookUrl)
        val dir = dirOf(bookUrl)
        rows.flatMap { it.fileNames }.forEach { File(dir, "$it.mp3").delete() }
        appDb.readAloudAudioDownloadDao.deleteForBook(bookUrl)
        return rows.sumOf { it.sentenceCount }
    }
}
