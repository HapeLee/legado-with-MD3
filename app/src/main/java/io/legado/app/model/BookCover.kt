package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.help.CacheManager
import io.legado.app.help.DefaultData
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.BlurTransformation
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.currentCoroutineContext
import splitties.init.appCtx
import java.io.File
import kotlin.random.Random

@Keep
object BookCover {

    private const val coverRuleConfigKey = "legadoCoverRuleConfig"
    const val configFileName = "coverRule.json"

    val defaultDrawable: Drawable
        @SuppressLint("UseCompatLoadingForDrawables")
        get() {
            val isNightTheme = AppConfig.isNightTheme
            val key = if (isNightTheme) PreferKey.defaultCoverDark else PreferKey.defaultCover
            val paths = appCtx.getPrefString(key)?.split(",")?.filter { it.isNotBlank() }

            if (paths.isNullOrEmpty()) {
                return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)
            }

            val randomPath = paths[Random.nextInt(paths.size)]
            return kotlin.runCatching {
                BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
            }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
        }

    // 兼容旧代码，空实现
    fun upDefaultCover() {}

    fun getRandomDefaultPath(
        seed: Any? = null,
        isNight: Boolean = AppConfig.isNightTheme
    ): String? {
        val key = if (isNight) PreferKey.defaultCoverDark else PreferKey.defaultCover
        val paths = appCtx.getPrefString(key)?.split(",")?.filter { it.isNotBlank() }
        if (paths.isNullOrEmpty()) return null
        val random = if (seed != null) Random(seed.hashCode()) else Random
        return paths[random.nextInt(paths.size)]
    }

    fun getRandomDefaultDrawable(
        seed: Any? = null,
        isNight: Boolean = AppConfig.isNightTheme
    ): Drawable {
        val randomPath = getRandomDefaultPath(seed, isNight)
            ?: return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)
        return kotlin.runCatching {
            BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
        }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
    }

    /**
     * 加载封面
     */
    fun load(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        onLoadFinish: (() -> Unit)? = null,
    ): RequestBuilder<Drawable> {
        val currentDefault = getRandomDefaultDrawable()
        if (AppConfig.useDefaultCover) {
            return ImageLoader.load(context, currentDefault)
                .centerCrop()
        }
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        var builder = ImageLoader.load(context, path)
            .apply(options)
        if (onLoadFinish != null) {
            builder = builder.addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>,
                    isFirstResource: Boolean,
                ): Boolean {
                    onLoadFinish.invoke()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    onLoadFinish.invoke()
                    return false
                }
            })
        }
        return builder.placeholder(currentDefault)
            .error(currentDefault)
            .centerCrop()
    }



    /**
     * 加载漫画图片
     */
    fun loadManga(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        transformation: Transformation<Bitmap>? = null,
    ): RequestBuilder<Drawable> {
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            .set(OkHttpModelLoader.mangaOption, true)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        var builder = ImageLoader.load(context, path)
            .apply(options)
            .override(context.resources.displayMetrics.widthPixels, SIZE_ORIGINAL)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(true)
        if (transformation != null) {
            builder = builder.transform(transformation)
        }
        builder = if (AppConfig.disableMangaCrossFade) {
            builder
        } else {
            builder.transition(DrawableTransitionOptions.withCrossFade())
        }

        return builder
    }


    fun preloadManga(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): RequestBuilder<File?> {
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            .set(OkHttpModelLoader.mangaOption, true)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        return Glide.with(context)
            .downloadOnly()
            .apply(options)
            .load(path)
    }

    /**
     * 加载模糊封面
     */
    fun loadBlur(
        context: Context,
        path: String?,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
    ): RequestBuilder<Drawable> {
        val currentDefault = getRandomDefaultDrawable()
        val loadBlur = ImageLoader.load(context, currentDefault)
            .transform(BlurTransformation(25), CenterCrop())
        if (AppConfig.useDefaultCover) {
            return loadBlur
        }
        var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
        if (sourceOrigin != null) {
            options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
        }
        return ImageLoader.load(context, path)
            .apply(options)
            .transform(BlurTransformation(25), CenterCrop())
            .transition(DrawableTransitionOptions.withCrossFade(1500))
            .thumbnail(loadBlur)
    }

    fun getCoverRule(): CoverRule {
        return getConfig() ?: DefaultData.coverRule
    }

    fun getConfig(): CoverRule? {
        return GSON.fromJsonObject<CoverRule>(CacheManager.get(coverRuleConfigKey))
            .getOrNull()
    }

    suspend fun searchCover(book: Book): String? {
        val config = getCoverRule()
        if (!config.enable || config.searchUrl.isBlank() || config.coverRule.isBlank()) {
            return null
        }
        val analyzeUrl = AnalyzeUrl(
            config.searchUrl,
            book.name,
            source = config,
            coroutineContext = currentCoroutineContext(),
            hasLoginHeader = false
        )
        val res = analyzeUrl.getStrResponseAwait()
        val analyzeRule = AnalyzeRule(book)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setContent(res.body)
        analyzeRule.setRedirectUrl(res.url)
        return analyzeRule.getString(config.coverRule, isUrl = true)
    }

    fun saveCoverRule(config: CoverRule) {
        val json = GSON.toJson(config)
        saveCoverRule(json)
    }

    fun saveCoverRule(json: String) {
        CacheManager.put(coverRuleConfigKey, json)
    }

    fun delCoverRule() {
        CacheManager.delete(coverRuleConfigKey)
    }

    @Keep
    data class CoverRule(
        var enable: Boolean = true,
        var searchUrl: String,
        var coverRule: String,
        override var concurrentRate: String? = null,
        override var loginUrl: String? = null,
        override var loginUi: String? = null,
        override var header: String? = null,
        override var jsLib: String? = null,
        override var enabledCookieJar: Boolean? = false,
    ) : BaseSource {

        override fun getTag(): String {
            return searchUrl
        }

        override fun getKey(): String {
            return searchUrl
        }
    }

}
