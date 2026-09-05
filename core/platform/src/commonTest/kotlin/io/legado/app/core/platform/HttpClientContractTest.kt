package io.legado.app.core.platform

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HttpClient 契约测试：用 MockEngine（无真实网络）证明 [KtorHttpClient] 把
 * [HttpRequest]↔Ktor↔[HttpResponse] 映射正确，在双 target 通过。
 */
class HttpClientContractTest {

    @Test
    fun `GET maps status headers and body`() = runTest {
        val engine = MockEngine { _ ->
            respond("ok-body", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
        }
        val client = KtorHttpClient(engine)

        val resp = client.send(HttpRequest(HttpMethod.GET, "https://x.test/"))

        assertEquals(200, resp.statusCode)
        assertEquals("ok-body", resp.body?.decodeToString())
        assertEquals("text/plain", resp.headers["Content-Type"])
    }

    @Test
    fun `non-2xx status code is preserved`() = runTest {
        val engine = MockEngine { _ -> respond("nope", HttpStatusCode.NotFound) }
        val client = KtorHttpClient(engine)

        val resp = client.send(HttpRequest(HttpMethod.GET, "https://x.test/missing"))

        assertEquals(404, resp.statusCode)
        assertEquals("nope", resp.body?.decodeToString())
    }

    @Test
    fun `POST with body completes without throwing`() = runTest {
        val engine = MockEngine { _ -> respond("accepted", HttpStatusCode.Accepted) }
        val client = KtorHttpClient(engine)

        val resp = client.send(
            HttpRequest(HttpMethod.POST, "https://x.test/up", body = byteArrayOf(1, 2, 3))
        )

        assertEquals(202, resp.statusCode)
        assertNotNull(resp.body)
    }

    @Test
    fun `empty response body yields a ByteArray`() = runTest {
        val engine = MockEngine { _ -> respond("", HttpStatusCode.NoContent) }
        val client = KtorHttpClient(engine)

        val resp = client.send(HttpRequest(HttpMethod.GET, "https://x.test/empty"))

        assertEquals(204, resp.statusCode)
        assertTrue(resp.body?.isEmpty() == true)
    }
}
