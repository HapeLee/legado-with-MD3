package io.legado.app.feature.reader.core.layout

import java.net.URLDecoder
import java.util.Base64

/**
 * 从书源的段评图里读评论数。
 *
 * 这类书源把数字画在 SVG 里（JSON 里没有 `count`）：
 * - 内嵌图：`data:image/svg+xml;base64,...`，本地解码就能读到；
 * - 服务端图：URL 指向返回 SVG 文本的接口，由平台层请求后交给 [fromSvgText]。
 */
object ReviewCountParser {
    private const val DATA_URI_SVG = "data:image/svg+xml"

    private val textTag = Regex("<text[^>]*>([^<]{1,6})</text>", RegexOption.IGNORE_CASE)

    /** 去掉 `,{...}` 里的 imageOptions，只留图片地址。 */
    fun imageUrl(source: String): String = source.substringBefore(",{")

    /** 段评图判据：内嵌 SVG 且能本地读出评论数字。服务端图片走 [serverReviewUrl]。 */
    fun isReviewSvg(source: String): Boolean = fromDataUri(source) != null

    /** 服务端段评接口：只认确定的接口名，避免把神评/本章说当成段评。 */
    fun serverReviewUrl(source: String): String? {
        val uri = imageUrl(source)
        if (!uri.startsWith("http", ignoreCase = true)) return null
        return uri.takeIf {
            it.contains("chapter_review", ignoreCase = true) ||
                it.contains("para_review", ignoreCase = true)
        }
    }

    /** 内嵌 SVG 的 data URI：本机解码成 SVG 文本，不产生任何请求。 */
    fun decodeDataUri(source: String): String? {
        val uri = imageUrl(source)
        if (!uri.startsWith(DATA_URI_SVG, ignoreCase = true)) return null
        val payload = uri.substringAfter(',', "")
        if (payload.isEmpty()) return null
        return if (uri.contains(";base64", ignoreCase = true)) {
            runCatching { String(Base64.getDecoder().decode(payload.trim())) }.getOrNull()
        } else {
            runCatching { URLDecoder.decode(payload, "UTF-8") }.getOrNull()
        }
    }

    /** 内嵌 SVG 的 data URI：本机解码后读数字，不产生任何请求。 */
    fun fromDataUri(source: String): Int? = decodeDataUri(source)?.let(::fromSvgText)

    /** 取 SVG 里第一个 `<text>` 的数字；`99+` 按 99 处理。 */
    fun fromSvgText(svg: String): Int? =
        textTag.find(svg)
            ?.groupValues?.get(1)
            ?.trim()
            ?.removeSuffix("+")
            ?.toIntOrNull()
}
