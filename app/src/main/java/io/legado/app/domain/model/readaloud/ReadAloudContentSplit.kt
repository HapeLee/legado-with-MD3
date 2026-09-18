package io.legado.app.domain.model.readaloud

import io.legado.app.domain.model.readaloud.ContentSplitPolicies.resolve
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode

/**
 * 一个朗读划分单元：在章节语义文本中的绝对位置 + 该位置的原始文本。
 *
 * [chapterPosition] 是模型侧唯一的坐标来源，调用方不得用累加偏移推算，
 * 因为按页切分不引入换行符，累加会漂移。
 */
data class ReadAloudSplitUnit(
    val text: String,
    val chapterPosition: Int,
)

/**
 * 内容划分策略：把使用者选择的划分方式固化成切分所需的最小输入。
 *
 * [allowRoleSplits] 为 false 时表示「一个划分单元内不再按引号/冒号拆成多个片段」，
 * 即整段/整页模式下段落本身就是一个播放单元，多角色朗读也不会在段内产生停顿。
 */
data class ContentSplitPolicy(
    val mode: ReadAloudContentSplitMode,
    val symbols: Set<Char>,
    val allowRoleSplits: Boolean,
) {
    /** 设置或缓存失效用的稳定标识，不参与切分计算。 */
    val identifier: String = buildString {
        append(mode.storageValue)
        append(':')
        append(if (allowRoleSplits) '1' else '0')
        append(':')
        append(symbols.sorted().joinToString(""))
    }

    companion object {
        /**
         * 不指定划分方式时的默认策略：按句末标点划分并允许段内角色切分。
         *
         * 这是「默认」划分方式的原语义，供不经过设置读取的调用方（单测、脚本）沿用；
         * 整段/整页划分必须由调用方显式传入对应策略，否则会退化成句级切分。
         */
        val SentenceLevel = ContentSplitPolicy(
            mode = ReadAloudContentSplitMode.Default,
            symbols = ReadAloudSplitSymbol.sentenceEnds,
            allowRoleSplits = true,
        )
    }
}

/**
 * 把使用者选择的划分方式与标点集合解析成实际切分策略。
 *
 * [ReadAloudContentSplitMode.Default] 的语义是多角色朗读开启时按句末标点切分，
 * 关闭时按整段切分，因此调用方应先用 [resolve] 把「默认」定下来再取策略。
 */
object ContentSplitPolicies {

    /** 整段划分：段落即单元，段内不再切分。 */
    val Paragraph = forMode(ReadAloudContentSplitMode.Paragraph)

    /**
     * 把「默认」按多角色朗读开关定成具体划分方式。
     *
     * 建章节（`ReaderReadAloudChapter.create`）与取播放单元（`paragraphs(policy)`）必须用
     * 同一个结果：只在一侧解析会让章节切分与播放单元粒度不一致。
     */
    fun resolve(
        mode: ReadAloudContentSplitMode,
        useMultiSpeaker: Boolean,
    ): ReadAloudContentSplitMode =
        if (mode == ReadAloudContentSplitMode.Default && !useMultiSpeaker) {
            ReadAloudContentSplitMode.Paragraph
        } else {
            mode
        }

    fun forMode(
        mode: ReadAloudContentSplitMode,
        storedSymbols: Collection<String> = emptySet(),
    ): ContentSplitPolicy = when (mode) {
        ReadAloudContentSplitMode.Default -> ContentSplitPolicy(
            mode = mode,
            symbols = ReadAloudSplitSymbol.sentenceEnds,
            allowRoleSplits = true,
        )

        ReadAloudContentSplitMode.Symbols -> ContentSplitPolicy(
            mode = mode,
            symbols = ReadAloudSplitSymbol.resolveSymbols(storedSymbols),
            allowRoleSplits = true,
        )

        ReadAloudContentSplitMode.Paragraph,
        ReadAloudContentSplitMode.Page,
            -> ContentSplitPolicy(mode = mode, symbols = emptySet(), allowRoleSplits = false)
    }
}

/**
 * 「内容划分方式」设置值与其配套标点集合的编解码。
 *
 * 两者同属一个设置项：划分方式决定是否展示标点多选，标点集合只对「按符号」有意义。
 * 由于 UI 只经单一意图通道回传，这里用 `方式|标点` 的紧凑编码把两者一起带上，
 * 避免为一个下拉框再增加一套意图。
 */
object ReadAloudContentSplitSetting {

    private const val SEPARATOR = '|'

    fun encode(mode: ReadAloudContentSplitMode, symbols: Collection<Char>): String =
        mode.storageValue +
                SEPARATOR +
                symbols.sorted().joinToString("")

