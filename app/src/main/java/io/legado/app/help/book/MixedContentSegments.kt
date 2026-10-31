package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode

/** Only the existing <usehtml> channel envelope; not an HTML parser or a document model. */
internal object MixedContentSegments {
    sealed interface Segment {
        data class Plain(val text: String) : Segment
        data class Rich(val html: String) : Segment
    }

    fun split(content: String): List<Segment> = buildList {
        var cursor = 0
        AppPattern.useHtmlRegex.findAll(content).forEach { match ->
            if (cursor < match.range.first) add(Segment.Plain(content.substring(cursor, match.range.first)))
            add(Segment.Rich(match.value.removePrefix("<usehtml>").removeSuffix("</usehtml>")))
            cursor = match.range.last + 1
        }
        if (cursor < content.length) add(Segment.Plain(content.substring(cursor)))
    }

    fun transform(content: String, plain: (String) -> String, rich: (String) -> String): String =
        split(content).joinToString("") { segment ->
            when (segment) {
                is Segment.Plain -> plain(segment.text)
                is Segment.Rich -> "<usehtml>${rich(segment.html)}</usehtml>"
            }
        }

    private val legacyTag = Regex("""<(?:(?:"[^"]*")|(?:'[^']*')|[^'">])*>""")

    fun transformPlainText(text: String, transform: (String) -> String): String {
        var cursor = 0
        return buildString {
            legacyTag.findAll(text).forEach { tag ->
                append(transform(text.substring(cursor, tag.range.first)))
                append(tag.value)
                cursor = tag.range.last + 1
            }
            append(transform(text.substring(cursor)))
        }
    }

    /** Transform decoded text nodes, preserving resource, anchor and CSS attributes. */
    fun transformHtmlText(html: String, transform: (String) -> String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        var changed = false
        fun visit(node: org.jsoup.nodes.Node) {
            if (node is org.jsoup.nodes.Element && node.normalName() in setOf("script", "style", "noscript", "template")) return
            if (node is TextNode) {
                val before = node.wholeText
                val after = transform(before)
                if (after != before) { node.text(after); changed = true }
            } else node.childNodes().toList().forEach(::visit)
        }
        visit(doc.body())
        return if (changed) doc.body().html() else html
    }

    /** Shared with final processing and the edit adapter, so their paragraph spaces agree. */
    fun paragraphs(content: String, indent: String, adaptHtml: Boolean, firstUnindented: Boolean = false): List<String> = buildList {
        fun plain(text: String) {
            text.split("\n").forEach { line ->
                val value = line.trim { it.code <= 0x20 || it == '　' }
                if (value.isNotEmpty()) add(if (firstUnindented && isEmpty()) value else indent + value)
            }
        }
        if (!adaptHtml) plain(content)
        else split(content).forEach { segment ->
            when (segment) {
                is Segment.Plain -> plain(segment.text)
                is Segment.Rich -> add("<usehtml>${segment.html}</usehtml>")
            }
        }
    }
}
