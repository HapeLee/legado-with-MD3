package io.legado.app.ui.rss.article

import io.legado.app.data.entities.RssArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 议题 #2598 的行为基线：从文章返回列表、或切回已看过的分类时，不应重新发起订阅源请求，
 * 也不应把列表滚动位置丢回第一条目。
 *
 * [RssArticlesRestoreState] 由 ViewModel 持有，跨 Compose 组合重建存活；这里只测它的
 * 决策与恢复语义，网络请求与 Room Flow 仍由真机路径验证。
 */
class RssArticlesRestoreStateTest {

    @Test
    fun `首次进入允许自动加载，返回同一分类不再加载`() {
        val state = RssArticlesRestoreState()

        assertTrue(state.requestAutoLoad(KEY))
        state.markLoaded(KEY)

        assertFalse(state.requestAutoLoad(KEY))
        assertFalse(state.requestAutoLoad(KEY))
    }

    @Test
    fun `加载进行中重复进入组合不会并发再请求`() {
        val state = RssArticlesRestoreState()

        assertTrue(state.requestAutoLoad(KEY))
        assertFalse(state.requestAutoLoad(KEY))
    }

    @Test
    fun `加载失败后允许下次进入重试`() {
        val state = RssArticlesRestoreState()

        assertTrue(state.requestAutoLoad(KEY))
        state.markFailed(KEY)

        assertTrue(state.requestAutoLoad(KEY))
    }

    @Test
    fun `下拉强制刷新成功后也不再自动加载`() {
        val state = RssArticlesRestoreState()

        state.markLoaded(KEY)

        assertFalse(state.requestAutoLoad(KEY))
    }

    @Test
    fun `切换分类或搜索词可以重新自动加载`() {
        val state = RssArticlesRestoreState()

        state.markLoaded(KEY)

        assertTrue(state.requestAutoLoad("https://a.test|分类二|https://a.test/2|"))
        assertTrue(state.requestAutoLoad("https://a.test|分类一|https://a.test/1|关键词"))
        assertFalse(state.requestAutoLoad(KEY))
    }

    @Test
    fun `失败不会抹掉此前成功的加载记录`() {
        val state = RssArticlesRestoreState()

        assertTrue(state.requestAutoLoad(KEY))
        state.markLoaded(KEY)
        state.markFailed(KEY)

        assertFalse(state.requestAutoLoad(KEY))
    }

    @Test
    fun `文章快照与滚动位置在组合重建后可读`() {
        val state = RssArticlesRestoreState()
        val articles = listOf(
            RssArticle(origin = "https://a.test", link = "1", sort = "分类一"),
            RssArticle(origin = "https://a.test", link = "2", sort = "分类一"),
        )

        state.rememberArticles(articles)
        state.rememberScrollPosition(12, 80)

        assertSame(articles, state.articles)
        assertEquals(12, state.scrollIndex)
        assertEquals(80, state.scrollOffset)
    }

    @Test
    fun `滚动位置不接受负值`() {
        val state = RssArticlesRestoreState()

        state.rememberScrollPosition(-3, -20)

        assertEquals(0, state.scrollIndex)
        assertEquals(0, state.scrollOffset)
    }

    private companion object {
        const val KEY = "https://a.test|分类一|https://a.test/1|"
    }
}
