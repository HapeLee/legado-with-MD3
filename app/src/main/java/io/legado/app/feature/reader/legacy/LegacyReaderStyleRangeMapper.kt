package io.legado.app.feature.reader.legacy

import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderUnderline
import io.legado.app.feature.reader.core.model.withBitmapSize
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderCharacterStyleResolver
import io.legado.app.feature.reader.core.style.ReaderStyleRange
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import io.legado.app.feature.reader.platform.ReaderTextBackgroundLoader
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.spToPx

object LegacyReaderStyleRangeMapper {

    /**
     * 角色气泡的优先级：高于任何一条高亮规则（规则用下标 0..n，用户标记用 10_000+）。
     *
     * 同一句台词上规则与角色气泡撞车时按用户口径「优先用角色的」——但只换气泡那两栏，
     * 字色/下划线/字号仍沿用那句里赢着的规则，见 [withBubbleOf]。
     */
    const val CAST_BUBBLE_PRIORITY = 20_000

    fun map(
        source: ReaderChapterSource,
        rules: List<HighlightRule>,
        processes: List<BookContentProcess>,
        /** 角色名 → 该角色自己设的气泡样式；空表 = 本书没人设气泡。 */
        castBubbles: Map<String, ReaderCharacterStyle> = emptyMap(),
    ): List<ReaderStyleRange> {
        val result = mutableListOf<ReaderStyleRange>()
        val bodyText = semanticBodyText(source)
        val titleText = source.semanticTitle
        rules.filter(HighlightRule::enabled).forEachIndexed { index, rule ->
            val regex = runCatching { Regex(rule.pattern) }.getOrNull() ?: return@forEachIndexed
            val targets = when (rule.targetScope) {
                HighlightRule.TARGET_TITLE -> listOf(titleText to ReaderStyleTarget.TITLE)
                HighlightRule.TARGET_BODY -> listOf(bodyText to ReaderStyleTarget.BODY)
                else -> listOf(titleText to ReaderStyleTarget.TITLE, bodyText to ReaderStyleTarget.BODY)
            }
            targets.forEach { (text, target) ->
                regex.findAll(text).forEach { match ->
                    val start = match.range.first
                    val endExclusive = match.range.last + 1
                    if (start < endExclusive) {
                        result += rule.matchRanges(start, endExclusive, target, index)
                    }
                }
            }
        }
        processes.asSequence()
            .filter { it.enabled && it.status == BookContentProcess.STATUS_ACTIVE && it.isUserMarking() }
            .forEachIndexed { index, process ->
                val anchor = GSON.fromJsonObject<TextProcessAnchor>(process.anchorJson).getOrNull()
                    ?: return@forEachIndexed
                val markingStyle = GSON.fromJsonObject<TextProcessStyle>(process.styleJson).getOrNull()
                    ?: return@forEachIndexed
                val range = BookContentProcessEngine.resolveRange(bodyText, anchor) ?: return@forEachIndexed
                result += ReaderStyleRange(
                    start = range.first,
                    endExclusive = range.last + 1,
                    target = ReaderStyleTarget.BODY,
                    style = markingStyle.toReaderStyle(process.id.removePrefix("mark:")),
                    priority = 10_000 + index,
                )
            }
        if (castBubbles.isNotEmpty()) {
            result += castBubbleRanges(source.blocks, result, castBubbles)
        }
        return result
    }

