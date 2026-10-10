package io.legado.app.help.http

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object OkHttpExceptionInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        try {
            return chain.proceed(request)
        } catch (e: IOException) {
            // 局域网地址在 Android 17+ 未授权时会被系统静默丢包，表现为超时/连接失败，
            // 这里兜底识别并请宿主补授权（AI 等有前置检查的入口不受影响）。
            LocalNetworkBlockedNotifier.onRequestFailed(request.url.host, e)
            throw e
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

}
