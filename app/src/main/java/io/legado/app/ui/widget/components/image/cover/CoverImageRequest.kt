package io.legado.app.ui.widget.components.image.cover

import android.content.Context
import coil.request.ImageRequest

fun buildCoverImageRequest(
    context: Context,
    data: Any?,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    crossfade: Boolean = true,
    memoryCacheKey: String? = null,
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
        }
        .setParameter("sourceOrigin", sourceOrigin)
        .setParameter("loadOnlyWifi", loadOnlyWifi)
        .apply(configure)
        .build()
}
