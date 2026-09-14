package io.legado.app.help.coil

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
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
            // 本地封面缓存快速路径：命中就改写为本地文件，跳过 AnalyzeUrl 的书源规则调用
            // （部分书源的 headerRule/coverUrl 会执行 JS/Java 检查脚本，冷启动或重载封面时
            // 会弹“未登录/版本检测”提示）与网络请求。
            //
            // 命中规则：
            //   ① 精确命中（md5(原始封面地址)）→ 任何页面都直接用本地文件；
            //   ② 书架类请求（PreferCache）额外接受“本书别名”命中：启动刷新把 coverUrl
            //     重写成带新 token 的链接时，图片内容其实没变，直接取别名指向的文件，
            //     不解析规则、不跑脚本、不联网；
            //   ③ 详情页不设 PreferCache：在线时仍走慢速路径拉新链接，成功后同时刷新
            //     精确键与别名键 → 下次书架展示的就是新封面。
            // 漫画模式走独立缓存目录不在此列；data: 内联图无需缓存。
            val isManga = request.extras[CoverExtras.Manga] == true
            val bookUrl = request.extras[CoverExtras.BookUrl]
            val preferCache = request.extras[CoverExtras.PreferCache] == true
            if (!isManga && !data.startsWith("data:", true)) {
                val exactFile = CoverFileCache.read(data)
                val cachedFile = exactFile
                    ?: bookUrl?.takeIf { preferCache }?.let { CoverFileCache.readByBookUrl(it) }
                cachedFile?.let { file ->
                    // 精确命中且带 bookUrl 时，顺手把本书别名指向这个文件，
                    // 之后书源轮换 URL 也能靠别名命中，不必重新下载。
                    if (exactFile != null && bookUrl != null) {
                        withContext(Dispatchers.IO) {
                            CoverFileCache.ensureAlias(bookUrl, file)
                        }
                    }
                    val localRequest = request.newBuilder()
                        .data(file)
                        .build()
                    return chain.withRequest(localRequest).proceed()
                }
            }

            val sourceOrigin = request.extras[CoverExtras.SourceOrigin]
            val source = sourceOrigin?.let { origin ->
                withContext(Dispatchers.IO) {
                    SourceHelp.getSource(origin)
                }
            }

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
                    extras[CoverExtras.Source] = source
                    extras[CoverExtras.Headers] = headers
                    // 携带原始地址，供 CoverFetcher 回写稳定键的持久缓存
                    extras[CoverExtras.OriginalUrl] = data
                    // 关闭 Coil 自带的磁盘缓存（位于 cacheDir/image_cache，系统低存储时会被回收，
                    // 设置页清缓存也会删）。Coil 磁盘缓存一旦命中就直接返回，CoverFetcher 不会执行，
                    // filesDir 下的持久缓存也就永远得不到回填；而 cacheDir 被回收后，断网重启会大量丢封面。
                    // 这里只对“确实会写持久缓存”的请求（带 bookUrl）关闭，保证字节一定经过
                    // CoverFetcher 落到 filesDir；无 bookUrl 的临时封面（发现页/搜索页）不写持久缓存，
                    // 保留 Coil 磁盘缓存，避免它们退化成只能重新联网。
                    if (!isManga && bookUrl != null) {
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
                .build()

            return chain.withRequest(newRequest).proceed()
        }
        return chain.proceed()
    }
}
