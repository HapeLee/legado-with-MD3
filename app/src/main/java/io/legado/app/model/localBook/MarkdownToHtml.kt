package io.legado.app.model.localBook

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * markdown → 阅读器富文本通道所需的语义 HTML。
 *
 * 输出目标是 [JsoupReaderPresentationParser] 与 [LocalBookSemanticHtml.plainText] 共同支持的
 * 标签集合：h1-h6、p、em/strong/del、code、pre、blockquote、ul/ol/li、hr、a、img。
 * 纯文本通道由 plainText 从同一份 HTML 派生，因此两条通道天然绑定。
 *
 * 两个刻意的发射约定（与 plainText 的标签清洗规则对齐）：
 * - 列表项内容包一层 `<p>`：formatKeepImg 会剥掉 li/ul 标签但不产生换行，
 *   没有 `<p>` 时相邻列表项在纯文本通道会黏成一行。
 * - GFM 表格没有阅读器网格渲染，按行拍平为「列1 | 列2」文本行。
 * 原生 HTML 只在阅读器可呈现的标签范围内直通；脚本/样式等容器在输出边界统一移除，
 * 否则 plainText 会把脚本源码当正文，而富文本通道会跳过这些节点。
 */
object MarkdownToHtml {

    /** 两条通道都不呈现的容器：连同内容一起移除，否则纯文本通道会把脚本/样式当正文。 */
    private val unsafeHtmlContainerRegex = Regex(
        "(?is)<(script|style|noscript|template|iframe|object|embed|svg|math|video|audio|canvas)\\b[^>]*>[\\s\\S]*?</\\1\\s*>"
    )

    /** 解析成独立 token 的残余危险标签（开标签、闭标签、自闭合）。 */
    private val unsafeHtmlTagRegex = Regex(
        "(?i)</?(?:script|style|noscript|template|iframe|object|embed|svg|math|video|audio|canvas)\\b[^>]*/?>"
    )

    /** 未闭合的危险容器：连同后续内容一起删到结尾，否则脚本源码会漏进纯文本通道。 */
    private val unclosedUnsafeHtmlContainerRegex = Regex(
        "(?is)<(?:script|style|noscript|template|iframe|object|embed|svg|math|video|audio|canvas)\\b[^>]*>[\\s\\S]*$"
    )

    /** 行内原生 HTML 只允许阅读器可呈现的标签；形如 `<占位符>` 的普通文本按文本处理。 */
    private val allowedInlineHtmlRegex = Regex(
        "(?i)^</?(?:h[1-6]|p|em|strong|del|code|pre|blockquote|ul|ol|li|hr|br|a|img|span|sub|sup|b|i|s|mark)\\b[^>]*/?>$"
    )

    /** CommonMark：闭合 # 序列前必须有空白，否则属于标题内容（如「使用 C#」）。 */
    private val closingHashesRegex = Regex("[ \\t]+#+[ \\t]*$")

