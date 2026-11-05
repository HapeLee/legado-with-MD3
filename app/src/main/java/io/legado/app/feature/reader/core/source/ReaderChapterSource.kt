package io.legado.app.feature.reader.core.source

import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.semantic.ReaderSemanticContract

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
        val presentation: ReaderLocalPresentation.Paragraph? = null,
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
    data class Image(
        val source: String,
        override val chapterPosition: Int,
        /** Explicit HTML image action; source-URL options remain at the legacy boundary. */
        val action: String? = null,
    ) : ReaderChapterInlineSource
    data class BlankLine(override val chapterPosition: Int) : ReaderChapterInlineSource

    /**
     * 段首背景音乐胶囊：只占行内宽度，不占任何语义字符（[semanticContent] UTF-16 内容不变），
     * 所以朗读链路读不到它，正文文字也与不开时完全一致。锚点是段落序号，
     * 与 BgmSceneStore.paragraphTexts 同一套计数。
     */
    data class BgmScene(
        override val chapterPosition: Int,
        val paragraphIndex: Int,
        val poolName: String,
        val trackName: String,
    ) : ReaderChapterInlineSource
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
    /** Body text in UTF-16 code units; titles and visual capsules are outside this space. */
    val semanticContent: String = "",
    /** Stored BGM ordinals identify input paragraphs, including a whole rich-text block. */
    val paragraphs: List<ReaderSourceParagraph> = emptyList(),
    val anchorPositions: Map<String, Int> = emptyMap(),
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

