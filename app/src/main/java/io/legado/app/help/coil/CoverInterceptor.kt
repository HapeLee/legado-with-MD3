package io.legado.app.help.coil

// ============================================================================
// [FIX-AI] 本文件由 AI 助手（Chatbox）修改（2026-09-13）。
// 搜索 [FIX-AI] 可定位本文件全部改动点，每处均注明 原版行为 -> 修复后行为。
// 问题背景与完整清单见 LegadoMD3/fix/README.md。
// ============================================================================


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
            // [FIX-AI] 新增本地封面缓存快速路径（原版无此段）。
            // 原版行为：任何封面请求都会执行 AnalyzeUrl 解析书源规则（包括运行
            // headerRule/coverUrl 里的 JS/Java 检查脚本），未登录/版本检测类书源会
            // 在书架冷启动、编辑页重载封面时弹出提示；解析后的最终 URL 带时效
            // token，又导致 Coil/OkHttp 缓存键每次变化、重启后封面全部重下。
            // 修复后：
            //   ① 精确命中（md5(原始地址)）→ 任何页面都直接用本地文件；
            //   ② 书架类请求（PreferCache）额外接受“本书别名”命中：启动刷新把
            //     coverUrl 重写成带新 token 的链接时，旧链接的图就是同一张图，
            //     直接拿别名指向的文件，不解析规则、不跑脚本、不联网；
            //   ③ 详情页不设 PreferCache：在线时仍走慢速路径拉新链接，成功后
            //     同时刷新精确键与别名键 → 下次书架展示的就是新封面（stale-while-revalidate）。
            // 漫画模式走独立缓存目录不在此列；data: 内联图无需缓存。
            val isManga = request.extras[CoverExtras.Manga] == true
            val bookUrl = request.extras[CoverExtras.BookUrl]
            val preferCache = request.extras[CoverExtras.PreferCache] == true
            if (!isManga && !data.startsWith("data:", true)) {
                val exactFile = CoverFileCache.read(data)
                val cachedFile = exactFile
                    ?: bookUrl?.takeIf { preferCache }?.let { CoverFileCache.readByBookUrl(it) }
                cachedFile?.let { file ->
                    // [FIX-AI] 新增：精确命中且带 bookUrl 时，顺手把本书别名指向这个文件。
                    // 这样旧版本只建了精确键的书，下次冷启动命中时自动补齐别名，
                    // 之后书源再轮换 URL 也能命中，彻底告别“重启重下”。
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
                    // [FIX-AI] 新增：携带原始地址供 CoverFetcher 回写稳定键的持久缓存
                    extras[CoverExtras.OriginalUrl] = data
                    // [FIX-AI] 新增（关键）：关闭 Coil 自带的磁盘缓存（位于 cacheDir/image_cache，
                    // 系统低存储时会自动回收，设置页清缓存也会删）。
                    // 原版行为：Coil 磁盘缓存命中时直接返回，CoverFetcher 根本不会执行，
                    // 导致我们的 filesDir 持久缓存永远得不到回填；一旦 cacheDir 被系统
                    // 回收，断网重启就大量封面丢失。其它分支（View 版 Glide）没有这层
                    // 会绕过写入的二级缓存，所以它们能稳定离线显示。
                    // 修复后：非漫画请求强制走 CoverFetcher，它必然把字节写入
                    // CoverFileCache（filesDir，不会被自动清理），持久化不再依赖 cacheDir。
                    if (!isManga) {
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
                .build()

            return chain.withRequest(newRequest).proceed()
        }
        return chain.proceed()
    }
}
