package io.legado.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CleanSelectedTextUseCaseTest {

    @Test
    fun `parses replacement JSON`() {
        assertEquals(
            "正确文字",
            parseCleanReplacement("""{"replacement":"正确文字"}"""),
        )
    }

    @Test
    fun `allows empty replacement for deletion`() {
        assertEquals(
            "",
            parseCleanReplacement(
                """
                ```json
                {"replacement":""}
                ```
                """.trimIndent()
            ),
        )
    }

    @Test
    fun `rejects response without replacement field`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCleanReplacement("""{"text":"错误字段"}""")
        }
    }
}
