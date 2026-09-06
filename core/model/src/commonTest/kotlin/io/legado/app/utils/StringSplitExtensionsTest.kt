package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals

class StringSplitExtensionsTest {

    @Test
    fun `vararg delimiters trim and discard blank fields`() {
        assertContentEquals(
            arrayOf("first", "second", "third"),
            " first, ,second; third ".splitNotBlank(",", ";"),
        )
    }

    @Test
    fun `regex delimiter follows the same trimming behavior`() {
        assertContentEquals(
            arrayOf("one", "two", "three"),
            " one | two || three ".splitNotBlank(Regex("\\|")),
        )
    }

    @Test
    fun `limit is applied before blank fields are filtered`() {
        assertContentEquals(
            arrayOf("first", "second, third"),
            " first, second, third ".splitNotBlank(",", limit = 2),
        )
    }
}
