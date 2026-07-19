package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceLoadFailureCacheTest {

    @Test
    fun `同一失效资源只尝试加载一次`() {
        val cache = ResourceLoadFailureCache<String>()
        var attempts = 0

        repeat(3) {
            val result = cache.load("missing") {
                attempts++
                null
            }
            assertNull(result)
        }

        assertEquals(1, attempts)
    }
}
