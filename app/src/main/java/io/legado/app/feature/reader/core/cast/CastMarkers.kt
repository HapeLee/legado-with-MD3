package io.legado.app.feature.reader.core.cast

/**
 * 多角色分配标记：`<<角色名（声音池）>>`。
 *
 * 权威存储是 `chapter_role_assignments` 表；构建章节内容（ContentProcessor 之后、
 * ReaderChapterSourceParser 之前）把已分配标记注入到对应锚点开引号之后：
 * 阅读器测量期把标记展开成行内角色胶囊（`ReaderMeasuredInlineItem.RoleCast`），
 * 朗读链路把标记当普通文本（“TTS 也读得到”角色名）。未分配的锚点不落文本——
 * 由测量侧同规则跟踪器就地合成「未分配」占位胶囊，不占语义坐标。
 * 原文缓存不被改写，本地 txt 的字节偏移体系不受影响，
 * 关闭开关或重新拉取源文也不会丢分配。
 *
 * 具体符号（`<<` `>>` `（` `）`）来自 [CastSyntaxHolder.current]，用户在
 * 「多角色规则 → 多角色识别」里可改；本对象只做转发，不写字面量。
 */
object CastMarkers {

    /** 当前生效的符号配置；名字里禁止出现的字符由它定义。 */
    private val syntax: CastSyntax get() = CastSyntaxHolder.current

    /** 成对引号表（与朗读分段器一致）：开 → 闭。不参与用户配置，见 [CastSyntax] 说明。 */
    val QuotePairs: List<Pair<Char, Char>> = listOf(
        '“' to '”', '‘' to '’', '「' to '」', '『' to '』', '"' to '"', '\'' to '\'',
    )

    /**
     * [QuotePairs] 里「单引号」那一类的**开**字符（弯单引号 `‘` 与直单引号 `'`）。
     *
     * 中文排版用单引号包内心独白（心声），双引号/方头引号（`“ ” 「 」 『 』`）是正常台词，
     * 所以这一档只认单引号。唯一的消费方是
     * [io.legado.app.help.readaloud.cast.CastAssignmentStore.thoughtOrdinals] 与 AI 分配那一趟：
     * 它们据此决定新建分配行时要不要默认给那句套上
     * [io.legado.app.help.readaloud.effect.VoiceEffectStore.THOUGHT_EFFECT]。
     */
    val SingleQuoteOpens: Set<Char> = setOf('‘', '\'')

    /** 一个还没闭合的引号：期望的闭符号、它占用的锚点序号（非锚点为 -1）、开符号在流里的下标。 */
    private class Frame(val close: Char, val ordinal: Int, val openAt: Int)

    /** 一段锚点对话：[start] 是开引号在喂入流里的下标，[endExclusive] 是闭引号后一位。 */
    data class ClosedSpan(val ordinal: Int, val start: Int, val endExclusive: Int)

    /**
     * 章级「分配锚点引号」跟踪器：注入侧与测量侧各持一个实例、喂同一字符流、同规则，
     * 保证 ordinal 一致。规则：
     * - 收到与栈顶期望闭引号相同的字符 → 出栈（嵌套引号优先按闭合处理）；
     * - 收到某对引号的开字符 → 入栈；栈深 < [maxDepth] 时该引号为「分配锚点」，
     *   占用下一个 ordinal（深度 0 = 一级对话；更深的嵌套引号不加胶囊）。
     */
    class CastQuoteTracker(private val maxDepth: Int = 2) {
        private val stack = ArrayDeque<Frame>()
        private var nextOrdinal = 0
        private var position = 0

        /** 最近一次命中的锚点 ordinal（仅 [feed] 返回 true 后有效）。 */
        var lastCastOrdinal = -1
            private set

        /**
         * 已经喂进去多少个字符。跟踪器是章级共享的，[lastClosedSpan] 的下标因此是**整章**的；
         * 一段一段喂的调用方要先记下进段前的这个数，再减回去才能落回本段字符串。
         */
        val fedCharacters: Int
            get() = position

        /**
         * 本次 [feed] 是否刚好闭合了一段锚点对话（每次 feed 重置，仅返回后有效）。
         *
         * 角色气泡要包住整句台词（连同引号和压在上面的胶囊），区间在这里一次算出，
         * 不再另写一份引号栈。
         */
        var lastClosedSpan: ClosedSpan? = null
            private set

        fun feed(ch: Char): Boolean {
            lastClosedSpan = null
            val at = position++
            val top = stack.lastOrNull()
            if (top != null && ch == top.close) {
                stack.removeLast()
                if (top.ordinal >= 0) {
                    lastClosedSpan = ClosedSpan(top.ordinal, top.openAt, at + 1)
                }
                return false
            }
            for ((open, close) in QuotePairs) {
                if (ch == open) {
                    val cast = stack.size < maxDepth
                    stack.addLast(Frame(close, if (cast) nextOrdinal else -1, at))
                    if (cast) lastCastOrdinal = nextOrdinal++
                    return cast
                }
            }
            return false
        }
    }

    /** 名字合法性（弹层输入与标记语法双重约束）。 */
    fun isValidName(name: String): Boolean = syntax.isValidName(name)

