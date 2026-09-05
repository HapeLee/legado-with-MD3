package io.legado.app.smoke.networkprobe

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * D3 PoC：证明 `io.ktor:ktor-client-core` 的 API（HttpClient / get / bodyAsText）
 * 在 commonMain 可用，为 P1 的 HttpClient 契约选型（Ktor vs 仅 okhttp）产出证据。
 * 不持有 client；调用方注入引擎（MockEngine 或真实 OkHttp 引擎）。
 */
object HttpProbe {
    suspend fun fetchBody(client: HttpClient, url: String): String =
        client.get(url).bodyAsText()
}
