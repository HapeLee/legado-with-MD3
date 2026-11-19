package io.legado.app.ui.rss.read

import io.legado.app.data.entities.RssArticle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读页「播放视频」入口的可观察条件。
 *
 * 列表解析不会写 `RssArticle.type`，所以入口必须以订阅源类型为准；文章自带的视频标记
 * 只用于兼容收藏/历史里的旧数据。
 */
class ReadRssVideoEntryTest {

    @Test
    fun videoSourceExposesEntryEvenWhenArticleTypeIsDefault() {
        assertTrue(canPlayRssVideo(sourceType = 2, article = article()))
    }

    @Test
    fun sourceTypeIdentifiesVideoSource() {
        assertTrue(isRssVideoSource(2))
        assertFalse(isRssVideoSource(0))
        assertFalse(isRssVideoSource(null))
    }

    @Test
    fun legacyArticleVideoMarkStillWorks() {
        assertTrue(canPlayRssVideo(sourceType = 0, article = article(type = 2)))
    }

    @Test
    fun webSourceAndPlainArticleHideEntry() {
        assertFalse(canPlayRssVideo(sourceType = 0, article = article()))
        assertFalse(canPlayRssVideo(sourceType = null, article = article()))
    }

    @Test
    fun noLoadedArticleHidesEntry() {
        assertFalse(canPlayRssVideo(sourceType = 2, article = null))
    }

    private fun article(type: Int = 0) = RssArticle(
        origin = "https://rss.test",
        link = "https://rss.test/article",
        sort = "sort",
        title = "RSS 视频",
        type = type,
    )
}
