package io.legado.app.platform

import android.content.Context
import io.legado.app.feature.settings.downloadcache.DownloadCachePlatform
import io.legado.app.feature.settings.downloadcache.HttpCacheKind
import io.legado.app.help.http.HttpCacheType
import io.legado.app.help.http.clearHttpCache
import io.legado.app.help.http.getHttpCacheSize
import io.legado.app.model.CacheBook
import io.legado.app.model.ImageProvider
import io.legado.app.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DownloadCachePlatform] 的 Android 实现（M5-7）。逐行照抄迁移前
 * `DownloadCacheConfigViewModel` / `DownloadCacheConfigScreen` 里那五处直连，
 * 只把 `appCtx` / 全局单例换成构造参数（`context`）或直接转发。
 *
 * ⚠️ **为什么那个映射函数是「顶层私有」而不是本类的成员。**
 * 本类有个成员 `override fun clearHttpCache(kind)`，而 `help.http` 里有个**顶层**函数
 * `clearHttpCache(type: HttpCacheType)`。若把 `when (...)` 映射写在类体内，
 * `clearHttpCache(HttpCacheType.COVER)` 会解析到**自己的成员**（成员优先于导入）⇒ 无限递归。
 *
 * 常规解法是 `import ... clearHttpCache as clearOkHttpCache`，但**这里刻意不用别名**：
 * `checkLegacyArchitecture` 的 `legacyHelp` 规则是 `^import io\.legado\.app\.help\.[A-Za-z0-9_.]+$`
 * （**行尾锚定**），带 ` as 别名` 的 import **不会被计入**。别名会让这条真实存在的
 * legacy 耦合在基线里凭空少 1 —— 本片的整个意义就是「把耦合从 ui 层搬进 platform 并记账」，
 * 记账少一笔就失去意义。
 *
 * 把映射挪到**顶层私有函数**即可两全：类外没有成员遮蔽，`clearHttpCache(...)` 正常解析到
 * `help.http` 那个顶层函数，而 import 保持最朴素的形式、如实计数。
 * （该盲点本身已记进 `docs/dev/legacy-architecture-report.md` 的备注，供后续修门禁参考。）
 */
class AndroidDownloadCachePlatform(
    private val context: Context,
) : DownloadCachePlatform {

    /** 唯一真源是引擎 —— 见 `DownloadCachePlatform` KDoc 第 1 条。 */
    override val maxDownloadConcurrency: Int = CacheBook.maxDownloadConcurrency

    override suspend fun httpCacheSizeBytes(kind: HttpCacheKind): Long = withContext(Dispatchers.IO) {
        getHttpCacheSize(kind.toAndroidCacheType())
    }

    override suspend fun clearHttpCache(kind: HttpCacheKind) = withContext(Dispatchers.IO) {
        // 走顶层私有函数，避开成员遮蔽 —— 见类 KDoc。
        kind.clearAndroidHttpCache()
    }

    // 块体而非 `= withContext { … }`：后者会把 `deleteRecursively()` 的 `Boolean?` 当成
    // lambda 的返回类型，与契约的 `Unit` 不符（编译器直接报「不是子类型」）。
    // 迁移前也是丢弃这个返回值，这里保持一致（不因它去改行为）。
    override suspend fun clearCacheDirectories() {
        withContext(Dispatchers.IO) {
            // `deleteRootDir` 取默认的 `true` —— 与迁移前 `FileUtils.delete(path)` 一致
            // （内部缓存目录整个删掉，之后 Android 会按需重建）。
            FileUtils.delete(context.cacheDir.absolutePath)
            context.externalCacheDir?.deleteRecursively()
        }
    }

    override fun resizeImageCache() {
        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
    }
}

/** 见 [AndroidDownloadCachePlatform] 的 KDoc：**必须是顶层**，否则被成员遮蔽成递归。 */
private fun HttpCacheKind.clearAndroidHttpCache() {
    when (this) {
        HttpCacheKind.COVER -> clearHttpCache(HttpCacheType.COVER)
        HttpCacheKind.MANGA -> clearHttpCache(HttpCacheType.MANGA)
    }
}

private fun HttpCacheKind.toAndroidCacheType(): HttpCacheType = when (this) {
    HttpCacheKind.COVER -> HttpCacheType.COVER
    HttpCacheKind.MANGA -> HttpCacheType.MANGA
}
