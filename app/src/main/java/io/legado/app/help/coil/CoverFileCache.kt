package io.legado.app.help.coil

import io.legado.app.utils.MD5Utils
import splitties.init.appCtx
import java.io.File

/**
 * filesDir 下的持久化封面文件缓存（不受“清除缓存”策略与系统低存储回收影响）。
 *
 * 存在的理由：封面链路的缓存键传统上落在 AnalyzeUrl 解析出的最终 URL 上，而不少
 * 书源的封面最终 URL 带时效 token/签名，每次解析都不同 → Coil 与 OkHttp 缓存全部
 * miss，重启后书架要重新走网络；解析过程还会执行书源 headerRule/coverUrl 里的
 * JS/Java 检查脚本，触发“未登录”“非官方版本”等书源自定义弹窗。
 *
 * 因此这里以【书架/详情页存下的原始封面地址】（稳定）的 MD5 为键保存解密后的图片字节：
 * - CoverInterceptor 命中时直接改写请求为本地文件，完全跳过规则解析与网络，
 *   重启加载、编辑页重载封面都不再执行书源脚本。
 * - 封面链接真的变化（换源/刷新详情）时 key 变化，自动回退到解析+网络路径。
 * - 总量上限 100MB，LRU 修剪到 80MB；单文件上限 20MB。
 * - 设置 → 下载与缓存 → “清除封面缓存”会连同本目录一起清空（见 HttpHelper.clearHttpCache）。
 */
object CoverFileCache {

    /** 持久缓存目录：filesDir 下，不受"清除缓存"策略与系统低存储清理影响 */
    private val cacheDir: File by lazy {
        File(appCtx.filesDir, "cover_cache").apply { if (!exists()) mkdirs() }
    }

    private const val MAX_CACHE_FILE_BYTES = 20L * 1024 * 1024        // 单封面 20MB 上限
    private const val CACHE_MAX_BYTES = 100L * 1024L * 1024L          // 总量 100MB 上限
    private const val CACHE_TRIM_TO_BYTES = 80L * 1024L * 1024L       // 修剪目标 80MB
    private const val TRIM_INTERVAL_MS = 30 * 60 * 1000L              // 修剪节流 30 分钟

    @Volatile
    private var lastTrimTime = 0L

    fun fileFor(keyUrl: String): File =
        File(cacheDir, MD5Utils.md5Encode(keyUrl) + ".img")

    /** 命中返回可读的缓存文件，未命中返回 null */
    fun read(keyUrl: String): File? {
        val f = fileFor(keyUrl)
        val len = f.length()
        return if (len > 0L && len <= MAX_CACHE_FILE_BYTES) f else null
    }

    // bookUrl 别名键。链接带 token 轮转的书源每次刷新 coverUrl 都会变，
    // 纯 URL 键必 miss；别名＝“这本书最近一次成功缓存的封面文件”，与链接无关。
    // 实现：bk_<md5(bookUrl)>.lnk 小文本文件，内容为主缓存文件名，不重复存图片字节。
    private fun aliasFileFor(bookUrl: String): File =
        File(cacheDir, "bk_" + MD5Utils.md5Encode(bookUrl) + ".lnk")

    /** 按书籍维度取最近一次成功缓存的封面文件；无别名/目标失效返回 null */
    fun readByBookUrl(bookUrl: String): File? {
        return try {
            val lnk = aliasFileFor(bookUrl)
            if (!lnk.exists()) return null
            val targetName = lnk.readText().trim()
            if (targetName.isEmpty()) return null
            val f = File(cacheDir, targetName)
            val len = f.length()
            if (len > 0L && len <= MAX_CACHE_FILE_BYTES) f else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 别名回填：拦截器精确键命中时调用，把“本书→该文件”的指针补上
     * （已有且指向相同则直接返回），之后书源再轮换 URL 也能命中。
     */
    fun ensureAlias(bookUrl: String, targetFile: File) {
        try {
            val lnk = aliasFileFor(bookUrl)
            if (lnk.exists() && lnk.readText().trim() == targetFile.name) return
            lnk.writeText(targetFile.name)
        } catch (e: Exception) {
            // 别名写失败不影响本次加载
        }
    }

    /** 原子写入：先写 tmp 再 rename，避免滚动并发读到半截文件；bookUrl 非空时同步更新别名 */
    fun write(keyUrl: String, bytes: ByteArray, bookUrl: String? = null) {
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_CACHE_FILE_BYTES) return
        val file = fileFor(keyUrl)
        try {
            val tmp = File(file.parentFile, file.name + "." + System.nanoTime() + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // 写缓存失败不影响本次加载
        }
        if (bookUrl != null) {
            try {
                aliasFileFor(bookUrl).writeText(file.name)
            } catch (e: Exception) {
                // 别名写失败不影响主缓存
            }
        }
        maybeTrim()
    }

    /** 设置页"清除封面缓存"调用 */
    fun clear() {
        try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        } catch (e: Exception) {
            // 清理失败下次重试
        }
    }

    /** 按 lastModified 做 LRU 清理，防止常年累积占满存储 */
    private fun maybeTrim() {
        val now = System.currentTimeMillis()
        if (now - lastTrimTime < TRIM_INTERVAL_MS) return
        lastTrimTime = now
        try {
            val files = cacheDir.listFiles()?.toList() ?: return
            var total = files.sumOf { it.length() }
            if (total <= CACHE_MAX_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= CACHE_TRIM_TO_BYTES) return
                total -= f.length()
                f.delete()
            }
        } catch (e: Exception) {
            // 清理失败不影响图片加载
        }
    }
}
