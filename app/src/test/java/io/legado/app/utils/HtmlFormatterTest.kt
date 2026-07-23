package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormatterTest {

    @Test
    fun formatDisplayText_removesScriptAndStyleContents() {
        val result = HtmlFormatter.formatDisplayText(
            """
                <style>.intro { color: red; }</style>
                <p>第一段</p>
                <script>window.alert('bad')</script>
                <div>第二段</div>
            """.trimIndent()
        )

        assertTrue(result.contains("第一段"))
        assertTrue(result.contains("第二段"))
        assertFalse(result.contains("color: red"))
        assertFalse(result.contains("window.alert"))
    }

    @Test
    fun formatDisplayText_keepsPlainText() {
        assertEquals("普通简介", HtmlFormatter.formatDisplayText("普通简介"))
    }
}
