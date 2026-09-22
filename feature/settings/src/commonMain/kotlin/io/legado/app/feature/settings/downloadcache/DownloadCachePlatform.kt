package io.legado.app.feature.settings.downloadcache

/**
 * OkHttp 缓存的两种 —— 对应迁移前 `io.legado.app.help.http.HttpCacheType` 的
 * `COVER` / `MANGA`（M5-7）。
 *
 * ⚠️ 共享层**只保留「是哪一种」**，刻意**不带** `HttpCacheType` 的 `dirName` / `maxSize`：
 * 那两个是存储与策略细节（`dirName` 是缓存目录名，`maxSize` 是 100 MB 上限），
 * 只有实现侧（读目录大小、删 OkHttp cache）用得着，共享层既不需要也不该知道。
 * 契约面因此只剩一个两值枚举，而不是把 Android 那个 enum 整体搬过来。
 */
enum class HttpCacheKind {
    COVER,
    MANGA,
}

/**
 * 下载/缓存设置页所需的**平台能力**（M5-7，`:feature:settings` 的第四个窄契约，
 * 落位与 `AiProviderStringSource` / `AboutDiagnostics` 同一约定：契约在 feature 的
 * commonMain，Android 实现在 `:app` 的 `io.legado.app.platform`，由 Koin 注入）。
 *
 * 迁移前 `DownloadCacheConfigViewModel` + `DownloadCacheConfigScreen` 里有 **5 处**直连
 * 平台，互不同源，但都服务于同一件事「让用户看到并清掉各类缓存」：
 *
 * | 迁移前 | 为什么不能进 commonMain |
 * |---|---|
 * | `CacheBook.maxDownloadConcurrency` | 下载引擎（`:app` 的 `model.CacheBook`）的并发上限 |
 * | `getHttpCacheSize(HttpCacheType.COVER/MANGA)` | 走 `appCtx.cacheDir` 遍历文件系统 |
 * | `clearHttpCache(HttpCacheType.COVER/MANGA)` | 直接 `okHttpClient.cache?.delete()` |
 * | `FileUtils.delete(appCtx.cacheDir.absolutePath)` + `appCtx.externalCacheDir?.deleteRecursively()` | `Context` + `java.io.File` |
 * | `ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)` | `android.graphics.Bitmap` 的 LruCache |
 *
 * ## 契约边界的两条判断
 *
 * 1. **[maxDownloadConcurrency] 走契约，而不是在共享层写一个 `const val 8`。**
 *    它是个纯数值、看起来可以搬，但那样**引擎与共享层就各有一份上限**，日后引擎放宽到 16
 *    时共享层会静默漂移（表现为「滑块拉不到底」这种没人会报的 bug）。
 *    实现侧转发 `CacheBook.maxDownloadConcurrency`，**唯一真源留在引擎**。
 *    它不是能力而是常数，之所以仍放在这个接口上，就是因为它与其余四项同属
 *    「本页需要、而共享层拿不到」的东西；单独为它建一个接口不划算。
 *
 * 2. **[clearCacheDirectories] 与 `ClearBookCacheUseCase` 的分工**：书缓存**条目**的清理由
 *    `:core:data` 的 `ClearBookCacheUseCase`（共享层，已有）负责；这里只做它之后那一步
 *    「把缓存目录本身也删掉」。迁移前这两步就在同一个 `when` 分支里前后相邻，
 *    本契约**不**把它们合并 —— 合并会让 `:core:data` 的 use case 失去独立性。
 *
 * 平台能力缺失必须显式建模：desktop 侧不提供实现（本契约未在 desktop 注册），
 * 不得用假实现伪装成「缓存大小为 0」。
 */
interface DownloadCachePlatform {

    /**
     * 下载引擎允许的最大并发数（迁移前 `CacheBook.maxDownloadConcurrency`，当前为 8）。
     * VM 用它把 `cacheBookThreadCount` 夹到合法区间，UI 用它当滑块的 `valueRange` 上界。
     */
    val maxDownloadConcurrency: Int

    /**
     * 某种 HTTP 缓存的当前占用（字节）。目录不存在时返回 `0`
     * （迁移前 `getHttpCacheSize` 的 `if (!dir.exists()) return 0`）。
     */
    suspend fun httpCacheSizeBytes(kind: HttpCacheKind): Long

    /** 清掉某种 HTTP 缓存（迁移前按类型分别 `okHttpClient` / `okHttpClientManga` 的 `cache?.delete()`）。 */
    suspend fun clearHttpCache(kind: HttpCacheKind)

    /**
     * 清空应用的缓存目录（内部缓存目录整个删掉 + 外部缓存目录递归删掉）。
     * 与迁移前 `FileUtils.delete(appCtx.cacheDir.absolutePath)`（`deleteRootDir` 默认 `true`）
     * 及 `appCtx.externalCacheDir?.deleteRecursively()` 逐行对应。
     */
    suspend fun clearCacheDirectories()

    /**
     * 让图片内存缓存按最新设置重新分配 —— 迁移前
     * `ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)`，
     * 在用户改完 `bitmapCacheSize` 之后调用。
     */
    fun resizeImageCache()
}
