package io.legado.app.feature.reader.core.source

sealed interface ReaderChapterSourceBlock {
    val chapterPosition: Int

    data class Text(
        val value: String,
        override val chapterPosition: Int,
        val isTitle: Boolean = false,
        val fontSizeScale: Float = 1f,
        val isSubtitle: Boolean = false,
    ) : ReaderChapterSourceBlock

    data class Image(
        val source: String,
        override val chapterPosition: Int,
    ) : ReaderChapterSourceBlock

    data class Paragraph(
        val items: List<ReaderChapterInlineSource>,
        override val chapterPosition: Int,
    ) : ReaderChapterSourceBlock

    data class Html(
        val value: String,
        override val chapterPosition: Int,
        val semanticLength: Int = value.length,
    ) : ReaderChapterSourceBlock

    data class PageBreak(override val chapterPosition: Int) : ReaderChapterSourceBlock
}

sealed interface ReaderChapterInlineSource {
    val chapterPosition: Int

    data class Text(
        val value: String,
        override val chapterPosition: Int,
        val style: ReaderInlineSourceStyle = ReaderInlineSourceStyle(),
    ) : ReaderChapterInlineSource
    data class Image(val source: String, override val chapterPosition: Int) : ReaderChapterInlineSource
    data class BlankLine(override val chapterPosition: Int) : ReaderChapterInlineSource
}

data class ReaderInlineSourceStyle(
    val colorArgb: Int? = null,
    val backgroundArgb: Int? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val link: String? = null,
    val fontSizeScale: Float = 1f,
    val fontFamily: String? = null,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
)

data class ReaderChapterSource(
    val chapterIndex: Int,
    val title: String,
    val blocks: List<ReaderChapterSourceBlock>,
    val characterCount: Int,
    /** Body text in the exact character-position space used by Canvas selection/navigation. */
    val semanticContent: String = "",
) {
    /** Rebuild presentation-only titles without changing body anchors or reloading content. */
    fun withTitleVisibility(
        visible: Boolean,
        segmentation: ReaderTitleSegmentation = ReaderTitleSegmentation(),
    ): ReaderChapterSource {
        val body = blocks.filterNot { it is ReaderChapterSourceBlock.Text && it.isTitle }
        val titles = if (visible) segmentation.blocks(title) else emptyList()
        return copy(blocks = titles + body)
    }

    /** Highlight rules use the displayed segments, including inserted paragraph separators. */
    val semanticTitle: String
        get() = blocks.filterIsInstance<ReaderChapterSourceBlock.Text>()
            .filter { it.isTitle }.joinToString("") { "${it.value}\n" }
}

fun interface ReaderHtmlSemanticTextResolver {
    fun resolve(html: String): String
}

/** Converts processed BookContent paragraphs into renderer-owned semantic blocks. */
object ReaderChapterSourceParser {
    private val imagePattern = Regex("<img[^>]*src=\"([^\"]*(?:\"[^>]+\\})?)\"[^>]*>")

    fun parse(
        chapterIndex: Int,
        title: String,
        paragraphs: List<String>,
        includeTitle: Boolean,
        adaptSpecialStyle: Boolean,
        htmlSemanticTextResolver: ReaderHtmlSemanticTextResolver = ReaderHtmlSemanticTextResolver(::htmlSemanticText),
    ): ReaderChapterSource {
        val result = mutableListOf<ReaderChapterSourceBlock>()
        val semanticContent = StringBuilder()
        var position = 0
        if (includeTitle && title.isNotBlank()) {
            result += ReaderTitleSegmentation().blocks(title)
        }
        paragraphs.forEach { rawParagraph ->
            val trimmed = rawParagraph.trim()
            if (adaptSpecialStyle && trimmed == "[newpage]") {
                result += ReaderChapterSourceBlock.PageBreak(position)
                return@forEach
            }
            if (adaptSpecialStyle && trimmed.startsWith("<usehtml>") && trimmed.endsWith("</usehtml>")) {
                val html = trimmed.removePrefix("<usehtml>").removeSuffix("</usehtml>")
                val semanticText = htmlSemanticTextResolver.resolve(html)
                val semanticLength = semanticText.length
                result += ReaderChapterSourceBlock.Html(html, position, semanticLength)
                semanticContent.append(semanticText).append('\n')
                position += semanticLength + 1
                return@forEach
            }
            val inlineItems = mutableListOf<ReaderChapterInlineSource>()
            val paragraphPosition = position
            var cursor = 0
            imagePattern.findAll(rawParagraph).forEach { match ->
                if (cursor < match.range.first) {
                    val text = rawParagraph.substring(cursor, match.range.first)
                    if (text.isNotEmpty()) {
                        inlineItems += ReaderChapterInlineSource.Text(text, position)
                        semanticContent.append(text)
                        position += text.length
                    }
                }
                inlineItems += ReaderChapterInlineSource.Image(match.groupValues[1], position)
                semanticContent.append('\uFFFC')
                position += 1
                cursor = match.range.last + 1
            }
            if (cursor < rawParagraph.length) {
                val text = rawParagraph.substring(cursor)
                if (text.isNotEmpty()) {
                    inlineItems += ReaderChapterInlineSource.Text(text, position)
                    semanticContent.append(text)
                    position += text.length
                }
            }
            if (inlineItems.isNotEmpty()) {
                result += ReaderChapterSourceBlock.Paragraph(inlineItems, paragraphPosition)
            }
            semanticContent.append('\n')
            position += 1 // Paragraph separator uses the same chapter-position unit as legacy layout.
        }
        return ReaderChapterSource(chapterIndex, title, result, position, semanticContent.toString())
    }

    private fun htmlSemanticText(html: String): String = html
        .replace(imagePattern, "\uFFFC")
        .replace(Regex("<(br|/p|/div|/li)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
