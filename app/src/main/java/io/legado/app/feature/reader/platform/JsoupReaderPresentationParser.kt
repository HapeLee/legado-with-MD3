package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.source.ReaderLocalPresentation
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.source.ReaderLocalImagePresentation
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Reads appearance and source ranges only. Canonical text is emitted by the legacy formatter. */
internal object JsoupReaderPresentationParser {
    private data class InlineCtx(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null,
        val colorArgb: Int? = null,
        val backgroundArgb: Int? = null,
        val fontFamily: String? = null,
        val fontSizeScale: Float = 1f,
        val superscript: Boolean = false,
        val subscript: Boolean = false,
        val marked: Boolean = false,
    )

    private data class BlockCtx(
        val alignment: ReaderTextAlignment? = null,
        val quoteDepth: Int = 0,
        val listStack: List<ListLevel> = emptyList(),
        val inPre: Boolean = false,
        val headingBold: Boolean = false,
        /** 表格、图注、定义列表等结构化块：排版由 HTML 语义决定，不套阅读器正文缩进。 */
        val structural: Boolean = false,
        val listItem: ListItem? = null,
    )

    private class ListLevel(val ordered: Boolean, var next: Int, val step: Int)
    private class ListItem(val label: String?, var emitted: Boolean = false)

    fun project(body: Element, html: String): ReaderLocalPresentation {
        val spans = mutableListOf<ReaderLocalPresentation.Span>()
        val paragraphs = mutableListOf<ReaderLocalPresentation.Paragraph>()
        val rules = mutableListOf<Int>()
        val anchors = linkedMapOf<String, Int>()
        val images = mutableListOf<ReaderLocalPresentation.Image>()
        var first = -1
        var last = -1
        var current = blockGeometry(BlockCtx(), 10f, 1f)
        fun flush() {
            if (first >= 0 && last > first) {
                paragraphs += ReaderLocalPresentation.Paragraph(first, last, current.firstMargin,
                    current.restMargin, current.alignment,
                    current.decorations.filterNot { it.kind == ReaderParagraphDecorationKind.BULLET && current.listItem?.emitted == true },
                    current.preserveBodyIndent)
                current.listItem?.emitted = true
            }
            first = -1
            last = -1
        }
        fun include(start: Int, end: Int) {
            if (first < 0) first = start
            last = maxOf(last, end)
        }
        fun blank(position: Int) {
            paragraphs += ReaderLocalPresentation.Paragraph(position, position, current.firstMargin,
                current.restMargin, current.alignment, emptyList(), false, blankLine = true)
        }
        fun walk(node: Node, inline: InlineCtx, block: BlockCtx) {
            val range = node.sourceRange()
            if (node is TextNode) {
                if (!range.isTracked) return
                val start = range.start().pos()
                val end = range.end().pos()
                if (node.wholeText.isBlank() && !block.inPre) return
                spans += ReaderLocalPresentation.Span(start, end, ReaderInlineSourceStyle(
                    colorArgb = inline.colorArgb, backgroundArgb = inline.backgroundArgb,
                    fontWeight = when (inline.bold) { true -> 700; false -> null; null -> if (block.headingBold) 700 else null },
                    italic = inline.italic.takeIf { it == true }, underline = inline.underline,
                    strikeThrough = inline.strike, link = inline.link,
                    fontSizeScale = inline.fontSizeScale.coerceIn(0.5f, 2f), fontFamily = inline.fontFamily,
                    superscript = inline.superscript, subscript = inline.subscript, marked = inline.marked))
                include(start, end)
                if (block.inPre) {
                    val raw = html.substring(start, end)
                    Regex("(?:^|\\n)[ \\t]*\\n").findAll(raw).forEach { blank(start + it.range.last) }
                }
                return
            }
            if (node !is Element) return
            val tag = node.normalName()
            if (range.isTracked) {
                for (id in listOf(node.id(), node.attr("name")).filter { it.isNotBlank() }) {
                    anchors.putIfAbsent(id, range.start().pos())
                }
            }
            when (tag) {
                "script", "style", "noscript", "template" -> return
                "br" -> {
                    val empty = first < 0
                    flush()
                    if (empty && range.isTracked) blank(range.start().pos())
                    return
                }
                "hr" -> {
                    flush()
                    if (range.isTracked) rules += range.start().pos()
                    return
                }
                "img", "image" -> {
                    if (range.isTracked) {
                        include(range.start().pos(), range.end().pos())
                        val declarations = node.attr("style").split(';').mapNotNull {
                            val split = it.indexOf(':')
                            if (split < 0) null else it.substring(0, split).trim().lowercase() to it.substring(split + 1).trim()
                        }.toMap()
                        images += ReaderLocalPresentation.Image(range.start().pos(), range.end().pos(),
                            ReaderLocalImagePresentation(inline.link, declarations["width"] ?: node.attr("width").takeIf { it.isNotBlank() },
                                declarations["height"] ?: node.attr("height").takeIf { it.isNotBlank() },
                                descendInline(node, tag, inline).fontSizeScale.coerceIn(0.5f, 2f)))
                    }
                    return
                }
            }
            val isBlock = tag in setOf("p", "div", "section", "article", "header", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
                "blockquote", "ul", "ol", "li", "pre", "figure", "figcaption", "aside", "dl", "dt", "dd", "tr", "center")
            if (isBlock) flush()
            val childBlock = if (isBlock) descendBlock(node, tag, block, 10f, 1f) else block
            if (isBlock) current = blockGeometry(childBlock, 10f, 1f)
            val childInline = descendInline(node, tag, inline)
            node.childNodes().forEach { walk(it, childInline, childBlock) }
            if (isBlock) {
                flush()
                current = blockGeometry(block, 10f, 1f)
            }
        }
        val context = BlockCtx(alignment = parseAlignment(body))
        current = blockGeometry(context, 10f, 1f)
        val bodyInline = descendInline(body, "body", InlineCtx())
        body.childNodes().forEach { walk(it, bodyInline, context) }
        flush()
        return ReaderLocalPresentation(html, spans, paragraphs, rules, anchors, images = images)
    }

