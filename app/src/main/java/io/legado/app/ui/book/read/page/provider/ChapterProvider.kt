package io.legado.app.ui.book.read.page.provider

import android.annotation.SuppressLint
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.net.toUri
import androidx.core.os.postDelayed
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadBookConfig.dottedBase
import io.legado.app.help.config.ReadBookConfig.dottedRatio
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isPad
import io.legado.app.utils.spToPx
import io.legado.app.utils.textHeight
import kotlinx.coroutines.CoroutineScope
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.io.File

/**
 * 解析内容生成章节和页面
 */
@Suppress("DEPRECATION", "ConstPropertyName")
object ChapterProvider {
    //用于图片字的替换
    const val srcReplaceChar = "袮" //▩▣ 丨⼁ //换成袮，这是不应该存在的汉字,替换为祢
    const val srcReplaceCharC = '袮' //可能有略微的提升
    const val srcReplaceCharD = '祢'

    //用于评论按钮的替换
    const val reviewChar = "꧁"

    const val indentChar = "　"

    val linePaint: Paint by lazy {
        Paint(contentPaint).apply {
            clearShadowLayer()
            isAntiAlias = true
            strokeWidth = ReadBookConfig.underlineHeight.toFloat()
            style = Paint.Style.STROKE
        }
    }

    var dashEffect = DashPathEffect(floatArrayOf(dottedBase, dottedRatio), 0f)

    /**
     * 排版度量的不可变快照。
     *
     * 这些值由主线程写入（[upStyle] / [upLayout] / [notifyViewSizeChange]），由 IO 线程上
     * 构造的 [TextChapterLayout] 与绘制路径读取。逐字段的可变静态量既无 happens-before、
     * 也无组内原子性——排版协程可能读到「新的 viewWidth 配旧的 paddingLeft」这种撕裂组合，
     * 表现为偶发排版错乱。收进一个不可变对象后，一次 volatile 写发布整组、一次 volatile
     * 读取得整组，两个问题一并消除。
     *
     * 注意：[titlePaint] / [contentPaint] 是可变对象，这里持有的是引用。[upThemeColors]
     * 仍会就地改它们的颜色——颜色不参与测量，故不影响排版结果；真要根治需让排版任务持有
     * 自己的 Paint 副本，属 Track D2 范围。
     */
    internal data class LayoutMetrics(
        val viewWidth: Int = 0,
        val viewHeight: Int = 0,
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        val paddingRight: Int = 0,
        val paddingBottom: Int = 0,
        val visibleWidth: Int = 0,
        val visibleHeight: Int = 0,
        val visibleRight: Int = 0,
        val visibleBottom: Int = 0,
        val lineSpacingExtra: Float = 0f,
        val titleLineSpacingExtra: Float = 0f,
        val titleLineSpacingSub: Float = 0f,
        val paragraphSpacing: Int = 0,
        val titleTopSpacing: Int = 0,
        val titleBottomSpacing: Int = 0,
        val indentCharWidth: Float = 0f,
        val titlePaintTextHeight: Float = 0f,
        val contentPaintTextHeight: Float = 0f,
        val titlePaintFontMetrics: FontMetrics = FontMetrics(),
        val contentPaintFontMetrics: FontMetrics = FontMetrics(),
        val typeface: Typeface? = Typeface.DEFAULT,
        val titlePaint: TextPaint = TextPaint(),
        val contentPaint: TextPaint = TextPaint(),
        val doublePage: Boolean = false,
        val visibleRect: RectF = RectF(),
    )

    @Volatile
    private var metrics = LayoutMetrics()

    /**
     * 原子地取整组排版度量。需要多个度量彼此自洽的调用方（典型是排版任务）用它，
     * 而不是逐个读下面的便捷访问器。
     */
    internal fun layoutMetrics(): LayoutMetrics = metrics

    /**
     * 绘制期用到的排版取值快照。
     *
     * 这些项过去由 `TextLine` / `TextColumn` / `TextHtmlColumn` / `TextPage` 在 `draw()`
     * 里逐个直读 [ReadBookConfig]。每次直读都要走
     * `config → durConfig → getConfig(styleSelect)`，而 `getConfig` 是 `@Synchronized`
     * ——等于**每行每列每帧**都去抢一次 `ReadBookConfig` 的监视器锁。收进快照后绘制路径
     * 只剩一次 volatile 读，且一帧内的颜色/下划线参数必定同属一份配置。
     *
     * 由 [upRenderStyle] 重建；配置是它唯一的输入，所以只要「配置可能变了」就重建一次即可
     * （[upStyle] / [upThemeColors] / `ReadBookController` 处理任何配置更新 effect 时）。
     */
    internal data class RenderStyle(
        val textColor: Int = 0,
        val textAccentColor: Int = 0,
        /** 已按日夜模式解析；0 表示「未单独设置标题色，跟随正文色」。 */
        val titleColor: Int = 0,
        val underline: Boolean = false,
        val dottedLine: Boolean = false,
        val underlineExtend: Boolean = false,
        val underlineColor: Int = 0,
        val underlineHeight: Int = 1,
        val underlinePadding: Int = 10,
        val textBottomJustify: Boolean = false,
    )