    fun decode(value: String): Pair<ReadAloudContentSplitMode, Set<Char>> {
        val modePart = value.substringBefore(SEPARATOR)
        val symbolPart = value.substringAfter(SEPARATOR, "")
        return ReadAloudContentSplitMode.fromStorage(modePart) to
                symbolPart.toCharArray().toSet()
    }
}

/**
 * 「按符号」划分方式可选的标点。展示顺序与设置页顺序一致。
 */
enum class ReadAloudSplitSymbol(val symbol: Char, val labelKey: String) {
    FullStop('。', "symbol_period"),
    Exclamation('！', "symbol_exclamation"),
    Question('？', "symbol_question"),
    Ellipsis('…', "symbol_ellipsis"),
    Semicolon('；', "symbol_semicolon"),
    Comma('，', "symbol_comma"),
    EnumerationComma('、', "symbol_enumeration_comma"),
    Colon('：', "symbol_colon"),
    Dot('.', "symbol_halfwidth_period"),
    Bang('!', "symbol_halfwidth_exclamation"),
    QuestionMark('?', "symbol_halfwidth_question"),
    HalfSemicolon(';', "symbol_halfwidth_semicolon"),
    HalfComma(',', "symbol_halfwidth_comma"),
    HalfColon(':', "symbol_halfwidth_colon"),
    ;

    companion object {
        /** 「默认」与「按符号」在未另行选择时的句末标点集合。 */
        val sentenceEnds: Set<Char> = setOf('。', '！', '？', '…')

        private val bySymbol: Map<Char, ReadAloudSplitSymbol> = entries.associateBy { it.symbol }

        fun fromSymbol(symbol: Char): ReadAloudSplitSymbol? = bySymbol[symbol]

        /** 把存储值过滤成合法标点集合；空集合回退到句末标点，避免「按符号」退化成整段。 */
        fun resolveSymbols(stored: Collection<String>): Set<Char> {
            val resolved = stored
                .mapNotNull { it.toCharArray().firstOrNull() }
                .filter { it in bySymbol }
                .toSet()
            return resolved.ifEmpty { sentenceEnds }
        }

        fun storageValues(symbols: Collection<Char>): Set<String> =
            symbols.map { it.toString() }.toSet()
    }
}

/**
 * 按 [policy] 把章节语义文本切成朗读划分单元。
 *
 * 「按符号」与「默认」把选中的标点切在单元末尾（标点本身保留在单元里，不丢字）；
 * 「整段/整页」由调用方提供已经切好的行段落，这里只负责透明返回。
 */
object ReadAloudContentSplitter {

    fun splitLines(
        semanticContent: String,
        policy: ContentSplitPolicy,
    ): List<ReadAloudSplitUnit> = splitParagraphs(lineParagraphs(semanticContent), policy)

    private fun ContentSplitPolicy.splitsAtSymbols(): Boolean =
        this.symbols.isNotEmpty() &&
                (mode == ReadAloudContentSplitMode.Symbols || mode == ReadAloudContentSplitMode.Default)

    fun splitParagraphs(
        paragraphs: List<ReadAloudSplitUnit>,
        policy: ContentSplitPolicy,
    ): List<ReadAloudSplitUnit> {
        if (!policy.splitsAtSymbols()) return paragraphs
        return paragraphs.flatMap { paragraph -> splitAtSymbols(paragraph, policy.symbols) }
    }

    /** 章节语义文本按换行切段，不引入额外字符位置。 */
    fun lineParagraphs(semanticContent: String): List<ReadAloudSplitUnit> = buildList {
        var start = 0
        semanticContent.forEachIndexed { index, character ->
            if (character == '\n') {
                if (index > start) add(
                    ReadAloudSplitUnit(
                        semanticContent.substring(start, index),
                        start
                    )
                )
                start = index + 1
            }
        }
        if (start < semanticContent.length) {
            add(ReadAloudSplitUnit(semanticContent.substring(start), start))
        }
    }

    private fun splitAtSymbols(
        paragraph: ReadAloudSplitUnit,
        symbols: Set<Char>,
    ): List<ReadAloudSplitUnit> {
        val text = paragraph.text
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<ReadAloudSplitUnit>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] !in symbols) {
                index++
                continue
            }
            // 连续标点视为一个整体，切在同一单元末尾
            var end = index + 1
            while (end < text.length && text[end] in symbols) end++
            result += ReadAloudSplitUnit(
                text = text.substring(start, end),
                chapterPosition = paragraph.chapterPosition + start,
            )
            start = end
            index = end
        }
        if (start < text.length) {
            result += ReadAloudSplitUnit(
                text = text.substring(start),
                chapterPosition = paragraph.chapterPosition + start,
            )
        }
        return result
    }
}
