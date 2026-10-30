package io.legado.app.model.localBook

import io.legado.app.data.entities.BookChapter
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.encodeURI
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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

    private val allowedImgAttrs = setOf("src", "alt", "width", "height", "class", "style")

    private val complexTags = setOf(
        "blockquote", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6",
        "pre", "hr", "table", "thead", "tbody", "tr", "td", "th", "figure",
        "figcaption", "aside", "dl", "dt", "dd"
    )

    private val blockTags = setOf(
        "p", "div", "section", "article", "main", "header", "footer",
        "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "pre",
        "blockquote", "table", "tr", "td", "th", "figure", "figcaption",
        "center", "aside", "dl", "dt", "dd", "hr"
    )

    private val containerTags = setOf(
        "div", "section", "article", "main", "header", "footer"
    )

    private const val inlineSemanticSelector =
        "a[href], [id], a[name], ruby, b, strong, i, em, cite, dfn, u, ins, s, del, strike, " +
            "code, kbd, samp, tt, sup, sub, big, small, br, " +
            "[style*=font-weight], [style*=font-style], [style*=text-decoration]"

    private const val blockSemanticSelector =
        "blockquote, ul, ol, h1, h2, h3, h4, h5, h6, pre, hr, table, figure, figcaption, aside, dl"

    private val allowedCssProps = setOf(
        "text-align", "text-indent",
        "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
        "border", "border-left", "border-right", "border-top", "border-bottom",
        "font-weight", "font-style", "font-size", "display"
    )

    private val displayNoneRegex = Regex("(^|;)\\s*display\\s*:\\s*none\\b", RegexOption.IGNORE_CASE)
    private val textAlignRegex = Regex("text-align\\s*:\\s*([a-z]+)", RegexOption.IGNORE_CASE)

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
     * Handles single-line compressed HTML and empty anchors without string surgery.
     */
    fun cutFragment(body: Element, startFragmentId: String?, endFragmentId: String?) {
        if (startFragmentId.isNullOrBlank() && endFragmentId.isNullOrBlank()) return
        val startEl = if (!startFragmentId.isNullOrBlank()) {
            findByIdOrName(body, startFragmentId)
        } else null
        val endEl = if (!endFragmentId.isNullOrBlank() && endFragmentId != startFragmentId) {
            findByIdOrName(body, endFragmentId)
        } else null
        if (startEl != null) {
            trimBefore(startEl, body)
        }
        if (endEl != null) {
            val attached = if (endEl.parent() == null) findByIdOrName(body, endFragmentId!!) else endEl
            if (attached != null) {
                trimAfter(attached, body)
            }
        }
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
    fun sanitizeImages(elements: Elements) {
        elements.select("img").forEach { img ->
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

    /** Remove elements hidden via inline or CSS-inlined `display: none` (with or without spaces). */
    fun removeHiddenElements(root: Element) {
        root.select("[style]").forEach { el ->
            if (displayNoneRegex.containsMatchIn(el.attr("style"))) {
                el.remove()
            }
        }
    }

    fun removeHiddenElements(elements: Elements) {
        elements.forEach(::removeHiddenElements)
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

    private fun parseStyleAttr(style: String): Map<String, String> {
        return parseDeclarations(style)
    }

    /** Apply only this content document's stylesheets, in document order. */
    fun inlineDocumentCssSemantics(document: Document, loadStylesheet: (String) -> String?) {
        val rules = mutableListOf<CssRule>()
        for (element in document.select("link, style")) {
            val css = when {
                element.normalName() == "style" -> element.data()
                element.attr("rel").split(Regex("\\s+")).any { it.equals("stylesheet", true) } ->
                    element.attr("href").takeIf { it.isNotBlank() }?.let(loadStylesheet)
                else -> null
            } ?: continue
            rules += parseCssRules(css, rules.size)
            if (rules.size > 2000) break
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
            removeHiddenElements(docBody)
            return
        }
        val all = docBody.allElements
        for (el in all) {
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
            val inline = parseStyleAttr(el.attr("style"))
            for ((k, v) in inline) merged[k] = v
            if (merged.isEmpty()) continue
            el.attr("style", merged.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        }
        normalizeLegacyAlignment(docBody)
        removeHiddenElements(docBody)
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

    private fun hasSemanticSignal(el: Element): Boolean {
        val tag = el.tagName().lowercase()
        if (tag in complexTags) return true
        if (el.id().isNotEmpty() || el.attr("name").isNotEmpty()) return true
        val style = el.attr("style").lowercase()
        if (style.contains("text-align") || style.contains("text-indent") ||
            style.contains("font-weight") || style.contains("font-style") ||
            style.contains("text-decoration")
        ) {
            return true
        }
        if (el.select(blockSemanticSelector).isNotEmpty()) {
            return true
        }
        if (el.select(inlineSemanticSelector).isNotEmpty()) {
            return true
        }
        return false
    }

    private fun isUnwrappableContainer(el: Element): Boolean {
        val tag = el.tagName().lowercase()
        if (tag !in containerTags) return false
        if (el.id().isNotEmpty() || el.attr("name").isNotEmpty()) return false
        return el.children().any { it.tagName().lowercase() in blockTags }
    }

    private fun propagateContainerAlignment(container: Element, child: Element) {
        val alignMatch = textAlignRegex.find(container.attr("style")) ?: return
        val existing = child.attr("style")
        if (!existing.contains("text-align", ignoreCase = true)) {
            val alignDecl = "text-align: ${alignMatch.groupValues[1].lowercase()}"
            child.attr("style", (existing.trimEnd(';') + "; $alignDecl").trimStart(';', ' '))
        }
    }

    /**
     * Emit mixed channel: simple text/image paragraphs stay plain (replace-friendly),
     * semantic blocks go through single-line <usehtml> wrappers.
     */
    fun emitMixedChannel(body: Element, chapterTitle: String? = null): String {
        dropLeadingTitle(body, chapterTitle)
        val lines = mutableListOf<String>()
        emitNodes(body, lines)
        return lines.joinToString("\n")
    }

    private fun emitNodes(container: Element, lines: MutableList<String>) {
        for (node in container.childNodes().toList()) {
            if (node is TextNode) {
                val text = node.text().trim()
                if (text.isNotEmpty()) {
                    lines += text
                }
                continue
            }
            if (node !is Element) continue
            val el = node
            val tag = el.tagName().lowercase()
            if (tag == "script" || tag == "style") continue
            if (el.text().isBlank() && el.select("img").isEmpty() && tag != "hr") {
                continue
            }
            if (isUnwrappableContainer(el)) {
                el.children().forEach { child -> propagateContainerAlignment(el, child) }
                emitNodes(el, lines)
                continue
            }
            if (hasSemanticSignal(el)) {
                // Keep the transport on one line without flattening author whitespace in pre.
                val singleLine = el.outerHtml().replace("\r", "&#13;").replace("\n", "&#10;").trim()
                if (singleLine.isNotEmpty()) {
                    lines += "<usehtml>$singleLine</usehtml>"
                }
            } else {
                val rawPlain = HtmlFormatter.formatKeepImg(el.outerHtml())
                    .replace("&lt;img", "&lt; img", ignoreCase = true)
                val plain = if (rawPlain.indexOf('&') > -1) {
                    StringEscapeUtils.unescapeHtml4(rawPlain).trim()
                } else {
                    rawPlain.trim()
                }
                if (plain.isEmpty()) continue
                for (part in plain.split("\n")) {
                    val t = part.trim()
                    if (t.isNotEmpty()) lines += t
                }
            }
        }
    }

    /**
     * Drop leading leaf blocks whose normalized text repeats [chapterTitle].
     *
     * Unwraps structural containers (`div`/`section`/...) to inspect up to 3 leading leaf
     * blocks. Jsoup's `Element.text()` already normalizes `<br/>` and inline tags into spaces,
     * so multi-line headings (`<h2>CHAPTER I.<br/>The Period</h2>`), split headings across
     * consecutive blocks (`<h1>CHAPTER I.</h1><h2>The Period</h2>`), and headings sharing a
     * wrapper `<div class="chapter">` with story paragraphs are matched without touching
     * inline words inside normal paragraphs.
     */
    fun dropLeadingTitle(body: Element, chapterTitle: String?): Boolean {
        if (chapterTitle.isNullOrBlank()) return false
        val normTitle = normalizeTitleText(chapterTitle)
        if (normTitle.isEmpty()) return false
        val candidates = mutableListOf<Element>()
        collectLeadingLeafBlocks(body, candidates, limit = 3)
        if (candidates.isEmpty()) return false

        var spaced = ""
        var flat = ""
        for (i in candidates.indices) {
            val part = normalizeTitleText(candidates[i].text())
            if (part.isEmpty()) continue
            spaced = if (spaced.isEmpty()) part else "$spaced $part"
            flat = normalizeTitleText(flat + part)
            if (spaced == normTitle || flat == normTitle) {
                for (j in 0..i) {
                    var parent: Element? = candidates[j].parent()
                    candidates[j].remove()
                    while (parent != null && parent != body &&
                        parent.text().isBlank() && parent.select("img, image, hr").isEmpty()
                    ) {
                        val up = parent.parent()
                        parent.remove()
                        parent = up
                    }
                }
                return true
            }
            if (!normTitle.startsWith(spaced) && !normTitle.startsWith(flat)) break
        }
        return false
    }

    private fun collectLeadingLeafBlocks(
        root: Element,
        out: MutableList<Element>,
        limit: Int
    ): Boolean {
        for (node in root.childNodes()) {
            if (node is TextNode) {
                if (node.text().isNotBlank()) return false
                continue
            }
            if (node !is Element) continue
            val tag = node.tagName().lowercase()
            if (tag == "script" || tag == "style") continue
            if (tag == "img" || tag == "image" || node.select("img, image").isNotEmpty()) {
                return false
            }
            if (isUnwrappableContainer(node) && node.ownText().isBlank()) {
                if (!collectLeadingLeafBlocks(node, out, limit)) return false
            } else if (node.text().isNotBlank()) {
                out += node
            }
            if (out.size >= limit) return false
        }
        return true
    }

    private fun normalizeTitleText(text: String): String =
        text.replace("[\\s\\u00a0\\u3000]+".toRegex(), " ").trim()

    private val externalSchemeRegex = Regex("^(https?|mailto|tel|javascript|data):", RegexOption.IGNORE_CASE)
    private val mobiChapterPrefixRegex = Regex("^(?:skip:)?\\d+:")

    fun isExternalLink(href: String): Boolean =
        href.trim().matches(Regex("^(https?|mailto|tel):.*", RegexOption.IGNORE_CASE))

    fun resolveRelativeHref(baseResourceHref: String, rawHref: String): String? {
        val trimmed = rawHref.trim()
        if (trimmed.isEmpty() || externalSchemeRegex.containsMatchIn(trimmed)) return trimmed.ifEmpty { null }
        val basePath = baseResourceHref.substringBefore("#").trim()
        if (trimmed.startsWith("#")) return if (basePath.isEmpty()) trimmed else "$basePath$trimmed"
        val pathPart = trimmed.substringBefore("#")
        val fragment = trimmed.substringAfter("#", "")
        val resolvedPath = runCatching {
            URLDecoder.decode(URI(basePath.encodeURI()).resolve(pathPart.encodeURI()).toString(), "UTF-8")
        }.getOrDefault(pathPart).removePrefix("/")
        return if (fragment.isNotEmpty()) "$resolvedPath#$fragment" else resolvedPath
    }

    fun resolveInternalLinkTarget(
        rawLink: String,
        chapters: List<BookChapter>,
        currentChapterIndex: Int = -1
    ): Int? {
        val link = runCatching { URLDecoder.decode(rawLink.trim(), "UTF-8") }
            .getOrDefault(rawLink.trim()).removePrefix("/").removePrefix("./")
        if (link.isEmpty() || chapters.isEmpty() || isExternalLink(link)) return null
        fun canonicalUrl(ch: BookChapter): String =
            ch.url.replace(mobiChapterPrefixRegex, "").removePrefix("/")

        val resolvedLink = if (link.startsWith("#")) {
            val curBase = chapters.getOrNull(currentChapterIndex)
                ?.let { canonicalUrl(it).substringBefore("#") }
                .orEmpty()
            "$curBase$link".removePrefix("/")
        } else link
        val linkBase = resolvedLink.substringBefore("#")

        fun matches(candidate: String, target: String): Boolean =
            candidate.equals(target, ignoreCase = true) ||
                candidate.endsWith("/$target", ignoreCase = true)

        // 1. Exact URL match (including #fragment or mobi filepos/kindle:pos)
        val exact = chapters.filter { matches(canonicalUrl(it), resolvedLink) }
        exact.firstOrNull { !it.isVolume }?.let { return it.index }
        exact.firstOrNull()?.let { return it.index }

        // 2. Same resource file match when #fragment is an in-chapter anchor
        val byPath = chapters.filter { matches(canonicalUrl(it).substringBefore("#"), linkBase) }
        if (byPath.any { it.index == currentChapterIndex }) return currentChapterIndex
        return (byPath.firstOrNull { !it.isVolume } ?: byPath.firstOrNull())?.index
    }
}