    private val flavour by lazy { GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true) }
    private val parser by lazy { MarkdownParser(flavour) }

    /** 首行 `---` 到闭合 `---` 的 YAML frontmatter 不进入正文。 */
    private val frontMatterRegex =
        Regex("\\A---[ \\t]*\\r?\\n[\\s\\S]*?\\r?\\n---[ \\t]*(?:\\r?\\n|\\z)")

    /**
     * 输出侧统一收口：危险标签在 [emitInline] 中保留原样，容器正则才能把标签与内容一起删掉；
     * 代码块走 escapeText，转义后的 `&lt;script&gt;` 不会命中，示例 HTML 仍是正文。
     */
    private fun stripUnsafeHtmlBlocks(text: String): String =
        unclosedUnsafeHtmlContainerRegex.replace(unsafeHtmlContainerRegex.replace(text, ""), "")

    fun convert(markdown: String): String {
        val body = markdown.removePrefix("\uFEFF")
        val stripped = frontMatterRegex.replace(body, "")
        if (stripped.isBlank()) return ""
        val root = try {
            parser.buildMarkdownTreeFromString(stripped)
        } catch (e: Exception) {
            return "<p>${escapeText(stripped)}</p>"
        }
        val sb = StringBuilder(stripped.length + 64)
        for (child in root.children) {
            emitBlock(child, stripped, sb)
        }
        //原生 HTML 直通的收口：移除两条通道都不呈现的危险容器后再交付，
        //保证 plainText 与 ReaderLocalPresentationResolver 看到同一份内容。
        val html = stripUnsafeHtmlBlocks(sb.toString())
        return unsafeHtmlTagRegex.replace(html, "")
    }

    private fun emitBlock(node: ASTNode, text: String, sb: StringBuilder) {
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> {
                sb.append("<p>")
                emitInlineChildren(node, text, sb)
                sb.append("</p>")
            }

            MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
            MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
                val level = when (node.type) {
                    MarkdownElementTypes.ATX_1 -> 1
                    MarkdownElementTypes.ATX_2 -> 2
                    MarkdownElementTypes.ATX_3 -> 3
                    MarkdownElementTypes.ATX_4 -> 4
                    MarkdownElementTypes.ATX_5 -> 5
                    else -> 6
                }
                emitAtxHeading(level, node, text, sb)
            }

            MarkdownElementTypes.SETEXT_1 -> emitSetextHeading(1, node, text, sb)
            MarkdownElementTypes.SETEXT_2 -> emitSetextHeading(2, node, text, sb)

            MarkdownElementTypes.UNORDERED_LIST -> {
                sb.append("<ul>")
                node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
                    .forEach { emitListItem(it, text, sb) }
                sb.append("</ul>")
            }

            MarkdownElementTypes.ORDERED_LIST -> {
                val start = node.children.firstOrNull { it.type == MarkdownElementTypes.LIST_ITEM }
                    ?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
                    ?.getTextInNode(text)?.trimEnd('.', ' ')?.toIntOrNull()
                if (start != null && start > 1) sb.append("<ol start=\"").append(start).append("\">")
                else sb.append("<ol>")
                node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
                    .forEach { emitListItem(it, text, sb) }
                sb.append("</ol>")
            }

            MarkdownElementTypes.BLOCK_QUOTE -> {
                sb.append("<blockquote>")
                node.children.forEach { emitBlock(it, text, sb) }
                sb.append("</blockquote>")
            }

            MarkdownTokenTypes.HORIZONTAL_RULE -> sb.append("<hr/>")

            MarkdownElementTypes.CODE_FENCE -> {
                val contents = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
                if (contents.isNotEmpty()) {
                    emitCodeBlock(text.substring(contents.first().startOffset, contents.last().endOffset), sb)
                }
            }

            MarkdownElementTypes.CODE_BLOCK -> emitCodeBlock(node.getTextInNode(text), sb)

            GFMElementTypes.TABLE -> emitTable(node, text, sb)

            MarkdownElementTypes.HTML_BLOCK -> {
                val raw = node.getTextInNode(text).trim()
                if (raw.isNotEmpty()) sb.append(raw)
            }

            else -> node.children.forEach { emitBlock(it, text, sb) }
        }
    }

    /** 标题按纯文本发射：行内标记（如标题里的粗体）极少见，换取对闭合 `#` 序列的稳定处理。 */
    private fun emitAtxHeading(level: Int, node: ASTNode, text: String, sb: StringBuilder) {
        val content = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT } ?: return
        val heading = closingHashesRegex.replace(content.getTextInNode(text).trim(), "").trim()
        if (heading.isEmpty()) return
        sb.append("<h").append(level).append(">")
            .append(escapeText(heading))
            .append("</h").append(level).append(">")
    }

    private fun emitSetextHeading(level: Int, node: ASTNode, text: String, sb: StringBuilder) {
        val content = node.children.firstOrNull() ?: return
        sb.append("<h").append(level).append(">")
        emitInlineChildren(content, text, sb)
        sb.append("</h").append(level).append(">")
    }

    private fun emitListItem(node: ASTNode, text: String, sb: StringBuilder) {
        sb.append("<li>")
        val inline = StringBuilder()
        fun flushInline() {
            if (inline.isNotBlank()) {
                sb.append("<p>").append(inline).append("</p>")
                inline.clear()
            }
        }
        for (child in node.children) {
            when (child.type) {
                MarkdownElementTypes.PARAGRAPH -> {
                    flushInline()
                    sb.append("<p>")
                    emitInlineChildren(child, text, sb)
                    sb.append("</p>")
                }

                MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                    flushInline()
                    emitBlock(child, text, sb)
                }

                GFMTokenTypes.CHECK_BOX -> inline.append(
                    if (child.getTextInNode(text).trim() == "[x]") "☑ " else "☐ "
                )

                MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER,
                MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> Unit

                else -> emitInline(child, text, inline)
            }
        }
        flushInline()
        sb.append("</li>")
    }

    private fun emitCodeBlock(code: String, sb: StringBuilder) {
        if (code.isBlank()) return
        sb.append("<pre><code>").append(escapeText(code)).append("</code></pre>")
    }

    private fun emitTable(node: ASTNode, text: String, sb: StringBuilder) {
        val cellsOf: (ASTNode) -> List<ASTNode> = { block ->
            block.children.filter { it.type == GFMTokenTypes.CELL }
        }
        val header = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
        val rows = node.children.filter { it.type == GFMElementTypes.ROW }
        fun emitRow(cells: List<ASTNode>, strong: Boolean) {
            if (cells.isEmpty()) return
            sb.append("<p>")
            cells.forEachIndexed { index, cell ->
                if (index > 0) sb.append(" | ")
                if (strong) sb.append("<strong>")
                emitInlineChildren(cell, text, sb)
                if (strong) sb.append("</strong>")
            }
            sb.append("</p>")
        }
        header?.let { emitRow(cellsOf(it), true) }
        rows.forEach { emitRow(cellsOf(it), false) }
    }

    private fun emitInlineChildren(node: ASTNode, text: String, sb: StringBuilder) {
        for (child in node.children) {
            emitInline(child, text, sb)
        }
    }

    /** 跳过包裹在首尾的标记 token（`*`/`**`/`~~`），否则标记字符会漏进语义标签里。 */
    private fun emitTrimmedChildren(
        node: ASTNode,
        text: String,
        sb: StringBuilder,
        marker: IElementType,
        size: Int,
    ) {
        val children = node.children
        var start = 0
        var end = children.size
        var trimmed = 0
        while (start < end && trimmed < size && children[start].type == marker) {
            start++
            trimmed++
        }
        trimmed = 0
        while (end > start && trimmed < size && children[end - 1].type == marker) {
            end--
            trimmed++
        }
        for (i in start until end) {
            emitInline(children[i], text, sb)
        }
    }

    private fun emitInline(node: ASTNode, text: String, sb: StringBuilder) {
        when (node.type) {
            MarkdownElementTypes.EMPH -> {
                sb.append("<em>")
                emitTrimmedChildren(node, text, sb, MarkdownTokenTypes.EMPH, 1)
                sb.append("</em>")
            }

            MarkdownElementTypes.STRONG -> {
                sb.append("<strong>")
                emitTrimmedChildren(node, text, sb, MarkdownTokenTypes.EMPH, 2)
                sb.append("</strong>")
            }

            GFMElementTypes.STRIKETHROUGH -> {
                sb.append("<del>")
                emitTrimmedChildren(node, text, sb, GFMTokenTypes.TILDE, 2)
                sb.append("</del>")
            }

            MarkdownElementTypes.CODE_SPAN -> sb.append("<code>")
                .append(escapeText(node.getTextInNode(text).trim('`')))
                .append("</code>")

            GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> sb.append("<code>")
                .append(escapeText(node.getTextInNode(text)))
                .append("</code>")

            MarkdownElementTypes.INLINE_LINK -> {
                val destination = node.children
                    .firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                    ?.getTextInNode(text)?.trim().orEmpty()
                sb.append("<a href=\"").append(escapeAttr(destination)).append("\">")
                node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                    ?.let { emitLinkText(it, text, sb) }
                sb.append("</a>")
            }

            MarkdownElementTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK -> {
                val link = node.getTextInNode(text).trim('<', '>')
                sb.append("<a href=\"").append(escapeAttr(link)).append("\">")
                    .append(escapeText(link)).append("</a>")
            }

            MarkdownElementTypes.IMAGE -> emitImage(node, text, sb)

            GFMTokenTypes.CHECK_BOX -> sb.append(
                if (node.getTextInNode(text).trim() == "[x]") "☑ " else "☐ "
            )

            // 段内换行按可见换行发射：笔记类 md 普遍把单换行当换行用，
            // br 在两条通道里都会成为独立段落行。
            MarkdownTokenTypes.EOL -> sb.append("<br/>")

            else -> {
                val children = node.children
                if (children.isEmpty()) {
                    val raw = node.getTextInNode(text)
                    //行内原生 HTML 只在白名单内直通；危险标签保留原样，交给输出侧的容器正则
                    //连内容一起删除；形如 <占位符> 的普通文本按文本转义，避免被 jsoup 吞掉。
                    if (raw.length > 2 && raw.startsWith('<') && raw.endsWith('>')) {
                        val isUnsafeTag = unsafeHtmlTagRegex.containsMatchIn(raw)
                        if (isUnsafeTag || allowedInlineHtmlRegex.matches(raw)) sb.append(raw)
                        else sb.append(escapeText(raw))
                    } else sb.append(escapeText(raw))
                } else children.forEach { emitInline(it, text, sb) }
            }
        }
    }

    private fun emitLinkText(node: ASTNode, text: String, sb: StringBuilder) {
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET,
                MarkdownTokenTypes.EXCLAMATION_MARK -> Unit
                else -> emitInline(child, text, sb)
            }
        }
    }

    private fun emitImage(node: ASTNode, text: String, sb: StringBuilder) {
        val source = node.findChild(MarkdownElementTypes.LINK_DESTINATION)
            ?.getTextInNode(text)?.trim().orEmpty()
        if (source.isEmpty()) return
        val alt = node.findChild(MarkdownElementTypes.LINK_TEXT)
            ?.getTextInNode(text)?.trim('!', '[', ']').orEmpty()
        // src 保持原样发出；相对路径图片由 MarkdownFile.getImage 在取图时按书籍目录解析，
        // 保持 HTML 与纯文本通道 src 一致。
        sb.append("<img src=\"").append(escapeAttr(source))
            .append("\" alt=\"").append(escapeAttr(alt)).append("\"/>")
    }

    private fun ASTNode.findChild(type: IElementType): ASTNode? {
        for (child in children) {
            if (child.type == type) return child
            child.findChild(type)?.let { return it }
        }
        return null
    }

    /** 库自带的 getTextInNode 返回 CharSequence，这里统一取 String，与 MarkdownBlock 同一处理。 */
    private fun ASTNode.getTextInNode(text: String): String = text.substring(startOffset, endOffset)

    private fun escapeText(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttr(text: String): String = escapeText(text).replace("\"", "&quot;")
}
