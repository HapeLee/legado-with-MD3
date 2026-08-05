package io.legado.app.help.update

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class AppUpdateGitHubTest {

    @Test
    fun `primary rate limit reports reset time`() {
        val error = githubApiException(
            code = 403,
            headers = headers(
                "X-RateLimit-Remaining", "0",
                "X-RateLimit-Reset", "1783936049"
            ),
            body = "",
            zoneId = ZoneOffset.UTC
        )

        assertEquals(
            "GitHub API 请求额度已用完，请在 2026-07-13 09:47 后重试",
            error.message
        )
    }

    @Test
    fun `secondary rate limit honors retry after`() {
        val error = githubApiException(
            code = 429,
            headers = headers("Retry-After", "90"),
            body = ""
        )

        assertEquals("GitHub API 请求受限，请在 90 秒后重试", error.message)
    }

    @Test
    fun `other api errors preserve github message`() {
        val error = githubApiException(
            code = 500,
            headers = headers(),
            body = """{"message":"Internal Server Error"}"""
        )

        assertEquals("获取新版本出错(500): Internal Server Error", error.message)
    }

    private fun headers(vararg namesAndValues: String): Headers {
        return Headers.Builder().apply {
            namesAndValues.asList().chunked(2).forEach { (name, value) ->
                add(name, value)
            }
        }.build()
    }
}
