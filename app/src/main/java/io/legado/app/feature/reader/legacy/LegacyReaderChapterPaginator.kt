package io.legado.app.feature.reader.legacy

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.HighlightRule
import io.legado.app.feature.reader.core.layout.ReaderChapterBlockMeasurer
import io.legado.app.feature.reader.core.layout.ReaderChapterMeasureResult
import io.legado.app.feature.reader.core.layout.ReaderChapterMeasureStyle
import io.legado.app.feature.reader.core.layout.ReaderImageDimensions
import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderPaginationConfig
import io.legado.app.feature.reader.core.layout.ReaderPaginationSession
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.feature.reader.core.layout.ReaderTextShaperFactory
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSourceResolver
import io.legado.app.feature.reader.platform.AndroidReaderTextShaper
import io.legado.app.feature.reader.platform.ReaderAndroidPaginationStyle
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.help.book.BookContent
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ImageProvider
import io.legado.app.utils.dpToPx
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx

sealed interface LegacyReaderChapterPaginationResult {
    data class Success(val pages: List<ReaderPage>) : LegacyReaderChapterPaginationResult
    data class Unsupported(val reason: String) : LegacyReaderChapterPaginationResult
}

data class LegacyReaderPaginationBatch(
    val pages: List<ReaderPage>,
    val unsupportedChapters: Map<Int, String>,
    val hasCurrentChapter: Boolean,
)

/** Every chapter-side input that can change measured page geometry or image resolution. */
data class LegacyReaderChapterLayoutIdentity(
    val chapterIndex: Int,
    val chapterUrl: String,
    val chapterBaseUrl: String,
    val displayTitle: String,
    val isVolume: Boolean,
    val contentHash: Int,
    val contentProcessesHash: Int,
    val sourceHash: Int,
    val bookUrl: String,
    val bookOrigin: String,
    val bookSourceHash: Int,
)

/**
 * Keeps a malformed adjacent chapter from cancelling pagination of the current chapter.
 * Cancellation is control flow and must still stop the whole generation.
 */
suspend fun paginateLegacyReaderChapterSafely(
    paginate: suspend () -> LegacyReaderChapterPaginationResult,
): LegacyReaderChapterPaginationResult = try {
    paginate()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    LegacyReaderChapterPaginationResult.Unsupported(
        "exception:${error::class.simpleName ?: "unknown"}",
    )
}

fun collectLegacyReaderPaginationBatch(
    currentChapterIndex: Int,
    results: List<Pair<Int, LegacyReaderChapterPaginationResult>>,
): LegacyReaderPaginationBatch {
    val pages = mutableListOf<ReaderPage>()
    val unsupported = linkedMapOf<Int, String>()
    var hasCurrentChapter = false
    results.forEach { (chapterIndex, result) ->
        when (result) {
            is LegacyReaderChapterPaginationResult.Success -> {
                pages += result.pages
                if (chapterIndex == currentChapterIndex) hasCurrentChapter = true
            }
            is LegacyReaderChapterPaginationResult.Unsupported -> unsupported[chapterIndex] = result.reason
        }
    }
    return LegacyReaderPaginationBatch(
        pages = pages,
        unsupportedChapters = unsupported,
        hasCurrentChapter = hasCurrentChapter,
    )
}

fun LegacyReaderPaginationBatch.failureReasonFor(chapterIndex: Int): String? =
    unsupportedChapters[chapterIndex]

/**
 * Temporary Android configuration adapter. Output pages belong entirely to the new reader core;
 * this bridge can be deleted once reader settings and chapter source have dedicated gateways.
 */