    /** 已分配标记全文；名字非法返回 null。 */
    fun markerText(characterName: String, voicePoolLabel: String): String? =
        syntax.markerText(characterName, voicePoolLabel)

    /** 胶囊显示标签：`名字（池）`。 */
    fun labelOf(characterName: String, voicePoolLabel: String): String =
        syntax.labelOf(characterName, voicePoolLabel)

    /**
     * 朗读侧：把标记换成**等长**空格。
     *
     * 朗读偏移（readAloudNumber、播放队列的 chapterStart/End）都按含标记的正文算，
     * 删字符会让队列校验和进度定位全线错位，所以偏移计算用的那一份只能原地抹掉。
     * 真正送进引擎与显示的那一份用 [strip]，不留这串空格。
     */
    fun blank(text: String): String =
        syntax.markerRegex.replace(text) { " ".repeat(it.value.length) }

    /** 整段删掉标记：只在偏移已经用完、马上要发声或上屏的最后一步用。 */
    fun strip(text: String): String = stripKeepingOffsets(text).text

    /**
     * 去掉标记的那一句 + **引擎下标 → [text] 之前的正文下标**的还原表。
     *
     * 标记是整段删掉的，引擎回来的 `onRangeStart` 下标因此比正文下标靠前若干字符；
     * 不换算回去，朗读进度、翻页判定和「从半句接着念」的偏移就全都错位。
     * [text] 是正文从 [base] 起的那一截，所以 [Spoken.storedOf] 直接给出正文绝对下标。
     * [base] 为 0 时就是纯去标记（显示、文件合成都用它，不碰偏移）。
     */
    fun stripKeepingOffsets(text: String, base: Int = 0): Spoken {
        val markers = syntax.markerRegex.findAll(text).map { it.range.first to it.range.last + 1 }
            .toList()
        if (markers.isEmpty()) return Spoken(text, base + text.length) { base + it }
        val sb = StringBuilder(text.length)
        var cursor = 0
        markers.forEach { (from, until) ->
            sb.append(text, cursor, from)
            cursor = until
        }
        sb.append(text, cursor, text.length)
        val spokenLength = sb.length
        val storedEnd = base + text.length
        return Spoken(sb.toString(), storedEnd) { index ->
            val spoken = index.coerceIn(0, spokenLength)
            var removed = 0
            for ((from, until) in markers) {
                // 引擎下标落在这一段标记之前：它前面删掉的字符数就是 removed
                if (spoken < from - removed) break
                removed += until - from
            }
            (base + spoken + removed).coerceIn(base, storedEnd)
        }
    }

    /** 送进引擎的那一句：文本 + 把引擎回传的下标换回正文绝对下标的 [storedOf]。 */
    class Spoken(val text: String, private val storedEnd: Int, private val map: (Int) -> Int) {

        /** 原样送给引擎（标记不删）：两份下标天然一致。 */
        companion object {
            fun passThrough(text: String, base: Int = 0) =
                Spoken(text, base + text.length) { (base + it).coerceIn(base, base + text.length) }
        }

        /** 引擎回传的下标 → 正文（含标记）的绝对下标。 */
        fun storedOf(spokenIndex: Int): Int = map(spokenIndex).coerceIn(0, storedEnd)
    }


    /** 段内标记出现位置（绝对偏移由调用方补）。 */
    fun markerRanges(text: String): List<Pair<Int, Int>> =
        syntax.markerRegex.findAll(text).map { it.range.first to (it.range.last + 1) }.toList()

    /** 从标记原文或 `名字（池）` 标签解析。 */
    data class Parsed(val name: String, val voicePoolLabel: String)

    fun parse(text: String): Parsed? =
        syntax.parse(text)?.let { (name, pool) -> Parsed(name, pool) }

    /** 标记在段内的出现。 */
    data class Match(
        val start: Int,
        val end: Int,
        val raw: String,
        val name: String,
        val voicePoolLabel: String,
    )

    fun findMarkers(paragraph: String): List<Match> =
        syntax.markerRegex.findAll(paragraph).map { match ->
            Match(
                start = match.range.first,
                end = match.range.last + 1,
                raw = match.value,
                name = match.groupValues[1].trim(),
                voicePoolLabel = (match.groupValues[2].ifBlank { match.groupValues[3] }).trim(),
            )
        }.toList()

    /**
     * 把已分配标记注入一个段落的可见文本：锚点开引号后插 [assignmentLabels]
     * 命中的标记（未命中不插，由测量侧就地合成占位胶囊）。[tracker] 章级共享、
     * 每段都要喂——序号推进必须连续。
     */
    fun injectParagraph(
        paragraph: String,
        tracker: CastQuoteTracker,
        assignmentLabels: Map<Int, String>,
    ): String {
        if (paragraph.isEmpty() || assignmentLabels.isEmpty()) {
            // 即使没有分配也要推进 tracker，保持与测量侧序号一致
            if (assignmentLabels.isEmpty()) {
                for (ch in paragraph) tracker.feed(ch)
            }
            return paragraph
        }
        val sb = StringBuilder(paragraph.length + 16)
        for (ch in paragraph) {
            sb.append(ch)
            if (tracker.feed(ch)) {
                val label = assignmentLabels[tracker.lastCastOrdinal]
                if (label != null) sb.append(label)
            }
        }
        return sb.toString()
    }
}
