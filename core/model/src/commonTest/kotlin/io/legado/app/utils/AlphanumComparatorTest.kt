package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphanumComparatorTest {

    @Test
    fun `sorts numeric filename chunks by numeric magnitude`() {
        val sorted = listOf("chapter10.txt", "chapter2.txt", "chapter1.txt")
            .sortedWith(AlphanumComparator)

        assertEquals(listOf("chapter1.txt", "chapter2.txt", "chapter10.txt"), sorted)
    }

    @Test
    fun `retains leading-zero ordering from the original comparator`() {
        assertTrue(AlphanumComparator.compare("page02", "page2") > 0)
        assertTrue(AlphanumComparator.compare("page2", "page02") < 0)
    }

    @Test
    fun `handles equal and empty names without reading a chunk`() {
        assertEquals(0, AlphanumComparator.compare("", ""))
        assertTrue(AlphanumComparator.compare("", "chapter1") < 0)
        assertTrue(AlphanumComparator.compare("chapter1", "") > 0)
    }
}
