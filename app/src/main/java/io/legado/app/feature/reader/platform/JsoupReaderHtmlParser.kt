package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderHtmlParagraph
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecoration
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Jsoup-based HTML parser shared by measurement and semantic text projection.
 *
 * [parse] builds both in one pass with a shared cursor, so every inline item's
 * chapterPosition stays aligned with the semantic string by construction.
 */
object JsoupReaderHtmlParser {

    data class ParsedHtml(
        val semantic: String,
        val paragraphs: List<ReaderHtmlParagraph>,
        val anchorPosition: Int? = null
    )

    private data class InlineCtx(
        val bold: Boolean = false,
        val italic: Boolean = false,
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
        val listStack: List<Boolean> = emptyList(), // true = ordered
        val inPre: Boolean = false,
        val headingScale: Float = 1f,
        val headingBold: Boolean = false,
        /** 表格、图注、定义列表等结构化块：排版由 HTML 语义决定，不套阅读器正文缩进。 */
        val structural: Boolean = false
    )

    private class Cursor(
        var pos: Int,
        val targetAnchorId: String? = null,
        var anchorPosition: Int? = null,
        val semantic: StringBuilder = StringBuilder(),
        val paragraphs: MutableList<ReaderHtmlParagraph> = mutableListOf(),
        var current: MutableList<ReaderChapterInlineSource> = mutableListOf(),
        var currentAlignment: ReaderTextAlignment? = null,
        var currentDecorations: List<ReaderParagraphDecoration> = emptyList(),
        var currentFirstMargin: Float = 0f,
        var currentRestMargin: Float = 0f,
        var currentPreserveBodyIndent: Boolean = true
    )

    fun parse(
        html: String,
        startPosition: Int,
        baseTextSizePx: Float,
        density: Float,
        targetAnchorId: String? = null
    ): ParsedHtml {
        val doc = Jsoup.parseBodyFragment(html)
        val body = doc.body()
        val cursor = Cursor(startPosition, targetAnchorId?.takeIf(String::isNotEmpty))
        // Seed block context from body-level alignment if present.
        val bodyCtx = BlockCtx(alignment = parseAlignment(body))
        for (child in body.childNodes().toList()) {
            walk(child, InlineCtx(), bodyCtx, cursor, baseTextSizePx, density)
        }
        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
        return ParsedHtml(cursor.semantic.toString(), cursor.paragraphs, cursor.anchorPosition)
    }

    fun semanticText(html: String): String {
        // Geometry does not affect offsets; use neutral metrics for the fast path.
        return parse(html, 0, 10f, 1f).semantic
    }

    fun findAnchorPosition(html: String, anchorId: String, startPosition: Int = 0): Int? {
        val target = anchorId.trim().removePrefix("#")
        if (target.isEmpty() || !html.contains(target, ignoreCase = true)) return null
        return parse(html, startPosition, 10f, 1f, target).anchorPosition
    }

