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
import io.legado.app.data.entities.BookSource
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

    /**
     * 朗读高亮 / 搜索命中那条线的画笔。
     *
     * 曾经是 `by lazy`：首次取用时按当时的 [contentPaint] 与 `underlineHeight` 定型，之后
     * 再改下划线粗细、字体、字号都不会反映到这条线上——`upThemeColors` 只就地改了它的
     * `color`，粗细和字形一直是首帧那份。现在与其它画笔一样由 [upStyle] 重建。
     */
    @Volatile
    var linePaint: Paint = Paint()
        private set

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
        // 下面三项来自 ReadConfig 而不是 ReadBookConfig，但对绘制路径是同一回事：
        // 每次经门面读都要全量构造一份 ReadSettings（几十个 preferences 查找），
        // 放在 draw() 里比 getConfig 的监视器锁还贵。
        val useUnderline: Boolean = false,
        val optimizeRender: Boolean = false,
        val isEInkMode: Boolean = false,
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
            underlinePadding = ReadBookConfig.underlinePadding,
            textBottomJustify = ReadBookConfig.textBottomJustify,
            useUnderline = ReadConfig.useUnderline,
            optimizeRender = ReadConfig.optimizeRender,
            isEInkMode = ReadConfig.isEInkMode,
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

    fun getTextChapterAsync(
        scope: CoroutineScope,
        book: Book,
        bookSource: BookSource?,
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
            createLayout(scope, book, bookSource, bookContent)
        }

        return textChapter
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        upRenderStyle()
        val typeface = getTypeface(ReadBookConfig.textFont)
        val (titlePaint, contentPaint) = getPaints(typeface)
        linePaint = Paint(contentPaint).apply {
            clearShadowLayer()
            isAntiAlias = true
            strokeWidth = renderStyle.underlineHeight.toFloat()
            style = Paint.Style.STROKE
        }
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
        tPaint.textSize = ReadBookConfig.titleSize.toFloat().spToPx()
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
