package io.legado.app.help.book

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.book.BookHelp.saveText
import io.legado.app.help.book.BookHelp.saveToLocalTxt
import io.legado.app.help.glide.progress.ProgressManager
import io.legado.app.help.glide.progress.ProgressResponseBody
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.ImageUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.exists
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isWifiConnect
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.JaccardSimilarity
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("unused", "ConstPropertyName")
object BookHelp {
    private val readGateway by lazy { GlobalContext.get().get<ReadSettingsGateway>() }
    private val cacheGateway by lazy { GlobalContext.get().get<DownloadCacheSettingsGateway>() }

    private const val CACHED_CONTENT_PREFIX_LENGTH = 1_024
    private val downloadDir: File = appCtx.externalFiles
    private const val cacheFolderName = "book_cache"
    private const val cacheImageFolderName = "images"
    private const val cacheEpubFolderName = "epub"
    private val imageFiles = BookImageFileStore(validate = ::checkImage)
    private const val ONLINE_IMAGE_BUDGET = 1000L * 1024 * 1024
    private val imageCleanupScope = CoroutineScope(SupervisorJob() + IO)
    private val imageCleanupRequests = Channel<Unit>(Channel.CONFLATED)
    private val pendingImageCacheMoves = ConcurrentHashMap<File, File>()

    val cachePath = FileUtils.getPath(downloadDir, cacheFolderName)

    init {
        imageCleanupScope.launch {
            for (request in imageCleanupRequests) {
                try {
                    // 合并一批预取/预览/瓦片租约变化，避免每次释放都重新遍历缓存。
                    kotlinx.coroutines.delay(250)
                    while (imageCleanupRequests.tryReceive().isSuccess) {
                        // 当前批次处理全部已到达的请求。
                    }
                    pendingImageCacheMoves.forEach { (source, target) ->
                        if (source.exists() && imageFiles.moveIfIdle(source, target, FileUtils::move)) {
                            pendingImageCacheMoves.remove(source, target)
                            if (pendingImageCacheMoves.isNotEmpty()) imageCleanupRequests.trySend(Unit)
                        } else if (!source.exists() && !pendingImageCacheMoves.containsValue(source)) {
                            pendingImageCacheMoves.remove(source, target)
                        }
                    }
                    imageFiles.trimOnlineCache(File(cachePath), ONLINE_IMAGE_BUDGET)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.put("在线漫画缓存清理失败", error)
                }
            }
        }
    }

    fun clearCache() {
        deleteImageCacheDirectory(File(FileUtils.getPath(downloadDir, cacheFolderName)))
    }

    fun clearCache(book: Book) {
        val filePath = FileUtils.getPath(downloadDir, cacheFolderName, book.getFolderName())
        deleteImageCacheDirectory(File(filePath))
    }

    private fun deleteImageCacheDirectory(directory: File) {
        imageFiles.deleteIfIdle(directory) {
            FileUtils.delete(it.absolutePath)
            !it.exists()
        }
    }

