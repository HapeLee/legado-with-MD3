package io.legado.app.model.localBook

import io.legado.app.utils.HtmlFormatter
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

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

    private val allowedCssProps = setOf(
        "text-align", "text-indent",
        "margin", "margin-top", "margin-bottom", "margin-left", "margin-right",
        "border", "border-left", "border-right", "border-top", "border-bottom",
        "font-weight", "font-style", "font-size", "display"
    )

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
            dropTopLevelBefore(body, startEl)
        }
        if (endEl != null) {
            val attached = if (endEl.parent() == null) findByIdOrName(body, endFragmentId!!) else endEl
            if (attached != null) {
                trimAfter(attached, body)
                dropTopLevelAfter(body, attached)
            }
        }
    }

    private fun findByIdOrName(body: Element, fragmentId: String): Element? {
        body.getElementById(fragmentId)?.let { return it }
        return body.getElementsByAttributeValue("name", fragmentId).firstOrNull()
    }

    private fun topLevelChild(body: Element, node: Element): Element? {
        var current: Element? = node
        while (current != null && current.parent() != body) {
            val parent = current.parent() ?: return null
            current = parent
        }
        return current
    }

    private fun dropTopLevelBefore(body: Element, startEl: Element) {
        val top = topLevelChild(body, startEl) ?: return
        val children = body.children().toList()
        val index = children.indexOf(top)
        if (index <= 0) return
        for (i in 0 until index) {
            children[i].remove()
        }
    }

    private fun dropTopLevelAfter(body: Element, endEl: Element) {
        val top = topLevelChild(body, endEl) ?: return
        val children = body.children().toList()
        val index = children.indexOf(top)
        if (index < 0) return
        for (i in children.size - 1 downTo index + 1) {
            children[i].remove()
        }
        if (top != endEl) {
            val ownText = top.ownText().trim()
            val hasContentBefore = top.childNodes().any { node ->
                node != endEl && node is TextNode && node.text().isNotBlank()
            }
            if (ownText.isEmpty() && !hasContentBefore && top.text().trim() == endEl.text().trim()) {
                top.remove()
            }
        } else {
            top.remove()
        }
    }

    private fun trimBefore(startEl: Element, body: Element) {
        var current: Element? = startEl
        while (current != null && current != body) {
            var sibling = current.previousElementSibling()
            while (sibling != null) {
                val prev = sibling.previousElementSibling()
                sibling.remove()
                sibling = prev
            }
            val parent = current.parent()
            if (parent is Element) {
                val idx = parent.childNodes().indexOf(current)
                if (idx > 0) {
                    for (i in idx - 1 downTo 0) {
                        val node = parent.childNodes()[i]
                        if (node is TextNode && node.text().isBlank()) {
                            node.remove()
                        } else if (node is TextNode) {
                            node.remove()
                            break
                        } else {
                            break
                        }
                    }
                }
            }
            current = parent
        }
    }

    private fun trimAfter(endEl: Element, body: Element) {
        var current: Element? = endEl
        while (current != null && current != body) {
            var sibling = current.nextElementSibling()
            while (sibling != null) {
                val next = sibling.nextElementSibling()
                sibling.remove()
                sibling = next
            }
            val parent = current.parent()
            if (parent is Element) {
                val nodes = parent.childNodes()
                val idx = nodes.indexOf(current)
                if (idx >= 0) {
                    for (i in nodes.size - 1 downTo idx + 1) {
                        nodes[i].remove()
                    }
                }
            }
            if (current == endEl) {
                current.remove()
            }
            current = if (parent is Element && parent != body) parent else null
            if (current == body) break
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
            return rules
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
        docBody.select("[style*=display:none]").remove()
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
        val style = el.attr("style").lowercase()
        if (style.contains("text-align") || style.contains("text-indent")) {
            return true
        }
        if (el.select("blockquote, ul, ol, h1, h2, h3, h4, h5, h6, pre, hr, table, figure, aside, dl").isNotEmpty()) {
            return true
        }
        return false
    }

    /**
     * Emit mixed channel: simple text/image paragraphs stay plain (replace-friendly),
     * semantic blocks go through single-line <usehtml> wrappers.
     */
    fun emitMixedChannel(body: Element, chapterTitle: String? = null): String {
        dropLeadingTitle(body, chapterTitle)
        val lines = mutableListOf<String>()
        val nodes = body.childNodes().toList()
        for (node in nodes) {
            if (node is TextNode) {
                val text = node.text().trim()
                if (text.isNotEmpty()) {
                    lines += text
                }
                continue
            }
            if (node !is Element) continue
            val el = node
            if (el.tagName().equals("script", ignoreCase = true) ||
                el.tagName().equals("style", ignoreCase = true)
            ) {
                continue
            }
            if (el.text().isBlank() && el.select("img").isEmpty() && !el.tagName().equals("hr", ignoreCase = true)) {
                continue
            }
            if (hasSemanticSignal(el)) {
                val singleLine = el.outerHtml().replace("\n", " ").replace("\r", " ").trim()
                if (singleLine.isNotEmpty()) {
                    lines += "<usehtml>$singleLine</usehtml>"
                }
            } else {
                val plain = HtmlFormatter.formatKeepImg(el.outerHtml()).trim()
                if (plain.isEmpty()) continue
                for (part in plain.split("\n")) {
                    val t = part.trim()
                    if (t.isNotEmpty()) lines += t
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun dropLeadingTitle(body: Element, chapterTitle: String?): Boolean {
        if (chapterTitle.isNullOrBlank()) return false
        val normTitle = normalizeTitleText(chapterTitle)
        if (normTitle.isEmpty()) return false
        data class Seg(val node: TextNode, val norm: String, val raw: String)
        val segs = mutableListOf<Seg>()
        var spaced = ""
        var flat = ""
        var matched = false
        val stack = ArrayDeque<Node>()
        val roots = body.childNodes()
        for (i in roots.size - 1 downTo 0) stack.addFirst(roots[i])
        while (stack.isNotEmpty() && !matched) {
            var stop = false
            when (val node = stack.removeFirst()) {
                is TextNode -> {
                    val norm = normalizeTitleText(node.wholeText)
                    if (norm.isEmpty()) continue
                    segs += Seg(node, norm, node.wholeText)
                    spaced = normalizeTitleText("$spaced $norm")
                    flat = normalizeTitleText(flat + norm)
                    if (spaced == normTitle || flat == normTitle) {
                        matched = hasTitleBoundary(body, node)
                    } else if (!normTitle.startsWith(spaced) && !normTitle.startsWith(flat)) {
                        stop = true
                    }
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "script", "style" -> Unit
                        "img", "image" -> stop = true
                        else -> {
                            val kids = node.childNodes()
                            for (i in kids.size - 1 downTo 0) stack.addFirst(kids[i])
                        }
                    }
                }
            }
            if (stop) return false
        }
        if (!matched) return false
        segs.forEach { it.node.remove() }
        pruneBlankAncestors(body, segs.map { it.node })
        return true
    }

    private fun hasTitleBoundary(body: Element, lastNode: TextNode): Boolean {
        val next = nextTextNode(body, lastNode) ?: return true
        if (blockAncestorOf(body, lastNode) != blockAncestorOf(body, next)) return true
        if (isBoundarySpace(lastNode.wholeText.lastOrNull() ?: ' ')) return true
        return isBoundarySpace(next.wholeText.firstOrNull() ?: ' ')
    }

    private fun isBoundarySpace(c: Char): Boolean =
        c.isWhitespace() || c == '\u00a0' || c == '\u3000'

    private val blockTags = setOf(
        "p", "div", "section", "article", "header", "footer",
        "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "pre",
        "blockquote", "table", "tr", "td", "th", "figure", "center",
        "aside", "dl", "dt", "dd", "hr"
    )

    private fun blockAncestorOf(body: Element, node: Node): Element {
        var cur: Node? = node
        while (cur != null && cur != body) {
            if (cur is Element && cur.tagName().lowercase() in blockTags) return cur
            cur = cur.parent()
        }
        return body
    }

    private fun nextTextNode(body: Element, from: Node): TextNode? {
        var cursor: Node? = from
        while (cursor != null && cursor != body) {
            var sibling = cursor.nextSibling()
            while (sibling != null) {
                val found = firstTextNode(sibling)
                if (found != null) return found
                sibling = sibling.nextSibling()
            }
            cursor = cursor.parent()
        }
        return null
    }

    private fun firstTextNode(node: Node): TextNode? {
        if (node is TextNode) return node
        if (node is Element) {
            val tag = node.tagName().lowercase()
            if (tag == "script" || tag == "style") return null
            for (child in node.childNodes()) {
                val found = firstTextNode(child)
                if (found != null) return found
            }
        }
        return null
    }

    private val prunableTags = setOf(
        "a", "span", "font", "b", "i", "em", "strong", "h1", "h2", "h3",
        "h4", "h5", "h6", "center", "small", "big", "sub", "sup", "u",
        "s", "del", "strike", "mark", "code", "kbd", "samp", "tt",
        "abbr", "cite", "dfn", "q", "ruby", "rt", "rp"
    )

    private fun pruneBlankAncestors(body: Element, removed: List<TextNode>) {
        for (node in removed) {
            var ancestor: Node? = node.parent()
            while (ancestor is Element && ancestor != body &&
                ancestor.tagName().lowercase() in prunableTags &&
                ancestor.text().isBlank() && ancestor.select("img").isEmpty()
            ) {
                val up = ancestor.parent()
                ancestor.remove()
                ancestor = up
            }
        }
    }

    private fun normalizeTitleText(text: String): String =
        text.replace("[\\s\\u3000]+".toRegex(), " ").trim()
}
