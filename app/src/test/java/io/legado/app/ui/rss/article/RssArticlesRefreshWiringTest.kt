package io.legado.app.ui.rss.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 议题 #2598 的接线契约：自动进入列表走「只加载一次」入口，用户主动下拉仍是强制刷新；
 * 组合重建时用 ViewModel 里的文章快照与滚动位置恢复，不让 Room Flow 的第一帧把列表清空。
 *
 * 静态契约只保证接线不被改回，不证明真机上的网络与滚动行为。
 */
class RssArticlesRefreshWiringTest {

    @Test
    fun automaticEntryLoadsOnlyOnceWhilePullToRefreshStaysForced() {
        val source = sourceOf(SCREEN)
        val automatic = source.substringAfter("LaunchedEffect(rssSource?.sourceUrl, sortName, sortUrl)")
            .substringBefore("LaunchedEffect(loadState.errorMessage)")

        assertTrue("进入列表仍会无条件重新拉取订阅源", automatic.contains("loadArticlesIfNeeded"))
        assertFalse("自动入口不应直接调强制刷新", automatic.contains("viewModel::loadArticles)"))
        assertTrue(
            "下拉刷新必须保留强制刷新",
            source.contains("onRefresh = { rssSource?.let(viewModel::loadArticles) }"),
        )
    }

    @Test
    fun restoredArticlesAndScrollPositionSurviveRecomposition() {
        val source = sourceOf(SCREEN)

        assertTrue(
            "重新订阅 Room Flow 时不能用空列表覆盖快照",
            source.contains("initialValue = viewModel.cachedArticles"),
        )
        assertTrue("文章快照未写回 ViewModel", source.contains("viewModel.cacheArticles(articles)"))
        assertEquals(
            "三种布局都要用保存的位置创建 Lazy state",
            3,
            Regex("initialFirstVisibleItemIndex = viewModel\\.scrollIndex").findAll(source).count(),
        )
        assertEquals(
            "三种布局都要回写滚动位置",
            3,
            Regex("TrackRssScrollPosition\\(viewModel\\)").findAll(source).count(),
        )
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/rss/article/RssArticlesCompose.kt"
    }
}
