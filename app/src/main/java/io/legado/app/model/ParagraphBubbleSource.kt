package io.legado.app.model

import android.net.Uri
import android.util.Base64
import io.legado.app.help.config.BubbleSettings
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.util.LinkedHashMap
import java.util.Locale

/**
 * 段评气泡的图片来源解析。
 *
 * 支持两种来源，语义与上游（Legado-Arc）保持一致：
 * 1. 原生气泡：`dp:<数量>,{...}`，直接生成气泡，永不联网；
 * 2. 强制软件气泡：书源把段评入口画成一张图片（`style=TEXT` / `type` 是段评类型 / `click`
 *    里含 `showCmt(` 等），开启开关后改用气泡渲染。
 */
object ParagraphBubbleSource {

    const val NATIVE_PREFIX = "dp:"
    const val SCHEME_PREFIX = ParagraphBubbleRenderer.SCHEME_PREFIX

    private val forcedBubbleTypes = setOf(
        "qd",
        "fqpl",
        "fanqie",
        "cmt",
        "comment",
        "comments",
        "review",
        "paragraph",
        "paragraphcomment"
    )

    private val svgTextRegex = Regex("<text[^>]*>([^<]{1,16})</text>", RegexOption.IGNORE_CASE)
    private val textParamRegex = Regex(
        """(?:^|[?&,])(?:displayText|num|count|text|label)=([^&,\s]{1,48})""",
        RegexOption.IGNORE_CASE
    )
    private val colorParamRegex = Regex(
        """(?:^|[?&,])(?:displayColor|color)=([^&,\s]{1,32})""",
        RegexOption.IGNORE_CASE
    )

    data class Spec(
        val displayText: String,
        val status: String,
        val displayColor: String?
    )

    private const val MAX_CACHE_ENTRIES = 512
    private const val MAX_CACHED_SRC_LENGTH = 4096
    private val parseCacheLock = Any()
    private val parseCache = object : LinkedHashMap<String, Spec?>(
        MAX_CACHE_ENTRIES, 0.75f, true
    ) {}

    /**
     * 该 src 是否应该渲染成气泡；返回 null 表示按普通图片处理。
     *
     * 一张图在排版全链路里会被问四遍（选项解析 → 量尺寸 → 缓存键 → 真正渲染），
     * 每问一次都要解一遍书源塞进来的图片选项 JSON。这里按 src 缓存结果，
     * 开关状态进 key —— 强制软件气泡是全局开关，切换后必须重新判定。
     */
    fun parse(src: String): Spec? {
        val value = src.trim()
        if (value.isEmpty()) return null
        if (value.length > MAX_CACHED_SRC_LENGTH) return parseUncached(value)
        val key = if (BubbleSettings.forceSoftwareParagraphBubble) {
            "1|$value"
        } else {
            "0|$value"
        }
        synchronized(parseCacheLock) {
            if (parseCache.containsKey(key)) return parseCache[key]
        }
        return parseUncached(value).also { spec ->
            synchronized(parseCacheLock) {
                parseCache[key] = spec
                while (parseCache.size > MAX_CACHE_ENTRIES) {
                    val eldest = parseCache.entries.firstOrNull() ?: break
                    parseCache.remove(eldest.key)
                }
            }
        }
    }

    private fun parseUncached(value: String): Spec? {
        if (value.startsWith(SCHEME_PREFIX, ignoreCase = true)) return parseBubbleScheme(value)
        if (value.startsWith(NATIVE_PREFIX, ignoreCase = true)) return parseNative(value)
        return parseForced(value)
    }



