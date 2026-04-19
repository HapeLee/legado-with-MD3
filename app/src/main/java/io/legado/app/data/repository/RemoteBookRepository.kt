package io.legado.app.data.repository

import io.legado.app.constant.AppConst
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isLocal
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.lib.webdav.Authorization
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.isContentScheme
import kotlinx.coroutines.flow.Flow

class RemoteBookRepository(
    private val appDb: AppDatabase
) {

    suspend fun getWebDav(book: Book): RemoteBookWebDav? {
        val remoteUrl = book.getRemoteUrl()
        val serverId = remoteUrl?.let { AnalyzeUrl(it).serverID }

        if (serverId != null && serverId != AppConst.DEFAULT_WEBDAV_ID) {
            appDb.serverDao.get(serverId)?.getWebDavConfig()?.let {
                return RemoteBookWebDav(it.url, Authorization(it), serverId)
            }
        }

        val currentServerId = ImportBookConfig.remoteServerId
        if (currentServerId != AppConst.DEFAULT_WEBDAV_ID) {
            appDb.serverDao.get(currentServerId)?.getWebDavConfig()?.let {
                return RemoteBookWebDav(it.url, Authorization(it), currentServerId)
            }
        }

        return AppWebDav.defaultBookWebDav
    }

    suspend fun refreshLocalBook(book: Book): Boolean {
        if (!book.isLocal) return false
        val remoteUrl = book.getRemoteUrl() ?: return false
        val webDav = getWebDav(book) ?: throw NoStackTraceException("webDav没有配置")
        val remoteBook = webDav.getRemoteBook(remoteUrl)
        if (remoteBook == null) {
            book.origin = BookType.localTag
            return true
        }
        if (remoteBook.lastModify > book.lastCheckTime) {
            val uri = webDav.downloadRemoteBook(remoteBook)
            book.bookUrl = if (uri.isContentScheme()) uri.toString() else uri.path!!
            book.lastCheckTime = remoteBook.lastModify
            return true
        }
        return false
    }

    suspend fun syncBookFromRemote(book: Book): Book {
        val remoteUrl = book.getRemoteUrl() ?: throw NoStackTraceException("不是远程书籍")
        val webDav = getWebDav(book) ?: throw NoStackTraceException("webDav没有配置")
        val remoteBook =
            webDav.getRemoteBook(remoteUrl) ?: throw NoStackTraceException("远程文件不存在")
        val downloadBookUri = webDav.downloadRemoteBook(remoteBook)
        val importedBooks = LocalBook.importFiles(downloadBookUri)
        val newBook = importedBooks.firstOrNull() ?: throw NoStackTraceException("导入失败")
        newBook.durChapterIndex = book.durChapterIndex
        newBook.durChapterPos = book.durChapterPos
        newBook.order = book.order
        newBook.group = book.group
        return newBook
    }

    suspend fun uploadBook(book: Book) {
        val webDav = getWebDav(book) ?: throw NoStackTraceException("未配置webDav")
        webDav.upload(book)
        book.lastCheckTime = System.currentTimeMillis()
    }

    suspend fun createWebDav(serverId: Long): RemoteBookWebDav? {
        return appDb.serverDao.get(serverId)
            ?.getWebDavConfig()
            ?.let {
                val authorization = Authorization(it)
                RemoteBookWebDav(it.url, authorization, serverId)
            }
    }

    suspend fun loadBooks(
        webDav: RemoteBookWebDav,
        path: String?
    ): List<RemoteBook> {
        val url = path ?: webDav.rootBookUrl
        return webDav.getRemoteBookList(url)
    }

    suspend fun downloadBook(
        webDav: RemoteBookWebDav,
        remoteBook: RemoteBook
    ) = webDav.downloadRemoteBook(remoteBook)

    suspend fun importRemoteBookToShelf(webDav: RemoteBookWebDav, remoteBook: RemoteBook): Book? {
        val downloadBookUri = downloadBook(webDav, remoteBook)
        val books = LocalBook.importFiles(downloadBookUri)
        val book = books.firstOrNull()
        book?.apply {
            origin = BookType.webDavTag + CustomUrl(remoteBook.path)
                .putAttribute("serverID", webDav.serverID)
                .toString()
            save()
        }
        return book
    }

    fun flowLocalBooks(): Flow<List<Book>> {
        return appDb.bookDao.flowLocal()
    }

}
