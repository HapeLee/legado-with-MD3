package io.legado.app.help.coil

import coil.intercept.Interceptor
import coil.request.ImageResult
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoverInterceptor : Interceptor {

    companion object {
        private const val RESOLVED_URL_CACHE_MAX_SIZE = 100

        /** LRU cache: "$url|$sourceOrigin" -> Pair(resolvedUrl, headers) */
        private val resolvedUrlCache = object : LinkedHashMap<String, Pair<String, Map<String, String>>>(
            16, 0.75f, true
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<String, Map<String, String>>>?
            ): Boolean {
                return size > RESOLVED_URL_CACHE_MAX_SIZE
            }
        }

        fun clearResolvedUrlCache() {
            synchronized(resolvedUrlCache) {
                resolvedUrlCache.clear()
            }
        }
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data

        if (data is String && data.isNotBlank()) {
            val sourceOrigin = request.parameters.value("sourceOrigin") as? String
            val source = sourceOrigin?.let { SourceHelp.getSource(it) }

            val cacheKey = "$data|$sourceOrigin"
            val cached = synchronized(resolvedUrlCache) {
                resolvedUrlCache[cacheKey]
            }

            val (finalUrl, headers) = cached ?: withContext(Dispatchers.IO) {
                AnalyzeUrl(data, source = source).getUrlAndHeaders()
            }.also { result ->
                synchronized(resolvedUrlCache) {
                    resolvedUrlCache[cacheKey] = result
                }
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
