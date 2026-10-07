package io.legado.app.help.readaloud.cast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.io.File

/**
 * 角色胶囊头像的位图缓存（多角色分配）。
 *
 * 绘制层同步读 [cached]，未命中画首字符占位；[load] 在分页前由
 * [CastRenderOptions] 预取（数量受角色表约束）。
 *
 * 这里**只裁成正方形、不打圆形蒙版**：头像的形状由胶囊自己的圆角设置决定
 * （绘制侧按 `avatarCornerPx` 裁），入库时再剪成圆形的话，圆角滑块拉到矩形
 * 也还是圆的——图片本身四个角已经被抠掉了。
 */
object CastAvatarCache {

    private val cache = LruCache<String, Bitmap>(6 * 1024 * 1024)

    fun cached(uri: String): Bitmap? =
        if (uri.isEmpty()) null else cache.get(uri)?.takeIf { !it.isRecycled }

    /** 加载并缓存方形头像；失败返回 null（首字符占位兜底）。 */
    suspend fun load(uri: String): Bitmap? {
        if (uri.isEmpty()) return null
        cached(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val src = decode(uri) ?: return@withContext null
                val square = centerSquareCrop(src)
                cache.put(uri, square)
                square
            } catch (_: Throwable) {
                null
            }
        }
    }

    private suspend fun decode(uri: String): Bitmap? = when {
        uri.startsWith("http://", true) || uri.startsWith("https://", true) -> fromNetwork(uri)
        uri.startsWith("content://") -> fromUri(uri)
        else -> fromFile(if (uri.startsWith("file://")) Uri.parse(uri).path ?: uri.removePrefix("file://") else uri)
    }

    /** 网络头像走全局 ImageLoader：和封面同一套拦截器，站点的防盗链头由它补。 */
    private suspend fun fromNetwork(uri: String): Bitmap? = runCatching {
        GlobalContext.get().get<ImageLoader>()
            .execute(
                ImageRequest.Builder(appCtx).data(uri).allowHardware(false).build()
            )
            .image?.toBitmap()
    }.getOrNull()

    /** content:// 头像（直接选了系统里的图，没经过裁剪落盘）交 Coil 之外自己解一次。 */
    private fun fromUri(uri: String): Bitmap? = runCatching {
        appCtx.contentResolver.openInputStream(Uri.parse(uri))?.use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()

    private fun fromFile(path: String): Bitmap? = runCatching {
        if (!File(path).isFile) null else BitmapFactory.decodeFile(path)
    }.getOrNull()

    /** 居中裁成正方形：不缩放、不裁形状，长条图取中间那块，避免拉成椭圆。 */
    private fun centerSquareCrop(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        if (size <= 0 || (src.width == size && src.height == size)) return src
        // 裁出来的这块和 src 共用同一份像素，回收 src 等于把缓存里这张图抽走，
        // 画胶囊时就是「trying to use a recycled bitmap」崩溃。交给 GC 收。
        return Bitmap.createBitmap(
            src,
            (src.width - size) / 2,
            (src.height - size) / 2,
            size,
            size,
        )
    }
}
