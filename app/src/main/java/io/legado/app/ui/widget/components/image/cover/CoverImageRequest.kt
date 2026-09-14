package io.legado.app.ui.widget.components.image.cover

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.legado.app.help.coil.CoverExtras

fun buildCoverImageRequest(
    context: Context,
    data: Any?,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    crossfade: Boolean = true,
    memoryCacheKey: String? = null,
    // bookUrl 供别名缓存键；preferCache 标记书架类
    // “本地优先、绝不跑书源脚本”的请求。
    bookUrl: String? = null,
    preferCache: Boolean = false,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(data)
        .crossfade(crossfade)
        .apply {
            if (memoryCacheKey != null) {
                memoryCacheKey(memoryCacheKey)
                placeholderMemoryCacheKey(memoryCacheKey)
            }
            extras[CoverExtras.SourceOrigin] = sourceOrigin
            extras[CoverExtras.LoadOnlyWifi] = loadOnlyWifi
            // 透传别名键与书架优先标志给 CoverInterceptor/CoverFetcher
            extras[CoverExtras.BookUrl] = bookUrl
            extras[CoverExtras.PreferCache] = preferCache
        }
        .apply(configure)
        .build()
}
