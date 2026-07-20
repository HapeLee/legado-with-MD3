package io.legado.app.data.repository

import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.RssArticleDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.dao.RssStarDao
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssSourceEditRepository(
    private val sourceDao: RssSourceDao,
    private val starDao: RssStarDao,
    private val articleDao: RssArticleDao,
    private val cacheDao: CacheDao,
) {

    suspend fun findByUrl(sourceUrl: String): RssSource? = withContext(Dispatchers.IO) {
        sourceDao.getByKey(sourceUrl)
    }

    suspend fun save(oldSource: RssSource?, source: RssSource): Boolean =
        withContext(Dispatchers.IO) {
            val originChanged = oldSource != null && oldSource.sourceUrl != source.sourceUrl
            oldSource?.let { sourceDao.delete(it) }
            if (originChanged) {
                starDao.updateOrigin(source.sourceUrl, oldSource.sourceUrl)
                articleDao.updateOrigin(source.sourceUrl, oldSource.sourceUrl)
                cacheDao.deleteSourceVariables(oldSource.sourceUrl)
            }
            sourceDao.insert(source)
            originChanged
        }
}
