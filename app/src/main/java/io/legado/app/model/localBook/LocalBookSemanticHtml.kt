package io.legado.app.model.localBook

import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.encodeURI
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URI
import java.net.URLDecoder

/**
 * Shared DOM helpers for the local-book rich-text channel.
 *
 * All functions are pure Kotlin + Jsoup so they stay unit-testable without Android.
 * Preserves structural HTML semantics while keeping character offsets stable.
 */
object LocalBookSemanticHtml {

    /** The pre-rich-channel local-book transport. Decoding happens once at this boundary. */
    fun plainText(html: String, decodeEntities: Boolean = true): String {
        val text = HtmlFormatter.formatKeepImg(html)
        return if (decodeEntities) StringEscapeUtils.unescapeHtml4(
            text.replace("&lt;img", "&lt; img", ignoreCase = true)) else text
    }

    private val allowedImgAttrs = setOf("src", "alt", "width", "height", "class", "style")

    private val allowedCssProps = setOf("text-align", "font-weight", "font-style", "text-decoration")

    data class CssRule(
        val tag: String?,
        val id: String?,
        val classes: Set<String>,
        val declarations: Map<String, String>,
        val specificity: Int,
        val order: Int
    )

    /**
     * Cut body content to [startFragmentId, endFragmentId) using DOM order.
     * Uses hidden anchors before applying the legacy inline-hidden filter.
     */
    fun cutChapterBody(body: Element, startFragmentId: String?, endFragmentId: String?) {
        val startId = startFragmentId?.let(::decodeFragment)
        val endId = endFragmentId?.let(::decodeFragment)
        val startEl = if (!startId.isNullOrBlank()) {
            findByIdOrName(body, startId)
        } else null
        val endEl = if (!endId.isNullOrBlank() && endId != startId) {
            findByIdOrName(body, endId)
        } else null
        if (startEl != null) {
            trimBefore(startEl, body)
        }
        if (endEl != null) {
            val attached = if (endEl.parent() == null) findByIdOrName(body, endId!!) else endEl
            if (attached != null) {
                trimAfter(attached, body)
            }
        }
        body.select("[style*=display:none]").remove()
    }

    private fun findByIdOrName(body: Element, fragmentId: String): Element? {
        body.getElementById(fragmentId)?.let { return it }
        return body.getElementsByAttributeValue("name", fragmentId).firstOrNull()
    }

    private fun trimBefore(startEl: Element, body: Element) {
        var current: Element? = startEl
        while (current != null && current != body) {
            val parent = current.parent() ?: break
            val idx = current.siblingIndex()
            if (idx > 0) {
                val nodes = parent.childNodes()
                for (i in idx - 1 downTo 0) {
                    nodes[i].remove()
                }
            }
            current = parent
        }
    }

    private fun trimAfter(endEl: Element, body: Element) {
        var current: Element? = endEl
        while (current != null && current != body) {
            val parent = current.parent() ?: break
            val nodes = parent.childNodes()
            val idx = current.siblingIndex()
            for (i in nodes.size - 1 downTo idx + 1) {
                nodes[i].remove()
            }
            current = parent
        }
        var ancestor: Element? = endEl.parent()
        endEl.remove()
        while (ancestor != null && ancestor != body &&
            ancestor.text().isBlank() && ancestor.select("img, image, hr").isEmpty()
        ) {
            val up = ancestor.parent()
            ancestor.remove()
            ancestor = up
        }
    }

    /** Keep only semantic image attributes; drop event handlers and layout hacks. */
    fun sanitizeImages(elements: Elements, preserveAppearance: Boolean = true) {
        elements.select("img").forEach { img ->
            if (!preserveAppearance) {
                if (img.attributesSize() > 1) {
                    val src = img.attr("src")
                    img.clearAttributes()
                    img.attr("src", src)
                }
                return@forEach
            }
            val kept = allowedImgAttrs.associateWith { img.attr(it) }
                .filterValues { it.isNotEmpty() }
            val src = img.attr("src")
            img.clearAttributes()
            if (src.isNotEmpty()) img.attr("src", src)
            kept.forEach { (k, v) ->
                if (k != "src") img.attr(k, v)
            }
        }
    }

