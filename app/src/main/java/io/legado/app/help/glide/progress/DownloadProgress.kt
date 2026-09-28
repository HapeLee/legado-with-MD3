package io.legado.app.help.glide.progress

/**
 * 图片下载进度事件。
 *
 * [url] 是「原始图片地址」（书源规则解析前的地址），与阅读页持有的 page.imageUrl 一致，
 * 由 [ProgressUrlTag] 显式带到 OkHttp 请求上，不依赖最终 URL 的字符串匹配。
 */
data class DownloadProgress(
    val url: String,
    /** 0..100；仅当 [totalBytes] > 0 时才会有事件产生。 */
    val percentage: Int,
    val bytesRead: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
)

/**
 * OkHttp 请求 tag：告诉进度拦截器这个请求的进度应该回报给哪个 URL。
 *
 * 漫画正文的 URL 会被书源规则（AnalyzeUrl）改写成带 token 的最终地址，
 * 直接用 `request.url` 做 key 会与阅读页持有的原始地址对不上。
 */
data class ProgressUrlTag(val url: String)
