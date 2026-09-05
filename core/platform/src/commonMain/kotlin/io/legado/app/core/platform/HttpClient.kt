package io.legado.app.core.platform

/**
 * 平台无关 HTTP 契约：共享层只见领域值（method/url/headers/body 字节），
 * 不暴露 `okhttp3.*` / Cronet / Ktor 类型。
 *
 * D3 PoC 已 Go（Ktor 3.5.1 client-core 双 target 可用），故实现走 Ktor。
 * help/http（14 文件，okhttp + Cronet）的全量迁移归 P2，本契约先立 seam。
 */
interface HttpClient {
    suspend fun send(request: HttpRequest): HttpResponse
}

enum class HttpMethod { GET, POST, PUT, DELETE, HEAD, PATCH }

/** HTTP 请求值。body 为原始字节；无 body 时为 null。 */
data class HttpRequest(
    val method: HttpMethod = HttpMethod.GET,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false
        return method == other.method && url == other.url && headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

/** HTTP 响应值。body 为原始字节；空响应体时为 null。 */
data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponse) return false
        return statusCode == other.statusCode && headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}
