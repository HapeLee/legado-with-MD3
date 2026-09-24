package io.legado.app.ui.main.bookshelf

import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.widget.components.image.cover.DefaultCoverPath
import io.legado.app.ui.widget.components.image.cover.bookshelfCoverMemoryCacheKey
import io.legado.app.ui.widget.components.image.cover.isDefaultCoverPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预热只有在“内存缓存键与卡片请求完全一致”时才有意义——键一旦漂移，预热写进去的是
 * 另一个条目，书架依旧要重新读盘解码（也就是入口处那片灰底）。这里把这条不变量钉住。
 */
class BookshelfCoverPreloadTest {

    @Test
    fun memoryKey_matchesShelfCardRequestKey() {
        assertEquals(
            "${bookCoverSharedElementKey("bookUrl", "bookshelf:7")}:cover:https://cover",
            bookshelfCoverMemoryCacheKey(groupId = 7L, bookUrl = "bookUrl", coverPath = "https://cover"),
        )
    }

    @Test
    fun preloadTargets_carryCardKeySizeAndOrigin() {
        val targets = buildCoverPreloadTargets(
            groupId = 7L,
            candidates = listOf(
                CoverPreloadCandidate("b1", "https://c1", "origin"),
                CoverPreloadCandidate("b2", "https://c2", null),
            ),
            coverWidthDp = 120,
            density = 3f,
            limit = 24,
        )

        assertEquals(2, targets.size)
        assertEquals(
            "${bookCoverSharedElementKey("b1", "bookshelf:7")}:cover:https://c1",
            targets[0].memoryCacheKey,
        )
        // 120dp 列宽 - 左右各 4dp 内边距 = 112dp；3x 密度 → 336px；高宽比 5:7 → 470px
        assertEquals(336, targets[0].widthPx)
        assertEquals(470, targets[0].heightPx)
        assertEquals("origin", targets[0].sourceOrigin)
        assertEquals("b1", targets[0].bookUrl)
    }

    @Test
    fun preloadTargets_respectFirstScreenLimit() {
        val candidates = (1..30).map { CoverPreloadCandidate("b$it", "https://c$it", null) }

        assertEquals(24, buildCoverPreloadTargets(7L, candidates, 120, 3f, limit = 24).size)
        assertEquals(12, buildCoverPreloadTargets(7L, candidates, 120, 3f, limit = 12).size)
    }

    @Test
    fun candidate_keepsOnlyBooksWithRealCoverAddress() {
        assertNull(book("b", coverUrl = null).toCoverPreloadCandidate())
        assertNull(book("b", coverUrl = "  ").toCoverPreloadCandidate())
        assertNull(book("b", coverUrl = DefaultCoverPath).toCoverPreloadCandidate())

        assertEquals("https://cover", book("b", coverUrl = "https://cover").toCoverPreloadCandidate()?.coverPath)
        // 自定义封面优先于书源封面，与卡片一致
        assertEquals(
            "file://custom",
            book("b", coverUrl = "https://cover", customCoverUrl = "file://custom")
                .toCoverPreloadCandidate()?.coverPath,
        )
    }

    @Test
    fun defaultCoverPath_isRecognisedWithoutComposeConfig() {
        assertEquals(true, isDefaultCoverPath(DefaultCoverPath))
        assertEquals(true, isDefaultCoverPath(null))
        assertEquals(false, isDefaultCoverPath("https://cover"))
    }

    private fun book(
        bookUrl: String,
        coverUrl: String?,
        customCoverUrl: String? = null,
        origin: String = "source",
    ) = BookShelfItem(
        bookUrl = bookUrl,
        name = bookUrl,
        author = "author",
        origin = origin,
        originName = "originName",
        coverUrl = coverUrl,
        customCoverUrl = customCoverUrl,
        durChapterTitle = null,
        durChapterTime = 0L,
        durChapterPos = 0,
        latestChapterTitle = null,
        latestChapterTime = 0L,
        lastCheckCount = 0,
        totalChapterNum = 0,
        durChapterIndex = 0,
        type = 0,
        group = 0L,
        order = 0,
    )
}
