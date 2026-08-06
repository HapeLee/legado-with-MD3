package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import io.legado.app.R
import io.legado.app.constant.AppConst.timeFormat
import io.legado.app.constant.ReadTipType
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ViewBookPageBinding
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadSessionState
import io.legado.app.model.ReaderBookmarkState
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TipStyleProvider
import io.legado.app.ui.book.read.sheet.CustomTipTarget
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.BatteryView
import io.legado.app.utils.activity
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.canvasrecorder.CanvasRecorder
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.statusBarHeight
import splitties.views.backgroundColor
import java.util.Date
import io.legado.app.utils.screenshot as viewScreenshot

/**
 * 页面视图
 */
class PageView(
    context: Context,
    callBack: ContentTextView.CallBack? = null,
) : FrameLayout(context) {

    private val binding = ViewBookPageBinding.inflate(LayoutInflater.from(context), this, true)
    private var battery = 100
    private var tvTitle: BatteryView? = null
    private var tvTime: BatteryView? = null
    private var tvBattery: BatteryView? = null
    private var tvBatteryP: BatteryView? = null
    private var tvPage: BatteryView? = null
    private var tvTotalProgress: BatteryView? = null
    private var tvTotalProgress1: BatteryView? = null
    private var tvPageAndTotal: BatteryView? = null
    private var tvWholeBookPage: BatteryView? = null
    private var tvWholeBookPageAndProgress: BatteryView? = null
    private var tvBookName: BatteryView? = null
    private var tvTimeBattery: BatteryView? = null
    private var tvTimeBatteryP: BatteryView? = null
    private var tvTitleArrow: BatteryView? = null
    private var tvBatteryInside: BatteryView? = null
    private var tvBatteryIcon: BatteryView? = null
    private var tvBatteryClassic: BatteryView? = null
    private var tvTimeBatteryClassic: BatteryView? = null
    private var tvTitleArrowClassic: BatteryView? = null

    private var isMainView = false
    var isScroll = false

    private var currentTextPage: TextPage? = null

    /**
     * 右上角书签角标：本页范围内落有书签时显示。
     *
     * 滚动模式一屏可见多页，`currentTextPage` 不再唯一对应可见内容，故该模式下不显示——
     * 与下滑手势在滚动模式下同样不启用保持一致。
     */
    fun upBookmarkBadge() {
        val page = currentTextPage
        val book = ReadBook.book
        val visible = book != null && page != null && !isScroll && !page.isMsgPage && page.lineSize > 0 &&
                ReaderBookmarkState.hasBookmarkInRange(
                    bookName = book.name,
                    bookAuthor = book.author,
                    chapterIndex = page.chapterIndex,
                    startPos = page.chapterPosition,
                    endPos = page.chapterPosition + page.charSize,
                )
        // 角标是固定的黄色书签丝带（见 ic_bookmark_badge），不随主题变色。
        binding.ivBookmarkBadge.isGone = !visible
    }

    val headerHeight: Int
        get() {
            val h1 = if (binding.vwStatusBar.isGone) 0 else binding.vwStatusBar.height
            val h2 = if (binding.llHeader.isGone) 0 else binding.llHeader.height
            return h1 + h2 + binding.vwRoot.paddingTop
        }

    val imgBgPaddingStart: Int
        get() {
            return binding.vwRoot.paddingStart
        }

    val contentOffsetX: Int get() = binding.contentTextView.left

    val contentOffsetY: Int get() = binding.contentTextView.top

    val contentWidth: Int get() = binding.contentTextView.width

    val contentHeight: Int get() = binding.contentTextView.height

    init {
        callBack?.let { binding.contentTextView.setCallBack(it) }
        upStyle()
        binding.vwStatusBar.applyStatusBarPadding()
        val initialNavigationBarHeight = currentNavigationBarHeight()
        if (initialNavigationBarHeight > 0) {
            binding.vwNavigationBar.updateLayoutParams {
                height = initialNavigationBarHeight
            }
        }
        binding.vwNavigationBar.setOnApplyWindowInsetsListenerCompat { v, windowInsets ->
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            if (isImeVisible) {
                return@setOnApplyWindowInsetsListenerCompat windowInsets
            }
            //Log.d("fansangg", "vwNavigationBar OnApplyWindowInsetsListener: navHeight=$navHeight, isImeVisible=${windowInsets.isVisible(WindowInsetsCompat.Type.ime())}, imeHeight=${windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom}, systemBarsHeight=${windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom}")
            val navHeight = windowInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            if (navHeight > 0) {
                ReadSessionState.lastNavigationBarHeight = navHeight
            }
            v.updateLayoutParams {
                height = navHeight
            }
            windowInsets
        }
    }

    private fun currentNavigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val stableHeight = activity?.windowManager?.currentWindowMetrics?.windowInsets
                ?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                ?.bottom
                ?: 0
            if (stableHeight > 0) {
                ReadSessionState.lastNavigationBarHeight = stableHeight
                return stableHeight
            }
        }
        return ReadSessionState.lastNavigationBarHeight
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        upBg()
    }

    fun upStyle() = binding.run {
        val style = TipStyleProvider.style
        upTipStyle(style)
        applyTipColors(style)
        upStatusBar()
        upNavigationBar()
        upPaddingDisplayCutouts()
        llHeader.setPadding(
            style.headerPaddingLeft.dpToPx(),
            style.headerPaddingTop.dpToPx(),
            style.headerPaddingRight.dpToPx(),
            style.headerPaddingBottom.dpToPx()
        )
        llFooter.setPadding(
            style.footerPaddingLeft.dpToPx(),
            style.footerPaddingTop.dpToPx(),
            style.footerPaddingRight.dpToPx(),
            style.footerPaddingBottom.dpToPx()
        )
        vwTopDivider.gone(llHeader.isGone || !style.showHeaderLine)
        vwBottomDivider.gone(llFooter.isGone || !style.showFooterLine)
        upTime()
        upBattery(battery)
    }

    /** 仅更新日夜模式相关颜色，避免主题切换时重新加载字体和重排正文。 */
    fun upThemeColors() = binding.run {
        applyTipColors(TipStyleProvider.style)
        upBookmarkBadge()
        contentTextView.invalidate()
    }

    /** 页眉/页脚文字色与分隔线色：0 表示跟随正文色，-1（分隔线）表示用主题色。 */
    private fun applyTipColors(style: TipStyleProvider.TipStyle) = binding.run {
        val textColor = style.textColor
        val headerColor = if (style.tipHeaderColor == 0) textColor else style.tipHeaderColor
        val footerColor = if (style.tipFooterColor == 0) textColor else style.tipFooterColor
        val dividerColor = when (style.tipDividerColor) {
            -1 -> ContextCompat.getColor(context, R.color.divider)
            0 -> textColor
            else -> style.tipDividerColor
        }
        tvHeaderLeft.setColor(headerColor)
        tvHeaderMiddle.setColor(headerColor)
        tvHeaderRight.setColor(headerColor)
        tvFooterLeft.setColor(footerColor)
        tvFooterMiddle.setColor(footerColor)
        tvFooterRight.setColor(footerColor)
        vwTopDivider.backgroundColor = dividerColor
        vwBottomDivider.backgroundColor = dividerColor
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() = with(binding.vwStatusBar) {
        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
        isGone = TipStyleProvider.style.hideStatusBar || activity?.isInMultiWindowMode == true
    }

    fun upNavigationBar() {
        binding.vwNavigationBar.isGone = TipStyleProvider.style.hideNavigationBar
    }

    fun upPaddingDisplayCutouts() {
        if (ReadConfig.paddingDisplayCutouts) {
            binding.vwRoot.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                binding.vwRoot.setPadding(
                    insets.left,
                    if (binding.vwStatusBar.isGone) insets.top else 0,
                    insets.right,
                    insets.bottom
                )
                windowInsets
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.vwRoot, null)
            binding.vwRoot.setPadding(0, 0, 0, 0)
        }
    }

    /**
     * 更新阅读信息
     */
    private fun upTipStyle(style: TipStyleProvider.TipStyle) = binding.run {
        tvHeaderLeft.tag = null
        tvHeaderMiddle.tag = null
        tvHeaderRight.tag = null
        tvFooterLeft.tag = null
        tvFooterMiddle.tag = null
        tvFooterRight.tag = null
        llHeader.isGone = when (style.headerMode) {
            1 -> false
            2 -> true
            else -> !style.hideStatusBar
        }
        llFooter.isGone = when (style.footerMode) {
            1 -> true
            else -> false
        }
        style.apply {
            tvHeaderLeft.isGone = tipHeaderLeft == ReadTipType.tipNone
            tvHeaderMiddle.isGone = tipHeaderMiddle == ReadTipType.tipNone
            if (tipHeaderRight == ReadTipType.tipNone) {
                if (tipHeaderMiddle == ReadTipType.tipNone && tipHeaderLeft == ReadTipType.tipNone) {
                    tvHeaderRight.isGone = true
                } else {
                    tvHeaderRight.isGone = false
                    tvHeaderRight.batteryMode = BatteryView.BatteryMode.EMPTY
                }
            } else {
                tvHeaderRight.isGone = false
                tvHeaderRight.batteryMode = BatteryView.BatteryMode.NO_BATTERY
            }
            tvFooterLeft.isGone = tipFooterLeft == ReadTipType.tipNone
            tvFooterMiddle.isGone = tipFooterMiddle == ReadTipType.tipNone
            if (tipFooterRight == ReadTipType.tipNone) {
                if (tipFooterLeft == ReadTipType.tipNone && tipFooterMiddle == ReadTipType.tipNone) {
                    tvFooterRight.isGone = true
                } else {
                    tvFooterRight.isGone = false
                    tvFooterRight.batteryMode = BatteryView.BatteryMode.EMPTY
                }
            } else {
                tvFooterRight.isGone = false
                tvFooterRight.batteryMode = BatteryView.BatteryMode.NO_BATTERY
            }
        }
        val tipTypeface = style.tipTypeface ?: ChapterProvider.typeface
        val tipTextSize = style.tipTextSize
        tvTitle = getTipView(ReadTipType.tipChapterTitle)?.apply {
            tag = ReadTipType.tipChapterTitle
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvTitleArrow = getTipView(ReadTipType.tipChapterTitleArrow)?.apply {
            tag = ReadTipType.tipChapterTitleArrow
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ARROW
        }
        tvTitleArrowClassic = getTipView(ReadTipType.tipChapterTitleArrowClassic)?.apply {
            tag = ReadTipType.tipChapterTitleArrowClassic
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ARROW
        }
        tvTime = getTipView(ReadTipType.tipTime)?.apply {
            tag = ReadTipType.tipTime
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvBattery = getTipView(ReadTipType.tipBattery)?.apply {
            tag = ReadTipType.tipBattery
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.OUTER
        }
        tvBatteryClassic = getTipView(ReadTipType.tipBatteryClassic)?.apply {
            tag = ReadTipType.tipBatteryClassic
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.CLASSIC
        }
        tvBatteryInside = getTipView(ReadTipType.tipBatteryInside)?.apply {
            tag = ReadTipType.tipBatteryInside
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.INNER
        }
        tvBatteryIcon = getTipView(ReadTipType.tipBatteryIcon)?.apply {
            tag = ReadTipType.tipBatteryIcon
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ICON
        }
        tvPage = getTipView(ReadTipType.tipPage)?.apply {
            tag = ReadTipType.tipPage
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvTotalProgress = getTipView(ReadTipType.tipTotalProgress)?.apply {
            tag = ReadTipType.tipTotalProgress
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTotalProgress1 = getTipView(ReadTipType.tipTotalProgress1)?.apply {
            tag = ReadTipType.tipTotalProgress1
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvPageAndTotal = getTipView(ReadTipType.tipPageAndTotal)?.apply {
            tag = ReadTipType.tipPageAndTotal
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvWholeBookPage = getTipView(ReadTipType.tipWholeBookPage)?.apply {
            tag = ReadTipType.tipWholeBookPage
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvWholeBookPageAndProgress =
            getTipView(ReadTipType.tipWholeBookPageAndProgress)?.apply {
                tag = ReadTipType.tipWholeBookPageAndProgress
                batteryMode = BatteryView.BatteryMode.NO_BATTERY
                typeface = tipTypeface
                textSize = tipTextSize
            }
        tvBookName = getTipView(ReadTipType.tipBookName)?.apply {
            tag = ReadTipType.tipBookName
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTimeBattery = getTipView(ReadTipType.tipTimeBattery)?.apply {
            tag = ReadTipType.tipTimeBattery
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.TIME
        }
        tvTimeBatteryClassic = getTipView(ReadTipType.tipTimeBatteryClassic)?.apply {
            tag = ReadTipType.tipTimeBatteryClassic
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.CLASSIC
        }
        tvBatteryP = getTipView(ReadTipType.tipBatteryPercentage)?.apply {
            tag = ReadTipType.tipBatteryPercentage
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTimeBatteryP = getTipView(ReadTipType.tipTimeBatteryPercentage)?.apply {
            tag = ReadTipType.tipTimeBatteryPercentage
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        // 自定义页眉/页脚模板：6 个位置共享同一段配置逻辑
        val customTypeface = tipTypeface
        val customTextSize = tipTextSize
        for (target in CUSTOM_TIP_TARGETS) {
            if (target.currentTipValue() != ReadTipType.tipCustom) continue
            val view = customTipViewFor(target)
            view.tag = ReadTipType.tipCustom
            view.typeface = customTypeface
            view.textSize = customTextSize
            view.batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }

        // 当页脚不应用页眉字体样式时，覆盖页脚视图的字体和字号
        if (!style.applyHeaderStyle) {
            val footerTypeface = style.footerTypeface ?: tipTypeface
            val footerTextSize = style.footerTextSize
            listOf(tvFooterLeft, tvFooterMiddle, tvFooterRight).forEach { view ->
                if (view.tag != null) {
                    view.typeface = footerTypeface
                    view.textSize = footerTextSize
                }
            }
        }
    }

    /**
     * 根据 [target] 取得对应的页眉/页脚 [BatteryView] 实例。
     */
    private fun customTipViewFor(target: CustomTipTarget): BatteryView = binding.run {
        when (target) {
            CustomTipTarget.HEADER_LEFT -> tvHeaderLeft
            CustomTipTarget.HEADER_MIDDLE -> tvHeaderMiddle
            CustomTipTarget.HEADER_RIGHT -> tvHeaderRight
            CustomTipTarget.FOOTER_LEFT -> tvFooterLeft
            CustomTipTarget.FOOTER_MIDDLE -> tvFooterMiddle
            CustomTipTarget.FOOTER_RIGHT -> tvFooterRight
        }
    }

    /**
     * 获取信息视图
     * @param tip 信息类型
     */
    private fun getTipView(tip: Int): BatteryView? = binding.run {
        val style = TipStyleProvider.style
        return when (tip) {
            style.tipHeaderLeft -> tvHeaderLeft
            style.tipHeaderMiddle -> tvHeaderMiddle
            style.tipHeaderRight -> tvHeaderRight
            style.tipFooterLeft -> tvFooterLeft
            style.tipFooterMiddle -> tvFooterMiddle
            style.tipFooterRight -> tvFooterRight
            else -> null
        }
    }

    /**
     * 更新背景
     */
    fun upBg() {
        binding.vwRoot.background = LayerDrawable(
            arrayOf(
                ReadSessionState.backgroundMeanColor.toDrawable(),
                ReadSessionState.background
            )
        )
        upBgAlpha()
    }

    /**
     * 更新背景透明度
     */
    fun upBgAlpha() {
        ReadSessionState.background?.alpha = (TipStyleProvider.style.bgAlpha / 100f * 255).toInt()
        binding.vwRoot.invalidate()
    }

    /**
     * 更新时间信息
     */
    fun upTime() {
        tvTime?.text = timeFormat.format(Date(System.currentTimeMillis()))
        upTimeBattery()
        upCustomTip(currentTextPage)
    }

    /**
     * 更新电池信息
     */
    @SuppressLint("SetTextI18n")
    fun upBattery(battery: Int) {
        this.battery = battery
        tvBattery?.setBattery(battery)
        tvBatteryClassic?.setBattery(battery)
        tvBatteryInside?.setBattery(battery)
        tvBatteryIcon?.setBattery(battery)
        tvBatteryP?.text = "$battery%"
        upTimeBattery()
        upCustomTip(currentTextPage)
    }

    /**
     * 更新电池信息
     */
    @SuppressLint("SetTextI18n")
    private fun upTimeBattery() {
        val time = timeFormat.format(Date(System.currentTimeMillis()))
        tvTimeBattery?.setBattery(battery, time)
        tvTimeBatteryP?.setBattery(battery, time)
        tvTimeBatteryClassic?.setBattery(battery, time)
        tvTimeBatteryClassic?.text = "$time $battery%"
        tvTimeBatteryP?.text = "$time $battery%"
    }

    /**
     * 将所有启用自定义模板的页眉/页脚视图按当前 textPage 与最新时间/电池进行渲染。
     * 当位置未启用 tipCustom 或模板为空时不做任何处理。
     */
    fun upCustomTip(textPage: TextPage?) {
        val page = textPage ?: return
        for (target in CUSTOM_TIP_TARGETS) {
            if (target.currentTipValue() != ReadTipType.tipCustom) continue
            val template = target.currentCustomTemplate()
            val view = customTipViewFor(target)
            if (template.isEmpty()) {
                view.setTextIfNotEqual("")
            } else {
                view.setTextIfNotEqual(resolveCustomTemplate(template, page))
            }
        }
    }

    /**
     * 将 [template] 中的预定义占位符替换为当前页面、时间、电池等实际值。
     *
     * 仅会替换 [CustomTipPlaceholder] 中枚举的合法 token；未识别的 `{Xxx}` 段将被原样保留
     * （校验由 [CustomTipDialog] / [CustomTipPlaceholder.isValid] 在保存前完成）。
     */
    @SuppressLint("SetTextI18n")
    private fun resolveCustomTemplate(template: String, textPage: TextPage): String {
        if (template.isEmpty()) return ""
        val time = timeFormat.format(Date(System.currentTimeMillis()))
        val bookName = ReadBook.book?.name.orEmpty()
        val chapterTitle = textPage.title
        val chapterIndexDisplay = textPage.chapterIndex.plus(1).toString()
        val chapterSizeDisplay = textPage.chapterSize.toString()
        val pageIndexDisplay = textPage.index.plus(1).toString()
        val pageSizeDisplay = if (textPage.textChapter.isCompleted) {
            textPage.pageSize.toString()
        } else {
            val pageSizeInt = textPage.pageSize
            if (pageSizeInt <= 0) "-" else pageSizeInt.toString()
        }
        val pageRemainingDisplay = formatCustomTipPageRemaining(
            pageSize = textPage.pageSize,
            pageIndex = textPage.index,
            isChapterCompleted = textPage.textChapter.isCompleted,
        )
        val readProgressDisplay = textPage.readProgress
        val wholeBookPage = ReadBook.getWholeBookPageState(textPage.chapterIndex, textPage.index)
        val fullPageIndexDisplay = wholeBookPage?.currentPage?.toString() ?: pageIndexDisplay
        val fullPageSizeDisplay = wholeBookPage?.totalPages?.toString() ?: pageSizeDisplay
        // 使用本 PageView 实例持有的电池字段，值由 [upBattery] 在系统电量变化时持续刷新。
        val batteryPercentDisplay = "$battery%"
        var result = template
        for (placeholder in CUSTOM_TIP_PLACEHOLDERS) {
            val replacement = when (placeholder) {
                CustomTipPlaceholder.BOOK_NAME -> bookName
                CustomTipPlaceholder.CHAPTER_TITLE -> chapterTitle
                CustomTipPlaceholder.TIME -> time
                CustomTipPlaceholder.BATTERY_PERCENT -> batteryPercentDisplay
                CustomTipPlaceholder.CHAPTER_INDEX -> chapterIndexDisplay
                CustomTipPlaceholder.CHAPTER_SIZE -> chapterSizeDisplay
                CustomTipPlaceholder.PAGE_INDEX -> pageIndexDisplay
                CustomTipPlaceholder.PAGE_SIZE -> pageSizeDisplay
                CustomTipPlaceholder.PAGE_REMAINING -> pageRemainingDisplay
                CustomTipPlaceholder.READ_PROGRESS -> readProgressDisplay
                CustomTipPlaceholder.FULL_PAGE_INDEX -> fullPageIndexDisplay
                CustomTipPlaceholder.FULL_PAGE_SIZE -> fullPageSizeDisplay
            }
            result = result.replace(placeholder.token, replacement)
        }
        return result
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage, resetPageOffset: Boolean = true) {
        if (isMainView && !isScroll) {
            setProgress(textPage)
        } else {
            post {
                setProgress(textPage)
            }
        }
        if (resetPageOffset) {
            resetPageOffset()
        }
        binding.contentTextView.setContent(textPage)
    }

    fun invalidateContentView() {
        binding.contentTextView.invalidate()
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        binding.contentTextView.resetPageOffset()
    }

    /**
     * 设置进度
     */
    @SuppressLint("SetTextI18n")
    fun setProgress(textPage: TextPage) = textPage.apply {
        currentTextPage = textPage
        tvBookName?.setTextIfNotEqual(ReadBook.book?.name)
        tvTitle?.setTextIfNotEqual(textPage.title)
        tvTitleArrow?.setTextIfNotEqual(textPage.title)
        tvTitleArrowClassic?.setTextIfNotEqual(textPage.title)
        val readProgress = readProgress
        val wholeBookPage = ReadBook.getWholeBookPageState(chapterIndex, index)
        val pageDisplay = wholeBookPage?.let {
            if (!it.estimated && it.allPreviousChaptersExact) {
                context.getString(R.string.whole_book_page_format, it.currentPage, it.totalPages)
            } else {
                context.getString(
                    R.string.whole_book_page_estimated_format,
                    it.currentPage.toString(),
                    it.totalPages.toString(),
                )
            }
        }
        tvTotalProgress?.setTextIfNotEqual(readProgress)
        tvTotalProgress1?.setTextIfNotEqual("${chapterIndex.plus(1)}/${chapterSize}")
        if (textChapter.isCompleted) {
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSize  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSize")
        } else {
            val pageSizeInt = pageSize
            val pageSize = if (pageSizeInt <= 0) "-" else pageSizeInt.toString()
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSize  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSize")
        }
        val wholeBookPageDisplay = pageDisplay
            ?: context.getString(R.string.whole_book_page_unavailable)
        tvWholeBookPage?.setTextIfNotEqual(wholeBookPageDisplay)
        tvWholeBookPageAndProgress?.setTextIfNotEqual(
            "$wholeBookPageDisplay  $readProgress"
        )
        upCustomTip(textPage)
        upBookmarkBadge()
        this@PageView.layoutSync()
    }

    fun layoutSync() {
        if (width <= 0 || height <= 0) return
        if (!isLayoutRequested) return
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
    }

    fun screenshot(canvasRecorder: CanvasRecorder) {
        if (!isMainView && !isScroll) {
            setProgress(textPage)
        }
        viewScreenshot(canvasRecorder)
    }

    fun screenshot(bitmap: Bitmap? = null, canvas: Canvas? = null): Bitmap? {
        if (!isMainView && !isScroll) {
            setProgress(textPage)
        }
        return viewScreenshot(bitmap, canvas)
    }

    fun setAutoPager(autoPager: AutoPager?) {
        binding.contentTextView.setAutoPager(autoPager)
    }

    fun submitRenderTask() {
        binding.contentTextView.submitRenderTask()
    }

    fun setIsScroll(value: Boolean) {
        isScroll = value
        binding.contentTextView.setIsScroll(value)
        upBookmarkBadge()
    }

    /**
     * 滚动事件
     */
    fun scroll(offset: Int) {
        binding.contentTextView.scroll(offset)
    }

    /**
     * 更新是否开启选择功能
     */
    fun upSelectAble(selectAble: Boolean) {
        binding.contentTextView.selectAble = selectAble
    }

    /**
     * 优先处理页面内单击
     * @return true:已处理, false:未处理
     */
    fun onClick(x: Float, y: Float): Boolean {
        return binding.contentTextView.click(x, y - headerHeight)
    }

    /**
     * 长按事件
     */
    fun longPress(
        x: Float, y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        return binding.contentTextView.longPress(x, y - headerHeight, select)
    }

    /**
     * 选择文本
     */
    fun selectText(
        x: Float, y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        return binding.contentTextView.selectText(x, y - headerHeight, select)
    }

    fun getCurVisiblePage(): TextPage {
        return binding.contentTextView.getCurVisiblePage()
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        return binding.contentTextView.getReadAloudPos()
    }

    fun markAsMainView() {
        isMainView = true
        binding.contentTextView.isMainView = true
    }

    fun selectStartMove(x: Float, y: Float) {
        binding.contentTextView.selectStartMove(x, y - headerHeight)
    }

    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int
    ) {
        binding.contentTextView.selectStartMoveIndex(relativePagePos, lineIndex, charIndex)
    }

    fun selectStartMoveIndex(textPos: TextPos) {
        binding.contentTextView.selectStartMoveIndex(textPos)
    }

    fun selectEndMove(x: Float, y: Float) {
        binding.contentTextView.selectEndMove(x, y - headerHeight)
    }

    fun selectEndMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int
    ) {
        binding.contentTextView.selectEndMoveIndex(relativePagePos, lineIndex, charIndex)
    }

    fun selectEndMoveIndex(textPos: TextPos) {
        binding.contentTextView.selectEndMoveIndex(textPos)
    }

    fun getReverseStartCursor(): Boolean {
        return binding.contentTextView.reverseStartCursor
    }

    fun getReverseEndCursor(): Boolean {
        return binding.contentTextView.reverseEndCursor
    }

    fun isLongScreenShot(): Boolean {
        return binding.contentTextView.longScreenshot
    }

    fun resetReverseCursor() {
        binding.contentTextView.resetReverseCursor()
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        binding.contentTextView.cancelSelect(clearSearchResult)
    }

    fun createBookmark(): Bookmark? {
        return binding.contentTextView.createBookmark()
    }

    fun relativePage(relativePagePos: Int): TextPage {
        return binding.contentTextView.relativePage(relativePagePos)
    }

    val textPage get() = binding.contentTextView.textPage

    val selectedText: String get() = binding.contentTextView.getSelectedText()

    val selectStartPos get() = binding.contentTextView.selectStart

    val selectEndPos get() = binding.contentTextView.selectEndPosition

    companion object {
        /**
         * 缓存枚举数组，避免每次调用 `values()` 触发 Kotlin 内部数组克隆。
         * 模板渲染在 `upCustomTip` / `upTime` / `upBattery` 等热路径上会反复触发。
         */
        private val CUSTOM_TIP_TARGETS: Array<CustomTipTarget> = CustomTipTarget.values()
        private val CUSTOM_TIP_PLACEHOLDERS: Array<CustomTipPlaceholder> = CustomTipPlaceholder.values()
    }
}

/**
 * 渲染层按位置取当前 tip 类型 / 自定义模板，读的是 [TipStyleProvider] 快照。
 *
 * 这两个函数在 `upCustomTip` / `upTime` / `upBattery` 热路径上反复触发，过去每次都直读
 * 可变全局 `ReadBookConfig`。设置面读的是 `ReadSheetConfigUiState`
 * （`CustomTipTarget.tipValueOf` / `customTemplateOf`），两侧互不影响。
 */
private fun CustomTipTarget.currentTipValue(): Int =
    TipStyleProvider.style.tipValueOf(this)

private fun CustomTipTarget.currentCustomTemplate(): String =
    TipStyleProvider.style.customTemplateOf(this)