    /**
     * 每个「分配了角色的引号段」盖一层该角色自己那套样式。
     *
     * 哪一句归谁已经写在注入的标记文本里（`<<名字（池）>>` 紧跟开引号），所以这里不再正则、
     * 也不重新发明引号栈：喂同一个 [CastMarkers.CastQuoteTracker]，拿它报出的闭合区间。
     * 区间包住整句台词连同引号——压在上面的角色胶囊因此落在同一个气泡里。
     *
     * 角色那一套与高亮规则同构（编辑弹层就是同一个，只是没有「规则信息」那一段，命中靠分配表），
     * 所以命中字距/行距也走正文那一份首字/段内/末字口径。覆盖写法：把区间按已有样式的边界切开，
     * 每一小段先问「这里原本谁赢」，再把角色设过的那几栏盖上去 —— 角色没设的仍归那句里赢着的
     * 规则，一句里原本没有任何规则时整段就只上角色的样式。
     */
    private fun castBubbleRanges(
        blocks: List<ReaderChapterSourceBlock>,
        existing: List<ReaderStyleRange>,
        bubbles: Map<String, ReaderCharacterStyle>,
    ): List<ReaderStyleRange> {
        val tracker = CastMarkers.CastQuoteTracker()
        val out = mutableListOf<ReaderStyleRange>()
        fun scan(text: String, base: Int) {
            // 跟踪器章级共享，它报的下标是整章的；减掉本段起点才落得回 text 与本段的语义坐标
            val origin = tracker.fedCharacters
            for (index in text.indices) {
                tracker.feed(text[index])
                val closed = tracker.lastClosedSpan ?: continue
                val start = closed.start - origin
                val endExclusive = closed.endExclusive - origin
                val quoted = text.substring(start, endExclusive)
                val owner = CastMarkers.findMarkers(quoted).firstOrNull() ?: continue
                val cast = bubbles[owner.name] ?: continue
                val from = base + start
                val to = base + endExclusive
                val cuts = buildList {
                    add(from)
                    add(to)
                    // 整句的首字与末字也要能单独切开：命中字距只让在那两个字上
                    if (to - from > 1) {
                        add(from + 1)
                        add(to - 1)
                    }
                    existing.forEach { range ->
                        if (range.start in from until to) add(range.start)
                        if (range.endExclusive in from until to) add(range.endExclusive)
                    }
                }.distinct().sorted()
                for (edge in 0 until cuts.size - 1) {
                    val pieceStart = cuts[edge]
                    val pieceEnd = cuts[edge + 1]
                    if (pieceStart >= pieceEnd) continue
                    out += ReaderStyleRange(
                        start = pieceStart,
                        endExclusive = pieceEnd,
                        target = ReaderStyleTarget.BODY,
                        style = ReaderCharacterStyleResolver.resolve(existing, pieceStart, false)
                            .overriddenBy(cast, isHead = pieceStart == from, isTail = pieceEnd == to),
                        priority = CAST_BUBBLE_PRIORITY,
                    )
                }
            }
        }
        blocks.forEach { block ->
            when (block) {
                is ReaderChapterSourceBlock.Text -> if (!block.isTitle) {
                    scan(block.value, block.chapterPosition)
                }
                is ReaderChapterSourceBlock.Paragraph -> block.items.forEach { item ->
                    if (item is ReaderChapterInlineSource.Text) {
                        scan(item.value, item.chapterPosition)
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    /**
     * **角色设过的一律优先**：这一栏角色给了值就换成角色的，没给（null，或命中排版那四个 0）
     * 才用这里原本赢着的那一份。
     *
     * 命中排版的口径与 [matchRanges] 一致：只有整句的首字带 before、只有末字带 after，
     * 段内一个字都不加。角色一旦设了自己的命中字距，整句的间距就完全归角色 ——
     * 这里若与规则取大，规则间距更大时角色的命中排版就不生效，与「气泡任何设置都优先」相反。
     * 行距同理（行级属性，角色给了就用角色的，不与规则取大）。
     */
    private fun ReaderCharacterStyle?.overriddenBy(
        cast: ReaderCharacterStyle,
        isHead: Boolean,
        isTail: Boolean,
    ): ReaderCharacterStyle {
        val winner = this
        val base = winner ?: cast
        // 角色一条间距都没设时，整句原样沿用规则那一份（含它自己的首末字让位）
        val castOwnsSpacing = cast.matchSpacingBeforePx > 0f || cast.matchSpacingAfterPx > 0f
        return base.copy(
            colorArgb = cast.colorArgb ?: base.colorArgb,
            backgroundArgb = cast.backgroundArgb ?: base.backgroundArgb,
            underline = cast.underline ?: base.underline,
            fontPath = cast.fontPath ?: base.fontPath,
            fontWeight = cast.fontWeight ?: base.fontWeight,
            italic = cast.italic ?: base.italic,
            fontSizeOffsetPx = cast.fontSizeOffsetPx.takeIf { it != 0f } ?: base.fontSizeOffsetPx,
            backgroundImage = cast.backgroundImage ?: base.backgroundImage,
            matchSpacingBeforePx = if (castOwnsSpacing) {
                if (isHead) cast.matchSpacingBeforePx else 0f
            } else {
                winner?.matchSpacingBeforePx ?: 0f
            },
            matchSpacingAfterPx = if (castOwnsSpacing) {
                if (isTail) cast.matchSpacingAfterPx else 0f
            } else {
                winner?.matchSpacingAfterPx ?: 0f
            },
            linePadTopPx = cast.linePadTopPx.takeIf { it > 0f } ?: base.linePadTopPx,
            linePadBottomPx = cast.linePadBottomPx.takeIf { it > 0f } ?: base.linePadBottomPx,
        )
    }

    /**
     * 一条规则换算成 core 侧的字符样式（dp→px、九宫格切线、位图尺寸都在这里面）。
     *
     * 角色气泡存的就是「与高亮规则同一套参数」的 [HighlightRule] JSON，平台层装配
     * `ReaderCastOptions.bubbles` 时走这里，不再写第二份换算。命中字距不在
     * [toReaderStyle] 里（它按字拆段时才落到首字/末字上），这里补带过去，
     * 好让 [overriddenBy] 与正文的 [matchRanges] 用同一份口径。
     */
    fun styleOf(rule: HighlightRule): ReaderCharacterStyle = rule.toReaderStyle().copy(
        matchSpacingBeforePx = rule.letterSpacingBefore.dpToPx().takeIf { it > 0f } ?: 0f,
        matchSpacingAfterPx = rule.letterSpacingAfter.dpToPx().takeIf { it > 0f } ?: 0f,
    )

    /**
     * 把一条规则的样式按**字面区间**挂上去，不走正则。
     *
     * 两个消费者：①规则编辑弹层的预览——每一项改动都必须看得见，而用户填的正则很可能命中不了
     * 示例句，那样整块预览是死的，所以样式直接钉在示例段上、不迁就正则；
     * ②角色气泡——哪一句归哪个角色由分配表给定，区间是已知的，不需要也不应该再正则一遍。
     * 样式换算与命中段拆分复用正文那一份 [matchRanges]，不另写一套。
     */
    fun rangesForLiteralRange(
        rule: HighlightRule,
        start: Int,
        endExclusive: Int,
        target: ReaderStyleTarget,
        priority: Int,
    ): List<ReaderStyleRange> =
        if (start < endExclusive) rule.matchRanges(start, endExclusive, target, priority)
        else emptyList()

    private fun semanticBodyText(source: ReaderChapterSource): String {
        val chars = CharArray(source.characterCount) { '\n' }
        source.blocks.forEach { block ->
            when (block) {
                is ReaderChapterSourceBlock.Text -> if (!block.isTitle) {
                    chars.writeText(block.chapterPosition, block.value)
                }
                is ReaderChapterSourceBlock.Image -> chars.setOrNull(block.chapterPosition, '\uFFFC')
                is ReaderChapterSourceBlock.Paragraph -> block.items.forEach { item ->
                    when (item) {
                        is ReaderChapterInlineSource.Text -> chars.writeText(item.chapterPosition, item.value)
                        is ReaderChapterInlineSource.Image -> chars.setOrNull(item.chapterPosition, '\uFFFC')
                        is ReaderChapterInlineSource.BlankLine -> Unit
                        // 配乐胶囊零字符，不写语义坐标
                        is ReaderChapterInlineSource.BgmScene -> Unit
                    }
                }
                is ReaderChapterSourceBlock.Html, is ReaderChapterSourceBlock.PageBreak -> Unit
            }
        }
        return chars.concatToString()
    }

    private fun CharArray.setOrNull(index: Int, value: Char) {
        if (index in indices) this[index] = value
    }

    private fun CharArray.writeText(start: Int, text: String) {
        if (start !in indices || text.isEmpty()) return
        val count = minOf(text.length, size - start)
        for (offset in 0 until count) this[start + offset] = text[offset]
    }

    /**
     * 一次命中拆成「首字 / 段内 / 末字」三段互不重叠的区间。
     *
     * 命中字距属于命中段与相邻字之间：只有首字那一段带 before、只有末字那一段带 after，
     * 段内一个字都不加——段内也加的话，调的就不是间距而是整段的字号了。行距是行级属性，
     * 三段都带上，由分页那边按整行取较大值（同一行里两条命中也只抬一次）。
     */
    private fun HighlightRule.matchRanges(
        start: Int,
        endExclusive: Int,
        target: ReaderStyleTarget,
        priority: Int,
    ): List<ReaderStyleRange> {
        val base = toReaderStyle()
        val before = letterSpacingBefore.dpToPx().takeIf { it > 0f } ?: 0f
        val after = letterSpacingAfter.dpToPx().takeIf { it > 0f } ?: 0f
        if (endExclusive - start == 1) {
            // 单字命中：它既是首字也是末字，两边都要让。
            return listOf(
                ReaderStyleRange(
                    start = start,
                    endExclusive = endExclusive,
                    target = target,
                    style = base.copy(
                        matchSpacingBeforePx = before,
                        matchSpacingAfterPx = after,
                    ),
                    priority = priority,
                )
            )
        }
        return buildList(3) {
            add(
                ReaderStyleRange(
                    start = start,
                    endExclusive = start + 1,
                    target = target,
                    style = base.copy(matchSpacingBeforePx = before),
                    priority = priority,
                )
            )
            add(
                ReaderStyleRange(
                    start = start + 1,
                    endExclusive = endExclusive - 1,
                    target = target,
                    style = base,
                    priority = priority,
                )
            )
            add(
                ReaderStyleRange(
                    start = endExclusive - 1,
                    endExclusive = endExclusive,
                    target = target,
                    style = base.copy(matchSpacingAfterPx = after),
                    priority = priority,
                )
            )
        }
    }

    private fun HighlightRule.toReaderStyle() = ReaderCharacterStyle(
        colorArgb = textColor,
        backgroundArgb = bgColor,
        underline = underlineMode.takeIf { it != 0 }?.let {
            ReaderUnderline(
                mode = it,
                // 兜底跟随正文色：旧 `TextLine.drawStyledUnderlines` 的
                // `underlineColor ?: textColor ?: ChapterProvider.renderStyle.textColor`
                // （即 ReadBookConfig.textColor），不是写死的主题绿。
                colorArgb = underlineColor ?: textColor ?: ReadBookConfig.textColor,
                widthPx = underlineWidth.dpToPx(),
                offsetPx = underlineOffset.dpToPx(),
                svgPath = underlineSvgPath.orEmpty(),
                dashOnPx = 8f.dpToPx(),
                dashOffPx = 5f.dpToPx(),
                waveAmplitudePx = 3f.dpToPx(),
                waveLengthPx = 12f.dpToPx(),
                doubleLineGapPx = 3f.dpToPx(),
            )
        },
        fontPath = fontPath,
        // 400 is the persisted/default "regular" value from the View reader, where an empty
        // font override left the body Paint untouched.  Passing it as an explicit override in
        // the new renderer reset bold/light body text to regular.  Keep it unset so the body
        // style remains the source of truth; non-default weights still override it.
        fontWeight = fontWeight.takeIf { it != 400 },
        italic = isItalic,
        fontSizeOffsetPx = fontSizeOffset.toFloat().spToPx(),
        linePadTopPx = lineSpacingTop.dpToPx().takeIf { it > 0f } ?: 0f,
        linePadBottomPx = lineSpacingBottom.dpToPx().takeIf { it > 0f } ?: 0f,
        backgroundImage = bgImage?.takeIf(String::isNotBlank)?.let {
            val automatic = if (manualNineSlice) null else {
                ReaderTextBackgroundLoader.nineSliceFractions(it)
            }
            ReaderTextBackgroundImage(
                source = it,
                fit = bgImageFit,
                scale = bgImageScale,
                ninePatchLeft = automatic?.left ?: npLeft,
                ninePatchRight = automatic?.right ?: npRight,
                ninePatchTop = automatic?.top ?: npTop,
                ninePatchBottom = automatic?.bottom ?: npBottom,
                lengthOffsetLeftPx = bgLengthOffsetLeft.dpToPx(),
                lengthOffsetRightPx = bgLengthOffsetRight.dpToPx(),
            ).let { image ->
                val (width, height) = backgroundImageSize(it)
                image.withBitmapSize(width, height)
            }
        },
    )

    private fun TextProcessStyle.toReaderStyle(markingId: String) = ReaderCharacterStyle(
        colorArgb = textColor,
        backgroundArgb = bgColor,
        underline = underlineMode.takeIf { it != 0 }?.let {
            ReaderUnderline(
                mode = it,
                // 兜底跟随正文色：旧 `TextLine.drawStyledUnderlines` 的
                // `underlineColor ?: textColor ?: ChapterProvider.renderStyle.textColor`
                // （即 ReadBookConfig.textColor），不是写死的主题绿。
                colorArgb = underlineColor ?: textColor ?: ReadBookConfig.textColor,
                widthPx = underlineWidth.dpToPx(),
                offsetPx = underlineOffset.dpToPx(),
                svgPath = underlineSvgPath.orEmpty(),
                dashOnPx = 8f.dpToPx(),
                dashOffPx = 5f.dpToPx(),
                waveAmplitudePx = 3f.dpToPx(),
                waveLengthPx = 12f.dpToPx(),
                doubleLineGapPx = 3f.dpToPx(),
            )
        },
        markingId = markingId,
    )

    private fun BookContentProcess.isUserMarking(): Boolean =
        kind == BookContentProcess.KIND_USER_UNDERLINE || kind == BookContentProcess.KIND_USER_HIGHLIGHT

    /**
     * 量这张气泡图有多大，并顺手把它解进缓存。
     *
     * 只报尺寸不解图，绘制侧就要等 `produceState` 在 IO 上解完才拿到位图：翻到那一页的
     * 头几帧气泡是空的，下一帧才补上，看上去就是「闪一下」。排版本来就在 IO 上跑，
     * 在这里解一次，首帧就已经是热缓存，画出来的和量出来的还是同一张图。
     */
    private fun backgroundImageSize(path: String): Pair<Int, Int> {
        val bitmap = ReaderTextBackgroundLoader.load(path)
        return if (bitmap == null) {
            ReaderTextBackgroundLoader.dimensions(path)
        } else {
            bitmap.width to bitmap.height
        }
    }
}
