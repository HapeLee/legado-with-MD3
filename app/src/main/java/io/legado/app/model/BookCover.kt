package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.help.CacheManager
import io.legado.app.help.DefaultData
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration
import kotlinx.coroutines.currentCoroutineContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import kotlin.random.Random

@Keep
object BookCover : KoinComponent {

    private const val coverRuleConfigKey = "legadoCoverRuleConfig"
    const val configFileName = "coverRule.json"
    private val coverAlbumUseCase: CoverAlbumUseCase by inject()
    private val shellSettingsGateway: AppShellSettingsGateway by inject()
    private val coverSettingsGateway: CoverSettingsGateway by inject()
    private val mangaSettingsGateway: MangaSettingsGateway by inject()
    private val imageLoader: ImageLoader by inject()

    private val isNightTheme: Boolean
        get() = when (shellSettingsGateway.currentSettings.themeMode) {
            "1" -> false
            "2" -> true
            else -> sysConfiguration.isNightMode
        }

    val defaultDrawable: Drawable
        @SuppressLint("UseCompatLoadingForDrawables")
        get() {
            val paths = coverAlbumUseCase.selectedImagePaths(isNightTheme)

            if (paths.isEmpty()) {
                return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)
            }

            val randomPath = paths[Random.nextInt(paths.size)]
            return kotlin.runCatching {
                BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
            }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
        }

    fun getRandomDefaultPath(
        seed: Any? = null,
        isNight: Boolean = isNightTheme
    ): String? {
        val paths = coverAlbumUseCase.selectedImagePaths(isNight)
        if (paths.isEmpty()) return null
        val random = if (seed != null) Random(seed.hashCode()) else Random
        return paths[random.nextInt(paths.size)]
    }

    // 缓存随机封面 Drawable，避免重复解码
    private val randomDrawableCache = mutableMapOf<String, Drawable>()

    fun getRandomDefaultDrawable(
        seed: Any? = null,
        isNight: Boolean = isNightTheme
    ): Drawable {
        val randomPath = getRandomDefaultPath(seed, isNight)
            ?: return appCtx.resources.getDrawable(R.drawable.image_cover_default, null)

        // 生成缓存键
        val cacheKey = "$randomPath-${isNight}"

        // 从缓存中获取，如果没有则解码并缓存
        val drawable = randomDrawableCache.getOrPut(cacheKey) {
            kotlin.runCatching {
                BitmapUtils.decodeBitmap(randomPath, 600, 900)!!.toDrawable(appCtx.resources)
            }.getOrDefault(appCtx.resources.getDrawable(R.drawable.image_cover_default, null))
        }

        // 返回克隆的实例并 mutate，防止多个 View 共享状态（如 bounds）导致显示异常
        return drawable.constantState?.newDrawable()?.mutate() ?: drawable
    }

    /** Android service callers use Coil directly instead of the legacy Glide wrapper. */
    suspend fun loadCoverBitmap(context: Context, path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(path)
                .build()
        )
        return (result as? SuccessResult)?.image?.toBitmap()
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
