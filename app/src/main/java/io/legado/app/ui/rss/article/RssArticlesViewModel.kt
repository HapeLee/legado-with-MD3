package io.legado.app.ui.rss.article

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.model.rss.Rss
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RssArticlesViewModel(application: Application) : BaseViewModel(application) {

    private val _loadState = MutableStateFlow(RssArticlesLoadState())
    val loadState: StateFlow<RssArticlesLoadState> = _loadState.asStateFlow()

    var order = System.currentTimeMillis()
    private var nextPageUrl: String? = null
    var sortName: String = ""
    var sortUrl: String = ""
    var page = 1

    fun init(bundle: Bundle?) {
        bundle?.let {
            init(
                sortName = it.getString("sortName") ?: "",
                sortUrl = it.getString("sortUrl") ?: ""
            )
        }
    }

    fun init(sortName: String, sortUrl: String) {
        if (this.sortName == sortName && this.sortUrl == sortUrl) return
        this.sortName = sortName
        this.sortUrl = sortUrl
        page = 1
        nextPageUrl = null
        _loadState.value = RssArticlesLoadState()
    }

    fun loadArticles(rssSource: RssSource) {
        _loadState.value = _loadState.value.copy(
            isRefreshing = true,
            isLoadingMore = false,
            errorMessage = null,
            hasMore = true
        )
        page = 1
        order = System.currentTimeMillis()
        Rss.getArticles(viewModelScope, sortName, sortUrl, rssSource, page).onSuccess(IO) {
            nextPageUrl = it.second
            val articles = it.first
            articles.forEach { rssArticle ->
                rssArticle.order = order--
            }
            appDb.rssArticleDao.insert(*articles.toTypedArray())
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                appDb.rssArticleDao.clearOld(rssSource.sourceUrl, sortName, order)
            }
            val hasMore = articles.isNotEmpty() && !rssSource.ruleNextPage.isNullOrEmpty()
            _loadState.value = _loadState.value.copy(
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = hasMore,
                errorMessage = null
            )
        }.onError {
            AppLog.put("rss获取内容失败", it)
            _loadState.value = _loadState.value.copy(
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = it.stackTraceStr
            )
        }
    }

    fun loadMore(rssSource: RssSource) {
        val currentState = _loadState.value
        if (currentState.isRefreshing || currentState.isLoadingMore || !currentState.hasMore) return

        val pageUrl = nextPageUrl
        if (pageUrl.isNullOrEmpty()) {
            _loadState.value = currentState.copy(hasMore = false)
            return
        }

        _loadState.value = currentState.copy(
            isLoadingMore = true,
            errorMessage = null
        )

        page++
        Rss.getArticles(viewModelScope, sortName, pageUrl, rssSource, page).onSuccess(IO) {
            nextPageUrl = it.second
            loadMoreSuccess(it.first)
        }.onError {
            AppLog.put("rss获取内容失败", it)
            if (page > 1) page--
            _loadState.value = _loadState.value.copy(
                isLoadingMore = false,
                errorMessage = it.stackTraceStr
            )
        }
    }

    private fun loadMoreSuccess(articles: MutableList<RssArticle>) {
        if (articles.isEmpty()) {
            _loadState.value = _loadState.value.copy(
                isLoadingMore = false,
                hasMore = false
            )
            return
        }
        val firstArticle = articles.first()
        val dbFirstArticle = appDb.rssArticleDao.getByLink(firstArticle.origin, firstArticle.link)
        val lastArticle = articles.last()
        val dbLastArticle = appDb.rssArticleDao.getByLink(lastArticle.origin, lastArticle.link)

        val shouldStop = dbFirstArticle != null && dbLastArticle != null
        if (shouldStop) {
            _loadState.value = _loadState.value.copy(
                isLoadingMore = false,
                hasMore = false
            )
            return
        }

        articles.forEach {
            it.order = order--
        }
        appDb.rssArticleDao.append(*articles.toTypedArray())

        _loadState.value = _loadState.value.copy(
            isLoadingMore = false,
            hasMore = !nextPageUrl.isNullOrBlank(),
            errorMessage = null
        )
    }
}

data class RssArticlesLoadState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
) {
    val canLoadMore: Boolean
        get() = hasMore && !isRefreshing && !isLoadingMore && errorMessage == null
}
