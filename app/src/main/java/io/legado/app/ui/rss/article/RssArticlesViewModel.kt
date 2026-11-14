package io.legado.app.ui.rss.article

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssArticleRepository
import io.legado.app.model.rss.Rss
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RssArticlesViewModel(
    application: Application,
    private val repository: RssArticleRepository,
) : BaseViewModel(application) {

    private val _loadState = MutableStateFlow(RssArticlesLoadState())
    val loadState: StateFlow<RssArticlesLoadState> = _loadState.asStateFlow()

    private val restoreState = RssArticlesRestoreState()
    val cachedArticles: List<RssArticle> get() = restoreState.articles
    val scrollIndex: Int get() = restoreState.scrollIndex
    val scrollOffset: Int get() = restoreState.scrollOffset

    fun cacheArticles(articles: List<RssArticle>) = restoreState.rememberArticles(articles)

    fun saveScrollPosition(index: Int, offset: Int) =
        restoreState.rememberScrollPosition(index, offset)

    var order = System.currentTimeMillis()
    private var nextPageUrl: String? = null
    var sortName: String = ""
    var sortUrl: String = ""
    var searchKey: String? = null
    var page = 1

    fun init(bundle: Bundle?) {
        bundle?.let {
            init(
                sortName = it.getString("sortName") ?: "",
                sortUrl = it.getString("sortUrl") ?: "",
                searchKey = it.getString("searchKey")
            )
        }
    }

    fun init(sortName: String, sortUrl: String, searchKey: String? = null) {
        if (this.sortName == sortName && this.sortUrl == sortUrl && this.searchKey == searchKey) return
        this.sortName = sortName
        this.sortUrl = sortUrl
        this.searchKey = searchKey
        page = 1
        nextPageUrl = null
        _loadState.value = RssArticlesLoadState()
    }

    /** 进入或返回列表时使用：同一个「源 + 分类 + 搜索词」只自动拉取一次。 */
    fun loadArticlesIfNeeded(rssSource: RssSource) {
        if (restoreState.requestAutoLoad(autoLoadKey(rssSource))) {
            loadArticles(rssSource)
        }
    }

    private fun autoLoadKey(rssSource: RssSource) = listOf(
        rssSource.sourceUrl,
        sortName,
        sortUrl,
        searchKey.orEmpty(),
    ).joinToString("|")

    /** 下拉刷新、加载失败重试等用户主动操作仍走强制刷新。 */
    fun loadArticles(rssSource: RssSource) {
        val loadKey = autoLoadKey(rssSource)
        _loadState.value = _loadState.value.copy(
            isRefreshing = true,
            isLoadingMore = false,
            errorMessage = null,
            hasMore = true
        )
        page = 1
        order = System.currentTimeMillis()
        Rss.getArticles(viewModelScope, sortName, sortUrl, rssSource, page, searchKey).onSuccess(IO) {
            nextPageUrl = it.second
            val articles = it.first
            articles.forEach { rssArticle ->
                rssArticle.order = order--
            }
            repository.insert(*articles.toTypedArray())
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                repository.clearOld(rssSource.sourceUrl, sortName, order)
            }
            val hasMore = articles.isNotEmpty() && !rssSource.ruleNextPage.isNullOrEmpty()
            _loadState.value = _loadState.value.copy(
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = hasMore,
                errorMessage = null
            )
            restoreState.markLoaded(loadKey)
        }.onError {
            AppLog.put("rss获取内容失败", it)
            _loadState.value = _loadState.value.copy(
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = it.stackTraceStr
            )
            restoreState.markFailed(loadKey)
        }
    }

    fun loadMore(rssSource: RssSource) {
        val currentState = _loadState.value
        if (currentState.isRefreshing || currentState.isLoadingMore || !currentState.hasMore) return

        val pageUrl = nextPageUrl
        if (pageUrl.isNullOrEmpty()) {
            return
        }

        _loadState.value = currentState.copy(
            isLoadingMore = true,
            errorMessage = null
        )

        page++
        Rss.getArticles(viewModelScope, sortName, pageUrl, rssSource, page, searchKey).onSuccess(IO) {
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

    private suspend fun loadMoreSuccess(articles: MutableList<RssArticle>) {
        if (articles.isEmpty()) {
            _loadState.value = _loadState.value.copy(
                isLoadingMore = false,
                hasMore = false
            )
            return
        }
        val firstArticle = articles.first()
        val dbFirstArticle = repository.findByLink(firstArticle.origin, firstArticle.link)
        val lastArticle = articles.last()
        val dbLastArticle = repository.findByLink(lastArticle.origin, lastArticle.link)

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
        repository.append(*articles.toTypedArray())

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
