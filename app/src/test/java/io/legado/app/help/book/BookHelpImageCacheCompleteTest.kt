package io.legado.app.help.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookHelpImageCacheCompleteTest {

    @Test
    fun completeOnlyWhenNoFailuresAndFilesCached() {
        assertTrue(isChapterImageCacheComplete(failures = 0, filesCached = true))
        assertFalse(isChapterImageCacheComplete(failures = 1, filesCached = true))
        assertFalse(isChapterImageCacheComplete(failures = 0, filesCached = false))
        assertFalse(isChapterImageCacheComplete(failures = 2, filesCached = false))
    }

    /** #2593：气泡地址没有远程资源，缓存链路必须跳过，否则整章被判「图片缓存未完成」。 */
    @Test
    fun paragraphBubbleSourcesAreSkippedByImageCache() {
        assertTrue(isParagraphBubbleImageSrc("dp:4,{\"click\":\"jjParaOpen(2416557,2,1,4)\"}"))
        assertTrue(isParagraphBubbleImageSrc("  DP:12  "))
        assertTrue(isParagraphBubbleImageSrc("dp:"))
        assertTrue(isParagraphBubbleImageSrc("bubble://paragraph?num=3"))
        assertTrue(isParagraphBubbleImageSrc("BUBBLE://paragraph?displayText=7"))
    }

    @Test
    fun regularImageSourcesStayCacheable() {
        assertFalse(isParagraphBubbleImageSrc("https://example.com/a.png"))
        assertFalse(isParagraphBubbleImageSrc("data:image/png;base64,AAAA"))
        assertFalse(isParagraphBubbleImageSrc("/images/a.jpg"))
        assertFalse(isParagraphBubbleImageSrc("dp"))
        assertFalse(isParagraphBubbleImageSrc(""))
    }
}
