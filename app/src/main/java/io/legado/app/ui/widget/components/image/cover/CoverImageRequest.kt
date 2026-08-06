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
        }
        .apply(configure)
        .build()
}
