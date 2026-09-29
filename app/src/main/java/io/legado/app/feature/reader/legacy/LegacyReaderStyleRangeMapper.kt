package io.legado.app.feature.reader.legacy

import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderUnderline
import io.legado.app.feature.reader.core.model.withBitmapSize
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderStyleRange
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import io.legado.app.feature.reader.platform.ReaderTextBackgroundLoader
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.spToPx

object LegacyReaderStyleRangeMapper {
    fun map(
        source: ReaderChapterSource,
        rules: List<HighlightRule>,
        processes: List<BookContentProcess>,
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
        return result
    }

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

    private fun backgroundImageSize(path: String): Pair<Int, Int> =
        ReaderTextBackgroundLoader.dimensions(path)
}
