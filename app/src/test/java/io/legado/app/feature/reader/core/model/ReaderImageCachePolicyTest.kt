package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageCachePolicyTest {
    @Test
    fun `source invalidation covers every decoded size but not a prefix collision`() {
        val source = "https://example.org/image.png"

        assertTrue(ReaderImageCachePolicy.belongsToSource("$source|100x200", source))
        assertTrue(ReaderImageCachePolicy.belongsToSource("$source|300x400", source))
        assertFalse(ReaderImageCachePolicy.belongsToSource("${source}2|100x200", source))
    }

    @Test
    fun `invalidated generations cannot share a cache identity`() {
        val base = "https://example.org/image.png|100x200"

        assertNotEquals(
            ReaderImageCachePolicy.withGeneration(base, 0L),
            ReaderImageCachePolicy.withGeneration(base, 1L),
        )
    }

    @Test
    fun `decoded byte estimate keeps the downsample headroom`() {
        val bounds = ReaderRect(0f, 0f, 1000f, 500f)

        assertEquals(1000L * 500L * 16L, ReaderImageCachePolicy.estimatedDecodedBytes(bounds))
    }

    @Test
    fun `window capacity covers every image and never exceeds the heap limit`() {
        val base = 32 * 1024 * 1024
        val limit = 128 * 1024 * 1024
        val small = ReaderRect(0f, 0f, 100f, 100f)
        val large = ReaderRect(0f, 0f, 1000f, 1000f)
        val largeBytes = ReaderImageCachePolicy.estimatedDecodedBytes(large).toInt()

        assertEquals(base, ReaderImageCachePolicy.targetCapacityBytes(emptyList(), base, limit))
        // 纯文字窗口不要求扩容：小图加起来也够不着基础容量
        assertEquals(base, ReaderImageCachePolicy.targetCapacityBytes(listOf(small, small), base, limit))
        assertEquals(
            largeBytes * 3,
            ReaderImageCachePolicy.targetCapacityBytes(List(3) { large }, base, limit),
        )
        assertEquals(limit, ReaderImageCachePolicy.targetCapacityBytes(List(20) { large }, base, limit))
    }
}
