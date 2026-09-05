package io.legado.app.core.platform

import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.contentType

/**
 * 基于 Ktor 的 [HttpClient] 实现（D3 已 Go：Ktor 3.5.1 client-core 双 target 可用）。
 * 引擎由调用方注入——Android/Desktop 注入 okhttp 引擎（`ktor-client-okhttp`），
 * 测试注入 `MockEngine`。映射 [HttpRequest]↔Ktor↔[HttpResponse]。
 *
 * 不在本类持有引擎；调用方负责生命周期（多次 send 复用同一 client，由 lazy 持有）。
 */
class KtorHttpClient(
    engine: HttpClientEngine,
) : HttpClient {
    private val client = KtorClient(engine)

    override suspend fun send(request: HttpRequest): HttpResponse {
        val response = client.request(request.url) {
            method = when (request.method) {
                HttpMethod.GET -> KtorHttpMethod.Get
                HttpMethod.POST -> KtorHttpMethod.Post
                HttpMethod.PUT -> KtorHttpMethod.Put
                HttpMethod.DELETE -> KtorHttpMethod.Delete
                HttpMethod.HEAD -> KtorHttpMethod.Head
                HttpMethod.PATCH -> KtorHttpMethod.Patch
            }
            request.headers.forEach { (key, value) -> header(key, value) }
            request.body?.let {
                contentType(ContentType.Application.OctetStream)
                setBody(it)
            }
        }
        return HttpResponse(
            statusCode = response.status.value,
            headers = response.headers.entries()
                .associate { entry -> entry.key to entry.value.joinToString(", ") },
            body = runCatching { response.readBytes() }.getOrNull(),
        )
    }
}