    fun updateCacheFolder(oldBook: Book, newBook: Book) {
        val oldFolderName = oldBook.getFolderNameNoCache()
        val newFolderName = newBook.getFolderNameNoCache()
        if (oldFolderName == newFolderName) return
        val oldFolderPath = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            oldFolderName
        )
        val newFolderPath = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            newFolderName
        )
        val source = File(oldFolderPath)
        val target = File(newFolderPath)
        if ((source.exists() || pendingImageCacheMoves.containsValue(source)) &&
            !imageFiles.moveIfIdle(source, target, FileUtils::move)
        ) {
            pendingImageCacheMoves[source] = target
            imageCleanupRequests.trySend(Unit)
        }
    }

    /**
     * 清除已删除书的缓存 解压缓存
     */
    suspend fun clearInvalidCache() {
        withContext(IO) {
            val bookFolderNames = hashSetOf<String>()
            val originNames = hashSetOf<String>()
            appDb.bookDao.all.forEach {
                clearComicCache(it)
                bookFolderNames.add(it.getFolderName())
                if (it.isEpub) originNames.add(it.originName)
            }
            downloadDir.getFile(cacheFolderName)
                .listFiles()?.forEach { bookFile ->
                    if (!bookFolderNames.contains(bookFile.name)) {
                        deleteImageCacheDirectory(bookFile)
                    }
                }
            downloadDir.getFile(cacheEpubFolderName)
                .listFiles()?.forEach { epubFile ->
                    if (!originNames.contains(epubFile.name)) {
                        FileUtils.delete(epubFile.absolutePath)
                    }
                }
            FileUtils.delete(ArchiveUtils.TEMP_PATH)
            val filesDir = appCtx.filesDir
            FileUtils.delete("$filesDir/shareBookSource.json")
            FileUtils.delete("$filesDir/shareRssSource.json")
            FileUtils.delete("$filesDir/books.json")
        }
    }

    //清除已经看过的漫画数据
    private fun clearComicCache(book: Book) {
        //只处理漫画
        //为0的时候，不清除已缓存数据
        if (!book.isImage || cacheGateway.currentSettings.imageRetainNum == 0) {
            return
        }
        //向前保留设定数量，向后保留预下载数量
        val startIndex = book.durChapterIndex - cacheGateway.currentSettings.imageRetainNum
        val endIndex = book.durChapterIndex + readGateway.currentSettings.preDownloadNum
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl, startIndex, endIndex)
        val imgNames = hashSetOf<String>()
        //获取需要保留章节的图片信息
        chapterList.forEach {
                val content = getContent(book, it)
                if (content != null) {
                    for (m in AppPattern.imgPattern.findAll(content)) {
                        val src = m.groupValues[1].takeIf { it.isNotEmpty() } ?: continue
                        val mSrc = NetworkUtils.getAbsoluteURL(it.url, src)
                        imgNames.add("${MD5Utils.md5Encode16(mSrc)}.${getImageSuffix(mSrc)}")
                    }
                }
        }
        downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            cacheImageFolderName
        ).listFiles()?.forEach { imgFile ->
            // 临时文件的所有权属于写入者；清理不能在校验/发布前删除它。
            if (!imgNames.contains(imgFile.name) && !imgFile.name.endsWith(".tmp")) {
                imageFiles.deleteIfIdle(imgFile) {
                    // 普通在线缓存由字节预算淘汰，不能被章节窗口清理绕过。
                    if (it.name == BookImageFileStore.ONLINE_MARKER_DIRECTORY ||
                        it.name == BookImageFileStore.DOWNLOAD_INDEX_DIRECTORY ||
                        imageFiles.isOnline(it) || imageFiles.isExplicitDownload(it)) false else it.delete()
                }
            }
        }
    }

    fun saveContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String
    ) {
        try {
            saveText(book, bookChapter, content)
            //saveImages(bookSource, book, bookChapter, content)
            postEvent(EventBus.SAVE_CONTENT, Pair(book, bookChapter))
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.put("保存正文失败 ${book.name} ${bookChapter.title}", e)
        }
    }

    fun saveText(
        book: Book,
        bookChapter: BookChapter,
        content: String,
        saveToSource: Boolean = false
    ) {
        if (content.isEmpty()) return
        if (book.isLocalTxt && saveToSource) {
            try {
                saveToLocalTxt(book, bookChapter, content)
                TextFile.clear()
            } catch (e: Exception) {
                AppLog.put("修改本地TXT失败: ${e.localizedMessage}", e)
            }
        }
        // 保存阅读缓存文本(.nb)
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName(),
        ).writeText(content)
        if (book.isOnLineTxt && readGateway.currentSettings.tocCountWords) {
            // 正文里携带的 <img src="data:base64">、内联 SVG 等富文本源码会把章节字数虚抬
            // 几倍（3 页正文显示 3000+ 字）。这里剔除标签/Base64 后按可读纯文本计数，
            // 取代原先的 StringUtils.wordCountFormat(content.length)。
            val readableLength = HtmlFormatter.countReadableTextLength(content)
            val wordCount = StringUtils.wordCountFormat(readableLength)
            bookChapter.wordCount = wordCount
            appDb.bookChapterDao.update(bookChapter)
        }
    }

    /**
     * 保存章名。[BookChapter] 是 Room 实体，就地改名后 update，与 [saveText] 里
     * `wordCount` 的写法一致。本地 TXT 的源文件标题由随后的 [saveText] →
     * [saveToLocalTxt] 写入（它用 `bookChapter.title` 当段首标题）。
     */
    fun saveChapterTitle(bookChapter: BookChapter, title: String) {
        bookChapter.title = title
        appDb.bookChapterDao.update(bookChapter)
    }

    private fun saveToLocalTxt(book: Book, bookChapter: BookChapter, content: String) {
        val start = bookChapter.start ?: return
        val end = bookChapter.end ?: return
        val uri = book.getLocalUri()
        val charset = book.fileCharset()
        val oldTitle = bookChapter.title

        val pfd = if (uri.isContentScheme()) {
            appCtx.contentResolver.openFileDescriptor(uri, "rw")
        } else {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_WRITE)
        }

        pfd?.use {
            val fd = it.fileDescriptor
            try {
                val totalLength = Os.fstat(fd).st_size

                // 尝试保留章节末尾的换行符
                val oldLength = end - start
                val readLen = if (oldLength > 32) 32 else oldLength.toInt()
                var gap = ""
                if (readLen > 0) {
                    val lastBytes = ByteArray(readLen)
                    Os.pread(fd, lastBytes, 0, readLen, end - readLen)
                    val lastString = String(lastBytes, charset)
                    gap = lastString.takeLastWhile { c -> c == '\n' || c == '\r' }
                }

                val newContent = oldTitle + "\n" + content + gap
                val newBytes = newContent.toByteArray(charset)
                val diff = newBytes.size - oldLength

                if (diff != 0L) {
                    val remaining = totalLength - end
                    if (remaining > 0) {
                        val buffer = ByteArray(1024 * 1024)
                        var pos = totalLength
                        while (pos > end) {
                            val readSize =
                                if (pos - end > buffer.size) buffer.size else (pos - end).toInt()
                            Os.pread(fd, buffer, 0, readSize, pos - readSize)
                            Os.pwrite(fd, buffer, 0, readSize, pos - readSize + diff)
                            pos -= readSize
                        }
                    }
                    if (diff < 0) {
                        Os.ftruncate(fd, totalLength + diff)
                    }
                }

                Os.pwrite(fd, newBytes, 0, newBytes.size, start)

                // 更新数据库中的偏移量
                if (diff != 0L) {
                    appDb.bookChapterDao.updateOffsets(book.bookUrl, bookChapter.index, diff)
                }
                bookChapter.end = start + newBytes.size
                appDb.bookChapterDao.update(bookChapter)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun flowImages(bookChapter: BookChapter, content: String): Flow<String> {
        return flow {
            for (m in AppPattern.imgPattern.findAll(content)) {
                val src = m.groupValues[1].takeIf { it.isNotEmpty() } ?: continue
                val mSrc = NetworkUtils.getAbsoluteURL(bookChapter.url, src)
                emit(mSrc)
            }
        }
    }

    fun countImagesInContent(bookChapter: BookChapter, content: String): Int {
        var count = 0
        for (m in AppPattern.imgPattern.findAll(content)) {
            if (m.groupValues[1].isNotEmpty()) count++
        }
        return count
    }

    /** 显式下载还需要当前章节的保留引用，不能仅凭在线文件命中跳过升级。 */
    fun hasExplicitImageContent(book: Book, chapter: BookChapter): Boolean {
        if (!hasContent(book, chapter)) return false
        var complete = true
        forEachImageSrc(book, chapter) { src ->
            val image = getImage(book, src)
            if (!imageFiles.hasDownloadChapter(image, "${chapter.index}.${MD5Utils.md5Encode16(chapter.url)}") ||
                !imageFiles.isValid(image)) complete = false
        }
        return complete
    }

    /** @return 失败的图片数量（0 表示全部成功或无需下载） */
    suspend fun saveImages(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String,
        concurrency: Int = cacheGateway.currentSettings.threadCount,
        onProgress: (suspend (completed: Int, total: Int) -> Unit)? = null,
        explicitDownload: Boolean = false,
    ): Int = coroutineScope {
        val imageUrls = flowImages(bookChapter, content).toList()
        val total = imageUrls.size
        onProgress?.invoke(0, total)
        if (total == 0) return@coroutineScope 0
        val progressMutex = Mutex()
        var completed = 0
        var failures = 0
        imageUrls.asFlow().onEachParallel(concurrency) { mSrc ->
            val ok = saveImage(
                bookSource, book, mSrc, bookChapter,
                explicitDownloadChapter = if (explicitDownload) {
                    "${bookChapter.index}.${MD5Utils.md5Encode16(bookChapter.url)}"
                } else null,
            )
            progressMutex.withLock {
                if (!ok) failures++
                completed++
                onProgress?.invoke(completed, total)
            }
        }.collect()
        failures
    }

    /** @return true 表示已复用有效图片，或本次获取、校验并原子发布成功。 */
    suspend fun saveImage(
        bookSource: BookSource?,
        book: Book,
        src: String,
        chapter: BookChapter? = null,
        onlineOnly: Boolean = false,
        loadOnlyWifi: Boolean = false,
        onDownload: () -> Unit = {},
        explicitDownloadChapter: String? = null,
    ): Boolean = withContext(IO) {
        try {
            // 按实际书籍文件串行获取；等待者重新检查有效性，成功时不重复联网。
            val image = getImage(book, src)
            imageFiles.withFileLock(image) {
                explicitDownloadChapter?.let { imageFiles.retainDownload(image, it) }
                if (imageFiles.isValid(image)) {
                    if (!onlineOnly) imageFiles.setOnline(image, false)
                    imageFiles.touch(image)
                    return@withFileLock true
                }
                onDownload()
                val context = currentCoroutineContext()
                val analyzeUrl = AnalyzeUrl(src, source = bookSource, coroutineContext = context)
                if (onlineOnly && loadOnlyWifi && !appCtx.isWifiConnect &&
                    !analyzeUrl.urlNoQuery.startsWith("data:", true)
                ) throw IOException("WiFi not available, loadOnlyWifi enabled")
                // 同文件获取互斥，跨图片并发只遵循书源自身的限流配置。
                if (!onlineOnly) imageFiles.setOnline(image, false)
                else if (!image.exists()) imageFiles.setOnline(image, true)
                if (ImageUtils.skipDecode(bookSource, isCover = false)) {
                    imageInputStream(analyzeUrl, src, onlineOnly).use { input ->
                        imageFiles.write(image, input) { context.ensureActive() }
                    }
                    true
                } else {
                    val bytes =
                        imageInputStream(analyzeUrl, src, onlineOnly).use(InputStream::readBytes)
                    val decoded = runScriptWithContext {
                        ImageUtils.decode(src, bytes, isCover = false, bookSource, book)
                    }
                    if (decoded == null) {
                        AppLog.put("${book.name} ${chapter?.title} 图片 $src 下载失败 解码为空")
                        false
                    } else {
                        decoded.inputStream().use { input ->
                            imageFiles.write(image, input) { context.ensureActive() }
                        }
                        true
                    }
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            val msg = "${book.name} ${chapter?.title} 图片 $src 下载失败\n${e.localizedMessage}"
            AppLog.put(msg, e)
            false
        }
    }

    private suspend fun imageInputStream(analyzeUrl: AnalyzeUrl, src: String, reportProgress: Boolean): InputStream {
        if (!reportProgress || analyzeUrl.urlNoQuery.startsWith("data:", true)) {
            return analyzeUrl.getInputStreamAwait()
        }
        val response = analyzeUrl.getResponseAwait()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return ProgressResponseBody(src, ProgressManager.LISTENER, response.body).byteStream()
    }

    /** Android 图片适配器使用：先持有租约，再获取文件，避免发布与淘汰之间存在空窗。 */
    suspend fun acquireReadingImage(
        bookSource: BookSource?, book: Book, src: String, loadOnlyWifi: Boolean = false,
        onDownload: () -> Unit = {},
    ): Pair<File, Closeable> {
        val file = getImage(book, src)
        val lease = pinImageFile(file)
        try {
            if (!saveImage(bookSource, book, src, onlineOnly = true, loadOnlyWifi = loadOnlyWifi, onDownload = onDownload)) {
                throw IOException("图片获取失败")
            }
            imageCleanupRequests.trySend(Unit)
            return file to lease
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
    }

    fun pinImageFile(file: File): Closeable {
        val lease = imageFiles.pin(file)
        val closed = AtomicBoolean()
        return Closeable {
            if (closed.compareAndSet(false, true)) {
                lease.close()
                imageCleanupRequests.trySend(Unit)
            }
        }
    }

    /**
     * 一次 saveImages 批次是否可视为章节图片缓存完成。
     */
    fun isChapterImageCacheComplete(failures: Int, filesCached: Boolean): Boolean {
        return io.legado.app.help.book.isChapterImageCacheComplete(failures, filesCached)
    }

    fun isChapterImageCacheComplete(
        book: Book,
        bookChapter: BookChapter,
        failures: Int,
    ): Boolean {
        return io.legado.app.help.book.isChapterImageCacheComplete(
            failures,
            failures == 0 && hasImageContent(book, bookChapter),
        )
    }

    fun getImage(book: Book, src: String): File {
        return downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            cacheImageFolderName,
            "${MD5Utils.md5Encode16(src)}.${getImageSuffix(src)}"
        )
    }

    @Synchronized
    fun writeImage(book: Book, src: String, bytes: ByteArray) {
        bytes.inputStream().use { imageFiles.write(getImage(book, src), it) }
    }

    @Synchronized
    fun isImageExist(book: Book, src: String): Boolean {
        return getImage(book, src).exists()
    }

    fun getImageSuffix(src: String): String {
        return UrlUtil.getSuffix(src, "jpg")
    }

    @Throws(IOException::class, FileNotFoundException::class)
    fun getEpubFile(book: Book): ZipFile {
        val uri = book.getLocalUri()
        if (uri.isContentScheme()) {
            FileUtils.createFolderIfNotExist(downloadDir, cacheEpubFolderName)
            val path = FileUtils.getPath(downloadDir, cacheEpubFolderName, book.originName)
            val file = File(path)
            val doc = DocumentFile.fromSingleUri(appCtx, uri)
                ?: throw IOException("文件不存在")
            if (!file.exists() || doc.lastModified() > book.latestChapterTime) {
                LocalBook.getBookInputStream(book).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            return ZipFile(file)
        }
        return ZipFile(uri.path)
    }

    /**
     * 获取本地书籍文件的ParcelFileDescriptor
     *
     * @param book
     * @return
     */
    @Throws(IOException::class, FileNotFoundException::class)
    fun getBookPFD(book: Book): ParcelFileDescriptor? {
        val uri = book.getLocalUri()
        return if (uri.isContentScheme()) {
            appCtx.contentResolver.openFileDescriptor(uri, "r")
        } else {
            ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    fun getChapterFiles(book: Book): HashSet<String> {
        val fileNames = hashSetOf<String>()
        if (book.isLocalTxt) {
            return fileNames
        }
        FileUtils.createFolderIfNotExist(
            downloadDir,
            subDirs = arrayOf(cacheFolderName, book.getFolderName())
        ).list()?.let {
            fileNames.addAll(it)
        }
        return fileNames
    }

    /**
     * 检测该章节是否下载
     */
    fun countCachedChapters(book: Book): Int {
        return appDb.bookChapterDao.getChapterList(book.bookUrl).count { chapter ->
            chapter.isVolume || isChapterCacheComplete(book, chapter)
        }
    }

    fun isChapterCacheComplete(book: Book, bookChapter: BookChapter): Boolean {
        return if (book.isLocal) {
            hasContent(book, bookChapter)
        } else {
            hasImageFilesCached(book, bookChapter)
        }
    }

    fun hasContent(book: Book, bookChapter: BookChapter): Boolean {
        return if (book.isLocalTxt ||
            (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title))
        ) {
            true
        } else {
            downloadDir.exists(
                cacheFolderName,
                book.getFolderName(),
                bookChapter.getFileName()
            )
        }
    }

    /**
     * UI/队列用：仅检查图片文件是否都存在，不做 bitmap 解码校验。
     */
    fun hasImageFilesCached(book: Book, bookChapter: BookChapter): Boolean {
        if (!hasContent(book, bookChapter)) {
            return false
        }
        var ret = true
        forEachImageSrc(book, bookChapter) { src ->
            if (!isImageExist(book, src)) {
                ret = false
                return@forEachImageSrc
            }
        }
        return ret
    }

    /**
     * 检测图片是否下载（含 bitmap 解码校验，用于实际下载决策）
     */
    fun hasImageContent(book: Book, bookChapter: BookChapter): Boolean {
        if (!hasContent(book, bookChapter)) {
            return false
        }
        var ret = true
        forEachImageSrc(book, bookChapter) { src ->
            if (!imageFiles.isValid(getImage(book, src))) ret = false
        }
        return ret
    }

    private inline fun forEachImageSrc(
        book: Book,
        bookChapter: BookChapter,
        action: (String) -> Unit
    ) {
        val file = downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
        if (file.exists()) {
            forEachImageSrc(file) { src ->
                action(NetworkUtils.getAbsoluteURL(bookChapter.url, src))
            }
            return
        }
        getContent(book, bookChapter)?.let { content ->
            for (m in AppPattern.imgPattern.findAll(content)) {
                val src = m.groupValues[1].takeIf { it.isNotEmpty() } ?: continue
                action(NetworkUtils.getAbsoluteURL(bookChapter.url, src))
            }
        }
    }

    private inline fun forEachImageSrc(file: File, action: (String) -> Unit) {
        val chars = CharArray(8192)
        val buffer = StringBuilder()
        file.bufferedReader().use { reader ->
            while (true) {
                val readSize = reader.read(chars)
                if (readSize < 0) break
                buffer.append(chars, 0, readSize)
                consumeImageSrcMatches(buffer, action)
                trimImageScanBuffer(buffer)
            }
            consumeImageSrcMatches(buffer, action)
        }
    }

    private inline fun consumeImageSrcMatches(
        buffer: StringBuilder,
        action: (String) -> Unit
    ) {
        var lastEnd = 0
        for (m in AppPattern.imgPattern.findAll(buffer)) {
            val src = m.groupValues[1].takeIf { it.isNotEmpty() } ?: continue
            action(src)
            lastEnd = m.range.last + 1
        }
        if (lastEnd > 0) {
            buffer.delete(0, lastEnd)
        }
    }

    private fun trimImageScanBuffer(buffer: StringBuilder) {
        val maxBufferSize = 16 * 1024
        if (buffer.length <= maxBufferSize) return
        val partialImgStart = buffer.lastIndexOf("<img")
        val keepFrom = when {
            partialImgStart >= 0 -> partialImgStart
            else -> max(buffer.length - 1024, 0)
        }
        buffer.delete(0, keepFrom)
    }

    private fun checkImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) return true
        // SvgUtils 的路径重载不负责关闭输入流；这里明确拥有并关闭。
        return file.inputStream().use { input ->
            SvgUtils.getSize(input)?.let { it.width > 0 && it.height > 0 } == true
        }
    }

    /**
     * 读取章节内容
     */
    fun getContent(book: Book, bookChapter: BookChapter): String? {
        val file = downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
        if (file.exists()) {
            val string = file.readText()
            if (string.isEmpty()) {
                return null
            }
            return string
        }
        if (book.isLocal) {
            val string = LocalBook.getContent(book, bookChapter)
            if (string != null && book.isEpub) {
                saveText(book, bookChapter, string)
            }
            return string
        }
        return null
    }

    /**
     * 只统计已经落盘的章节缓存，不读取本地书源，也不会触发网络请求。
     */
    fun getCachedContentLength(book: Book, bookChapter: BookChapter): Int? {
        return getCachedContentInfo(book, bookChapter)?.contentLength
    }

    /**
     * 流式读取已落盘正文，只保留用于页数缓存校验的短前缀，不加载本地书源或网络正文。
     */
    fun getCachedContentInfo(book: Book, bookChapter: BookChapter): CachedContentInfo? {
        val file = downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
        if (!file.isFile) return null

        return try {
            var length = 0L
            val prefix = StringBuilder(CACHED_CONTENT_PREFIX_LENGTH)
            file.bufferedReader().use { reader ->
                val buffer = CharArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val readCount = reader.read(buffer)
                    if (readCount < 0) break
                    length += readCount
                    if (prefix.length < CACHED_CONTENT_PREFIX_LENGTH) {
                        prefix.append(
                            buffer,
                            0,
                            min(readCount, CACHED_CONTENT_PREFIX_LENGTH - prefix.length),
                        )
                    }
                    if (length > Int.MAX_VALUE) {
                        return CachedContentInfo(Int.MAX_VALUE, prefix.toString())
                    }
                }
            }
            length.toInt().takeIf { it > 0 }?.let {
                CachedContentInfo(it, prefix.toString())
            }
        } catch (_: IOException) {
            null
        }
    }

    data class CachedContentInfo(
        val contentLength: Int,
        val prefix: String,
    )

    /**
     * 删除章节内容
     */
    fun delContent(book: Book, bookChapter: BookChapter) {
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        ).delete()
    }

    /**
     * 设置是否禁用正文的去除重复标题,针对单个章节
     */
    fun setRemoveSameTitle(book: Book, bookChapter: BookChapter, removeSameTitle: Boolean) {
        val fileName = bookChapter.getFileName("nr")
        val contentProcessor = ContentProcessor.get(book)
        if (removeSameTitle) {
            val path = FileUtils.getPath(
                downloadDir,
                cacheFolderName,
                book.getFolderName(),
                fileName
            )
            contentProcessor.removeSameTitleCache.remove(fileName)
            File(path).delete()
        } else {
            FileUtils.createFileIfNotExist(
                downloadDir,
                cacheFolderName,
                book.getFolderName(),
                fileName
            )
            contentProcessor.removeSameTitleCache.add(fileName)
        }
    }

    /**
     * 获取是否去除重复标题
     */
    fun removeSameTitle(book: Book, bookChapter: BookChapter): Boolean {
        val path = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName("nr")
        )
        return !File(path).exists()
    }

    /**
     * 格式化书名
     */
    fun formatBookName(name: String): String {
        return name
            .replace(AppPattern.nameRegex, "")
            .trim { it <= ' ' }
    }

    /**
     * 格式化作者
     */
    fun formatBookAuthor(author: String): String {
        return author
            .replace(AppPattern.authorRegex, "")
            .trim { it <= ' ' }
    }

    private val jaccardSimilarity by lazy {
        JaccardSimilarity()
    }

    /**
     * 根据目录名获取当前章节
     */
    fun getDurChapter(
        oldDurChapterIndex: Int,
        oldDurChapterName: String?,
        newChapterList: List<BookChapter>,
        oldChapterListSize: Int = 0
    ): Int {
        if (oldDurChapterIndex <= 0) return 0
        if (newChapterList.isEmpty()) return oldDurChapterIndex
        val oldChapterNum = getChapterNum(oldDurChapterName)
        val oldName = getPureChapterName(oldDurChapterName)
        val newChapterSize = newChapterList.size
        val durIndex =
            if (oldChapterListSize == 0) oldDurChapterIndex
            else oldDurChapterIndex * oldChapterListSize / newChapterSize
        val min = max(0, min(oldDurChapterIndex, durIndex) - 10)
        val max = min(newChapterSize - 1, max(oldDurChapterIndex, durIndex) + 10)
        var nameSim = 0.0
        var newIndex = 0
        var newNum = 0
        if (oldName.isNotEmpty()) {
            for (i in min..max) {
                val newName = getPureChapterName(newChapterList[i].title)
                val temp = jaccardSimilarity.apply(oldName, newName)
                if (temp > nameSim) {
                    nameSim = temp
                    newIndex = i
                }
            }
        }
        if (nameSim < 0.96 && oldChapterNum > 0) {
            for (i in min..max) {
                val temp = getChapterNum(newChapterList[i].title)
                if (temp == oldChapterNum) {
                    newNum = temp
                    newIndex = i
                    break
                } else if (abs(temp - oldChapterNum) < abs(newNum - oldChapterNum)) {
                    newNum = temp
                    newIndex = i
                }
            }
        }
        return if (nameSim > 0.96 || abs(newNum - oldChapterNum) < 1) {
            newIndex
        } else {
            min(max(0, newChapterList.size - 1), oldDurChapterIndex)
        }
    }

    fun getDurChapter(
        oldBook: Book,
        newChapterList: List<BookChapter>
    ): Int {
        return oldBook.run {
            getDurChapter(durChapterIndex, durChapterTitle, newChapterList, totalChapterNum)
        }
    }

    private val chapterNamePattern1 by lazy {
        Regex(
            ".*?第([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话]"
        )
    }

    @Suppress("RegExpSimplifiable")
    private val chapterNamePattern2 by lazy {
        Regex(
            "^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、]|\\.[^\\d])"
        )
    }

    private val regexA by lazy {
        return@lazy "\\s".toRegex()
    }

    private fun getChapterNum(chapterName: String?): Int {
        chapterName ?: return -1
        val chapterName1 = StringUtils.fullToHalf(chapterName).replace(regexA, "")
        return StringUtils.stringToInt(
            (
                    chapterNamePattern1.find(chapterName1)?.groups?.get(1)?.value
                        ?: chapterNamePattern2.find(chapterName1)?.groups?.get(1)?.value
                    ) ?: "-1"
        )
    }

    private val regexOther by lazy {
        // 所有非字母数字中日韩文字 CJK区+扩展A-F区
        @Suppress("RegExpDuplicateCharacterInClass")
        return@lazy "[^\\w\\u4E00-\\u9FEF〇\\u3400-\\u4DBF\\u20000-\\u2A6DF\\u2A700-\\u2EBEF]".toRegex()
    }

    @Suppress("RegExpUnnecessaryNonCapturingGroup", "RegExpSimplifiable")
    private val regexB by lazy {
        //章节序号，排除处于结尾的状况，避免将章节名替换为空字串
        return@lazy "^.*?第(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话](?!$)|^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、](?!$)|\\.(?=[^\\d]))".toRegex()
    }

    private val regexC by lazy {
        //前后附加内容，整个章节名都在括号中时只剔除首尾括号，避免将章节名替换为空字串
        return@lazy "(?!^)(?:[〖【《〔\\[{(][^〖【《〔\\[{()〕》》】〗\\]}]+)?[)〕》》】〗\\]}]$|^[〖【《〔\\[{(](?:[^〖【《〔\\[{()〕》》】〗\\]}]+[〕》》】〗\\]})])?(?!$)".toRegex()
    }

    private fun getPureChapterName(chapterName: String?): String {
        return if (chapterName == null) "" else StringUtils.fullToHalf(chapterName)
            .replace(regexA, "")
            .replace(regexB, "")
            .replace(regexC, "")
            .replace(regexOther, "")
    }

}
