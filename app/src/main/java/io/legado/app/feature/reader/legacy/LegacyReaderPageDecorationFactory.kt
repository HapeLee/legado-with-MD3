package io.legado.app.feature.reader.legacy

import android.graphics.Paint
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.constant.AppConst.timeFormat
import io.legado.app.constant.ReadTipType
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.feature.reader.core.model.ReaderBookmarkBadge
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageDecoration
import io.legado.app.feature.reader.core.model.ReaderPageTip
import io.legado.app.feature.reader.core.model.ReaderTipAlignment
import io.legado.app.feature.reader.core.model.ReaderTipRow
import io.legado.app.feature.reader.core.model.ReaderTipRowLayout
import io.legado.app.feature.reader.core.model.ReaderTipValueContext
import io.legado.app.feature.reader.core.model.ReaderTipValueFormatter
import io.legado.app.feature.reader.core.model.ReaderTipValueType
import io.legado.app.feature.reader.core.model.ReaderTipVisual
import io.legado.app.feature.reader.core.model.resolveReaderTipColor
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.utils.dpToPx
import io.legado.app.utils.spToPx
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import java.io.File
import java.text.DecimalFormat
import java.util.Date

/** Android settings adapter for the Canvas reader's page header and footer. */
object LegacyReaderPageDecorationFactory : KoinComponent {
    private val readSettings: ReadSettingsGateway by inject()

    /**
     * 旧 `PageView` 的页眉页脚字体解析：`tipTypeface ?: ChapterProvider.typeface` —— 没设就回落
     * **正文字体**（含自定义字体与 serif/monospace 系统字体族），不是系统 sans-serif。
     */
    private fun tipFontPath(configured: String): String =
        configured.ifBlank { ReadBookConfig.textFont }

    private fun tipFontFamily(): String = when (readSettings.currentSettings.systemTypefaces) {
        1 -> "serif"
        2 -> "monospace"
        else -> "sans-serif"
    }

    fun headerExtentPx(): Float = if (headerVisible()) {
        tipRowExtentPx(
            fontSizePx = ReadBookConfig.headerFontSize.toFloat().spToPx(),
            fontPath = tipFontPath(ReadBookConfig.headerFont),
            fontFamily = tipFontFamily(),
            paddingTopPx = ReadBookConfig.headerPaddingTop.dpToPx().toFloat(),
            paddingBottomPx = ReadBookConfig.headerPaddingBottom.dpToPx().toFloat(),
            dividerVisible = ReadBookConfig.showHeaderLine,
        )
    } else 0f

    fun footerExtentPx(): Float = if (footerVisible()) {
        tipRowExtentPx(
            fontSizePx = (if (ReadBookConfig.applyHeaderStyle) {
                ReadBookConfig.headerFontSize
            } else {
                ReadBookConfig.footerFontSize
            }).toFloat().spToPx(),
            fontPath = tipFontPath(
                if (ReadBookConfig.applyHeaderStyle) ReadBookConfig.headerFont else ReadBookConfig.footerFont
            ),
            fontFamily = tipFontFamily(),
            paddingTopPx = ReadBookConfig.footerPaddingTop.dpToPx().toFloat(),
            paddingBottomPx = ReadBookConfig.footerPaddingBottom.dpToPx().toFloat(),
            dividerVisible = ReadBookConfig.showFooterLine,
        )
    } else 0f