    @Volatile
    internal var renderStyle = RenderStyle()
        private set

    /** 重建 [renderStyle]。纯派生、幂等，重复调用只是多读十几个字段。 */
    fun upRenderStyle() {
        renderStyle = RenderStyle(
            textColor = ReadBookConfig.textColor,
            textAccentColor = ReadBookConfig.textAccentColor,
            titleColor = ReadBookConfig.resolvedTitleColor,
            underline = ReadBookConfig.underline,
            dottedLine = ReadBookConfig.dottedLine,
            underlineExtend = ReadBookConfig.underlineExtend,
            underlineColor = ReadBookConfig.durConfig.curUnderlineColor(),
            underlineHeight = ReadBookConfig.underlineHeight,
            underlinePadding = ReadBookConfig.durConfig.underlinePadding,
            textBottomJustify = ReadBookConfig.textBottomJustify,
        )
    }

    @JvmStatic
    val viewWidth get() = metrics.viewWidth

    @JvmStatic
    val viewHeight get() = metrics.viewHeight

    @JvmStatic
    val paddingLeft get() = metrics.paddingLeft

    @JvmStatic
    val paddingTop get() = metrics.paddingTop

    @JvmStatic
    val paddingRight get() = metrics.paddingRight

    @JvmStatic
    val paddingBottom get() = metrics.paddingBottom

    @JvmStatic
    val visibleWidth get() = metrics.visibleWidth

    @JvmStatic
    val visibleHeight get() = metrics.visibleHeight

    @JvmStatic
    val visibleRight get() = metrics.visibleRight

    @JvmStatic
    val visibleBottom get() = metrics.visibleBottom

    @JvmStatic
    val lineSpacingExtra get() = metrics.lineSpacingExtra

    val titleLineSpacingExtra get() = metrics.titleLineSpacingExtra

    val titleLineSpacingSub get() = metrics.titleLineSpacingSub

    @JvmStatic
    val paragraphSpacing get() = metrics.paragraphSpacing

    @JvmStatic
    val titleTopSpacing get() = metrics.titleTopSpacing

    @JvmStatic
    val titleBottomSpacing get() = metrics.titleBottomSpacing

    @JvmStatic
    val indentCharWidth get() = metrics.indentCharWidth

    @JvmStatic
    val titlePaintTextHeight get() = metrics.titlePaintTextHeight

    @JvmStatic
    val contentPaintTextHeight get() = metrics.contentPaintTextHeight

    @JvmStatic
    val titlePaintFontMetrics get() = metrics.titlePaintFontMetrics

    @JvmStatic
    val contentPaintFontMetrics get() = metrics.contentPaintFontMetrics

    @JvmStatic
    val typeface get() = metrics.typeface

    @JvmStatic
    val titlePaint get() = metrics.titlePaint

    @JvmStatic
    val contentPaint get() = metrics.contentPaint

    @JvmStatic
    var reviewPaint: TextPaint = TextPaint()

    @JvmStatic
    val doublePage get() = metrics.doublePage

    @JvmStatic
    val visibleRect get() = metrics.visibleRect

    private val handler by lazy {
        buildMainHandler()
    }

    private var upViewSizeRunnable: Runnable? = null

    init {
        upStyle()
    }