    private fun checkAnchor(node: Element, cursor: Cursor) {
        val target = cursor.targetAnchorId ?: return
        if (cursor.anchorPosition != null) return
        if (node.id().equals(target, ignoreCase = true) ||
            node.attr("name").equals(target, ignoreCase = true)
        ) {
            cursor.anchorPosition = cursor.pos
        }
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
                cursor.current += ReaderChapterInlineSource.Text(
                    value = normalized,
                    chapterPosition = cursor.pos,
                    style = ReaderInlineSourceStyle(
                        colorArgb = inline.colorArgb,
                        backgroundArgb = inline.backgroundArgb,
                        fontWeight = if (inline.bold || block.headingBold) 700 else null,
                        italic = if (inline.italic) true else null,
                        underline = inline.underline,
                        strikeThrough = inline.strike,
                        link = inline.link,
                        fontSizeScale = inline.fontSizeScale * block.headingScale,
                        fontFamily = inline.fontFamily,
                        superscript = inline.superscript,
                        subscript = inline.subscript
                    )
                )
                cursor.semantic.append(normalized)
                cursor.pos += normalized.length
            }
            is Element -> {
                when (val tag = node.tagName().lowercase()) {
                    "script", "style", "noscript", "template" -> return
                    "rt", "rp" -> return // handled via parent check; skip subtree
                    "br" -> {
                        checkAnchor(node, cursor)
                        flushParagraph(cursor, preserveEmpty = true, baseTextSizePx, density)
                    }
                    "hr" -> {
                        // preserve as a blank separator; TODO：upgrades to Rule.
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        checkAnchor(node, cursor)
                        cursor.paragraphs += ReaderHtmlParagraph(
                            listOf(ReaderChapterInlineSource.BlankLine(cursor.pos)),
                            0f, 0f, null, emptyList()
                        )
                        cursor.semantic.append('\n')
                        cursor.pos += 1
                    }
                    "img", "image" -> {
                        checkAnchor(node, cursor)
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
                        cursor.semantic.append('\uFFFC')
                        cursor.pos += 1
                    }
                    "p", "div", "section", "article", "header", "footer",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "blockquote", "ul", "ol", "li", "pre", "figure",
                    "figcaption", "aside", "dl", "dt", "dd", "tr", "center" -> {
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        checkAnchor(node, cursor)
                        val childBlock = descendBlock(node, tag, block, baseTextSizePx, density)
                        // Establish geometry for the upcoming paragraph run.
                        val geo = blockGeometry(childBlock, baseTextSizePx, density)
                        cursor.currentAlignment = geo.alignment
                        cursor.currentDecorations = geo.decorations
                        cursor.currentFirstMargin = geo.firstMargin
                        cursor.currentRestMargin = geo.restMargin
                        cursor.currentPreserveBodyIndent = geo.preserveBodyIndent
                        for (child in node.childNodes().toList()) {
                            walk(child, inline, childBlock, cursor, baseTextSizePx, density)
                        }
                        flushParagraph(cursor, preserveEmpty = false, baseTextSizePx, density)
                        // Reset to parent geometry for following siblings.
                        val parentGeo = blockGeometry(block, baseTextSizePx, density)
                        cursor.currentAlignment = parentGeo.alignment
                        cursor.currentDecorations = parentGeo.decorations
                        cursor.currentFirstMargin = parentGeo.firstMargin
                        cursor.currentRestMargin = parentGeo.restMargin
                        cursor.currentPreserveBodyIndent = parentGeo.preserveBodyIndent
                    }
                    "td", "th" -> {
                        checkAnchor(node, cursor)
                        if (cursor.current.isNotEmpty()) {
                            val prev = (cursor.current.lastOrNull() as? ReaderChapterInlineSource.Text)?.value
                            if (prev == null || !prev.endsWith(" ")) {
                                cursor.current += ReaderChapterInlineSource.Text(" ", cursor.pos)
                                cursor.semantic.append(' ')
                                cursor.pos += 1
                            }
                        }
                        val childInline = descendInline(node, tag, inline)
                        for (child in node.childNodes().toList()) {
                            walk(child, childInline, block, cursor, baseTextSizePx, density)
                        }
                    }
                    else -> {
                        checkAnchor(node, cursor)
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
        val preserveBodyIndent: Boolean
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
        block.listStack.forEachIndexed { index, _ ->
            decorations += ReaderParagraphDecoration(
                ReaderParagraphDecorationKind.BULLET,
                null,
                4f * density.coerceAtLeast(1f),
                (block.quoteDepth + index) * step
            )
        }
        val totalLevels = block.quoteDepth + block.listStack.size
        val margin = totalLevels * step
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
        when (tag) {
            "blockquote" -> quoteDepth += 1
            "ul" -> listStack = listStack + false
            "ol" -> listStack = listStack + true
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
        return BlockCtx(alignment, quoteDepth, listStack, inPre, headingScale, headingBold, structural)
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
                }
            }
            declarations["font-style"]?.let { v ->
                if (v.equals("italic", ignoreCase = true) || v.equals("oblique", ignoreCase = true)) {
                    out = out.copy(italic = true)
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
            "justify" -> return ReaderTextAlignment.START
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
                cursor.currentDecorations,
                cursor.currentPreserveBodyIndent
            )
            cursor.semantic.append('\n')
            cursor.pos += 1
        } else if (preserveEmpty) {
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
            cursor.semantic.append('\n')
            cursor.pos += 1
        }
        cursor.current = mutableListOf()
    }
}
