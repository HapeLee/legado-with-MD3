package io.legado.app.model.localBook

import io.legado.app.utils.MD5Utils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64

/**
 * Markdown 正文里出现的图片地址。
 *
 * 这些地址来自书籍内容本身，是外部数据而不是书源规则：只允许按普通资源下载，绝不能交给
 * [io.legado.app.model.analyzeRule.AnalyzeUrl]——它在 initUrl 阶段会执行地址里的
 * `<js>`/`@js:`/`{{}}`，等于让文档内容直接执行脚本（可读写书源运行时绑定）。
 * 相对路径不在这里处理，由 [MarkdownFile.getImage] 按书籍所在目录解析。
 */
sealed class MarkdownImageSource {

    /** 普通网络图片地址；[url] 已通过 HTTP URL 解析校验。 */
    data class Http(val url: String) : MarkdownImageSource()

    /** base64 形式的 data URI，直接给出解码后的图片字节。 */
    class DataUri(val bytes: ByteArray) : MarkdownImageSource()

    /** 既不是可解析的 http(s) 地址，也不是 base64 data URI：调用方按占位图降级。 */
    object Unsupported : MarkdownImageSource()

    companion object {
        //单张图片上限：内嵌 base64 与网络响应体都按这个口径截断，避免撑爆正文与 .nb 缓存
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

        /** 内嵌图片短占位前缀；HTML 与纯文本通道都不再携带整段 base64 载荷。 */
        const val INLINE_PREFIX = "md-inline:"

        //进程内注册表：转换时登记一次，取图时按占位 key 找回字节；达到上限就不再改写
        private const val INLINE_CACHE_BYTES = 32 * 1024 * 1024
        private val inlineImages = HashMap<String, ByteArray>()
        private var inlineImageBytes = 0L

        //只接受 base64 载荷；data:text/html,<js>…</js> 这类可直接执行的形态一律拒绝
        private val base64DataUriRegex = Regex("^data:[^,]*;base64,([A-Za-z0-9+/=\\s]+)$")
        private val mimeDecoder = Base64.getMimeDecoder()

        /**
         * 把 base64 data URI 换成稳定的短占位。注册表只增不逐出，保证已写进 HTML 的 key
         * 在本次进程内始终可解析；超过上限时返回 null，调用方保留原 src（不回退功能）。
         */
        @Synchronized
        fun inlineDataUri(src: String): String? {
            if (src.startsWith(INLINE_PREFIX, true)) return null
            val parsed = parse(src)
            if (parsed !is DataUri) return null
            val key = INLINE_PREFIX + MD5Utils.md5Encode16(src)
            if (inlineImages.containsKey(key)) return key
            if (inlineImageBytes + parsed.bytes.size > INLINE_CACHE_BYTES) return null
            inlineImages[key] = parsed.bytes
            inlineImageBytes += parsed.bytes.size
            return key
        }

        @Synchronized
        private fun inlineBytes(key: String): ByteArray? = inlineImages[key]

        fun parse(src: String): MarkdownImageSource {
            val trimmed = src.trim()
            if (trimmed.isEmpty()) return Unsupported
            if (trimmed.startsWith(INLINE_PREFIX, true)) {
                return inlineBytes(trimmed)?.let { DataUri(it) } ?: Unsupported
            }
            base64DataUriRegex.matchEntire(trimmed)?.let { match ->
                val payload = match.groupValues[1]
                if (payload.length > MAX_IMAGE_BYTES) return Unsupported
                val bytes = runCatching { mimeDecoder.decode(payload) }.getOrNull()
                    ?: return Unsupported
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return Unsupported
                return DataUri(bytes)
            }
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                return Unsupported
            }
            //规则语法字符在普通 URL 里必须百分号转义，出现即说明地址不是普通资源：
            //HttpUrl 会把它们编码掉，所以在解析前显式拒绝，避免"看起来能下载其实带脚本"。
            if (trimmed.any { it == '<' || it == '>' || it == '{' || it == '}' } ||
                trimmed.contains("@js:", true)
            ) {
                return Unsupported
            }
            //HttpUrl 解析会拒绝空白等非法字符；
            //先补空格再解析，只是容忍未编码的文件名空格，不改变地址语义。
            val parsed = trimmed.toHttpUrlOrNull()
                ?: trimmed.replace(" ", "%20").toHttpUrlOrNull()
                ?: return Unsupported
            return Http(parsed.toString())
        }
    }
}