    private data class BlockGeo(
        val alignment: ReaderTextAlignment?,
        val decorations: List<ReaderParagraphDecoration>,
        val firstMargin: Float,
        val restMargin: Float,
        val preserveBodyIndent: Boolean,
        val listItem: ListItem?,
    )

    private fun blockGeometry(
        block: BlockCtx,
        baseTextSizePx: Float,
        density: Float
    ): BlockGeo {
        val decorations = mutableListOf<ReaderParagraphDecoration>()
        val step = baseTextSizePx * 1.2f
        if (block.quoteDepth > 0) {
            repeat(block.quoteDepth) { level ->
                decorations += ReaderParagraphDecoration(
                    ReaderParagraphDecorationKind.QUOTE,
                    null,
                    2f * density.coerceAtLeast(1f),
                    level * step
                )
            }
        }
        val listDepth = block.listStack.size
        val item = block.listItem
        if (listDepth > 0 && item != null && !item.emitted) {
            decorations += ReaderParagraphDecoration(
                ReaderParagraphDecorationKind.BULLET,
                null,
                4f * density.coerceAtLeast(1f),
                (block.quoteDepth + listDepth - 1) * step,
                label = item.label,
            )
        }
        val totalLevels = block.quoteDepth + block.listStack.size
        val margin = totalLevels * step + ((item?.label?.length ?: 0) - 2).coerceAtLeast(0) * baseTextSizePx * .6f
        return BlockGeo(
            alignment = block.alignment,
            decorations = decorations,
            firstMargin = margin,
            restMargin = margin,
            // 只有普通正文段落沿用阅读器段首缩进。标题/引用/列表/pre/表格等由 HTML 语义自己排版。
            preserveBodyIndent = block.quoteDepth == 0 &&
                block.listStack.isEmpty() &&
                !block.inPre &&
                !block.structural &&
                !block.headingBold,
            listItem = item,
        )
    }

