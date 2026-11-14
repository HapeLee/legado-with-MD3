package io.legado.app.ui.rss.article

import io.legado.app.data.entities.RssArticle

/**
 * 订阅文章页跨 Compose 组合重建保留的界面状态，由 [RssArticlesViewModel] 持有。
 *
 * `loadedKey` 表示某个「源 + 分类 + 搜索词」已经完成过一次加载，返回列表或切回已看过的
 * 分类时不再重复请求；`pendingKey` 防止加载进行中组合重建造成并发重复请求。只有加载成功
 * 才记为已完成，失败允许下次进入重试。文章快照和滚动位置用于新组合的第一帧，避免 Room
 * Flow 重新订阅时先发射空列表把 Lazy state 夹回第一条目。
 */
internal class RssArticlesRestoreState {

    private var loadedKey: String? = null
    private var pendingKey: String? = null

    var articles: List<RssArticle> = emptyList()
        private set

    var scrollIndex: Int = 0
        private set

    var scrollOffset: Int = 0
        private set

    fun requestAutoLoad(key: String): Boolean {
        if (key == loadedKey || key == pendingKey) return false
        pendingKey = key
        return true
    }

    fun markLoaded(key: String) {
        loadedKey = key
        if (pendingKey == key) pendingKey = null
    }

    fun markFailed(key: String) {
        if (pendingKey == key) pendingKey = null
    }

    fun rememberArticles(articles: List<RssArticle>) {
        this.articles = articles
    }

    fun rememberScrollPosition(index: Int, offset: Int) {
        scrollIndex = index.coerceAtLeast(0)
        scrollOffset = offset.coerceAtLeast(0)
    }
}
