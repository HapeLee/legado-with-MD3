package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderHtmlParagraph
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.semantic.ReaderSemanticContract
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Optional local HTML appearance parser. Its text is only a projection witness, never the chapter body.
 *
 * [parse] builds both in one pass with a shared cursor, so every inline item's
 * positions belong to this appearance witness; the resolver must map them to canonical body ranges.
 */
internal object JsoupReaderPresentationParser {

    data class ParsedHtml(
        val text: String,
        val paragraphs: List<ReaderHtmlParagraph>,
    )

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
        val subscript: Boolean = false
    )

    private data class BlockCtx(
        val alignment: ReaderTextAlignment? = null,
        val quoteDepth: Int = 0,
        val listStack: List<ListLevel> = emptyList(),
        val inPre: Boolean = false,
        val headingScale: Float = 1f,
        val headingBold: Boolean = false,
        /** 表格、图注、定义列表等结构化块：排版由 HTML 语义决定，不套阅读器正文缩进。 */
        val structural: Boolean = false,
        val listItem: ListItem? = null,
    )

    private class ListLevel(val ordered: Boolean, var next: Int, val step: Int)
    private class ListItem(val label: String?, var emitted: Boolean = false)

    private class Cursor(
        var pos: Int,
        val text: StringBuilder = StringBuilder(),
        val paragraphs: MutableList<ReaderHtmlParagraph> = mutableListOf(),
        var current: MutableList<ReaderChapterInlineSource> = mutableListOf(),
        var currentAlignment: ReaderTextAlignment? = null,
        var currentDecorations: List<ReaderParagraphDecoration> = emptyList(),
        var currentFirstMargin: Float = 0f,
        var currentRestMargin: Float = 0f,
        var currentPreserveBodyIndent: Boolean = true,
        var currentListItem: ListItem? = null,
        var pendingPreLine: Boolean = false,
        val elementPositions: MutableMap<Element, Int>? = null,
    ) {
        fun apply(geo: BlockGeo) {
            currentAlignment = geo.alignment
            currentDecorations = geo.decorations
            currentFirstMargin = geo.firstMargin
            currentRestMargin = geo.restMargin
            currentPreserveBodyIndent = geo.preserveBodyIndent
            currentListItem = geo.listItem
        }
    }

    fun parse(
        html: String,
        startPosition: Int,
        baseTextSizePx: Float,
        density: Float,
    ): ParsedHtml {
        val doc = Jsoup.parseBodyFragment(html)
        return project(doc.body(), startPosition, baseTextSizePx, density)
    }

    internal fun project(
        body: Element,
        startPosition: Int = 0,
        baseTextSizePx: Float = 10f,
        density: Float = 1f,
        elementPositions: MutableMap<Element, Int>? = null,
    ): ParsedHtml {
        val cursor = Cursor(startPosition, elementPositions = elementPositions)
        // Seed block context from body-level alignment if present.
        val bodyCtx = BlockCtx(alignment = parseAlignment(body))
        for (child in body.childNodes().toList()) {
            walk(child, InlineCtx(), bodyCtx, cursor, baseTextSizePx, density)
        }
        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
        return ParsedHtml(cursor.text.toString(), cursor.paragraphs)
    }

    private fun recordPosition(node: Element, cursor: Cursor) {
        cursor.elementPositions?.put(node, cursor.pos)
    }

    private fun walk(
        node: Node,
        inline: InlineCtx,
        block: BlockCtx,
        cursor: Cursor,
        baseTextSizePx: Float,
        density: Float
    ) {
        when (node) {
            is TextNode -> {
                // Skip ruby annotations from the semantic stream
                val parent = node.parent()
                if (parent is Element) {
                    val tag = parent.tagName().lowercase()
                    if (tag == "rt" || tag == "rp") return
                }
                val raw = if (block.inPre) node.wholeText else node.text()
                if (raw.isEmpty()) return
                var normalized = if (block.inPre) {
                    raw.replace("\r\n", "\n").replace("\r", "\n")
                } else {
                    // Collapse whitespace like COMPACT mode, but keep single spaces.
                    raw.replace("[\\s\\u00a0]+".toRegex(), " ")
                }
                if (!block.inPre) {
                    if (cursor.current.isEmpty()) {
                        normalized = normalized.trimStart(' ')
                    } else {
                        val prevText = (cursor.current.lastOrNull() as? ReaderChapterInlineSource.Text)?.value
                        if (prevText != null && prevText.endsWith(" ") && normalized.startsWith(" ")) {
                            normalized = normalized.trimStart(' ')
                        }
                    }
                }
                if (normalized.isEmpty()) return
                val lines = if (block.inPre) normalized.split('\n') else listOf(normalized)
                lines.forEachIndexed { index, value ->
                    if (value.isNotEmpty()) {
                        cursor.pendingPreLine = false
                        cursor.current += ReaderChapterInlineSource.Text(
                            value = value,
                            chapterPosition = cursor.pos,
                            style = ReaderInlineSourceStyle(
                                colorArgb = inline.colorArgb,
                                backgroundArgb = inline.backgroundArgb,
                                fontWeight = when (inline.bold) {
                                    true -> 700
                                    false -> 400
                                    null -> if (block.headingBold) 700 else null
                                },
                                italic = inline.italic,
                                underline = inline.underline,
                                strikeThrough = inline.strike,
                                link = inline.link,
                                fontSizeScale = inline.fontSizeScale * block.headingScale,
                                fontFamily = inline.fontFamily,
                                superscript = inline.superscript,
                                subscript = inline.subscript,
                            ),
                        )
                        cursor.text.append(value)
                        cursor.pos += value.length
                    }
                    if (index < lines.lastIndex) {
                        flushParagraph(cursor, preserveEmpty = true, baseTextSizePx, density)
                        cursor.pendingPreLine = true
                    }
                }
            }
            is Element -> {
                when (val tag = node.tagName().lowercase()) {
                    "script", "style", "noscript", "template" -> return
                    "rt", "rp" -> return // handled via parent check; skip subtree
                    "br" -> {
                        recordPosition(node, cursor)
                        flushParagraph(cursor, preserveEmpty = true, baseTextSizePx, density)
                    }
                    "hr" -> {
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        recordPosition(node, cursor)
                        cursor.paragraphs += ReaderHtmlParagraph(
                            listOf(ReaderChapterInlineSource.BlankLine(cursor.pos)),
                            0f, 0f, null, emptyList(), rule = true,
                        )
                        cursor.text.append(ReaderSemanticContract.PARAGRAPH_BREAK)
                        cursor.pos += 1
                    }
                    "img", "image" -> {
                        recordPosition(node, cursor)
                        val src = if (tag == "image") {
                            node.attr("xlink:href").takeIf { it.isNotEmpty() }
                                ?: node.attr("src")
                        } else {
                            node.attr("src")
                        }
                        if (src.isEmpty()) return
                        cursor.current += ReaderChapterInlineSource.Image(
                            source = src,
                            chapterPosition = cursor.pos,
                            action = node.attr("data-legado-pclick").takeIf { it.isNotBlank() }
                                ?: node.attr("data-legado-click").takeIf { it.isNotBlank() }
                        )
                        cursor.text.append(ReaderSemanticContract.IMAGE_CHAR)
                        cursor.pos += 1
                    }
                    "p", "div", "section", "article", "header", "footer",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "blockquote", "ul", "ol", "li", "pre", "figure",
                    "figcaption", "aside", "dl", "dt", "dd", "tr", "center" -> {
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        recordPosition(node, cursor)
                        val childBlock = descendBlock(node, tag, block, baseTextSizePx, density)
                        val childInline = descendInline(node, tag, inline)
                        // Establish geometry for the upcoming paragraph run.
                        cursor.apply(blockGeometry(childBlock, baseTextSizePx, density))
                        for (child in node.childNodes().toList()) {
                            walk(child, childInline, childBlock, cursor, baseTextSizePx, density)
                        }
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        // Reset to parent geometry for following siblings.
                        cursor.apply(blockGeometry(block, baseTextSizePx, density))
                    }
                    "td", "th" -> {
                        recordPosition(node, cursor)
                        if (cursor.current.isNotEmpty()) {
                            val prev = (cursor.current.lastOrNull() as? ReaderChapterInlineSource.Text)?.value
                            if (prev == null || !prev.endsWith(" ")) {
                                cursor.current += ReaderChapterInlineSource.Text(" ", cursor.pos)
                                cursor.text.append(' ')
                                cursor.pos += 1
                            }
                        }
                        val childInline = descendInline(node, tag, inline)
                        for (child in node.childNodes().toList()) {
                            walk(child, childInline, block, cursor, baseTextSizePx, density)
                        }
                    }
                    else -> {
                        recordPosition(node, cursor)
                        val childInline = descendInline(node, tag, inline)
                        for (child in node.childNodes().toList()) {
                            walk(child, childInline, block, cursor, baseTextSizePx, density)
                        }
                    }
                }
            }
        }
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
                block.headingScale <= 1f &&
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
        var headingScale = parent.headingScale
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
            "h1" -> { headingScale = 1.35f; headingBold = true }
            "h2" -> { headingScale = 1.25f; headingBold = true }
            "h3" -> { headingScale = 1.15f; headingBold = true }
            "h4" -> { headingScale = 1.05f; headingBold = true }
            "h5", "h6" -> { headingScale = 1f; headingBold = true }
            "tr", "td", "th", "figure", "figcaption",
            "aside", "dl", "dt", "dd" -> structural = true
        }
        // li does not push a new level; its parent ul/ol already did.
        return BlockCtx(alignment, quoteDepth, listStack, inPre, headingScale, headingBold, structural, listItem)
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
            // Keeps <mark> semantics only (no theme-coupled background);
            // TODO: Adds theme-adaptive emphasis styling.
        }
        // Inline style hints (structure only）
        // TODO: color/background/font-size (not promoted).
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
            // Ignores original color/background/font-size
        // TODO: kept in DOM.
        }
        return out
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

    private fun flushParagraph(
        cursor: Cursor,
        preserveEmpty: Boolean,
        baseTextSizePx: Float,
        density: Float
    ) {
        if (cursor.current.isNotEmpty()) {
            cursor.paragraphs += ReaderHtmlParagraph(
                cursor.current.toList(),
                cursor.currentFirstMargin,
                cursor.currentRestMargin,
                cursor.currentAlignment,
                cursor.currentDecorations.filterNot { it.kind == ReaderParagraphDecorationKind.BULLET && cursor.currentListItem?.emitted == true },
                cursor.currentPreserveBodyIndent
            )
            cursor.text.append(ReaderSemanticContract.PARAGRAPH_BREAK)
            cursor.pos += 1
            cursor.currentListItem?.emitted = true
        } else if (preserveEmpty || cursor.pendingPreLine) {
            // Preserve leading and consecutive empty paragraphs; the trailing
            // start == end line is still omitted by the final non-preserving flush.
            cursor.paragraphs += ReaderHtmlParagraph(
                listOf(ReaderChapterInlineSource.BlankLine(cursor.pos)),
                cursor.currentFirstMargin,
                cursor.currentRestMargin,
                cursor.currentAlignment,
                cursor.currentDecorations,
                cursor.currentPreserveBodyIndent
            )
            cursor.text.append(ReaderSemanticContract.PARAGRAPH_BREAK)
            cursor.pos += 1
        }
        cursor.current = mutableListOf()
        cursor.pendingPreLine = false
    }
}