object LegacyReaderChapterPaginator {
    suspend fun paginate(
        book: Book,
        bookSource: BookSource?,
        chapter: BookChapter,
        displayTitle: String,
        content: BookContent,
        source: ReaderChapterSource,
        revision: Long,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        contentPaddingLeftPx: Int = 0,
        contentPaddingTopPx: Int = 0,
        contentPaddingRightPx: Int = 0,
        contentPaddingBottomPx: Int = 0,
        paginationStyle: ReaderAndroidPaginationStyle,
        highlightRules: List<HighlightRule>,
        /** 每页成型即回调（对照旧 View `TextChapterLayout` 的 `channel.trySend`）；为空表示只要整章批次。 */
        onPage: ((ReaderPage) -> Unit)? = null,
    ): LegacyReaderChapterPaginationResult {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return LegacyReaderChapterPaginationResult.Unsupported("viewport")
        }
        val imageLayoutMode = when (book.getImageStyle()?.uppercase()) {
            Book.imgStyleText -> ReaderImageLayoutMode.INLINE
            Book.imgStyleFull -> ReaderImageLayoutMode.FULL_WIDTH
            Book.imgStyleSingle -> ReaderImageLayoutMode.SINGLE_PAGE
            else -> ReaderImageLayoutMode.AUTO
        }
        val singleImage = imageLayoutMode == ReaderImageLayoutMode.SINGLE_PAGE
        val layoutSource = source.withTitleVisibility(
            ReadBookConfig.titleMode != 2 || chapter.isVolume || content.textList.isEmpty(),
            paginationStyle.titleSegmentation,
        )
        val styleRanges = LegacyReaderStyleRangeMapper.map(
            source = layoutSource,
            rules = highlightRules,
            processes = content.effectiveContentProcesses,
        )
        val bodyPaint = paginationStyle.bodyPaint
        val titlePaint = paginationStyle.titlePaint
        val bodyStyle = paginationStyle.bodyStyle
        val titleStyle = paginationStyle.titleStyle
        val measurer = ReaderChapterBlockMeasurer(
            bodyShaper = AndroidReaderTextShaper(bodyPaint),
            titleShaper = AndroidReaderTextShaper(titlePaint),
            imageDimensionsResolver = { imageSource ->
                try {
                    ImageProvider.getImageSize(book, imageSource, bookSource).let {
                        ReaderImageDimensions(it.width.toFloat(), it.height.toFloat())
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            },
            textShaperFactory = ReaderTextShaperFactory { textStyle ->
                AndroidReaderTextShaper(ReaderAndroidPaintFactory.createTextPaint(textStyle))
            },
            htmlSourceResolver = AndroidReaderHtmlSourceResolver(
                baseTextSizePx = bodyPaint.textSize,
                density = appCtx.resources.displayMetrics.density,
            ),
            imageOptionsResolver = LegacyReaderImageOptionsResolver,
        )
        // 分页会话先于测量建立：块一到就推进排版游标，页成型即经 [onPage] 流出
        // （旧 View `TextChapterLayout` 也是边排版边 `channel.trySend`）。
        val paginationConfig = ReaderPaginationConfig(
            chapterIndex = chapter.index,
            chapterTitle = displayTitle,
            columnCount = paginationStyle.columnCount(viewportWidthPx, viewportHeightPx),
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            paddingLeftPx = (paginationStyle.paddingLeftPx + contentPaddingLeftPx).toFloat(),
            paddingTopPx = (paginationStyle.paddingTopPx + contentPaddingTopPx).toFloat() +
                    LegacyReaderPageDecorationFactory.headerExtentPx(),
            paddingRightPx = (paginationStyle.paddingRightPx + contentPaddingRightPx).toFloat(),
            paddingBottomPx = (paginationStyle.paddingBottomPx + contentPaddingBottomPx).toFloat() +
                    LegacyReaderPageDecorationFactory.footerExtentPx(),
            lineHeightPx = paginationStyle.bodyTextHeightPx,
            baselineOffsetPx = paginationStyle.bodyBaselineOffsetPx,
            lineSpacingMultiplier = paginationStyle.lineSpacingExtra,
            continuousScroll = paginationStyle.isScroll,
            singleImageStyle = singleImage,
            chapterEndPaddingPx = CHAPTER_END_PADDING_DP.dpToPx(),
            inlineImagesPreserveScrollLine = imageLayoutMode == ReaderImageLayoutMode.INLINE,
            textBottomJustify = paginationStyle.textBottomJustify,
            // 旧 `setTypeHtml` 的 `setLineSpacing(paragraphSpacing.toFloat(), ...)`：用原始设置值。
            htmlLineSpacingAddPx = paginationStyle.paragraphSpacing.toFloat(),
            pageUnderline = paginationStyle.pageUnderline,
            emphasisUnderlineStyle = paginationStyle.emphasisUnderlineStyle,
            paragraphSpacingPx = paginationStyle.bodyTextHeightPx * paginationStyle.paragraphSpacing / 10f,
            titleTopSpacingPx = paginationStyle.titleTopSpacingPx,
            titleBottomSpacingPx = paginationStyle.titleBottomSpacingPx,
            // 旧 `TextChapterLayout.kt:977-1010` 的居中只发生在"空正文"（`emptyContent`）与
            // `imgStyleSingle` 两种情形；**卷章有正文时旧版不居中**（走 `durY + titleTopSpacing`），
            // 所以这里不并入 `chapter.isVolume`。
            titlePageCenterVertical = content.textList.isEmpty() || singleImage,
            titleParagraphSpacingPx = paginationStyle.titleTextHeightPx * paginationStyle.paragraphSpacing / 10f,
            titleSegmentSpacingPx = paginationStyle.titleTextHeightPx * paginationStyle.titleLineSpacingSub,
            letterSpacingPx = bodyPaint.letterSpacing * bodyPaint.textSize,
            revision = revision,
        )
        val paginationSession = ReaderPaginationSession(paginationConfig)
        paginationSession.onPage = onPage
        val measured = ReaderPerfTrace.section("pagination.measure") {
            measurer.measure(
            layoutSource,
            ReaderChapterMeasureStyle(
                bodyStyle = bodyStyle,
                titleStyle = titleStyle,
                bodyIndentCharacters = ReadBookConfig.paragraphIndent.length,
                bodyIndentText = ReadBookConfig.paragraphIndent,
                bodyAlignment = if (ReadBookConfig.textFullJustify) ReaderTextAlignment.JUSTIFY else ReaderTextAlignment.START,
                // 旧 TextChapterLayout 让 `imgStyleSingle` 的章标题同样居中（水平，见
                // `addCharsToLineNatural` 的 startX；垂直见 setTypeText 的 durY 分支）。
                titleAlignment = if (ReadBookConfig.isMiddleTitle || chapter.isVolume ||
                    content.textList.isEmpty() || singleImage
                ) ReaderTextAlignment.CENTER else ReaderTextAlignment.START,
                imagePageBreakBefore = singleImage,
                imagePageBreakAfter = singleImage,
                titlePageBreakAfter = singleImage && content.textList.isNotEmpty(),
                imageLayoutMode = imageLayoutMode,
                excludeActionImages = paginationStyle.excludeActionImages,
                imageAvailableWidthPx = (
                    viewportWidthPx / paginationStyle.columnCount(viewportWidthPx, viewportHeightPx) -
                        paginationStyle.paddingLeftPx - paginationStyle.paddingRightPx -
                        contentPaddingLeftPx - contentPaddingRightPx
                    ).coerceAtLeast(0).toFloat(),
                bodyLineHeightPx = paginationStyle.bodyTextHeightPx,
                bodyBaselineOffsetPx = paginationStyle.bodyBaselineOffsetPx,
                titleLineHeightPx = paginationStyle.titleTextHeightPx,
                titleBaselineOffsetPx = paginationStyle.titleBaselineOffsetPx,
                bodyLineSpacingMultiplier = paginationStyle.lineSpacingExtra,
                titleLineSpacingMultiplier = paginationStyle.titleLineSpacingExtra,
                letterSpacingEm = bodyPaint.letterSpacing,
                styleRanges = styleRanges,
            ),
                onBlock = { block -> paginationSession.accept(block) },
            )
        }
        if (measured is ReaderChapterMeasureResult.Unsupported) {
            // 测量失败说明这一章的页不可信：已流出的部分页由调用方按 Unsupported 丢弃。
            return LegacyReaderChapterPaginationResult.Unsupported(measured.reason)
        }
        // 测量与分页现在交错进行，"pagination.pages" 只剩收尾（章末页）的开销，两者之和不变。
        val pages = ReaderPerfTrace.section("pagination.pages") { paginationSession.finish() }
        return LegacyReaderChapterPaginationResult.Success(pages)
    }
}

/** 旧 `TextChapterLayout.setTypeText` 收尾时给章末页加的留白（20dp）。 */
private const val CHAPTER_END_PADDING_DP = 20f
