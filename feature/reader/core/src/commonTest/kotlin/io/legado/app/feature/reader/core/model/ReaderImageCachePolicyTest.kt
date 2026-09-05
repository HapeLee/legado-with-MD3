package io.legado.app.feature.reader.core.model

import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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
}
