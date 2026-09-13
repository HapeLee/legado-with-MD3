package io.legado.app.ui.widget.components.image.cover

// ============================================================================
// [FIX-AI] 本文件由 AI 助手（Chatbox）修改（2026-09-13）。
// 搜索 [FIX-AI] 可定位本文件全部改动点，每处均注明 原版行为 -> 修复后行为。
// 问题背景与完整清单见 LegadoMD3/fix/README.md。
// ============================================================================


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
    // [FIX-AI] 新增两个参数（原版无）：bookUrl 供别名缓存键；preferCache 标记书架类
    // “本地优先、绝不跑书源脚本”的请求。默认值保持原版行为不变。
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
            // [FIX-AI] 新增：透传别名键与书架优先标志给 CoverInterceptor/CoverFetcher
            extras[CoverExtras.BookUrl] = bookUrl
            extras[CoverExtras.PreferCache] = preferCache
        }
        .apply(configure)
        .build()
}
