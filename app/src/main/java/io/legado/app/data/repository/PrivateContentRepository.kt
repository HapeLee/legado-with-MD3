package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.domain.gateway.PrivateContentGateway
import io.legado.app.domain.model.PrivateBookFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 私密书籍标记的数据实现。
 *
 * 只负责"单本标记"这一半；"所属分组私密"那一半由书架按 group 掩码在内存侧求并集，
 * 这样无需改动 BookDao 里十几处 BookShelfItem 投影。
 */
class PrivateContentRepository(
    private val bookDao: BookDao,
) : PrivateContentGateway {

    override fun flowPrivateBookUrls(): Flow<Set<String>> = bookDao.flowPrivateBookUrls()
        .map { it.toSet() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override suspend fun factsOf(bookUrl: String): PrivateBookFacts =
        withContext(Dispatchers.IO) { bookDao.privateFacts(bookUrl) }

    override suspend fun factsOfByName(name: String, author: String): PrivateBookFacts? =
        withContext(Dispatchers.IO) {
            val book = bookDao.getBook(name, author) ?: return@withContext null
            bookDao.privateFacts(book.bookUrl)
        }

    override suspend fun setBooksPrivate(bookUrls: Set<String>, isPrivate: Boolean) {
        if (bookUrls.isEmpty()) return
        withContext(Dispatchers.IO) { bookDao.setBooksPrivate(bookUrls, isPrivate) }
    }
}