    /** Parse simple selectors (tag, #id, .class, tag.class) with whitelisted props. */
    fun parseCssRules(cssText: String, baseOrder: Int = 0): List<CssRule> {
        return runCatching {
            val cleaned = cssText.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            val rules = mutableListOf<CssRule>()
            var order = baseOrder
            val blockRegex = Regex("([^{}]+)\\{([^}]*)\\}")
            for (match in blockRegex.findAll(cleaned)) {
                val selectorGroup = match.groupValues[1].trim()
                val body = match.groupValues[2]
                if (selectorGroup.startsWith("@")) continue
                val declarations = parseDeclarations(body)
                if (declarations.isEmpty()) continue
                for (rawSelector in selectorGroup.split(",")) {
                    val selector = rawSelector.trim()
                    if (selector.isEmpty()) continue
                    if (selector.contains(" ") || selector.contains(">") ||
                        selector.contains("+") || selector.contains("~") ||
                        selector.contains("[") || selector.contains(":")
                    ) {
                        continue
                    }
                    val (tag, id, classes) = parseSimpleSelector(selector) ?: continue
                    val specificity = (if (id != null) 100 else 0) +
                        classes.size * 10 + (if (tag != null) 1 else 0)
                    rules += CssRule(tag, id, classes, declarations, specificity, order++)
                }
            }
            rules
        }.getOrDefault(emptyList())
    }