    /*
    /**
     * 获取拆分完的章节数据
     */
    suspend fun getTextChapter(
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
        chapterSize: Int,
    ): TextChapter {
        val contents = bookContent.textList
        val textPages = arrayListOf<TextPage>()
        val stringBuilder = StringBuilder()
        var absStartX = paddingLeft
        var durY = 0f
        textPages.add(TextPage())
        if (ReadBookConfig.titleMode != 2 || bookChapter.isVolume) {
            //标题非隐藏
            displayTitle.splitNotBlank("\n").forEach { text ->
                setTypeText(
                    book, absStartX, durY,
                    if (ReadConfig.enableReview) text + reviewChar else text,
                    textPages,
                    stringBuilder,
                    titlePaint,
                    titlePaintTextHeight,
                    titlePaintFontMetrics,
                    isTitle = true,
                    emptyContent = contents.isEmpty(),
                    isVolumeTitle = bookChapter.isVolume
                ).let {
                    absStartX = it.first
                    durY = it.second
                }
            }
            textPages.last().lines.last().isParagraphEnd = true
            stringBuilder.append("\n")
            durY += titleBottomSpacing
        }
        contents.forEach { content ->
            if (book.getImageStyle().equals(Book.imgStyleText, true)) {
                //图片样式为文字嵌入类型
                var text = content.replace(srcReplaceChar, "▣")
                val srcList = LinkedList<String>()
                val sb = StringBuffer()
                val matcher = AppPattern.imgPattern.matcher(text)
                while (matcher.find()) {
                    matcher.group(1)?.let { src ->
                        srcList.add(src)
                        matcher.appendReplacement(sb, srcReplaceChar)
                    }
                }
                matcher.appendTail(sb)
                text = sb.toString()
                setTypeText(
                    book,
                    absStartX,
                    durY,
                    text,
                    textPages,
                    stringBuilder,
                    contentPaint,
                    contentPaintTextHeight,
                    contentPaintFontMetrics,
                    srcList = srcList
                ).let {
                    absStartX = it.first
                    durY = it.second
                }
            } else {
                val matcher = AppPattern.imgPattern.matcher(content)
                var start = 0
                while (matcher.find()) {
                    val text = content.substring(start, matcher.start())
                    if (text.isNotBlank()) {
                        setTypeText(
                            book,
                            absStartX,
                            durY,
                            text,
                            textPages,
                            stringBuilder,
                            contentPaint,
                            contentPaintTextHeight,
                            contentPaintFontMetrics
                        ).let {
                            absStartX = it.first
                            durY = it.second
                        }
                    }
                    setTypeImage(
                        book,
                        matcher.group(1)!!,
                        absStartX,
                        durY,
                        textPages,
                        contentPaintTextHeight,
                        stringBuilder,
                        book.getImageStyle()
                    ).let {
                        absStartX = it.first
                        durY = it.second
                    }
                    start = matcher.end()
                }
                if (start < content.length) {
                    val text = content.substring(start, content.length)
                    if (text.isNotBlank()) {
                        setTypeText(
                            book, absStartX, durY,
                            if (ReadConfig.enableReview) text + reviewChar else text,
                            textPages,
                            stringBuilder,
                            contentPaint,
                            contentPaintTextHeight,
                            contentPaintFontMetrics
                        ).let {
                            absStartX = it.first
                            durY = it.second
                        }
                    }
                }
            }
            textPages.last().lines.last().isParagraphEnd = true
            stringBuilder.append("\n")
        }
        val textPage = textPages.last()
        val endPadding = 20.dpToPx()
        val durYPadding = durY + endPadding
        if (textPage.height < durYPadding) {
            textPage.height = durYPadding
        } else {
            textPage.height += endPadding
        }
        textPage.text = stringBuilder.toString()
        textPages.forEachIndexed { index, item ->
            item.index = index
            //item.pageSize = textPages.size
            item.chapterIndex = bookChapter.index
            item.chapterSize = chapterSize
            item.title = displayTitle
            item.doublePage = doublePage
            item.paddingTop = paddingTop
            item.upLinesPosition()
        }

        return TextChapter(
            bookChapter,
            bookChapter.index, displayTitle,
            //textPages,
            chapterSize,
            bookContent.sameTitleRemoved,
            bookChapter.isVip,
            bookChapter.isPay,
            bookContent.effectiveReplaceRules,
            bookContent.effectiveContentProcesses,
        )
    }
    */

    fun getTextChapterAsync(
        scope: CoroutineScope,
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
        chapterSize: Int,
    ): TextChapter {

        val textChapter = TextChapter(
            bookChapter,
            bookChapter.index, displayTitle,
            chapterSize,
            bookContent.sameTitleRemoved,
            bookChapter.isVip,
            bookChapter.isPay,
            bookContent.effectiveReplaceRules,
            bookContent.effectiveContentProcesses,
        ).apply {
            createLayout(scope, book, bookContent)
        }

        return textChapter
    }

