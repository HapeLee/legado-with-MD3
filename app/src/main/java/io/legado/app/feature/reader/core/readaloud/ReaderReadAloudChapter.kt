package io.legado.app.feature.reader.core.readaloud

import io.legado.app.constant.AppPattern
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ContentSplitPolicies
import io.legado.app.domain.model.readaloud.ContentSplitPolicy
import io.legado.app.domain.model.readaloud.ReadAloudContentSplitter
import io.legado.app.domain.model.readaloud.ReadAloudSplitUnit
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.feature.reader.core.cast.CastMarkers

data class ReaderReadAloudParagraph(
    val text: String,
    val chapterPosition: Int,
    val isParagraphEnd: Boolean,
) {
    val endPosition: Int get() = chapterPosition + text.length
}

data class ReaderReadAloudChapter(
    val chapterIndex: Int,
    val title: String,
    val pageStarts: List<Int>,
    val paragraphs: List<ReaderReadAloudParagraph>,
    val pageParagraphs: List<ReaderReadAloudParagraph>,
    val chapterLength: Int,
    /** 生成 [paragraphs] 所用的划分方式，朗读服务据此保持段落与播放队列同粒度。 */
    val contentSplitMode: ReadAloudContentSplitMode = ReadAloudContentSplitMode.Default,
) {
    val pageCount: Int get() = pageStarts.size

    fun pageStart(index: Int): Int = pageStarts.getOrElse(index) { pageStarts.lastOrNull() ?: 0 }

    fun pageIndexAt(position: Int): Int = pageStarts
        .indexOfLast { it <= position }
        .coerceAtLeast(0)
        .coerceAtMost((pageStarts.size - 1).coerceAtLeast(0))

    /**
     * 朗读单元集合。
     *
     * [splitByPage] 决定用整页切分视图还是段落视图；[policy] 在其上再做一次划分方式覆盖
     * （「按符号」需要重切，整段/整页本身已是目标粒度，重切是恒等操作）。
     */
    fun paragraphs(
        splitByPage: Boolean,
        policy: ContentSplitPolicy? = null,
    ): List<ReaderReadAloudParagraph> {
        val source = if (splitByPage) pageParagraphs else paragraphs
        return if (policy == null) source else policy.applyTo(source)
    }

    /**
     * 朗读单元的**可读**视图：空白与纯符号段念不出东西，不进朗读单元集合。
     *
     * 把它们留在集合里，游标会落在不会出声的段上：高亮停在那儿不动，而引擎入队时又跳过它，
     * 回调下标与游标整条错开（见 [isUnspeakableReadAloudUnit]）。
     * 整章都没有可发声单元（纯符号页、图片页）时退回原始列表：由「读完本章」那一套收尾，
     * 而不是卡在定位不到段落上。
     */
    fun readAloudParagraphs(
        splitByPage: Boolean,
        policy: ContentSplitPolicy? = null,
    ): List<ReaderReadAloudParagraph> {
        val source = paragraphs(splitByPage, policy)
        return source.filterNot { it.text.isUnspeakableReadAloudUnit() }.ifEmpty { source }
    }

    /** 定位朗读游标：落在不发声段上时交给它后面的第一个可发声单元。 */
    fun paragraphIndexAtOrAfter(
        position: Int,
        splitByPage: Boolean,
        policy: ContentSplitPolicy? = null,
    ): Int = readAloudParagraphs(splitByPage, policy).indexOfFirst { it.endPosition >= position }

    fun canonicalSpeechParagraphs(
        splitByPage: Boolean,
        policy: ContentSplitPolicy? = null,
    ): List<CanonicalSpeechParagraph> =
        paragraphs(splitByPage, policy).mapIndexed { index, paragraph ->
            CanonicalSpeechParagraph(
                index,
                paragraph.text.sanitizeForSpeech(),
                paragraph.chapterPosition
            )
        }

    companion object {
        fun create(
            chapterIndex: Int,
            title: String,
            semanticContent: String,
            pageStarts: List<Int>,
            contentSplitMode: ReadAloudContentSplitMode = ReadAloudContentSplitMode.Paragraph,
        ): ReaderReadAloudChapter {
            val normalizedStarts = pageStarts
                .asSequence()
                .filter { it >= 0 }
                .distinct()
                .sorted()
                .toList()
                .ifEmpty { listOf(0) }
            val policy = ContentSplitPolicies.forMode(contentSplitMode)
            val units = ReadAloudContentSplitter.splitLines(semanticContent, policy)
            val paragraphs = units.map { it.toParagraph(isParagraphEnd = true) }
            // 页边界用整段单元切分，保证 pageParagraphs 与 paragraphs 粒度一致：
            // 按页朗读时「整页」划分应由调用方另外选择 Page 划分方式。
            val pageParagraphs = paragraphs.flatMap { paragraph ->
                val cuts = normalizedStarts.filter {
                    it > paragraph.chapterPosition && it < paragraph.endPosition
                }
                buildList {
                    var start = paragraph.chapterPosition
                    (cuts + paragraph.endPosition).forEach { end ->
                        if (end > start) {
                            add(
                                ReaderReadAloudParagraph(
                                    text = semanticContent.substring(start, end),
                                    chapterPosition = start,
                                    isParagraphEnd = end == paragraph.endPosition,
                                )
                            )
                        }
                        start = end
                    }
                }
            }
            return ReaderReadAloudChapter(
                chapterIndex = chapterIndex,
                title = title,
                pageStarts = normalizedStarts,
                paragraphs = paragraphs,
                pageParagraphs = pageParagraphs,
                chapterLength = paragraphs.lastOrNull()?.endPosition ?: 0,
                contentSplitMode = contentSplitMode,
            )
        }
    }
}