    private fun descendBlock(
        el: Element,
        tag: String,
        parent: BlockCtx,
        baseTextSizePx: Float,
        density: Float
    ): BlockCtx {
        var alignment = parseAlignment(el) ?: parent.alignment
        var quoteDepth = parent.quoteDepth
        var listStack = parent.listStack
        var inPre = parent.inPre
        var headingBold = parent.headingBold
        var structural = parent.structural
        var listItem = parent.listItem
        when (tag) {
            "blockquote" -> quoteDepth += 1
            "ul", "ol" -> {
                val reversed = el.hasAttr("reversed")
                val start = el.attr("start").toIntOrNull() ?: if (reversed) el.children().count { it.normalName() == "li" } else 1
                listStack = listStack + ListLevel(tag == "ol", start, if (reversed) -1 else 1)
                listItem = null
            }
            "li" -> {
                val level = listStack.lastOrNull()
                val value = el.attr("value").toIntOrNull() ?: level?.next ?: 1
                if (level != null) level.next = value + level.step
                listItem = ListItem(if (level?.ordered == true) "$value." else null)
            }
            "pre" -> inPre = true
            "center" -> alignment = ReaderTextAlignment.CENTER
            "h1", "h2", "h3", "h4", "h5", "h6" -> headingBold = true
            "tr", "td", "th", "figure", "figcaption",
            "aside", "dl", "dt", "dd" -> structural = true
        }
        // li does not push a new level; its parent ul/ol already did.
        return BlockCtx(alignment, quoteDepth, listStack, inPre, headingBold, structural, listItem)
    }

    private fun descendInline(el: Element, tag: String, parent: InlineCtx): InlineCtx {
        var out = parent
        when (tag) {
            "b", "strong" -> out = out.copy(bold = true)
            "i", "em", "cite", "dfn" -> out = out.copy(italic = true)
            "u", "ins" -> out = out.copy(underline = true)
            "del", "s", "strike" -> out = out.copy(strike = true)
            "code", "kbd", "samp", "tt" -> out = out.copy(
                fontFamily = out.fontFamily ?: "monospace"
            )
            "mark" -> out = out.copy(marked = true)
            "h1" -> out = out.copy(fontSizeScale = parent.fontSizeScale * 1.35f)
            "h2" -> out = out.copy(fontSizeScale = parent.fontSizeScale * 1.25f)
            "h3" -> out = out.copy(fontSizeScale = parent.fontSizeScale * 1.15f)
            "h4" -> out = out.copy(fontSizeScale = parent.fontSizeScale * 1.05f)
            "h5", "h6" -> out = out.copy(fontSizeScale = parent.fontSizeScale)
            "sup" -> out = out.copy(superscript = true)
            "sub" -> out = out.copy(subscript = true)
            "big" -> out = out.copy(fontSizeScale = out.fontSizeScale * 1.25f)
            "small" -> out = out.copy(fontSizeScale = out.fontSizeScale * 0.8f)
            "a" -> {
                val href = el.attr("href").takeIf { it.isNotEmpty() }
                if (href != null) out = out.copy(link = href)
            }
            "font" -> {
            // default (reader unified style): ignore original <font color/face> structure only.
            // TODO: Add Colors/fonts with the AN original-typography switch.
            }
        }
        // Consume only the supported appearance subset; canonical text is unchanged.
        val style = el.attr("style")
        if (style.isNotEmpty()) {
            val declarations = style.split(";").mapNotNull { decl ->
                val idx = decl.indexOf(":")
                if (idx < 0) null else decl.substring(0, idx).trim().lowercase() to
                    decl.substring(idx + 1).trim()
            }.toMap()
            declarations["font-weight"]?.let { v ->
                if (v.equals("bold", ignoreCase = true) || (v.toIntOrNull() ?: 400) >= 600) {
                    out = out.copy(bold = true)
                } else if (v.equals("normal", true) || v.equals("initial", true) || v.toIntOrNull() != null) {
                    out = out.copy(bold = false)
                }
            }
            declarations["font-style"]?.let { v ->
                if (v.equals("italic", ignoreCase = true) || v.equals("oblique", ignoreCase = true)) {
                    out = out.copy(italic = true)
                } else if (v.equals("normal", true) || v.equals("initial", true)) {
                    out = out.copy(italic = false)
                }
            }
            declarations["text-decoration"]?.let { v ->
                val lower = v.lowercase()
                if ("underline" in lower) out = out.copy(underline = true)
                if ("line-through" in lower) out = out.copy(strike = true)
            }
            declarations["color"]?.let { value ->
                when (value.lowercase()) {
                    "initial" -> out = out.copy(colorArgb = null)
                    "inherit", "currentcolor" -> out = out.copy(colorArgb = parent.colorArgb)
                    else -> parseColor(value)?.let { out = out.copy(colorArgb = it) }
                }
            }
            declarations["font-size"]?.let { value ->
                parseFontSize(value, parent.fontSizeScale)?.let { out = out.copy(fontSizeScale = it) }
            }
        }
        return out
    }