    /*
    /**
     * 排版图片
     */
    private suspend fun setTypeImage(
        book: Book,
        src: String,
        x: Int,
        y: Float,
        textPages: ArrayList<TextPage>,
        textHeight: Float,
        stringBuilder: StringBuilder,
        imageStyle: String?,
    ): Pair<Int, Float> {
        var absStartX = x
        var durY = y
        val size = ImageProvider.getImageSize(book, src, ReadBook.bookSource)
        if (size.width > 0 && size.height > 0) {
            if (durY > visibleHeight) {
                val textPage = textPages.last()
                if (textPage.height < durY) {
                    textPage.height = durY
                }
                textPage.text = stringBuilder.toString().ifEmpty { "本页无文字内容" }
                stringBuilder.clear()
                textPages.add(TextPage())
                durY = 0f
            }
            var height = size.height
            var width = size.width
            when (imageStyle?.uppercase(Locale.ROOT)) {
                Book.imgStyleFull -> {
                    width = visibleWidth
                    height = size.height * visibleWidth / size.width
                }

                Book.imgStyleSingle -> {
                    width = visibleWidth
                    height = size.height * visibleWidth / size.width
                    if (height > visibleHeight) {
                        width = width * visibleHeight / height
                        height = visibleHeight
                    }
                    if (durY > 0f) {
                        val textPage = textPages.last()
                        if (doublePage && absStartX < viewWidth / 2) {
                            //当前页面左列结束
                            textPage.leftLineSize = textPage.lineSize
                            absStartX = viewWidth / 2 + paddingLeft
                        } else {
                            //当前页面结束
                            if (textPage.leftLineSize == 0) {
                                textPage.leftLineSize = textPage.lineSize
                            }
                            textPage.text = stringBuilder.toString().ifEmpty { "本页无文字内容" }
                            stringBuilder.clear()
                            textPages.add(TextPage())
                        }
                        // 双页的 durY 不正确，可能会小于实际高度
                        if (textPage.height < durY) {
                            textPage.height = durY
                        }
                        durY = 0f
                    }

                    // 图片竖直方向居中：调整 Y 坐标
                    if (height < visibleHeight) {
                        val adjustHeight = (visibleHeight - height) / 2f
                        durY = adjustHeight // 将 Y 坐标设置为居中位置
                    }
                }

                else -> {
                    if (size.width > visibleWidth) {
                        height = size.height * visibleWidth / size.width
                        width = visibleWidth
                    }
                    if (height > visibleHeight) {
                        width = width * visibleHeight / height
                        height = visibleHeight
                    }
                    if (durY + height > visibleHeight) {
                        val textPage = textPages.last()
                        if (doublePage && absStartX < viewWidth / 2) {
                            //当前页面左列结束
                            textPage.leftLineSize = textPage.lineSize
                            absStartX = viewWidth / 2 + paddingLeft
                        } else {
                            //当前页面结束
                            if (textPage.leftLineSize == 0) {
                                textPage.leftLineSize = textPage.lineSize
                            }
                            textPage.text = stringBuilder.toString().ifEmpty { "本页无文字内容" }
                            stringBuilder.clear()
                            textPages.add(TextPage())
                        }
                        // 双页的 durY 不正确，可能会小于实际高度
                        if (textPage.height < durY) {
                            textPage.height = durY
                        }
                        durY = 0f
                    }
                }
            }
            val textLine = TextLine(isImage = true)
            textLine.lineTop = durY + paddingTop
            durY += height
            textLine.lineBottom = durY + paddingTop
            val (start, end) = if (visibleWidth > width) {
                val adjustWidth = (visibleWidth - width) / 2f
                Pair(adjustWidth, adjustWidth + width)
            } else {
                Pair(0f, width.toFloat())
            }
            textLine.addColumn(
                ImageColumn(start = x + start, end = x + end, src = src)
            )
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(" ") // 确保翻页时索引计算正确
            textPages.last().addLine(textLine)
        }
        return absStartX to durY + textHeight * paragraphSpacing / 10f
    }

