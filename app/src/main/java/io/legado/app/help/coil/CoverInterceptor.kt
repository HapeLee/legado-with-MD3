package io.legado.app.help.coil

import coil3.BitmapImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Size
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.isAbsUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class CoverInterceptor(
    private val acquireMangaFile: suspend (ImageRequest, String) -> Pair<File, Closeable> = { request, data ->
        val bookUrl = requireNotNull(request.extras[CoverExtras.MangaBookUrl])
        val book = requireNotNull(appDb.bookDao.getBook(bookUrl)) { "Manga book missing" }
        val source = appDb.bookSourceDao.getBookSource(
            request.extras[CoverExtras.SourceOrigin] ?: book.origin,
        )
        BookHelp.acquireReadingImage(
            source, book, data, request.extras[CoverExtras.LoadOnlyWifi] == true,
            request.extras[CoverExtras.MangaFileTransferStarted] ?: {},
        )
    },
) : Interceptor {

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
        // Telephoto 取消 Coil 的尺寸上限后，按宽度加载长图会生成无法绘制的整图。
        // 整图仅作预览；分页和条漫的高清细节都由原图区域解码补足。
        val incoming = chain.request
        val boundedChain = if (incoming.extras[CoverExtras.Manga] == true ||
            incoming.extras[CoverExtras.MangaFileOwner] != null
        ) {
            chain.withRequest(incoming.newBuilder().maxBitmapSize(Size(4096, 4096)).build())
        } else chain
        val request = boundedChain.request
        val data = request.data

        val fileOwner = request.extras[CoverExtras.MangaFileOwner]
        // 分页与条漫在线页复用同一原图；本地漫画与 content/file URI 保持平台加载器。
        if (fileOwner != null && data is String &&
            (data.isAbsUrl() || data.startsWith("data:", true)) &&
            !data.startsWith("file:", true) && !data.startsWith("content:", true)
        ) {
            val (file, version, ratio) = withContext(Dispatchers.IO) {
                val acquired = fileOwner.acquire(data) { acquireMangaFile(request, data) }
                Triple(
                    acquired,
                    "${acquired.length()}:${acquired.lastModified()}",
                    MangaImageDimensions.read(acquired)
                )
            }
            fileOwner.setAspectRatio(ratio)
            ratio?.let { request.extras[CoverExtras.MangaAspectRatio]?.invoke(it) }
            val memoryKey = "${request.memoryCacheKey ?: data}:original:$version"
            val webtoon =
                request.extras[CoverExtras.MangaWebtoon] == true && request.extras[CoverExtras.MangaDataPrefetch] != true
            val previewKey = if (webtoon) MangaPreviewCache.key(memoryKey, request) else ""
            val snapshot =
                if (webtoon) withContext(Dispatchers.IO) { MangaPreviewCache.open(previewKey) } else null

            fun localRequest(dataFile: File, cachedPreview: Boolean) = request.newBuilder()
                .data(dataFile).memoryCacheKey(memoryKey)
                .placeholderMemoryCacheKey(memoryKey).diskCachePolicy(CachePolicy.DISABLED)
                .apply {
                    if (webtoon) allowHardware(false)
                    if (cachedPreview) transformations(emptyList())
                }.build()

            var usedPreview = snapshot != null
            var result = try {
                boundedChain.withRequest(
                    localRequest(
                        snapshot?.data?.toFile() ?: file,
                        usedPreview
                    )
                )
                    .proceed()
            } finally {
                if (snapshot != null) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { snapshot.close() }
            }
            if (usedPreview && result is ErrorResult) {
                withContext(Dispatchers.IO) { MangaPreviewCache.remove(previewKey) }
                usedPreview = false
                result = boundedChain.withRequest(localRequest(file, false)).proceed()
            }
            if (webtoon && !usedPreview && result is SuccessResult && result.image is BitmapImage) {
                MangaPreviewCache.save(previewKey, (result.image as BitmapImage).bitmap)
            }
            // Telephoto 会对内存命中也重新淡入；已准备的预览直接显示，避免滑动时露出底色。
            return if (result is SuccessResult && result.dataSource == DataSource.MEMORY_CACHE) {
                result.copy(request = result.request.newBuilder().crossfade(false).build())
            } else result
        }

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
                    return boundedChain.withRequest(localRequest).proceed()
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

            return boundedChain.withRequest(newRequest).proceed()
        }
        return boundedChain.proceed()
    }
}