    private fun tipRowExtentPx(
        fontSizePx: Float,
        fontPath: String,
        fontFamily: String,
        paddingTopPx: Float,
        paddingBottomPx: Float,
        dividerVisible: Boolean,
    ): Float {
        val metrics = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = fontSizePx
            typeface = ReaderAndroidPaintFactory.loadTypeface(fontPath, 400, false, fontFamily)
        }.fontMetrics
        // 旧版页眉是 `includeFontPadding="false"` 的 TextView：实测高 = descent − ascent，
        // 基线 = −ascent。用 top/bottom 会把 leading（含异常 leading）算进预留高度。
        return ReaderTipRowLayout.extent(
            paddingTopPx = paddingTopPx,
            fontTopPx = metrics.ascent,
            fontBottomPx = metrics.descent,
            paddingBottomPx = paddingBottomPx,
            dividerExtentPx = if (dividerVisible) DIVIDER_THICKNESS_DP.dpToPx() else 0f,
        )
    }

    private const val DIVIDER_THICKNESS_DP = 0.5f

    fun create(
        page: ReaderPage,
        chapterPageCount: Int,
        time: String,
        batteryPercent: Int,
        hasBookmark: Boolean = false,
        contentPaddingLeftPx: Int = 0,
        contentPaddingTopPx: Int = 0,
        contentPaddingRightPx: Int = 0,
        contentPaddingBottomPx: Int = 0,
    ): ReaderPageDecoration {
        val settings = readSettings.currentSettings
        val wholeBook = ReadBook.getWholeBookPageState(page.id.chapterIndex, page.id.pageIndex)
        val context = ReaderTipValueContext(
            bookName = ReadBook.book?.name.orEmpty(),
            chapterTitle = page.chapterTitle,
            time = time.ifBlank { timeFormat.format(Date()) },
            batteryPercent = batteryPercent.coerceIn(0, 100),
            chapterIndex = page.id.chapterIndex,
            chapterCount = ReadBook.chapterSize.coerceAtLeast(1),
            pageIndex = page.id.pageIndex,
            pageCount = chapterPageCount.coerceAtLeast(1),
            // 章还没排出任何页（占位/消息页）时页数未知，旧 `PageView.setProgress` 给的是 `-`；
            // 排版中但已有页时旧版给的是**已排出的页数**，所以这里只看计数是否为零。
            pageCountText = if (chapterPageCount <= 0) {
                ReaderTipValueFormatter.UNKNOWN_PAGE_COUNT
            } else {
                chapterPageCount.toString()
            },
            readProgress = readProgress(page.id.chapterIndex, page.id.pageIndex, chapterPageCount),
            // 对照旧 `PageView.setProgress`/`resolveCustomTemplate`：整书页数文案带本地化前缀，
            // 精确/估算两种格式，无估算时回落到 `whole_book_page_unavailable`（`全文 - / -`）。
            wholeBookPageText = when {
                wholeBook == null -> appCtx.getString(R.string.whole_book_page_unavailable)
                !wholeBook.estimated && wholeBook.allPreviousChaptersExact -> appCtx.getString(
                    R.string.whole_book_page_format,
                    wholeBook.currentPage,
                    wholeBook.totalPages,
                )

                else -> appCtx.getString(
                    R.string.whole_book_page_estimated_format,
                    wholeBook.currentPage.toString(),
                    wholeBook.totalPages.toString(),
                )
            },
            wholeBookPageIndex = wholeBook?.currentPage,
            wholeBookPageCount = wholeBook?.totalPages,
        )
        val dividerColor = when (val configured = ReadBookConfig.tipDividerColor) {
            -1 -> ContextCompat.getColor(appCtx, R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> configured
        }
        return ReaderPageDecoration(
            bookmarkBadge = ReaderBookmarkBadge.create(
                hasBookmark = hasBookmark,
                isScroll = ReadBook.pageAnim() == 3,
                pageWidthPx = page.widthPx,
                contentTopPx = page.contentTopPx,
                contentRightPaddingPx = ReadBookConfig.paddingRight.dpToPx(),
                density = appCtx.resources.displayMetrics.density,
                sizeDp = settings.bookmarkBadgeSize,
                imageSource = settings.bookmarkBadgeImage,
                imageVersion = settings.bookmarkBadgeImage.takeIf { hasBookmark && it.isNotBlank() }
                    ?.let { File(it).let { file -> "${file.lastModified()}:${file.length()}" } }.orEmpty(),
            ),
            header = ReaderTipRow(
                visible = headerVisible(),
                tips = tips(
                    context,
                    ReadBookConfig.tipHeaderLeft to ReadBookConfig.customTipHeaderLeft,
                    ReadBookConfig.tipHeaderMiddle to ReadBookConfig.customTipHeaderMiddle,
                    ReadBookConfig.tipHeaderRight to ReadBookConfig.customTipHeaderRight,
                ),
                colorArgb = resolveReaderTipColor(
                    ReadBookConfig.resolvedTipHeaderColor,
                    ReadBookConfig.textColor,
                ),
                fontSizePx = ReadBookConfig.headerFontSize.toFloat().spToPx(),
                fontPath = tipFontPath(ReadBookConfig.headerFont),
                paddingLeftPx = ReadBookConfig.headerPaddingLeft.dpToPx() + contentPaddingLeftPx.toFloat(),
                paddingTopPx = ReadBookConfig.headerPaddingTop.dpToPx() + contentPaddingTopPx.toFloat(),
                paddingRightPx = ReadBookConfig.headerPaddingRight.dpToPx() + contentPaddingRightPx.toFloat(),
                paddingBottomPx = ReadBookConfig.headerPaddingBottom.dpToPx().toFloat(),
                dividerColorArgb = dividerColor.takeIf { ReadBookConfig.showHeaderLine },
                fontFamily = tipFontFamily(),
                insetLeftPx = contentPaddingLeftPx.toFloat(),
                insetRightPx = contentPaddingRightPx.toFloat(),
            ),
            footer = ReaderTipRow(
                visible = footerVisible(),
                tips = tips(
                    context,
                    ReadBookConfig.tipFooterLeft to ReadBookConfig.customTipFooterLeft,
                    ReadBookConfig.tipFooterMiddle to ReadBookConfig.customTipFooterMiddle,
                    ReadBookConfig.tipFooterRight to ReadBookConfig.customTipFooterRight,
                ),
                colorArgb = resolveReaderTipColor(
                    ReadBookConfig.resolvedTipFooterColor,
                    ReadBookConfig.textColor,
                ),
                fontSizePx = (if (ReadBookConfig.applyHeaderStyle) {
                    ReadBookConfig.headerFontSize
                } else {
                    ReadBookConfig.footerFontSize
                }).toFloat().spToPx(),
                fontPath = tipFontPath(
                    if (ReadBookConfig.applyHeaderStyle) ReadBookConfig.headerFont else ReadBookConfig.footerFont
                ),
                paddingLeftPx = ReadBookConfig.footerPaddingLeft.dpToPx() + contentPaddingLeftPx.toFloat(),
                paddingTopPx = ReadBookConfig.footerPaddingTop.dpToPx().toFloat(),
                paddingRightPx = ReadBookConfig.footerPaddingRight.dpToPx() + contentPaddingRightPx.toFloat(),
                paddingBottomPx = ReadBookConfig.footerPaddingBottom.dpToPx() + contentPaddingBottomPx.toFloat(),
                dividerColorArgb = dividerColor.takeIf { ReadBookConfig.showFooterLine },
                fontFamily = tipFontFamily(),
                insetLeftPx = contentPaddingLeftPx.toFloat(),
                insetRightPx = contentPaddingRightPx.toFloat(),
            ),
        )
    }

    /**
     * 同一类型（含自定义模板）配到多个槽位时，只有**首个**槽位取值，其余留空——对照旧
     * `PageView.getTipView` 只给第一个命中的 View 赋值。
     */
    private fun tips(
        context: ReaderTipValueContext,
        left: Pair<Int, String>,
        middle: Pair<Int, String>,
        right: Pair<Int, String>,
    ): List<ReaderPageTip> {
        val configured = listOf(left, middle, right)
        val alignments = listOf(
            ReaderTipAlignment.START,
            ReaderTipAlignment.CENTER,
            ReaderTipAlignment.END,
        )
        val seen = mutableSetOf<Pair<Int, String>>()
        return configured.mapIndexedNotNull { index, config ->
            if (!seen.add(config)) return@mapIndexedNotNull null
            tip(config, context, alignments[index])
        }.filter { it.text.isNotEmpty() || it.visual != ReaderTipVisual.TEXT }
    }

    private fun tip(
        config: Pair<Int, String>,
        context: ReaderTipValueContext,
        alignment: ReaderTipAlignment,
    ): ReaderPageTip {
        val visual = when (config.first) {
            ReadTipType.tipBattery -> ReaderTipVisual.BATTERY_OUTER
            ReadTipType.tipBatteryInside,
            ReadTipType.tipTimeBattery -> ReaderTipVisual.BATTERY_INNER
            ReadTipType.tipBatteryIcon -> ReaderTipVisual.BATTERY_ICON
            ReadTipType.tipBatteryClassic,
            ReadTipType.tipTimeBatteryClassic -> ReaderTipVisual.BATTERY_CLASSIC
            ReadTipType.tipChapterTitleArrow,
            ReadTipType.tipChapterTitleArrowClassic -> ReaderTipVisual.ARROW
            else -> ReaderTipVisual.TEXT
        }
        val text = when (config.first) {
            ReadTipType.tipBattery,
            ReadTipType.tipBatteryInside,
            ReadTipType.tipBatteryIcon,
            ReadTipType.tipBatteryClassic -> ""
            ReadTipType.tipTimeBattery,
            ReadTipType.tipTimeBatteryClassic -> context.time
            else -> value(config, context)
        }
        return ReaderPageTip(
            text = text,
            alignment = alignment,
            visual = visual,
            batteryPercent = context.batteryPercent,
        )
    }

    private fun value(config: Pair<Int, String>, context: ReaderTipValueContext): String =
        ReaderTipValueFormatter.format(type(config.first), context, config.second)

    private fun type(type: Int): ReaderTipValueType = when (type) {
        ReadTipType.tipNone -> ReaderTipValueType.NONE
        ReadTipType.tipChapterTitle -> ReaderTipValueType.CHAPTER_TITLE
        ReadTipType.tipTime -> ReaderTipValueType.TIME
        ReadTipType.tipBattery,
        ReadTipType.tipBatteryPercentage,
        ReadTipType.tipBatteryInside,
        ReadTipType.tipBatteryIcon,
        ReadTipType.tipBatteryClassic -> ReaderTipValueType.BATTERY
        ReadTipType.tipPage -> ReaderTipValueType.PAGE
        ReadTipType.tipTotalProgress -> ReaderTipValueType.TOTAL_PROGRESS
        ReadTipType.tipPageAndTotal -> ReaderTipValueType.PAGE_AND_TOTAL
        ReadTipType.tipBookName -> ReaderTipValueType.BOOK_NAME
        ReadTipType.tipTimeBattery,
        ReadTipType.tipTimeBatteryPercentage,
        ReadTipType.tipTimeBatteryClassic -> ReaderTipValueType.TIME_BATTERY
        ReadTipType.tipTotalProgress1 -> ReaderTipValueType.CHAPTER_INDEX_AND_TOTAL
        ReadTipType.tipChapterTitleArrow,
        ReadTipType.tipChapterTitleArrowClassic -> ReaderTipValueType.CHAPTER_TITLE_ARROW
        ReadTipType.tipCustom -> ReaderTipValueType.CUSTOM
        ReadTipType.tipWholeBookPage -> ReaderTipValueType.WHOLE_BOOK_PAGE
        ReadTipType.tipWholeBookPageAndProgress -> ReaderTipValueType.WHOLE_BOOK_PAGE_AND_PROGRESS
        else -> ReaderTipValueType.NONE
    }

    /**
     * 阅读进度，逐条对照旧 `TextPage.readProgress`（`TextPage.kt:249-263`）：
     * `chapterSize == 0` 或"首页且章还没排出页" → `0.0%`；章还没排出页 → 按章计；
     * 其余按"章 + 页"计，且未真正读完时不允许显示 100%。
     */
    private fun readProgress(chapterIndex: Int, pageIndex: Int, pageCount: Int): String {
        val chapterCount = ReadBook.chapterSize
        if (chapterCount == 0 || (pageCount == 0 && chapterIndex == 0)) return ZERO_PROGRESS
        val formatter = DecimalFormat("0.0%")
        if (pageCount == 0) {
            return formatter.format((chapterIndex + 1.0f) / chapterCount.toDouble())
        }
        var progress = formatter.format(
            chapterIndex * 1.0f / chapterCount +
                    1.0f / chapterCount * (pageIndex + 1) / pageCount.toDouble(),
        )
        if (progress == "100.0%" && (chapterIndex + 1 != chapterCount || pageIndex + 1 != pageCount)) {
            progress = "99.9%"
        }
        return progress
    }

    private const val ZERO_PROGRESS = "0.0%"

    private fun headerVisible(): Boolean = when (ReadBookConfig.headerMode) {
        1 -> true
        2 -> false
        else -> ReadBookConfig.hideStatusBar
    }

    private fun footerVisible(): Boolean = ReadBookConfig.footerMode != 1
}