    /**
     * 排版文字
     */
    private suspend fun setTypeText(
        book: Book,
        x: Int,
        y: Float,
        text: String,
        textPages: ArrayList<TextPage>,
        stringBuilder: StringBuilder,
        textPaint: TextPaint,
        textHeight: Float,
        fontMetrics: FontMetrics,
        isTitle: Boolean = false,
        emptyContent: Boolean = false,
        isVolumeTitle: Boolean = false,
        srcList: LinkedList<String>? = null
    ): Pair<Int, Float> {
        var absStartX = x
        val layout = if (ReadBookConfig.useZhLayout) {
            ZhLayout(
                text, textPaint, visibleWidth, emptyList(), emptyList(),
                ReadBookConfig.paragraphIndent.length
            )
        } else {
            StaticLayout(text, textPaint, visibleWidth, Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true)
        }
        var durY = when {
            //标题y轴居中
            emptyContent && textPages.size == 1 -> {
                val textPage = textPages.last()
                if (textPage.lineSize == 0) {
                    val ty = (visibleHeight - layout.lineCount * textHeight) / 2
                    if (ty > titleTopSpacing) ty else titleTopSpacing.toFloat()
                } else {
                    var textLayoutHeight = layout.lineCount * textHeight
                    val fistLine = textPage.getLine(0)
                    if (fistLine.lineTop < textLayoutHeight + titleTopSpacing) {
                        textLayoutHeight = fistLine.lineTop - titleTopSpacing
                    }
                    textPage.lines.forEach {
                        it.lineTop -= textLayoutHeight
                        it.lineBase -= textLayoutHeight
                        it.lineBottom -= textLayoutHeight
                    }
                    y - textLayoutHeight
                }
            }

            isTitle && textPages.size == 1 && textPages.last().lines.isEmpty() ->
                y + titleTopSpacing

            else -> y
        }
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine(isTitle = isTitle)
            if (durY + textHeight > visibleHeight) {
                val textPage = textPages.last()
                if (doublePage && absStartX < viewWidth / 2) {
                    //当前页面左列结束
                    textPage.leftLineSize = textPage.lineSize
                    absStartX = viewWidth / 2 + paddingLeft
                } else {
                    //当前页面结束,设置各种值
                    if (textPage.leftLineSize == 0) {
                        textPage.leftLineSize = textPage.lineSize
                    }
                    textPage.text = stringBuilder.toString()
                    //新建页面
                    textPages.add(TextPage())
                    stringBuilder.clear()
                    absStartX = paddingLeft
                }
                if (textPage.height < durY) {
                    textPage.height = durY
                }
                durY = 0f
            }
            val lineStart = layout.getLineStart(lineIndex)
            val lineEnd = layout.getLineEnd(lineIndex)
            val lineText = text.substring(lineStart, lineEnd)
            val (words, widths) = measureTextSplit(lineText, textPaint)
            val desiredWidth = widths.fastSum()
            when {
                lineIndex == 0 && layout.lineCount > 1 && !isTitle -> {
                    //第一行 非标题
                    textLine.text = lineText
                    addCharsToLineFirst(
                        book, absStartX, textLine, words,
                        desiredWidth, widths, srcList
                    )
                }

                lineIndex == layout.lineCount - 1 -> {
                    //最后一行
                    textLine.text = lineText
                    //标题x轴居中
                    val startX = if (
                        isTitle &&
                        (ReadBookConfig.isMiddleTitle || emptyContent || isVolumeTitle)
                    ) {
                        (visibleWidth - desiredWidth) / 2
                    } else {
                        0f
                    }
                    addCharsToLineNatural(
                        book, absStartX, textLine, words,
                        startX, !isTitle && lineIndex == 0, widths, srcList
                    )
                }

                else -> {
                    if (
                        isTitle &&
                        (ReadBookConfig.isMiddleTitle || emptyContent || isVolumeTitle)
                    ) {
                        //标题居中
                        val startX = (visibleWidth - desiredWidth) / 2
                        addCharsToLineNatural(
                            book, absStartX, textLine, words,
                            startX, false, widths, srcList
                        )
                    } else {
                        //中间行
                        textLine.text = lineText
                        addCharsToLineMiddle(
                            book, absStartX, textLine, words,
                            desiredWidth, 0f, widths, srcList
                        )
                    }
                }
            }
            if (doublePage) {
                textLine.isLeftLine = absStartX < viewWidth / 2
            }
            calcTextLinePosition(textPages, textLine, stringBuilder.length)
            stringBuilder.append(lineText)
            textLine.upTopBottom(durY, textHeight, fontMetrics)
            val textPage = textPages.last()
            textPage.addLine(textLine)
            durY += textHeight * lineSpacingExtra
            if (textPage.height < durY) {
                textPage.height = durY
            }
        }
        durY += textHeight * paragraphSpacing / 10f
        return Pair(absStartX, durY)
    }

    private fun calcTextLinePosition(
        textPages: ArrayList<TextPage>,
        textLine: TextLine,
        sbLength: Int
    ) {
        val lastLine = textPages.last().lines.lastOrNull { it.paragraphNum > 0 }
            ?: textPages.getOrNull(
                textPages.lastIndex - 1
            )?.lines?.lastOrNull { it.paragraphNum > 0 }
        val paragraphNum = when {
            lastLine == null -> 1
            lastLine.isParagraphEnd -> lastLine.paragraphNum + 1
            else -> lastLine.paragraphNum
        }
        textLine.paragraphNum = paragraphNum
        textLine.chapterPosition =
            (textPages.getOrNull(textPages.lastIndex - 1)?.lines?.lastOrNull()?.run {
                chapterPosition + charSize + if (isParagraphEnd) 1 else 0
            } ?: 0) + sbLength
        textLine.pagePosition = sbLength
    }

    /**
     * 有缩进,两端对齐
     */
    private suspend fun addCharsToLineFirst(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        /**自然排版长度**/
        desiredWidth: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?
    ) {
        var x = 0f
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                x, true, textWidths, srcList
            )
            return
        }
        val bodyIndent = ReadBookConfig.paragraphIndent
        for (i in bodyIndent.indices) {
            val x1 = x + indentCharWidth
            textLine.addColumn(
                TextColumn(
                    charData = indentChar,
                    start = absStartX + x,
                    end = absStartX + x1
                )
            )
            x = x1
            textLine.indentWidth = x
        }
        if (words.size > bodyIndent.length) {
            val text1 = words.subList(bodyIndent.length, words.size)
            val textWidths1 = textWidths.subList(bodyIndent.length, textWidths.size)
            addCharsToLineMiddle(
                book, absStartX, textLine, text1,
                desiredWidth, x, textWidths1, srcList
            )
        }
    }

    /**
     * 无缩进,两端对齐
     */
    private suspend fun addCharsToLineMiddle(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        /**自然排版长度**/
        desiredWidth: Float,
        /**起始x坐标**/
        startX: Float,
        textWidths: List<Float>,
        srcList: LinkedList<String>?
    ) {
        if (!ReadBookConfig.textFullJustify) {
            addCharsToLineNatural(
                book, absStartX, textLine, words,
                startX, false, textWidths, srcList
            )
            return
        }
        val residualWidth = visibleWidth - desiredWidth
        val spaceSize = words.count { it == " " }
        if (spaceSize > 1) {
            val d = residualWidth / spaceSize
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (char == " ") {
                    if (index != words.lastIndex) (x + cw + d) else (x + cw)
                } else {
                    (x + cw)
                }
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList
                )
                x = x1
            }
        } else {
            val gapCount: Int = words.lastIndex
            val d = residualWidth / gapCount
            var x = startX
            for (index in words.indices) {
                val char = words[index]
                val cw = textWidths[index]
                val x1 = if (index != words.lastIndex) (x + cw + d) else (x + cw)
                addCharToLine(
                    book, absStartX, textLine, char,
                    x, x1, index + 1 == words.size, srcList
                )
                x = x1
            }
        }
        exceed(absStartX, textLine, words)
    }

    /**
     * 自然排列
     */
    private suspend fun addCharsToLineNatural(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        words: List<String>,
        startX: Float,
        hasIndent: Boolean,
        textWidths: List<Float>,
        srcList: LinkedList<String>?
    ) {
        val indentLength = ReadBookConfig.paragraphIndent.length
        var x = startX
        for (index in words.indices) {
            val char = words[index]
            val cw = textWidths[index]
            val x1 = x + cw
            addCharToLine(book, absStartX, textLine, char, x, x1, index + 1 == words.size, srcList)
            x = x1
            if (hasIndent && index == indentLength - 1) {
                textLine.indentWidth = x
            }
        }
        exceed(absStartX, textLine, words)
    }

    /**
     * 添加字符
     */
    private suspend fun addCharToLine(
        book: Book,
        absStartX: Int,
        textLine: TextLine,
        char: String,
        xStart: Float,
        xEnd: Float,
        isLineEnd: Boolean,
        srcList: LinkedList<String>?
    ) {
        val column = when {
            srcList != null && char == srcReplaceChar -> {
                val src = srcList.removeFirst()
                ImageProvider.cacheImage(book, src, ReadBook.bookSource)
                ImageColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    src = src
                )
            }

            isLineEnd && char == reviewChar -> {
                ReviewColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    count = 100
                )
            }

            else -> {
                TextColumn(
                    start = absStartX + xStart,
                    end = absStartX + xEnd,
                    charData = char
                )
            }
        }
        textLine.addColumn(column)
    }

    /**
     * 超出边界处理
     */
    private fun exceed(absStartX: Int, textLine: TextLine, words: List<String>) {
        val visibleEnd = absStartX + visibleWidth
        val endX = textLine.columns.lastOrNull()?.end ?: return
        if (endX > visibleEnd) {
            val cc = (endX - visibleEnd) / words.size
            for (i in 0..words.lastIndex) {
                textLine.getColumnReverseAt(i).let {
                    val py = cc * (words.size - i)
                    it.start -= py
                    it.end -= py
                }
            }
        }
    }

    fun measureTextSplit(
        text: String,
        paint: TextPaint
    ): Pair<ArrayList<String>, ArrayList<Float>> {
        val length = text.length
        val widthsArray = FloatArray(length)
        paint.getTextWidths(text, widthsArray)
        val clusterCount = widthsArray.count { it > 0f }
        val widths = ArrayList<Float>(clusterCount)
        val stringList = ArrayList<String>(clusterCount)
        var i = 0
        while (i < length) {
            val clusterBaseIndex = i++
            widths.add(widthsArray[clusterBaseIndex])
            while (i < length && widthsArray[i] == 0f) {
                i++
            }
            stringList.add(text.substring(clusterBaseIndex, i))
        }
        return stringList to widths
    }
    */

    /**
     * 更新样式
     */
    fun upStyle() {
        upRenderStyle()
        val typeface = getTypeface(ReadBookConfig.textFont)
        val (titlePaint, contentPaint) = getPaints(typeface)
        dashEffect = DashPathEffect(
            floatArrayOf(ReadBookConfig.durConfig.dottedBase, ReadBookConfig.durConfig.dottedRatio),
            0f
        )
        val bodyIndent = ReadBookConfig.paragraphIndent
        val indentCharWidth = if (bodyIndent.isNotEmpty()) {
            var indentWidth = StaticLayout.getDesiredWidth(bodyIndent, contentPaint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                indentWidth += contentPaint.letterSpacing * contentPaint.textSize
            }
            indentWidth / bodyIndent.length
        } else {
            0f
        }
        // 样式与布局一起算完再发布，避免中间态被排版协程读到
        metrics = withLayout(
            metrics.copy(
                typeface = typeface,
                titlePaint = titlePaint,
                contentPaint = contentPaint,
                //间距
                lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f,
                titleLineSpacingExtra = ReadBookConfig.titleLineSpacingExtra / 10f,
                titleLineSpacingSub = ReadBookConfig.titleLineSpacingSub / 10f,
                paragraphSpacing = ReadBookConfig.paragraphSpacing,
                titleTopSpacing = ReadBookConfig.titleTopSpacing.dpToPx(),
                titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dpToPx(),
                indentCharWidth = indentCharWidth,
                titlePaintTextHeight = titlePaint.textHeight,
                contentPaintTextHeight = contentPaint.textHeight,
                titlePaintFontMetrics = titlePaint.fontMetrics,
                contentPaintFontMetrics = contentPaint.fontMetrics,
            )
        )
    }

    /** 主题切换只更新绘制颜色，不触发字体加载或正文重排。 */
    fun upThemeColors() {
        upRenderStyle()
        val textColor = ReadBookConfig.textColor
        titlePaint.color = textColor
        contentPaint.color = textColor
        linePaint.color = textColor
        reviewPaint.color = textColor
        if (ReadBookConfig.textShadow) {
            val shadowColor = ReadBookConfig.textShadowColor
            titlePaint.setShadowLayer(
                ReadBookConfig.shadowRadius,
                ReadBookConfig.shadowDx,
                ReadBookConfig.shadowDy,
                shadowColor,
            )
            contentPaint.setShadowLayer(
                ReadBookConfig.shadowRadius,
                ReadBookConfig.shadowDx,
                ReadBookConfig.shadowDy,
                shadowColor,
            )
        } else {
            titlePaint.clearShadowLayer()
            contentPaint.clearShadowLayer()
        }
    }

    private fun getTypeface(fontPath: String): Typeface? {
        return kotlin.runCatching {
            when {
                fontPath.isContentScheme() -> {
                    appCtx.contentResolver
                        .openFileDescriptor(fontPath.toUri(), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }

                fontPath.isNotEmpty() -> {
                    Typeface.Builder(File(fontPath)).build()
                }

                else -> {
                    when (ReadConfig.systemTypefaces) {
                        1 -> Typeface.SERIF
                        2 -> Typeface.MONOSPACE
                        else -> Typeface.SANS_SERIF
                    }
                }
            }
        }.getOrElse {
            GlobalContext.get().get<ReadStyleGateway>().clearMissingTextFont()
            Typeface.SANS_SERIF
        } ?: Typeface.DEFAULT
    }

    @SuppressLint("UseKtx")
    private fun getPaints(typeface: Typeface?): Pair<TextPaint, TextPaint> {
        val titleTypeface = runCatching {
            val titleFontPath = ReadBookConfig.titleFont
            when {
                titleFontPath.isContentScheme() -> {
                    appCtx.contentResolver
                        .openFileDescriptor(titleFontPath.toUri(), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }
                titleFontPath.isNotEmpty() -> {
                    Typeface.Builder(File(titleFontPath)).build()
                }
                else -> typeface
            }
        }.getOrNull() ?: typeface

        val bold = Typeface.create(typeface, Typeface.BOLD)
        val normal = Typeface.create(typeface, Typeface.NORMAL)
        val titleBoldTypeface = Typeface.create(titleTypeface, Typeface.BOLD)
        val titleNormalTypeface = Typeface.create(titleTypeface, Typeface.NORMAL)
        val titleFontTypeface = when (ReadBookConfig.titleBold) {
            1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(titleTypeface, 900, false)
            else
                titleBoldTypeface

            2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(titleTypeface, 300, false)
            else
                titleNormalTypeface

            0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(titleTypeface, 400, false)
            else
                titleNormalTypeface

            in 100..900 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(titleTypeface, ReadBookConfig.titleBold, false)
            else
                titleNormalTypeface

            else -> titleNormalTypeface
        }

        val textFont = when (ReadBookConfig.textBold) {
            1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(typeface, 900, false)
            else
                bold

            2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(typeface, 300, false)
            else
                normal

            0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(typeface, 400, false)
            else
                normal

            in 100..900 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                Typeface.create(typeface, ReadBookConfig.textBold, false)
            else
                normal

            else -> normal
        }


        //标题
        val tPaint = TextPaint()
        tPaint.color = ReadBookConfig.textColor
        tPaint.letterSpacing = ReadBookConfig.letterSpacing
        tPaint.typeface = titleFontTypeface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ReadBookConfig.titleBold in 100..900)
            tPaint.setFontVariationSettings("'wght' ${ReadBookConfig.titleBold}")
        tPaint.textSize = with(ReadBookConfig) { textSize + titleSize }.toFloat().spToPx()
        tPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && ReadConfig.optimizeRender) {
            tPaint.isLinearText = true
        }
        if (ReadBookConfig.textItalic) {
            tPaint.textSkewX = -0.25f
        }
        if (ReadBookConfig.textShadow) {
            tPaint.setShadowLayer(
                ReadBookConfig.shadowRadius,
                ReadBookConfig.shadowDx,
                ReadBookConfig.shadowDy,
                ReadBookConfig.textShadowColor
            )
        }
        //正文
        val cPaint = TextPaint()
        cPaint.color = ReadBookConfig.textColor
        cPaint.letterSpacing = ReadBookConfig.letterSpacing
        cPaint.typeface = textFont
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ReadBookConfig.textBold in 100..900)
            cPaint.setFontVariationSettings("'wght' ${ReadBookConfig.textBold}")
        cPaint.textSize = ReadBookConfig.textSize.toFloat().spToPx()
        cPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && ReadConfig.optimizeRender) {
            cPaint.isLinearText = true
        }
        if (ReadBookConfig.textItalic) {
            cPaint.textSkewX = -0.25f
        }
        if (ReadBookConfig.textShadow) {
            cPaint.setShadowLayer(
                ReadBookConfig.shadowRadius,
                ReadBookConfig.shadowDx,
                ReadBookConfig.shadowDy,
                ReadBookConfig.textShadowColor
            )
        }
        return Pair(tPaint, cPaint)
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize(width: Int, height: Int) {
        upViewSizeRunnable?.let {
            handler.removeCallbacks(it)
            upViewSizeRunnable = null
        }
        if (width <= 0 || height <= 0) {
            return
        }
        if (width != viewWidth || height != viewHeight) {
            if (width == viewWidth) {
                upViewSizeRunnable = handler.postDelayed(300) {
                    upViewSizeRunnable = null
                    notifyViewSizeChange(width, height)
                }
            } else {
                notifyViewSizeChange(width, height)
            }
        }
    }

    private fun notifyViewSizeChange(width: Int, height: Int) {
        metrics = withLayout(metrics.copy(viewWidth = width, viewHeight = height))
        ReadBook.requestWholeBookPageEstimate()
        ReadConfigUpdateBus.post(setOf(ConfigUpdateAction.RelayoutContent))
    }

    /**
     * 更新绘制尺寸
     */
    fun upLayout() {
        metrics = withLayout(metrics)
    }

    /**
     * 由 [base] 的样式与视图尺寸推导全部绘制尺寸，返回新快照。纯函数，不写全局——
     * 调用方负责一次性发布，保证排版协程读不到中间态。
     */
    private fun withLayout(base: LayoutMetrics): LayoutMetrics {
        val doublePage = when (ReadConfig.doubleHorizontalPage) {
            "0" -> false
            "1" -> true
            "2" -> (base.viewWidth > base.viewHeight) && ReadBook.pageAnim() != 3
            "3" -> (base.viewWidth > base.viewHeight || appCtx.isPad) && ReadBook.pageAnim() != 3
            else -> base.doublePage
        }

        if (base.viewWidth <= 0 || base.viewHeight <= 0) {
            return base.copy(doublePage = doublePage)
        }

        val paddingLeft = ReadBookConfig.paddingLeft.dpToPx()
        val paddingTop = ReadBookConfig.paddingTop.dpToPx()
        val paddingRight = ReadBookConfig.paddingRight.dpToPx()
        val paddingBottom = ReadBookConfig.paddingBottom.dpToPx()
        val visibleWidth = if (doublePage) {
            base.viewWidth / 2 - paddingLeft - paddingRight
        } else {
            base.viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        val visibleHeight = base.viewHeight - paddingTop - paddingBottom
        val visibleRight = base.viewWidth - paddingRight
        val visibleBottom = paddingTop + visibleHeight

        val shadowPad = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (base.contentPaint.shadowLayerRadius + 2).toInt()
        } else {
            20
        }

        val italicPad = if (ReadBookConfig.textItalic)  (ReadBookConfig.textSize * 0.25f).spToPx() else 0f

        val visibleRect = RectF(
            (paddingLeft - shadowPad - italicPad),
            (paddingTop - shadowPad).toFloat(),
            (visibleRight + shadowPad + italicPad),
            (visibleBottom + shadowPad).toFloat()
        )

        //TODO: 有关测量相关问题
        return base.copy(
            doublePage = doublePage,
            paddingLeft = paddingLeft,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
            visibleWidth = visibleWidth,
            visibleHeight = visibleHeight,
            visibleRight = visibleRight,
            visibleBottom = visibleBottom,
            visibleRect = visibleRect,
        )
    }

}
