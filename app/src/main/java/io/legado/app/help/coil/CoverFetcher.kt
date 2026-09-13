package io.legado.app.help.coil

// ============================================================================
// [FIX-AI] 本文件由 AI 助手（Chatbox）修改（2026-09-13）。
// 搜索 [FIX-AI] 可定位本文件全部改动点，每处均注明 原版行为 -> 修复后行为。
// 问题背景与完整清单见 LegadoMD3/fix/README.md。
// ============================================================================


import android.util.Base64
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.legado.app.data.appDb
import io.legado.app.utils.ImageUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.isWifiConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CoverFetcher(
    private val url: String,
    private val options: Options,
    private val callFactory: Call.Factory,
    private val loadOnlyWifi: Boolean,
) : Fetcher {

    companion object {
        /** Tag applied to cover requests so [cacheControlInterceptor] can identify them. */
        val COVER_REQUEST_TAG = Unit

        private const val FAIL_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        /** URL -> failure timestamp. Prevents infinite retries for permanently broken URLs. */
        private val failCache = ConcurrentHashMap<String, Long>()

        // [FIX-AI] 上一版修复曾在本类内嵌持久化封面缓存（以最终 URL 为键）；
        // 现已抽到 CoverFileCache 统一治理，并改用【原始封面地址】作稳定键，
        // 避免书源动态 token 导致重启后 miss。旧键文件作为回退仍会被读取一次，
        // 读到后自动升级写入新键，LRU 最终会清理旧文件。

        fun isFailed(url: String): Boolean {
            val ts = failCache[url] ?: return false
            if (System.currentTimeMillis() - ts > FAIL_CACHE_TTL_MS) {
                failCache.remove(url)
                return false
            }
            return true
        }

        fun markFailed(url: String) {
            failCache[url] = System.currentTimeMillis()
        }

        fun clearFailure(url: String) {
            failCache.remove(url)
        }

        fun clearFailCache() {
            failCache.clear()
        }

        /**
         * [FIX-AI] 过渡兼容：上一版修复把持久缓存存在 cover_cache/<md5(最终URL)>.img，
         * 动态 token 书源重启后键会变导致 miss。这里读一次旧文件并交给调用方升级写入
         * 新键（原始 URL 的 md5）；旧文件由 LRU 自行清理。
         */
        private fun readLegacyCacheFile(url: String): ByteArray? {
            return try {
                val f = File(legacyCacheDir, MD5Utils.md5Encode(url) + ".img")
                val len = f.length()
                if (len in 1..20L * 1024 * 1024) f.readBytes() else null
            } catch (e: Exception) {
                null
            }
        }

        /** [FIX-AI] 与 CoverFileCache 同目录，用于读取上一版遗留的旧键缓存文件 */
        private val legacyCacheDir: File by lazy { File(appCtx.filesDir, "cover_cache") }
    }

    override suspend fun fetch(): FetchResult {
        val source = options.extras[CoverExtras.Source]
        val isManga = options.extras[CoverExtras.Manga] == true
        val mangaBook = options.extras[CoverExtras.MangaBookUrl]
            ?.let { bookUrl -> withContext(Dispatchers.IO) { appDb.bookDao.getBook(bookUrl) } }

        if (url.startsWith("data:", true)) {
            val base64Data = url.substringAfter("base64,", "")
            if (base64Data.isEmpty()) {
                throw IOException("Invalid data URI")
            }
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            return SourceFetchResult(
                source = ImageSource(
                    source = Buffer().write(bytes),
                    fileSystem = options.fileSystem
                ),
                mimeType = null,
                dataSource = DataSource.MEMORY
            )
        }

        // ===== 本地优先：持久化封面文件缓存 =====
        // [FIX-AI] 键改为【原始封面地址】（CoverInterceptor 携带），书架与详情页共享；
        // 链接带动态 token 的书源重启后也能命中。兼容读一次旧版 finalUrl 键的文件，
        // 命中后升级写入新键。
        val originalUrl = options.extras[CoverExtras.OriginalUrl] ?: url
        // [FIX-AI] 新增：本书的 bookUrl，用于别名缓存键的写入与失败回退。
        // [FIX-AI] 只有携带 bookUrl 的请求（书架/详情页等“书”维度页面）才写入持久缓存；
        // 发现页/搜索页/首页模块的临时封面不传 bookUrl → 不再污染 cover_cache，
        // 缓存体积只与书架藏书量成正比（原版行为：无差别缓存所有封面 URL）。
        val bookUrl = options.extras[CoverExtras.BookUrl]
        if (!isManga && bookUrl != null) {
            val legacyBytes = withContext(Dispatchers.IO) { readLegacyCacheFile(url) }
            if (legacyBytes != null) {
                withContext(Dispatchers.IO) {
                    CoverFileCache.write(originalUrl, legacyBytes, bookUrl)
                }
                return SourceFetchResult(
                    source = ImageSource(
                        source = Buffer().write(legacyBytes),
                        fileSystem = options.fileSystem
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }

        val requestHeaders = options.extras[CoverExtras.Headers]

        // ===== 第二级：OkHttp HTTP 缓存（FORCE_CACHE 只读缓存，miss 返回 504，不碰网络）=====
        // 注意：WiFi 限制与失败冷却不能挡在本地缓存读取之前，
        // 否则某个 URL 一次失败后 5 分钟内连本地已有缓存都会被强制置灰。
        var rawBytes: ByteArray? = null
        var fromCache = false
        try {
            withContext(Dispatchers.IO) {
                val cacheRequest = Request.Builder()
                    .url(url)
                    .tag(io.legado.app.data.entities.BaseSource::class.java, source)
                    .apply { requestHeaders?.forEach { (key, value) -> addHeader(key, value) } }
                    .cacheControl(CacheControl.FORCE_CACHE)
                    .build()
                val cacheResponse = callFactory.newCall(cacheRequest).execute()
                if (cacheResponse.isSuccessful) {
                    fromCache = true
                    cacheResponse.body.use { rawBytes = it.bytes() }
                } else {
                    cacheResponse.close()
                    // 缓存 miss，下面再走网络
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // HTTP 缓存读取异常（如缓存损坏），降级走网络
        }

        // ===== 第三级：本地全部 miss，才允许请求网络 =====
        if (rawBytes == null) {
            if (loadOnlyWifi && !appCtx.isWifiConnect) {
                throw IOException("WiFi not available, loadOnlyWifi enabled")
            }

            if (isFailed(url)) {
                throw IOException("URL previously failed, skipping: $url")
            }

            rawBytes = try {
                withContext(Dispatchers.IO) {
                    val networkRequest = Request.Builder()
                        .url(url)
                        .tag(io.legado.app.data.entities.BaseSource::class.java, source)
                        .apply { requestHeaders?.forEach { (key, value) -> addHeader(key, value) } }
                        .tag(COVER_REQUEST_TAG)
                        .cacheControl(
                            CacheControl.Builder()
                                .maxAge(30, TimeUnit.DAYS)
                                .build()
                        )
                        .build()
                    val networkResponse = callFactory.newCall(networkRequest).execute()
                    val body = networkResponse.body
                    if (!networkResponse.isSuccessful) {
                        body.close()
                        throw IOException("HTTP ${networkResponse.code}")
                    }
                    body.use { it.bytes() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markFailed(url)
                // [FIX-AI] 新增：网络彻底失败（断网/源挂）时，回退到本书别名缓存。
                // 原版行为：直接报错 → 封面灰图。现在只要这本书曾经成功加载过封面，
                // 弱网/断网下仍显示上次缓存的封面。
                if (!isManga && bookUrl != null) {
                    val stale = withContext(Dispatchers.IO) {
                        CoverFileCache.readByBookUrl(bookUrl)?.let { f ->
                            try {
                                if (f.length() in 1..20L * 1024 * 1024) f.readBytes() else null
                            } catch (ex: Exception) {
                                null
                            }
                        }
                    }
                    if (stale != null) {
                        return SourceFetchResult(
                            source = ImageSource(
                                source = Buffer().write(stale),
                                fileSystem = options.fileSystem
                            ),
                            mimeType = null,
                            dataSource = DataSource.DISK
                        )
                    }
                }
                throw e
            }
        }

        // 到这里必定已拿到字节（本地缓存/OkHttp 缓存/网络三选一，否则已抛出）。
        // rawBytes 在 lambda 内赋值，Kotlin 无法智能转换为非空，这里显式收敛。
        val fetchedBytes = rawBytes ?: throw IOException("封面数据为空: $url")

        // Decrypt if needed (applies to both cached and network bytes)
        val decodedBytes = if (ImageUtils.skipDecode(source, !isManga)) {
            fetchedBytes
        } else {
            withContext(Dispatchers.IO) {
                if (isManga) {
                    ImageUtils.decode(url, fetchedBytes, false, source, mangaBook)
                } else {
                    ImageUtils.decode(url, fetchedBytes, true, source)
                }
            } ?: throw IOException("图片解密失败")
        }

        clearFailure(url)
        // [FIX-AI] 网络/OkHttp 缓存/解密完成后，按原始 URL 精确键 + bookUrl 别名键
        // 双写持久缓存：下次冷启动由 CoverInterceptor 快速路径直接命中；
        // 书源刷新换了带 token 的新链接时，书架靠别名键也能秒出旧图。
        // [FIX-AI] 仅书维度请求（bookUrl 非空）写入，发现/搜索临时封面不落盘。
        if (!isManga && bookUrl != null) {
            withContext(Dispatchers.IO) { CoverFileCache.write(originalUrl, decodedBytes, bookUrl) }
        }
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(decodedBytes),
                fileSystem = options.fileSystem
            ),
            mimeType = null,
            dataSource = if (fromCache) DataSource.DISK else DataSource.NETWORK
        )
    }

    class Factory(
        private val okHttpClient: OkHttpClient,
        private val okHttpClientManga: OkHttpClient,
    ) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme != "http" && scheme != "https" && scheme != "data") return null

            val isManga = options.extras[CoverExtras.Manga] == true
            val loadOnlyWifi = options.extras[CoverExtras.LoadOnlyWifi] == true
            val client = if (isManga) okHttpClientManga else okHttpClient

            return CoverFetcher(data.toString(), options, client, loadOnlyWifi)
        }
    }
}