data class ReaderSourceParagraph(val ordinal: Int, val start: Int, val endExclusive: Int)

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
        htmlSemanticTextResolver: ReaderHtmlSemanticTextResolver? = null,
        /**
         * 多角色分配：null = 关闭（行为与原版逐字节一致）。非 null = 开启：锚点开引号后
         * 插入命中的 `<<名字（池）>>` 标记（占语义坐标，朗读链路当普通文本读，
         * 渲染层展开成胶囊）；未命中的锚点不插文本，由测量侧同规则跟踪器就地
         * 合成「未分配」占位胶囊。
         */
        castLabels: Map<Int, String>? = null,
        /**
         * 背景音乐分配：段落序号 → (音乐池名, 指定曲目名)。空 = 关闭。
         * 只在段首挂一个视觉胶囊，不向正文注入任何字符。
         */
        bgmScenes: Map<Int, Pair<String, String>> = emptyMap(),
        localPresentation: ReaderLocalPresentation? = null,
    ): ReaderChapterSource {
        val appearance = localPresentation?.bind(if (paragraphs.isEmpty()) "" else paragraphs.joinToString("\n", postfix = "\n"))
        val projection = appearance?.let { TextProjection(it.content.length) }
        val edits = mutableListOf<TextProjection.Edit>()
        var inputPosition = 0
        val result = mutableListOf<ReaderChapterSourceBlock>()
        val semanticContent = StringBuilder()
        val sourceParagraphs = mutableListOf<ReaderSourceParagraph>()
        var position = 0
        var ordinal = -1
        // 多角色分配锚点跟踪器：与测量侧 CastQuoteTracker 同规则，保证 ordinal 一致
        val castTracker = CastMarkers.CastQuoteTracker()
        if (includeTitle && title.isNotBlank()) {
            result += ReaderTitleSegmentation().blocks(title)
        }
        paragraphs.forEach { rawParagraph ->
            val trimmed = rawParagraph.trim()
            val paragraphIndex = if (trimmed.isNotEmpty()) ++ordinal else -1
            val start = position
            fun recordParagraph() {
                if (paragraphIndex >= 0) sourceParagraphs += ReaderSourceParagraph(paragraphIndex, start, position)
            }
            fun addBgmScene() {
                bgmScenes[paragraphIndex]?.let { (pool, track) ->
                    result += ReaderChapterSourceBlock.Paragraph(listOf(
                        ReaderChapterInlineSource.BgmScene(start, paragraphIndex, pool, track)), start)
                }
            }
            if (adaptSpecialStyle && trimmed == "[newpage]") {
                if (projection != null) edits += TextProjection.Edit(inputPosition, inputPosition + rawParagraph.length + 1, 0)
                inputPosition += rawParagraph.length + 1
                result += ReaderChapterSourceBlock.PageBreak(position)
                recordParagraph()
                return@forEach
            }
            if (adaptSpecialStyle && trimmed.startsWith("<usehtml>") && trimmed.endsWith("</usehtml>")) {
                val html = trimmed.removePrefix("<usehtml>").removeSuffix("</usehtml>")
                val semanticText = requireNotNull(htmlSemanticTextResolver) { "Explicit HTML requires the platform resolver" }.resolve(html)
                val semanticLength = semanticText.length
                if (projection != null) edits += TextProjection.Edit(inputPosition,
                    inputPosition + rawParagraph.length, semanticLength)
                inputPosition += rawParagraph.length + 1
                addBgmScene()
                result += ReaderChapterSourceBlock.Html(html, position, semanticLength)
                semanticContent.append(semanticText).append(ReaderSemanticContract.PARAGRAPH_BREAK)
                position += semanticLength + 1
                recordParagraph()
                return@forEach
            }
            val inlineItems = mutableListOf<ReaderChapterInlineSource>()
            val paragraphPosition = position
            var cursor = 0
            // 多角色分配：在 Text 片段内把标记插到对应开引号后，并推进全局引号计数
            fun injectCast(text: String, start: Int): String {
                val labels = castLabels ?: return text
                return CastMarkers.injectParagraph(text, castTracker, labels,
                    onInsert = if (projection == null) null else { offset, count ->
                        edits += TextProjection.Edit(inputPosition + start + offset,
                            inputPosition + start + offset, count, inheritPrevious = true)
                    })
            }
            imagePattern.findAll(rawParagraph).forEach { match ->
                if (cursor < match.range.first) {
                    val text = rawParagraph.substring(cursor, match.range.first)
                    if (text.isNotEmpty()) {
                        val castText = injectCast(text, cursor)
                        inlineItems += ReaderChapterInlineSource.Text(castText, position)
                        semanticContent.append(castText)
                        position += castText.length
                    }
                }
                if (projection != null) edits += TextProjection.Edit(inputPosition + match.range.first,
                    inputPosition + match.range.last + 1, 1)
                inlineItems += ReaderChapterInlineSource.Image(match.groupValues[1], position)
                semanticContent.append(ReaderSemanticContract.IMAGE_CHAR)
                position += 1
                cursor = match.range.last + 1
            }
            if (cursor < rawParagraph.length) {
                val text = rawParagraph.substring(cursor)
                if (text.isNotEmpty()) {
                    val castText = injectCast(text, cursor)
                    inlineItems += ReaderChapterInlineSource.Text(castText, position)
                    semanticContent.append(castText)
                    position += castText.length
                }
            }
            if (inlineItems.isNotEmpty()) addBgmScene()
            if (inlineItems.isNotEmpty()) {
                result += ReaderChapterSourceBlock.Paragraph(inlineItems, paragraphPosition)
            }
            semanticContent.append(ReaderSemanticContract.PARAGRAPH_BREAK)
            position += 1 // Paragraph separator uses the same chapter-position unit as legacy layout.
            recordParagraph()
            inputPosition += rawParagraph.length + 1
        }
        val source = ReaderChapterSource(chapterIndex, title, result, position, semanticContent.toString(), sourceParagraphs)
        if (projection == null) return source
        projection.replace(edits)
        return checkNotNull(appearance).remapOrNull(projection, source.semanticContent)?.applyTo(source) ?: source
    }

    /** Existing assignment eligibility: images and explicit legacy HTML are not quote anchors.
     * Local books now use ordinary paragraphs, so all their visible dialogue follows this path.
     */
    fun castAnchorText(paragraphs: List<String>, adaptSpecialStyle: Boolean): List<String> =
        paragraphs.map { raw ->
            val trimmed = raw.trim()
            when {
                adaptSpecialStyle && trimmed == "[newpage]" -> ""
                adaptSpecialStyle && trimmed.startsWith("<usehtml>") && trimmed.endsWith("</usehtml>") -> ""
                else -> imagePattern.replace(raw, "\uFFFC")
            }
        }
}