    private fun parseBubbleScheme(src: String): Spec? {
        val query = src.substringAfter('?', "")
        if (query.isBlank()) return null
        val values = query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = pair.substringAfter('=', "")
            key.lowercase(Locale.ROOT) to Uri.decode(value)
        }.toMap()
        return buildSpec(
            displayText = values["displaytext"].orEmpty().ifBlank { values["num"].orEmpty() },
            status = values["status"].orEmpty(),
            displayColor = values["displaycolor"].orEmpty().ifBlank { values["color"].orEmpty() }
        )
    }

    private fun parseNative(src: String): Spec? {
        val payload = src.substring(NATIVE_PREFIX.length).trim()
        if (payload.isEmpty()) return null
        val optionIndex = payload.indexOf(",{")
        val count = if (optionIndex >= 0) {
            payload.substring(0, optionIndex)
        } else {
            payload
        }.trim()
        val options = readOptions(payload, optionIndex).orEmpty()
        return buildSpec(
            displayText = firstTextOption(options) ?: count.takeIf { it.isNotBlank() },
            status = options.valueIgnoreCase("status").orEmpty(),
            displayColor = colorFromOptions(options) ?: colorFromParam(src)
        )
    }

    private fun parseForced(src: String): Spec? {
        if (!BubbleSettings.forceSoftwareParagraphBubble) return null
        val options = readOptions(src, src.indexOf(",{")) ?: return null
        if (!isForcedCandidate(src, options)) return null
        return buildSpec(
            displayText = firstTextOption(options)
                ?: textFromParam(src)
                ?: textFromInlineSvg(src),
            status = options.valueIgnoreCase("status").orEmpty(),
            displayColor = colorFromOptions(options) ?: colorFromParam(src)
        )
    }

    /**
     * 起点的段评图标是把数字画在内嵌 SVG 里（`<text>3</text>`）+ base64/scheme 编码的字符串，
     * 书源没有给出 displayText 参数时，这里就是唯一的数字来源。
     */
    private fun textFromInlineSvg(src: String): String? {
        if (!src.startsWith("data:image/svg+xml", ignoreCase = true)) return null
        // 先掐掉尾随的 "{...}" 图片选项，否则编解码会在读到 JSON 时就失败
        val body = src.substringBefore(",{")
        val base64Encoded = "base64," in body
        val payload = if (base64Encoded) {
            body.substringAfter("base64,")
        } else {
            body.substringAfter(",")
        }.trim()
        if (payload.isEmpty()) return null
        val markup = if (base64Encoded) {
            runCatching { Base64.decode(payload, Base64.DEFAULT).toString(Charsets.UTF_8) }
                .getOrNull() ?: Uri.decode(payload)
        } else {
            Uri.decode(payload)
        }
        return svgTextRegex.find(markup)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun buildSpec(displayText: String?, status: String?, displayColor: String?): Spec? {
        val text = displayText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Spec(
            displayText = text,
            status = status?.trim()?.takeIf { it.isNotEmpty() } ?: "normal",
            displayColor = displayColor?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun isForcedCandidate(src: String, options: Map<String, String>): Boolean {
        val style = options.valueIgnoreCase("style")
        val styleText = style.equals("TEXT", ignoreCase = true)
        if (!style.isNullOrBlank() && !styleText) return false
        val type = options.valueIgnoreCase("type").orEmpty().lowercase(Locale.ROOT)
        if (type in forcedBubbleTypes) return true
        val click = listOfNotNull(
            options.valueIgnoreCase("click"),
            options.valueIgnoreCase("pclick")
        ).joinToString(separator = "\n").lowercase(Locale.ROOT)
        if (click.contains("showcmt(") ||
            click.contains("showcomment(") ||
            click.contains("paragraph")
        ) {
            return true
        }
        val head = src.substringBefore(",{").trimStart()
        return styleText && head.startsWith("data:image/svg+xml", ignoreCase = true)
    }

    private fun firstTextOption(options: Map<String, String>): String? {
        listOf("displayText", "num", "count", "text", "label").forEach { key ->
            options.valueIgnoreCase(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun colorFromOptions(options: Map<String, String>): String? {
        return options.valueIgnoreCase("displayColor")?.trim()?.takeIf { it.isNotEmpty() }
            ?: options.valueIgnoreCase("color")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun textFromParam(src: String): String? {
        return textParamRegex.find(src)?.groupValues?.getOrNull(1)?.let(Uri::decode)
    }

    private fun colorFromParam(src: String): String? {
        return colorParamRegex.find(src)?.groupValues?.getOrNull(1)?.let(Uri::decode)
    }

    private fun readOptions(source: String, optionIndex: Int): Map<String, String>? {
        val separator = if (optionIndex >= 0) {
            optionIndex
        } else {
            AnalyzeUrl.paramPattern.find(source)?.range?.first ?: return null
        }
        return GSON.fromJsonObject<Map<String, String>>(source.substring(separator + 1)).getOrNull()
    }

    private fun Map<String, String>.valueIgnoreCase(name: String): String? =
        get(name) ?: entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}
