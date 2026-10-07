package io.legado.app.feature.reader.core.source

import io.legado.app.feature.reader.core.cast.CastMarkers

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
    data class Image(
        val source: String,
        override val chapterPosition: Int,
        /**
         * 只有 HTML 段落（旧 `setTypeHtml`）的图片有：ImageSpan 在 `StaticLayout` 里的占位尺寸。
         * 旧实现的行内图**宽**取 span 的 advance、**行盒高**取 span 的 ascent/descent，都不是
         * 占位字 advance；纯文本路径（`getTextChapter`）为 `null`，走占位字规则。
         */
        val htmlSpanExtent: ReaderHtmlImageSpanExtent? = null,
    ) : ReaderChapterInlineSource
    data class BlankLine(override val chapterPosition: Int) : ReaderChapterInlineSource

    /**
     * 段首背景音乐胶囊：只占行内宽度，不占任何语义字符（[semanticContent] 逐字节不变），
     * 所以朗读链路读不到它，正文文字也与不开时完全一致。锚点是段落序号，
     * 与 BgmSceneStore.ordinals 同一套计数。
     */
    data class BgmScene(
        override val chapterPosition: Int,
        val paragraphIndex: Int,
        val poolName: String,
        val trackName: String,
    ) : ReaderChapterInlineSource
}

/**
 * 旧 `setTypeHtml` 里 `ImageSpan` 的占位尺寸。
 *
 * - [widthPx]：`getPrimaryHorizontal(i + 1) - getPrimaryHorizontal(i)`，即 span 的 advance
 *   （没有 ImageGetter 时框架用系统 fallback 图标的固有宽）。
 * - [heightPx]：该 span 让静态布局行盒至少长到的高度（`getSize` 里 `ascent = -bounds.bottom`）。
 *
 * 旧版行内图的绘制几何是「宽 = 该 advance，高 = 宽 ÷ 位图宽 × 位图高，在行盒内居中」，行盒由
 * [heightPx] 撑开；新 core 用同样两个数就能复刻。
 */
data class ReaderHtmlImageSpanExtent(val widthPx: Float, val heightPx: Float)

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
    ): ReaderChapterSource {
        val result = mutableListOf<ReaderChapterSourceBlock>()
        val semanticContent = StringBuilder()
        var position = 0
        // 多角色分配锚点跟踪器：与测量侧 CastQuoteTracker 同规则，保证 ordinal 一致
        val castTracker = CastMarkers.CastQuoteTracker()
        if (includeTitle && title.isNotBlank()) {
            result += ReaderTitleSegmentation().blocks(title)
        }
        paragraphs.forEachIndexed { paragraphIndex, rawParagraph ->
            val trimmed = rawParagraph.trim()
            if (adaptSpecialStyle && trimmed == "[newpage]") {
                result += ReaderChapterSourceBlock.PageBreak(position)
                return@forEachIndexed
            }
            if (adaptSpecialStyle && trimmed.startsWith("<usehtml>") && trimmed.endsWith("</usehtml>")) {
                val html = trimmed.removePrefix("<usehtml>").removeSuffix("</usehtml>")
                val semanticText = htmlSemanticTextResolver.resolve(html)
                val semanticLength = semanticText.length
                result += ReaderChapterSourceBlock.Html(html, position, semanticLength)
                semanticContent.append(semanticText).append('\n')
                position += semanticLength + 1
                return@forEachIndexed
            }
            val inlineItems = mutableListOf<ReaderChapterInlineSource>()
            val paragraphPosition = position
            var cursor = 0
            // 多角色分配：在 Text 片段内把标记插到对应开引号后，并推进全局引号计数
            fun injectCast(text: String): String {
                val labels = castLabels ?: return text
                return CastMarkers.injectParagraph(text, castTracker, labels)
            }
            imagePattern.findAll(rawParagraph).forEach { match ->
                if (cursor < match.range.first) {
                    val text = rawParagraph.substring(cursor, match.range.first)
                    if (text.isNotEmpty()) {
                        val castText = injectCast(text)
                        inlineItems += ReaderChapterInlineSource.Text(castText, position)
                        semanticContent.append(castText)
                        position += castText.length
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
                    val castText = injectCast(text)
                    inlineItems += ReaderChapterInlineSource.Text(castText, position)
                    semanticContent.append(castText)
                    position += castText.length
                }
            }
            bgmScenes[paragraphIndex]?.takeIf { inlineItems.isNotEmpty() }?.let { (pool, track) ->
                // 配乐胶囊自己成一段（排在正文段之前）：与首句同行会被行首缩进挤到字里，
                // 单占一行才看得清「这一段换什么场景」。它零语义字符，所以不影响章节偏移。
                result += ReaderChapterSourceBlock.Paragraph(
                    listOf(
                        ReaderChapterInlineSource.BgmScene(
                            chapterPosition = paragraphPosition,
                            paragraphIndex = paragraphIndex,
                            poolName = pool,
                            trackName = track,
                        ),
                    ),
                    paragraphPosition,
                )
            }
            if (inlineItems.isNotEmpty()) {
                result += ReaderChapterSourceBlock.Paragraph(inlineItems, paragraphPosition)
            }
            semanticContent.append('\n')
            position += 1 // Paragraph separator uses the same chapter-position unit as legacy layout.
        }
        return ReaderChapterSource(chapterIndex, title, result, position, semanticContent.toString())
    }

    /**
     * 锚点计数用的「与 [parse] 逐字同源」正文视图。
     *
     * [parse] 只对普通段落里的 Text 片段喂引号跟踪器（[CastMarkers.CastQuoteTracker]）：
     * `[newpage]` 与 `<usehtml>…</usehtml>` 整段提前 return，一个字都不喂；
     * `<img …>` 被切成 Image 片段，标签里那对成对直引号同样不喂。
     * 所以 AI 分配、试听取句这类**离线数锚点**的代码如果直接喂原始段落，
     * 在含图或含 HTML 的章里会多出序号，从第一个图片段开始整章错位——
     * 错位后分配的名字就挂到别的句子上。
     *
     * 本函数就在 [parse] 旁边、共用它的 [imagePattern]，规则漂移了会在同一处看见。
     * 图片位置换成 U+FFFC（不含引号，计数与不喂等价）。
     */
    fun castAnchorText(paragraphs: List<String>, adaptSpecialStyle: Boolean): List<String> =
        paragraphs.map { raw ->
            val trimmed = raw.trim()
            when {
                adaptSpecialStyle && trimmed == "[newpage]" -> ""
                adaptSpecialStyle &&
                    trimmed.startsWith("<usehtml>") && trimmed.endsWith("</usehtml>") -> ""
                else -> imagePattern.replace(raw, "\uFFFC")
            }
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
