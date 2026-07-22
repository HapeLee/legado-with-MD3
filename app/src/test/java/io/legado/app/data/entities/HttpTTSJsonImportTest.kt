package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTTSJsonImportTest {

    @Test
    fun `导入保留登录脚本依赖`() {
        val source = """
            {
              "id": 1,
              "name": "测试引擎",
              "url": "https://example.com/tts",
              "loginUi": "@js:styleM()",
              "jsLib": "function styleM() { return 'ok' }",
              "enabledCookieJar": true
            }
        """.trimIndent()

        val result = HttpTTS.fromJson(source).getOrThrow()

        assertEquals("function styleM() { return 'ok' }", result.jsLib)
        assertEquals(true, result.enabledCookieJar)
    }
}
