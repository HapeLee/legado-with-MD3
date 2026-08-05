package io.legado.app.ui.dict

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal data class DictHtmlDocument(
    val paragraphs: List<DictHtmlParagraph>,
)

internal data class DictHtmlParagraph(
    val content: List<DictHtmlInline>,
)

internal sealed interface DictHtmlInline {
    data class Text(
        val value: String,
        val style: DictHtmlTextStyle,
        val link: String? = null,
    ) : DictHtmlInline

    data class Image(
        val source: String,
        val description: String?,
        val link: String? = null,
    ) : DictHtmlInline

    data object LineBreak : DictHtmlInline
}

internal data class DictHtmlTextStyle(
    val color: String? = null,
    val backgroundColor: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val relativeFontSize: Float = 1f,
    val baseline: DictHtmlBaseline = DictHtmlBaseline.Normal,
)

internal enum class DictHtmlBaseline { Normal, Subscript, Superscript }

internal object DictHtmlParser {

    private val blockTags = setOf(
        "address", "article", "aside", "blockquote", "div", "dl", "fieldset", "figcaption",
        "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
        "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul",
    )

    fun parse(html: String): DictHtmlDocument {
        val paragraphs = mutableListOf<DictHtmlParagraph>()
        parseContainer(
            nodes = Jsoup.parseBodyFragment(html).body().childNodes(),
            inheritedStyle = DictHtmlTextStyle(),
            inheritedLink = null,
            paragraphs = paragraphs,
        )
        return DictHtmlDocument(paragraphs)
    }

    private fun parseContainer(
        nodes: List<Node>,
        inheritedStyle: DictHtmlTextStyle,
        inheritedLink: String?,
        paragraphs: MutableList<DictHtmlParagraph>,
    ) {
        var current = mutableListOf<DictHtmlInline>()

        fun flush() {
            trimBoundaryWhitespace(current)
            if (current.isNotEmpty()) paragraphs += DictHtmlParagraph(current)
            current = mutableListOf()
        }

        nodes.forEach { node ->
            if (node is Element && node.normalName() in blockTags) {
                flush()
                parseBlock(node, inheritedStyle, inheritedLink, paragraphs)
            } else {
                parseInline(node, inheritedStyle, inheritedLink, current)
            }
        }
        flush()
    }

    private fun parseBlock(
        element: Element,
        inheritedStyle: DictHtmlTextStyle,
        inheritedLink: String?,
        paragraphs: MutableList<DictHtmlParagraph>,
    ) {
        val style = mergeStyle(inheritedStyle, element)
        when (element.normalName()) {
            "div", "section", "article", "main", "header", "footer", "nav", "aside", "form",
            "fieldset", "figure", "figcaption", "address", "blockquote" -> parseContainer(
                element.childNodes(), style, inheritedLink, paragraphs
            )

            "ul", "ol" -> parseList(element, style, inheritedLink, paragraphs)
            "table" -> element.select("tr").forEach { row ->
                val content = mutableListOf<DictHtmlInline>()
                row.select(":scope > th, :scope > td").forEachIndexed { index, cell ->
                    if (index > 0) appendText(content, "  ", style, inheritedLink)
                    cell.childNodes().forEach { parseInline(it, style, inheritedLink, content) }
                }
                addParagraph(content, paragraphs)
            }

            "hr" -> paragraphs += DictHtmlParagraph(
                listOf(DictHtmlInline.Text("────────", style, inheritedLink))
            )

            else -> {
                val content = mutableListOf<DictHtmlInline>()
                element.childNodes().forEach { parseInline(it, style, inheritedLink, content) }
                addParagraph(content, paragraphs)
            }
        }
    }

