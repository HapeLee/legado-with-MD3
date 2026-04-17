package io.legado.app.ui.widget.components.cover

import android.content.Context
import coil.request.ImageRequest

fun buildCoverImageRequest(
    context: Context,
    data: Any?,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    crossfade: Boolean = true,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(data)
        .crossfade(crossfade)
        .setParameter("sourceOrigin", sourceOrigin)
        .setParameter("loadOnlyWifi", loadOnlyWifi)
        .apply(configure)
        .build()
}
