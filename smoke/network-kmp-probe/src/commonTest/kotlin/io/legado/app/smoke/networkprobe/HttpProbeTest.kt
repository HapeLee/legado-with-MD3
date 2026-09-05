package io.legado.app.smoke.networkprobe

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D3 PoC 契约测试：用 MockEngine（无真实网络）证明 ktor-client-core 的
 * HttpClient/get/bodyAsText 在双 target 编译并按预期运行。
 */
class HttpProbeTest {

    @Test
    fun `fetchBody returns mocked response body`() = runTest {
        val client = HttpClient(MockEngine { request ->
            respond(
                "hello-ktor",
                HttpStatusCode.OK,
                headersOf("Content-Type", "text/plain"),
            )
        })
        val body = HttpProbe.fetchBody(client, "https://probe.test/x")
        assertEquals("hello-ktor", body)
        client.close()
    }
}