    private fun parseList(
        list: Element,
        style: DictHtmlTextStyle,
        link: String?,
        paragraphs: MutableList<DictHtmlParagraph>,
    ) {
        val ordered = list.normalName() == "ol"
        val start = list.attr("start").toIntOrNull() ?: 1
        list.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
            val content = mutableListOf<DictHtmlInline>()
            appendText(content, if (ordered) "${start + index}. " else "• ", style, link)
            item.childNodes().forEach { parseInline(it, style, link, content) }
            addParagraph(content, paragraphs)
        }
    }

    private fun parseInline(
        node: Node,
        inheritedStyle: DictHtmlTextStyle,
        inheritedLink: String?,
        output: MutableList<DictHtmlInline>,
    ) {
        when (node) {
            is TextNode -> appendText(
                output,
                normalizeWhitespace(node.wholeText),
                inheritedStyle,
                inheritedLink
            )

            is Element -> {
                val style = mergeStyle(inheritedStyle, node)
                val link = if (node.normalName() == "a") node.attr("href")
                    .ifBlank { inheritedLink } else inheritedLink
                when (node.normalName()) {
                    "br" -> output += DictHtmlInline.LineBreak
                    "img" -> node.attr("src").takeIf(String::isNotBlank)?.let { source ->
                        output += DictHtmlInline.Image(
                            source,
                            node.attr("alt").ifBlank { null },
                            link
                        )
                    }

                    in blockTags -> {
                        if (output.isNotEmpty() && output.last() !is DictHtmlInline.LineBreak) {
                            output += DictHtmlInline.LineBreak
                        }
                        node.childNodes().forEach { parseInline(it, style, link, output) }
                        if (output.isNotEmpty() && output.last() !is DictHtmlInline.LineBreak) {
                            output += DictHtmlInline.LineBreak
                        }
                    }

                    else -> node.childNodes().forEach { parseInline(it, style, link, output) }
                }
            }
        }
    }

    private fun appendText(
        output: MutableList<DictHtmlInline>,
        value: String,
        style: DictHtmlTextStyle,
        link: String?,
    ) {
        if (value.isEmpty()) return
        val previous = output.lastOrNull() as? DictHtmlInline.Text
        if (previous != null && previous.style == style && previous.link == link) {
            output[output.lastIndex] = previous.copy(value = previous.value + value)
        } else {
            output += DictHtmlInline.Text(value, style, link)
        }
    }

    private fun addParagraph(
        content: MutableList<DictHtmlInline>,
        paragraphs: MutableList<DictHtmlParagraph>,
    ) {
        trimBoundaryWhitespace(content)
        if (content.isNotEmpty()) paragraphs += DictHtmlParagraph(content)
    }

    private fun trimBoundaryWhitespace(content: MutableList<DictHtmlInline>) {
        val first = content.firstOrNull() as? DictHtmlInline.Text
        if (first != null) {
            val trimmed = first.value.trimStart()
            if (trimmed.isEmpty()) content.removeAt(0) else content[0] = first.copy(value = trimmed)
        }
        val last = content.lastOrNull() as? DictHtmlInline.Text
        if (last != null) {
            val trimmed = last.value.trimEnd()
            if (trimmed.isEmpty()) content.removeAt(content.lastIndex)
            else content[content.lastIndex] = last.copy(value = trimmed)
        }
        while (content.firstOrNull() is DictHtmlInline.LineBreak) content.removeAt(0)
        while (content.lastOrNull() is DictHtmlInline.LineBreak) content.removeAt(content.lastIndex)
    }

    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ")

    private fun mergeStyle(parent: DictHtmlTextStyle, element: Element): DictHtmlTextStyle {
        var style = when (element.normalName()) {
            "b", "strong" -> parent.copy(bold = true)
            "i", "em", "cite", "dfn" -> parent.copy(italic = true)
            "u", "ins" -> parent.copy(underline = true)
            "s", "strike", "del" -> parent.copy(strikeThrough = true)
            "sub" -> parent.copy(
                relativeFontSize = parent.relativeFontSize * 0.8f,
                baseline = DictHtmlBaseline.Subscript
            )

            "sup" -> parent.copy(
                relativeFontSize = parent.relativeFontSize * 0.8f,
                baseline = DictHtmlBaseline.Superscript
            )

            "small" -> parent.copy(relativeFontSize = parent.relativeFontSize * 0.8f)
            "big" -> parent.copy(relativeFontSize = parent.relativeFontSize * 1.2f)
            "h1" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 2f)
            "h2" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 1.5f)
            "h3" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 1.17f)
            "h4", "h5", "h6" -> parent.copy(bold = true)
            "mark" -> parent.copy(backgroundColor = "#ffff00")
            else -> parent
        }
        if (element.normalName() == "font") {
            element.attr("color").takeIf(String::isNotBlank)?.let { style = style.copy(color = it) }
            element.attr("size").toIntOrNull()?.let { size ->
                val scale = when (size.coerceIn(1, 7)) {
                    1 -> .63f; 2 -> .82f; 3 -> 1f; 4 -> 1.13f; 5 -> 1.5f; 6 -> 2f; else -> 3f
                }
                style = style.copy(relativeFontSize = scale)
            }
        }
        element.attr("style").split(';').forEach { declaration ->
            val (name, rawValue) = declaration.split(':', limit = 2).takeIf { it.size == 2 }
                ?: return@forEach
            val value = rawValue.trim().removeSuffix("!important").trim()
            style = when (name.trim().lowercase()) {
                "color" -> style.copy(color = value)
                "background", "background-color" -> style.copy(backgroundColor = value)
                "font-weight" -> style.copy(
                    bold = value.equals("bold", true) || value.toIntOrNull()
                        ?.let { it >= 600 } == true)

                "font-style" -> style.copy(
                    italic = value.equals(
                        "italic",
                        true
                    ) || value.equals("oblique", true)
                )

                "text-decoration", "text-decoration-line" -> style.copy(
                    underline = "underline" in value.lowercase(),
                    strikeThrough = "line-through" in value.lowercase(),
                )

                "font-size" -> parseRelativeFontSize(value)?.let { style.copy(relativeFontSize = it) }
                    ?: style

                "vertical-align" -> style.copy(
                    baseline = when (value.lowercase()) {
                        "sub" -> DictHtmlBaseline.Subscript
                        "super" -> DictHtmlBaseline.Superscript
                        else -> DictHtmlBaseline.Normal
                    }
                )

                else -> style
            }
        }
        return style
    }

    private fun parseRelativeFontSize(value: String): Float? = when {
        value.endsWith("em", true) -> value.dropLast(2).trim().toFloatOrNull()
        value.endsWith("%") -> value.dropLast(1).trim().toFloatOrNull()?.div(100f)
        value.endsWith("px", true) -> value.dropLast(2).trim().toFloatOrNull()?.div(16f)
        value.equals("smaller", true) -> 0.8f
        value.equals("larger", true) -> 1.2f
        else -> null
    }?.coerceIn(0.5f, 4f)
}