    private val basicColors = mapOf(
        "black" to 0x000000, "silver" to 0xC0C0C0, "gray" to 0x808080, "white" to 0xFFFFFF,
        "maroon" to 0x800000, "red" to 0xFF0000, "purple" to 0x800080, "fuchsia" to 0xFF00FF,
        "green" to 0x008000, "lime" to 0x00FF00, "olive" to 0x808000, "yellow" to 0xFFFF00,
        "navy" to 0x000080, "blue" to 0x0000FF, "teal" to 0x008080, "aqua" to 0x00FFFF,
    )
    private val hexColor = Regex("#[0-9a-f]{3}([0-9a-f]{3})?")
    private val rgbColor = Regex("rgb\\(\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*\\)")
    private val relativeSize = Regex("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(%|em)")

    private fun parseColor(value: String): Int? {
        val normalized = value.trim().lowercase()
        val rgb = basicColors[normalized] ?: when {
            hexColor.matches(normalized) -> {
                val digits = normalized.drop(1)
                (if (digits.length == 3) digits.flatMap { listOf(it, it) }.joinToString("") else digits).toInt(16)
            }
            else -> {
                val channels = rgbColor.matchEntire(normalized)?.groupValues?.drop(1)
                    ?.map { it.toInt() } ?: return null
                if (channels.any { it !in 0..255 }) return null
                (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
            }
        }
        return 0xFF000000.toInt() or rgb
    }

    private fun parseFontSize(value: String, parent: Float): Float? {
        val normalized = value.trim().lowercase()
        val scale = when (normalized) {
            "small" -> 0.8f
            "medium", "initial" -> 1f
            "large" -> 1.25f
            "smaller" -> parent * 0.8f
            "larger" -> parent * 1.25f
            "inherit" -> parent
            else -> {
                val match = relativeSize.matchEntire(normalized) ?: return null
                val amount = match.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
                    ?: return null
                parent * (if (match.groupValues[2] == "%") amount / 100f else amount)
            }
        }
        return scale.takeIf { it.isFinite() && it > 0f }
    }

    private fun parseAlignment(el: Element): ReaderTextAlignment? {
        val style = el.attr("style").lowercase()
        // Fast path for the common inlined declarations.
        val match = Regex("text-align\\s*:\\s*([a-z]+)").find(style)?.groupValues?.get(1)
        when (match) {
            "center" -> return ReaderTextAlignment.CENTER
            "right", "end" -> return ReaderTextAlignment.END
            "left", "start" -> return ReaderTextAlignment.START
            "justify" -> return ReaderTextAlignment.JUSTIFY
        }
        return null
    }

}