/**
 * 用 [policy] 重切已有的朗读单元，保留每个单元的绝对位置。
 * 整页单元可能横跨多个原始段落，因此按绝对位置反查所属单元。
 */
internal fun ContentSplitPolicy.applyTo(
    units: List<ReaderReadAloudParagraph>,
): List<ReaderReadAloudParagraph> {
    if (symbols.isEmpty()) return units
    val splitUnits = ReadAloudContentSplitter.splitParagraphs(
        paragraphs = units.map { ReadAloudSplitUnit(it.text, it.chapterPosition) },
        policy = this,
    )
    return splitUnits.map { split ->
        val owner = units.lastOrNull { it.chapterPosition <= split.chapterPosition }
        ReaderReadAloudParagraph(
            text = split.text,
            chapterPosition = split.chapterPosition,
            // 只有切到所属单元末尾才算段末；中间切出的子单元仍属同一段
            isParagraphEnd = owner != null &&
                    split.chapterPosition + split.text.length >= owner.endPosition,
        )
    }
}

private fun ReadAloudSplitUnit.toParagraph(isParagraphEnd: Boolean): ReaderReadAloudParagraph =
    ReaderReadAloudParagraph(
        text = text,
        chapterPosition = chapterPosition,
        isParagraphEnd = isParagraphEnd,
    )

private fun String.sanitizeForSpeech(): String = replace(Regex("[袮祢꧁\uFFFC]"), " ")

/**
 * 朗读单元是否**念不出东西**：去掉角色标记后只剩空白/控制/分隔/标点/符号。
 *
 * 判据与朗读服务的跳过口径同源（[AppPattern.notReadAloudRegex]），服务侧送进引擎前还会
 * 去掉不可念的标记与字符，所以这里判为不发声的段在那边必然也发不出声。章内常见的 `——`、
 * `……` 分隔行与空行都落在这条判据里；「①②③」这类序号字符不属于标点/符号类，照旧朗读。
 */
fun String.isUnspeakableReadAloudUnit(): Boolean =
    CastMarkers.strip(this).matches(AppPattern.notReadAloudRegex)
