package io.legado.app.help.readaloud.cast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import io.legado.app.feature.reader.core.cast.CastCapsuleStyle
import io.legado.app.help.config.ReadStyleResolver
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import kotlin.math.max

/**
 * 朗读胶囊样式的读写出口（设置页与正文绘制侧都只经这里）。
 *
 * 三类胶囊各存一份样式：角色胶囊、未分配占位胶囊、配乐胶囊。存法是 SharedPreferences
 * 里的一条 JSON，跟背景音乐池那几个开关同一个文件，不占数据库版本。
 *
 * 读取侧是同步的（画每一颗胶囊都要拿），所以内存里留一份快照，写的时候一起换；
 * [signature] 每次写自增，分页缓存把它算进章节身份——头像位移会改胶囊宽度，
 * 身份不变的话旧页会一直挂着量好的宽度。
 */
object CastCapsuleStyleStore {

    const val ROLE = "role"
    const val PLACEHOLDER = "placeholder"
    const val BGM = "bgm"

    /** 设置页的类型切换顺序。 */
    val TYPES = listOf(ROLE, PLACEHOLDER, BGM)

    private const val PREFS = "cast_prefs"
    private const val IMAGE_DIR = "cast_capsule_images"

    @Volatile
    private var snapshot: Map<String, CastCapsuleStyle> = emptyMap()

    @Volatile
    private var cachedNight: Map<String, CastCapsuleStyle> = emptyMap()

    @Volatile
    private var cachedNightKey: Long = -1L

    /** 每次写样式 +1，[io.legado.app.help.readaloud.cast.CastRenderOptions] 据此判定要不要重排。 */
    @Volatile
    var signature: Int = 0
        private set

    fun prefs(): android.content.SharedPreferences =
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 原始样式（深浅两套都在里面）。 */
    fun raw(type: String): CastCapsuleStyle = snapshot[type] ?: runCatching {
        prefs().getString(key(type), null)?.let {
            GSON.fromJson(it, CastCapsuleStyle::class.java)
        }
    }.getOrNull()?.also { snapshot = snapshot + (type to it) } ?: CastCapsuleStyle.Default

    /** 按当前深浅模式取好色与图的那一份；一帧里反复调用同一类型不会重复算。 */
    fun current(type: String): CastCapsuleStyle {
        val night = ReadStyleResolver.isNightTheme()
        val key = (signature.toLong() shl 1) or (if (night) 1L else 0L)
        if (key == cachedNightKey) return cachedNight[type] ?: raw(type).resolved(night)
        val resolved = TYPES.associateWith { raw(it).resolved(night) }
        cachedNight = resolved
        cachedNightKey = key
        return resolved[type] ?: CastCapsuleStyle.Default
    }

    fun set(type: String, style: CastCapsuleStyle) {
        snapshot = snapshot + (type to style)
        cachedNightKey = -1L
        prefs().edit().putString(key(type), GSON.toJson(style)).apply()
        signature++
    }

    fun reset(type: String) {
        set(type, CastCapsuleStyle.Default)
    }

    private fun key(type: String) = "capsuleStyle_${type}"

    private fun CastCapsuleStyle.resolved(night: Boolean) = copy(
        bgColor = backgroundColor(night),
        bgImage = backgroundImage(night),
        bgColorNight = 0,
        bgImageNight = "",
    )

    // ---------- 底图 ----------

    /** 把选中的图片复制进应用目录（内容 URI 的授权会过期，落盘后才管长期显示）。 */
    fun saveImage(context: Context, source: Uri): String {
        val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val file = File(dir, "capsule_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "打不开这张图片" }
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file).toString()
    }

    /** 换图或清除时把旧文件删掉，只删自己那个目录里的。 */
    fun deleteImage(uri: String) {
        if (uri.isBlank()) return
        val imageDir = File(appCtx.filesDir, IMAGE_DIR).canonicalFile
        val file = runCatching { File(Uri.parse(uri).path.orEmpty()).canonicalFile }.getOrNull()
            ?: return
        if (file.parentFile == imageDir) file.delete()
    }

    suspend fun preload(style: CastCapsuleStyle) {
        style.bgImage.takeIf { it.isNotEmpty() }?.let { CastCapsuleImageCache.load(it) }
    }
}

/**
 * 胶囊底图的位图缓存：绘制侧同步取，未命中就只画底色。
 *
 * 上限按堆算，最少 8 MB：解码最长边夹在 1024，一张 1024×2048 的图就要 8 MB；
 * 上限小于一张图的尺寸时条目一进缓存就被挤出去，每帧都是未命中（翻页首帧底图为空）。
 */
object CastCapsuleImageCache {

    private val cache = LruCache<String, Bitmap>(
        maxOf(8 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt()),
    )

    fun cached(uri: String): Bitmap? =
        if (uri.isEmpty()) null else cache.get(uri)?.takeIf { !it.isRecycled }

    suspend fun load(uri: String): Bitmap? {
        if (uri.isEmpty()) return null
        cached(uri)?.let { return it }
        return runCatching {
            decode(uri)?.also { cache.put(uri, it) }
        }.getOrNull()
    }

    private fun decode(uri: String): Bitmap? {
        val path = if (uri.startsWith("file://")) Uri.parse(uri).path ?: uri else uri
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        if (side <= 0) return null
        val sample = (side / 1024).coerceAtLeast(1)
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    /** 等比铺满目标框后居中裁掉多余部分（底图多半不是胶囊的宽高比）。 */
    fun coverRects(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Pair<android.graphics.Rect, android.graphics.RectF>? {
        val boxW = right - left
        val boxH = bottom - top
        if (boxW <= 0f || boxH <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return null
        val scale = max(boxW / bitmap.width, boxH / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val dst = android.graphics.RectF(
            left + (boxW - drawW) / 2f,
            top + (boxH - drawH) / 2f,
            left + (boxW + drawW) / 2f,
            top + (boxH + drawH) / 2f,
        )
        return android.graphics.Rect(0, 0, bitmap.width, bitmap.height) to dst
    }
}
