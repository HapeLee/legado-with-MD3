package io.legado.app.data.repository

import io.legado.app.data.dao.RssArticleDao
import io.legado.app.data.entities.RssArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssArticleRepository(
    private val dao: RssArticleDao,
) {

    suspend fun findByLink(origin: String, link: String): RssArticle? =
        withContext(Dispatchers.IO) {
            dao.getByLink(origin, link)
        }

    suspend fun insert(vararg articles: RssArticle) = withContext(Dispatchers.IO) {
        dao.insert(*articles)
    }

    suspend fun append(vararg articles: RssArticle) = withContext(Dispatchers.IO) {
        dao.append(*articles)
    }

    suspend fun clearOld(origin: String, sort: String, order: Long) =
        withContext(Dispatchers.IO) {
            dao.clearOld(origin, sort, order)
        }

    suspend fun deleteByOrigin(origin: String) = withContext(Dispatchers.IO) {
        dao.delete(origin)
    }
}
