package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import io.legado.app.R
import io.legado.app.constant.AppConst.timeFormat
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ViewBookPageBinding
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadSessionState
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.sheet.CustomTipTarget
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.BatteryView
import androidx.core.view.updateLayoutParams
import io.legado.app.utils.activity
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.canvasrecorder.CanvasRecorder
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.statusBarHeight
import splitties.views.backgroundColor
import java.io.File
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

    init {
        callBack?.let { binding.contentTextView.setCallBack(it) }
        upStyle()
        binding.vwStatusBar.applyStatusBarPadding()
        if (ReadSessionState.lastNavigationBarHeight > 0) {
            binding.vwNavigationBar.updateLayoutParams {
                height = ReadSessionState.lastNavigationBarHeight
            }
        }
        binding.vwNavigationBar.setOnApplyWindowInsetsListenerCompat { v, windowInsets ->
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            if (isImeVisible) {
                return@setOnApplyWindowInsetsListenerCompat windowInsets
            }
            //Log.d("fansangg", "vwNavigationBar OnApplyWindowInsetsListener: navHeight=$navHeight, isImeVisible=${windowInsets.isVisible(WindowInsetsCompat.Type.ime())}, imeHeight=${windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom}, systemBarsHeight=${windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom}")
            val navHeight = windowInsets.navigationBarHeight
            if (navHeight > 0) {
                ReadSessionState.lastNavigationBarHeight = navHeight
            }
            v.updateLayoutParams {
                height = navHeight
            }
            windowInsets
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        upBg()
    }

    fun upStyle() = binding.run {
        upTipStyle()
        ReadBookConfig.let {
            val textColor = it.textColor
            val headerColor = with(ReadBookConfig) {
                if (resolvedTipHeaderColor == 0) textColor else resolvedTipHeaderColor
            }
            val footerColor = with(ReadBookConfig) {
                if (resolvedTipFooterColor == 0) textColor else resolvedTipFooterColor
            }
            val tipDividerColor = with(ReadBookConfig) {
                when (tipDividerColor) {
                    -1 -> ContextCompat.getColor(context, R.color.divider)
                    0 -> textColor
                    else -> tipDividerColor
                }
            }
            tvHeaderLeft.setColor(headerColor)
            tvHeaderMiddle.setColor(headerColor)
            tvHeaderRight.setColor(headerColor)
            tvFooterLeft.setColor(footerColor)
            tvFooterMiddle.setColor(footerColor)
            tvFooterRight.setColor(footerColor)
            vwTopDivider.backgroundColor = tipDividerColor
            vwBottomDivider.backgroundColor = tipDividerColor
            upStatusBar()
            upNavigationBar()
            upPaddingDisplayCutouts()
            llHeader.setPadding(
                it.headerPaddingLeft.dpToPx(),
                it.headerPaddingTop.dpToPx(),
                it.headerPaddingRight.dpToPx(),
                it.headerPaddingBottom.dpToPx()
            )
            llFooter.setPadding(
                it.footerPaddingLeft.dpToPx(),
                it.footerPaddingTop.dpToPx(),
                it.footerPaddingRight.dpToPx(),
                it.footerPaddingBottom.dpToPx()
            )
            vwTopDivider.gone(llHeader.isGone || !it.showHeaderLine)
            vwBottomDivider.gone(llFooter.isGone || !it.showFooterLine)
        }
        upTime()
        upBattery(battery)
    }

    /** 仅更新日夜模式相关颜色，避免主题切换时重新加载字体和重排正文。 */
    fun upThemeColors() = binding.run {
        val textColor = ReadBookConfig.textColor
        val headerColor = with(ReadBookConfig) {
            if (resolvedTipHeaderColor == 0) textColor else resolvedTipHeaderColor
        }
        val footerColor = with(ReadBookConfig) {
            if (resolvedTipFooterColor == 0) textColor else resolvedTipFooterColor
        }
        val tipDividerColor = with(ReadBookConfig) {
            when (tipDividerColor) {
                -1 -> ContextCompat.getColor(context, R.color.divider)
                0 -> textColor
                else -> tipDividerColor
            }
        }
        tvHeaderLeft.setColor(headerColor)
        tvHeaderMiddle.setColor(headerColor)
        tvHeaderRight.setColor(headerColor)
        tvFooterLeft.setColor(footerColor)
        tvFooterMiddle.setColor(footerColor)
        tvFooterRight.setColor(footerColor)
        vwTopDivider.backgroundColor = tipDividerColor
        vwBottomDivider.backgroundColor = tipDividerColor
        contentTextView.invalidate()
    }

    private fun loadTypeface(fontPath: String): Typeface? {
        return runCatching {
            when {
                fontPath.isContentScheme() -> {
                    context.contentResolver
                        .openFileDescriptor(fontPath.toUri(), "r")
                        ?.use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }
                fontPath.isNotEmpty() -> {
                    Typeface.Builder(File(fontPath)).build()
                }
                else -> null
            }
        }.getOrNull()
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() = with(binding.vwStatusBar) {
        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
        isGone = ReadBookConfig.hideStatusBar || activity?.isInMultiWindowMode == true
    }

    fun upNavigationBar() {
        binding.vwNavigationBar.isGone = ReadBookConfig.hideNavigationBar
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
    private fun upTipStyle() = binding.run {
        tvHeaderLeft.tag = null
        tvHeaderMiddle.tag = null
        tvHeaderRight.tag = null
        tvFooterLeft.tag = null
        tvFooterMiddle.tag = null
        tvFooterRight.tag = null
        llHeader.isGone = when (ReadBookConfig.headerMode) {
            1 -> false
            2 -> true
            else -> !ReadBookConfig.hideStatusBar
        }
        llFooter.isGone = when (ReadBookConfig.footerMode) {
            1 -> true
            else -> false
        }
        ReadBookConfig.apply {
            tvHeaderLeft.isGone = tipHeaderLeft == tipNone
            tvHeaderMiddle.isGone = tipHeaderMiddle == tipNone
            if (tipHeaderRight == tipNone) {
                if (tipHeaderMiddle == tipNone && tipHeaderLeft == tipNone) {
                    tvHeaderRight.isGone = true
                } else {
                    tvHeaderRight.isGone = false
                    tvHeaderRight.batteryMode = BatteryView.BatteryMode.EMPTY
                }
            } else {
                tvHeaderRight.isGone = false
                tvHeaderRight.batteryMode = BatteryView.BatteryMode.NO_BATTERY
            }
            tvFooterLeft.isGone = tipFooterLeft == tipNone
            tvFooterMiddle.isGone = tipFooterMiddle == tipNone
            if (tipFooterRight == tipNone) {
                if (tipFooterLeft == tipNone && tipFooterMiddle == tipNone) {
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
        val tipTypeface = loadTypeface(ReadBookConfig.headerFont) ?: ChapterProvider.typeface
        val tipTextSize = ReadBookConfig.headerFontSize.toFloat()
        tvTitle = getTipView(ReadBookConfig.tipChapterTitle)?.apply {
            tag = ReadBookConfig.tipChapterTitle
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvTitleArrow = getTipView(ReadBookConfig.tipChapterTitleArrow)?.apply {
            tag = ReadBookConfig.tipChapterTitleArrow
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ARROW
        }
        tvTitleArrowClassic = getTipView(ReadBookConfig.tipChapterTitleArrowClassic)?.apply {
            tag = ReadBookConfig.tipChapterTitleArrowClassic
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ARROW
        }
        tvTime = getTipView(ReadBookConfig.tipTime)?.apply {
            tag = ReadBookConfig.tipTime
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvBattery = getTipView(ReadBookConfig.tipBattery)?.apply {
            tag = ReadBookConfig.tipBattery
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.OUTER
        }
        tvBatteryClassic = getTipView(ReadBookConfig.tipBatteryClassic)?.apply {
            tag = ReadBookConfig.tipBatteryClassic
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.CLASSIC
        }
        tvBatteryInside = getTipView(ReadBookConfig.tipBatteryInside)?.apply {
            tag = ReadBookConfig.tipBatteryInside
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.INNER
        }
        tvBatteryIcon = getTipView(ReadBookConfig.tipBatteryIcon)?.apply {
            tag = ReadBookConfig.tipBatteryIcon
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.ICON
        }
        tvPage = getTipView(ReadBookConfig.tipPage)?.apply {
            tag = ReadBookConfig.tipPage
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
        }
        tvTotalProgress = getTipView(ReadBookConfig.tipTotalProgress)?.apply {
            tag = ReadBookConfig.tipTotalProgress
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTotalProgress1 = getTipView(ReadBookConfig.tipTotalProgress1)?.apply {
            tag = ReadBookConfig.tipTotalProgress1
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvPageAndTotal = getTipView(ReadBookConfig.tipPageAndTotal)?.apply {
            tag = ReadBookConfig.tipPageAndTotal
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvBookName = getTipView(ReadBookConfig.tipBookName)?.apply {
            tag = ReadBookConfig.tipBookName
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTimeBattery = getTipView(ReadBookConfig.tipTimeBattery)?.apply {
            tag = ReadBookConfig.tipTimeBattery
            typeface = Typeface.DEFAULT
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.TIME
        }
        tvTimeBatteryClassic = getTipView(ReadBookConfig.tipTimeBatteryClassic)?.apply {
            tag = ReadBookConfig.tipTimeBatteryClassic
            typeface = tipTypeface
            textSize = tipTextSize
            batteryMode = BatteryView.BatteryMode.CLASSIC
        }
        tvBatteryP = getTipView(ReadBookConfig.tipBatteryPercentage)?.apply {
            tag = ReadBookConfig.tipBatteryPercentage
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        tvTimeBatteryP = getTipView(ReadBookConfig.tipTimeBatteryPercentage)?.apply {
            tag = ReadBookConfig.tipTimeBatteryPercentage
            batteryMode = BatteryView.BatteryMode.NO_BATTERY
            typeface = tipTypeface
            textSize = tipTextSize
        }
        // 自定义页眉/页脚模板：6 个位置共享同一段配置逻辑
        val customTypeface = tipTypeface
        val customTextSize = tipTextSize
        for (target in CUSTOM_TIP_TARGETS) {
            if (target.tipValue != ReadBookConfig.tipCustom) continue
            val view = customTipViewFor(target)
            view.tag = ReadBookConfig.tipCustom
            view.typeface = customTypeface
            view.textSize = customTextSize
            view.batteryMode = BatteryView.BatteryMode.NO_BATTERY
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
        return when (tip) {
            ReadBookConfig.tipHeaderLeft -> tvHeaderLeft
            ReadBookConfig.tipHeaderMiddle -> tvHeaderMiddle
            ReadBookConfig.tipHeaderRight -> tvHeaderRight
            ReadBookConfig.tipFooterLeft -> tvFooterLeft
            ReadBookConfig.tipFooterMiddle -> tvFooterMiddle
            ReadBookConfig.tipFooterRight -> tvFooterRight
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
        ReadSessionState.background?.alpha = (ReadBookConfig.bgAlpha / 100f * 255).toInt()
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
            if (target.tipValue != ReadBookConfig.tipCustom) continue
            val template = target.customTemplate
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
            if (pageSizeInt <= 0) "-" else "~$pageSizeInt"
        }
        val readProgressDisplay = textPage.readProgress
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
                CustomTipPlaceholder.READ_PROGRESS -> readProgressDisplay
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
        tvTotalProgress?.setTextIfNotEqual(readProgress)
        tvTotalProgress1?.setTextIfNotEqual("${chapterIndex.plus(1)}/${chapterSize}")
        if (textChapter.isCompleted) {
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSize  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSize")
        } else {
            val pageSizeInt = pageSize
            val pageSize = if (pageSizeInt <= 0) "-" else "~$pageSizeInt"
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSize  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSize")
        }
        upCustomTip(textPage)
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

    companion object {
        /**
         * 缓存枚举数组，避免每次调用 `values()` 触发 Kotlin 内部数组克隆。
         * 模板渲染在 `upCustomTip` / `upTime` / `upBattery` 等热路径上会反复触发。
         */
        private val CUSTOM_TIP_TARGETS: Array<CustomTipTarget> = CustomTipTarget.values()
        private val CUSTOM_TIP_PLACEHOLDERS: Array<CustomTipPlaceholder> = CustomTipPlaceholder.values()
    }
}
