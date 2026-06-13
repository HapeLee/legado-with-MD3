package io.legado.app.ui.rss.article

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.removeSortCache
import io.legado.app.help.source.sortUrls
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

class RssSortViewModel(application: Application) : BaseViewModel(application) {
    var url: String? = null
    var rssSource: RssSource? = null
    val titleLiveData = MutableLiveData<String>()
    var order = System.currentTimeMillis()
    val isGridLayout get() = rssSource?.articleStyle == 2
    val isWaterLayout get() = rssSource?.articleStyle == 3

    fun initDataSource(sourceUrl: String?) {
        url = sourceUrl
        url?.let { src ->
            rssSource = appDb.rssSourceDao.getByKey(src)
            rssSource?.let {
                titleLiveData.postValue(it.sourceName)
            } ?: run {
                rssSource = RssSource(sourceUrl = src)
            }
        }
    }

    suspend fun loadSorts(sortUrl: String? = null, searchKey: String? = null): List<Pair<String, String>> {
        if (searchKey != null) {
            return rssSource?.searchUrl?.takeIf { it.isNotBlank() }?.let {
                listOf("搜索" to it)
            }.orEmpty()
        }
        sortUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return parseSortUrl(url)
        }
        return rssSource?.sortUrls().orEmpty()
    }

    private fun parseSortUrl(sortUrl: String): List<Pair<String, String>> {
        return try {
            if (sortUrl.isJsonObject()) {
                GSONStrict.fromJsonObject<Map<String, String>>(sortUrl)
                    .getOrThrow()
                    .map { it.key to it.value }
            } else {
                listOf("" to sortUrl)
            }
        } catch (_: Exception) {
            listOf("" to sortUrl)
        }
    }

    fun currentArticleStyle(): Int = rssSource?.articleStyle ?: 0

    fun switchLayout() {
        rssSource?.let {
            if (it.articleStyle < 3) {
                it.articleStyle += 1
            } else {
                it.articleStyle = 0
            }
            execute {
                appDb.rssSourceDao.update(it)
            }
        }
    }

    fun read(rssArticle: RssArticle) {
        execute {
            val rssReadRecord = RssReadRecord(
                record = rssArticle.link,
                title = rssArticle.title,
                readTime = System.currentTimeMillis()
            )
            appDb.rssReadRecordDao.insertRecord(rssReadRecord)
        }
    }

    fun clearArticles() {
        execute {
            url?.let {
                appDb.rssArticleDao.delete(it)
            }
            order = System.currentTimeMillis()
        }
    }

    suspend fun clearSortCache() {
        rssSource?.removeSortCache()
    }

    fun setSourceVariable(variable: String?) {
        rssSource?.setVariable(variable)
    }

    fun getRecords(): List<RssReadRecord> {
        return appDb.rssReadRecordDao.getRecords()
    }

    fun countRecords(): Int {
        return appDb.rssReadRecordDao.countRecords
    }

    fun deleteAllRecord() {
        execute {
            appDb.rssReadRecordDao.deleteAllRecord()
        }
    }

    fun updateRssSourceRedirectPolicy(sourceUrl: String, redirectPolicy: String) {
        execute {
            appDb.rssSourceDao.updateRedirectPolicy(sourceUrl, redirectPolicy)
            rssSource?.redirectPolicy = redirectPolicy
        }.onError {
            appCtx.toastOnUi("保存失败: ${it.localizedMessage}")
        }
    }
}
