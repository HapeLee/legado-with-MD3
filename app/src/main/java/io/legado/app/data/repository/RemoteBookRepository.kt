package io.legado.app.data.repository

import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.lib.webdav.Authorization
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.RemoteBookWebDav
import kotlinx.coroutines.flow.Flow

class RemoteBookRepository(
    private val appDb: AppDatabase
) {

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
