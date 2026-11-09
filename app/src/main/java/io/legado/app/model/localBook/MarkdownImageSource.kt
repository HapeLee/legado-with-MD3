package io.legado.app.model.localBook

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
        //只接受 base64 载荷；data:text/html,<js>…</js> 这类可直接执行的形态一律拒绝
        private val base64DataUriRegex = Regex("^data:[^,]*;base64,([A-Za-z0-9+/=\\s]+)$")
        private val mimeDecoder = Base64.getMimeDecoder()

        fun parse(src: String): MarkdownImageSource {
            val trimmed = src.trim()
            if (trimmed.isEmpty()) return Unsupported
            base64DataUriRegex.matchEntire(trimmed)?.let { match ->
                val bytes = runCatching { mimeDecoder.decode(match.groupValues[1]) }.getOrNull()
                    ?: return Unsupported
                return if (bytes.isEmpty()) Unsupported else DataUri(bytes)
            }
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                return Unsupported
            }
            //HttpUrl 解析会拒绝空白与 `<`、`>`、`{`、`}` 等规则语法字符；
            //先补空格再解析，只是容忍未编码的文件名空格，不改变地址语义。
            val parsed = trimmed.toHttpUrlOrNull()
                ?: trimmed.replace(" ", "%20").toHttpUrlOrNull()
                ?: return Unsupported
            return Http(parsed.toString())
        }
    }
}
