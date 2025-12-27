package io.legado.app.help.coil

import coil.intercept.Interceptor
import coil.request.ImageResult
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.isWifiConnect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class CoverInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data

        if (data is String && data.isNotBlank()) {
            val sourceOrigin = request.parameters.value("sourceOrigin") as? String
            val source = sourceOrigin?.let { SourceHelp.getSource(it) }

            val (finalUrl, headers) = withContext(Dispatchers.IO) {
                AnalyzeUrl(data, source = source).getUrlAndHeaders()
            }

            val newRequest = request.newBuilder()
                .data(finalUrl)
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                    tag(BaseSource::class.java, source)
                }
                .build()

            return chain.proceed(newRequest)
        }
        return chain.proceed(request)
    }
}