    private fun parseSimpleSelector(selector: String): Triple<String?, String?, Set<String>>? {
        if (selector.isEmpty() || selector == "*" || selector.contains("*")) return null
        var tag: String? = null
        var id: String? = null
        val classes = mutableSetOf<String>()
        val parts = selector.split(Regex("(?=[.#])")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (part in parts) {
            when {
                part.startsWith("#") -> {
                    if (id != null) return null
                    id = part.drop(1).lowercase()
                    if (id.isEmpty() || !id.matches(Regex("[a-z0-9_-]+"))) return null
                }
                part.startsWith(".") -> {
                    val cls = part.drop(1).lowercase()
                    if (cls.isEmpty()) return null
                    classes += cls
                }
                else -> {
                    tag = part.lowercase()
                    if (!tag.matches(Regex("[a-z][a-z0-9]*"))) return null
                }
            }
        }
        if (tag == null && id == null && classes.isEmpty()) return null
        return Triple(tag, id, classes)
    }

    private fun parseDeclarations(body: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (decl in body.split(";")) {
            val idx = decl.indexOf(":")
            if (idx < 0) continue
            val prop = decl.substring(0, idx).trim().lowercase()
            val value = decl.substring(idx + 1).trim()
            if (prop in allowedCssProps && value.isNotEmpty()) {
                out[prop] = value
            }
        }
        return out
    }

    /** Apply only this content document's stylesheets, in document order. */
    fun inlineDocumentCssSemantics(
        document: Document,
        maxRules: Int = 2000,
        loadStylesheet: (String) -> String?,
    ) {
        val rules = mutableListOf<CssRule>()
        for (element in document.select("link, style")) {
            val css = when {
                element.normalName() == "style" -> element.data()
                element.attr("rel").split(Regex("\\s+")).any { it.equals("stylesheet", true) } ->
                    element.attr("href").takeIf { it.isNotBlank() }?.let(loadStylesheet)
                else -> null
            } ?: continue
            rules += parseCssRules(css, rules.size)
            if (rules.size > maxRules) break
        }
        inlineCssSemantics(document.body(), rules)
    }

    /**
     * Merge matching CSS rules into each element's inline style (whitelisted props only).
     * Inline style wins over CSS; later rules win on equal specificity.
     */
    fun inlineCssSemantics(docBody: Element, cssRules: List<CssRule>) {
        if (cssRules.isEmpty()) {
            normalizeLegacyAlignment(docBody)
            return
        }
        val all = docBody.allElements
        for (el in all) {
            // Preserve the legacy inline-hidden flag until chapter boundaries have been used.
            if (el.attr("style").contains("display:none", ignoreCase = true)) continue
            val tag = el.tagName().lowercase()
            val id = el.id().lowercase().takeIf { it.isNotEmpty() }
            val classes = el.classNames().map { it.lowercase() }.toSet()
            val matched = cssRules.filter { rule ->
                (rule.tag == null || rule.tag == tag) &&
                    (rule.id == null || rule.id == id) &&
                    (rule.classes.isEmpty() || classes.containsAll(rule.classes))
            }.sortedWith(compareBy({ it.specificity }, { it.order }))
            if (matched.isEmpty()) continue
            val merged = mutableMapOf<String, String>()
            for ((_, _, _, declarations) in matched) {
                for ((k, v) in declarations) merged[k] = v
            }
            val inline = parseDeclarations(el.attr("style"))
            for ((k, v) in inline) merged[k] = v
            if (merged.isEmpty()) continue
            el.attr("style", merged.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        }
        normalizeLegacyAlignment(docBody)
    }

    private fun normalizeLegacyAlignment(body: Element) {
        body.select("center").forEach { el ->
            val existing = el.attr("style")
            if (!existing.contains("text-align", ignoreCase = true)) {
                el.attr("style", (existing.trimEnd(';') + "; text-align: center").trimStart(';', ' '))
            }
            el.tagName("div")
        }
        body.select("[align]").forEach { el ->
            val align = el.attr("align").lowercase()
            val mapped = when (align) {
                "center", "middle" -> "center"
                "right", "end" -> "right"
                "left", "start" -> "left"
                "justify" -> "justify"
                else -> null
            }
            if (mapped != null) {
                val existing = el.attr("style")
                if (!existing.contains("text-align", ignoreCase = true)) {
                    el.attr("style", (existing.trimEnd(';') + "; text-align: $mapped").trimStart(';', ' '))
                }
            }
            el.removeAttr("align")
        }
    }

    private val schemeRegex = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
    private val mobiLinkRegex = Regex("^(filepos:[0-9]+|kindle:pos:fid:[0-9a-v]+:off:[0-9a-v]+)$", RegexOption.IGNORE_CASE)
    private val mobiChapterPrefixRegex = Regex("^(?:skip:)?\\d+:")

    enum class LinkKind { INTERNAL, EXTERNAL, UNSUPPORTED }

    /** URI fragments use percent encoding; a literal '+' is not a form-encoded space. */
    fun decodeFragment(fragment: String): String = runCatching {
        URLDecoder.decode(fragment.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(fragment)

    fun resolveReaderLink(href: String, localBook: Boolean, chapterUrl: String): String? {
        val link = href.trim()
        return when (classifyLink(link)) {
            LinkKind.UNSUPPORTED -> null
            LinkKind.EXTERNAL -> if (link.startsWith("//")) "https:$link" else link
            LinkKind.INTERNAL -> if (localBook) link else runCatching {
                // Chapter identities may carry Legado's trailing URL-options JSON.
                val baseUrl = chapterUrl.split(Regex("\\s*,\\s*(?=\\{)"), limit = 2).first()
                val base = baseUrl.replace(" ", "%20")
                val relative = link.replace(" ", "%20")
                // URI.resolve treats a query-only reference as a directory URL.
                // A browser reference instead keeps the current resource's path.
                val resolved = if (relative.startsWith("?")) {
                    URI(base.substringBefore('#').substringBefore('?') + relative)
                } else URI(base).resolve(URI(relative))
                resolved.toString()
                    .takeIf { classifyLink(it) == LinkKind.EXTERNAL }
            }.getOrNull()
        }
    }

    fun classifyLink(href: String): LinkKind {
        val link = href.trim()
        if (link.isEmpty() || link.any { it.code < 0x20 }) return LinkKind.UNSUPPORTED
        if (link.startsWith("//")) return LinkKind.EXTERNAL
        val scheme = schemeRegex.find(link)?.value?.dropLast(1)?.lowercase()
            ?: return LinkKind.INTERNAL
        return when {
            scheme in setOf("http", "https", "mailto", "tel") -> LinkKind.EXTERNAL
            // Existing MOBI/KF8 chapter identities must remain navigable.
            mobiLinkRegex.matches(link) -> LinkKind.INTERNAL
            else -> LinkKind.UNSUPPORTED
        }
    }

    fun isExternalLink(href: String): Boolean = classifyLink(href) == LinkKind.EXTERNAL

    fun resolveRelativeHref(baseResourceHref: String, rawHref: String): String? {
        val trimmed = rawHref.trim()
        when (classifyLink(trimmed)) {
            LinkKind.UNSUPPORTED -> return null
            LinkKind.EXTERNAL -> return trimmed
            LinkKind.INTERNAL -> if (schemeRegex.containsMatchIn(trimmed)) return trimmed
        }
        val basePath = baseResourceHref.substringBefore("#").trim()
        if (trimmed.startsWith("#")) return if (basePath.isEmpty()) trimmed else "$basePath$trimmed"
        val pathPart = trimmed.substringBefore("#")
        val fragment = trimmed.substringAfter("#", "")
        val resolvedPath = runCatching {
            URLDecoder.decode(URI(basePath.encodeURI()).resolve(pathPart.encodeURI()).toString(), "UTF-8")
        }.getOrDefault(pathPart).removePrefix("/")
        return if (fragment.isNotEmpty()) "$resolvedPath#$fragment" else resolvedPath
    }

    suspend fun resolveInternalLinkTarget(
        rawLink: String,
        chapters: List<BookChapter>,
        currentChapterIndex: Int = -1,
        anchorPositions: suspend (BookChapter) -> Map<String, Int> = { emptyMap() },
    ): Pair<Int, Int>? {
        val decoded = decodeFragment(rawLink.trim())
        if (classifyLink(decoded) != LinkKind.INTERNAL || chapters.isEmpty()) return null
        val link = decoded.removePrefix("/").removePrefix("./")
        fun canonicalUrl(ch: BookChapter): String =
            decodeFragment(ch.url.replace(mobiChapterPrefixRegex, "")).removePrefix("/")

        val resolvedLink = if (link.startsWith("#")) {
            val curBase = chapters.firstOrNull { it.index == currentChapterIndex }
                ?.let { canonicalUrl(it).substringBefore("#") }
                .orEmpty()
            "$curBase$link".removePrefix("/")
        } else link
        val linkBase = resolvedLink.substringBefore("#")

        fun matches(candidate: String, target: String): Boolean =
            candidate.equals(target, ignoreCase = true) ||
                candidate.endsWith("/$target", ignoreCase = true)

        // Directory entries already identify a chapter boundary, including MOBI identities.
        val exact = chapters.filter { matches(canonicalUrl(it), resolvedLink) }
        val exactTargets = exact.filterNot { it.isVolume }.ifEmpty { exact }
        if (exactTargets.isNotEmpty()) return exactTargets.singleOrNull()?.let { it.index to 0 }

        val candidates = chapters.filterNot { it.isVolume }
            .filter { matches(canonicalUrl(it).substringBefore("#"), linkBase) }
        val fragment = resolvedLink.substringAfter('#', "")
        if (fragment.isEmpty()) {
            return (candidates.firstOrNull { it.index == currentChapterIndex } ?: candidates.firstOrNull())
                ?.let { it.index to 0 }
        }
        var target: Pair<Int, Int>? = null
        for (chapter in candidates) {
            val position = anchorPositions(chapter)[fragment] ?: continue
            if (target != null) return null // Duplicate anchors across slices are ambiguous.
            target = chapter.index to position
        }
        return target
    }
}
