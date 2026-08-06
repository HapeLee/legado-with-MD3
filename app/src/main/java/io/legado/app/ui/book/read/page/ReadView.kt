package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.model.ReadSessionState

import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.delegate.CoverPageDelegate
import io.legado.app.ui.book.read.page.delegate.FadePageDelegate
import io.legado.app.ui.book.read.page.delegate.HorizontalPageDelegate
import io.legado.app.ui.book.read.page.delegate.NoAnimPageDelegate
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.delegate.SimulationPageDelegate
import io.legado.app.ui.book.read.page.delegate.SlidePageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TipStyleProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.invisible
import io.legado.app.utils.statusBarHeight

import io.legado.app.utils.throttle
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

/**
 * 阅读视图
 */
class ReadView(
    context: Context,
    attrs: AttributeSet? = null,
    private val callBack: CallBack,
    contentCallBack: ContentTextView.CallBack? = null,
    private val eventListener: ReaderEventListener,
    private val pageSource: ReaderPageSource,
) :
    FrameLayout(context, attrs),
    DataSource, LayoutProgressListener {

    var pageFactory: TextPageFactory = TextPageFactory(this, pageSource)
    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
            upContent()
        }
    override var isScroll = false
    val prevPage by lazy { PageView(context, contentCallBack) }
    val curPage by lazy { PageView(context, contentCallBack) }
    val nextPage by lazy { PageView(context, contentCallBack) }
    val defaultAnimationSpeed = 300
    private var pressDown = false
    private var isMove = false

    //起始点
    var startX: Float = 0f
    var startY: Float = 0f

    //上一个触碰点
    var lastX: Float = 0f
    var lastY: Float = 0f

    //触碰点
    var touchX: Float = 0f
    var touchY: Float = 0f

    //是否停止动画动作
    var isAbortAnim = false

    //长按
    private var longPressed = false
    private val longPressTimeout = 600L
    private val longPressRunnable = Runnable {
        longPressed = true
        onLongPress()
    }
    var isTextSelected = false
    private var pressOnTextSelected = false
    private val initialTextPos = TextPos(0, 0, 0)

    /**
     * 本次手势**仍可能**是「下滑切换书签」。
     *
     * 只是候选而非认领：每帧 MOVE 都按当前位移重新判定，因此中途转为横向占优的斜划会
     * 交还 [pageDelegate] 正常翻页，不会被吞掉。候选期间不喂 [pageDelegate]，
     * 否则横向委托会同时启动翻页动画。
     */
    private var isBookmarkSwipe = false

    /**
     * 手势已交还 [pageDelegate]，本次触摸不再收回。
     *
     * 交还那一刻委托就可能起了翻页动画，中途再抢回来会留下一个停在半路的动画。
     */
    private var bookmarkSwipeReleased = false
    private val bookmarkSwipeMinDistance by lazy { BOOKMARK_SWIPE_MIN_DISTANCE_DP.dpToPx() }

    /**
     * 本次触摸是否启用下滑书签手势。在 DOWN 时读一次开关（DataStore 快照重建较贵，
     * 不宜放进逐帧的 [isBookmarkSwipeCandidate]），改设置在下次触摸生效。
     */
    private var bookmarkSwipeEnabled = false

    /**
     * 顶部「松手加入书签」提示条：下滑距离达标后出现，松手即执行切换。
     */
    private val bookmarkSwipeHint = TextView(context).apply {
        text = context.getString(R.string.bookmark_swipe_release_to_add)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 14f
        includeFontPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            cornerRadius = BOOKMARK_SWIPE_HINT_CORNER_RADIUS_DP.dpToPx()
            setColor(Color.argb(160, 0, 0, 0))
        }
        setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            topMargin = BOOKMARK_SWIPE_HINT_TOP_MARGIN_DP.dpToPx()
        }
        visibility = View.GONE
    }

    private val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private var pageSlopSquare: Int = slopSquare
    var pageSlopSquare2: Int = pageSlopSquare * pageSlopSquare
    private val tlRect = RectF()
    private val tcRect = RectF()
    private val trRect = RectF()
    private val mlRect = RectF()
    private val mcRect = RectF()
    private val mrRect = RectF()
    private val blRect = RectF()
    private val bcRect = RectF()
    private val brRect = RectF()
    private val boundary by lazy { BreakIterator.getWordInstance(Locale.getDefault()) }
    private val upProgressThrottle = throttle(200) { post { upProgress() } }
    val autoPager = AutoPager(this)
    val isAutoPage get() = autoPager.isRunning
    private var accessibilityPageText = ""

    init {
        if (!isInEditMode) {
            // 重开阅读器的唯一快照重建入口：ReadConfigUpdateBus 无 replay，上一会话关闭后改的
            // 配置（如设置页的「隐藏状态栏」）没有任何 effect 会补发；PageView 构造期就要读
            // TipStyleProvider.style，所以必须在 addView 之前重建。两份快照纯派生、幂等。
            ChapterProvider.upRenderStyle()
            TipStyleProvider.upTipStyle()
        }
        addView(nextPage)
        addView(curPage)
        addView(prevPage)
        addView(bookmarkSwipeHint)
        // 三个 PageView 只负责把同一页绘制到 Canvas；它们的内部 View 没有正文语义。
        // 读屏应只命中下面由 ReadView 提供的当前页文本节点。
        listOf(prevPage, curPage, nextPage).forEach {
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusable = true
        ViewCompat.setScreenReaderFocusable(this, true)
        prevPage.invisible()
        nextPage.invisible()
        curPage.markAsMainView()
        if (!isInEditMode) {
            upBg()
            setWillNotDraw(false)
            upPageAnim()
            upPageSlopSquare()
        }
    }

    private fun setRect9x() {
        tlRect.set(0f, 0f, width * 0.33f, height * 0.33f)
        tcRect.set(width * 0.33f, 0f, width * 0.66f, height * 0.33f)
        trRect.set(width * 0.36f, 0f, width.toFloat(), height * 0.33f)
        mlRect.set(0f, height * 0.33f, width * 0.33f, height * 0.66f)
        mcRect.set(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
        mrRect.set(width * 0.66f, height * 0.33f, width.toFloat(), height * 0.66f)
        blRect.set(0f, height * 0.66f, width * 0.33f, height.toFloat())
        bcRect.set(width * 0.33f, height * 0.66f, width * 0.66f, height.toFloat())
        brRect.set(width * 0.66f, height * 0.66f, width.toFloat(), height.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setRect9x()
        prevPage.x = -w.toFloat()
        pageDelegate?.setViewSize(w, h)
        if (w > 0 && h > 0) {
            upBg()
            callBack.upSystemUiVisibility()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        pageDelegate?.onDraw(canvas)
        autoPager.onDraw(canvas)
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager.computeOffset()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    // Canvas 正文没有子级语义，触摸探索必须命中本页级节点。
    override fun onInterceptHoverEvent(event: MotionEvent): Boolean = true

    /**
     * 触摸事件
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = this.rootWindowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.mandatorySystemGestures()
            )
            val height = activity?.windowManager?.currentWindowMetrics?.bounds?.height()
            if (height != null) {
                if (event.y > height.minus(insets.bottom)
                    && event.action != MotionEvent.ACTION_UP
                    && event.action != MotionEvent.ACTION_CANCEL
                ) {
                    return true
                }
            }
        }

        //在多点触控时，事件不走ACTION_DOWN分支而产生的特殊事件处理
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            pageDelegate?.onTouch(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                callBack.screenOffTimerStart()
                if (isTextSelected) {
                    curPage.cancelSelect()
                    isTextSelected = false
                    pressOnTextSelected = true
                } else {
                    pressOnTextSelected = false
                }
                longPressed = false
                postDelayed(longPressRunnable, longPressTimeout)
                pressDown = true
                isMove = false
                bookmarkSwipeEnabled = ReadConfig.swipeToAddBookmark
                resetBookmarkSwipe()
                pageDelegate?.onTouch(event)
                pageDelegate?.onDown()
                setStartPoint(event.x, event.y, false)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!pressDown) return true
                val absX = abs(startX - event.x)
                val absY = abs(startY - event.y)
                if (!isMove) {
                    isMove = absX > slopSquare || absY > slopSquare
                }
                if (isMove) {
                    longPressed = false
                    removeCallbacks(longPressRunnable)
                    // 每帧按当前累计位移重新判定，方向翻转时才能交还 pageDelegate。
                    if (!bookmarkSwipeReleased) {
                        isBookmarkSwipe = isBookmarkSwipeCandidate(event.y - startY, absX, absY)
                        if (!isBookmarkSwipe) {
                            bookmarkSwipeReleased = true
                            // 交还 pageDelegate 前把页面拉回原位，避免委托拿到被移位的 curPage。
                            endBookmarkSwipeDrag()
                        } else {
                            // 页面随手指下移；到位后顶部亮起「松手加入书签」。
                            updateBookmarkSwipeDrag(event.y - startY)
                        }
                    }
                    if (isTextSelected) {
                        selectText(event.x, event.y)
                    } else if (!isBookmarkSwipe) {
                        pageDelegate?.onTouch(event)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                callBack.screenOffTimerStart()
                removeCallbacks(longPressRunnable)
                if (!pressDown) return true
                pressDown = false
                if (pageDelegate?.isMoved == false && !isMove) {
                    if (!longPressed && !pressOnTextSelected) {
                        performClick()
                        val handled = curPage.onClick(startX, startY)
                        if (!handled) {
                            onSingleTapUp()
                        }
                        return true
                    }
                }
                if (isBookmarkSwipe) {
                    // 以抬手时的最终位移复核：MOVE 之后手指还可能拐弯或回抽。
                    val dy = event.y - startY
                    if (dy >= bookmarkSwipeMinDistance &&
                        isBookmarkSwipeCandidate(dy, abs(startX - event.x), abs(dy))
                    ) {
                        eventListener.onEvent(ReaderEvent.ToggleBookmark)
                    }
                    resetBookmarkSwipe()
                } else if (isTextSelected) {
                    callBack.showTextActionMenu()
                } else if (pageDelegate!!.isMoved) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (!pressDown) return true
                pressDown = false
                resetBookmarkSwipe()
                if (isTextSelected) {
                    callBack.showTextActionMenu()
                } else if (pageDelegate!!.isMoved) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
                autoPager.resume()
            }
        }
        return true
    }

    /**
     * 当前位移是否仍算「下滑切换书签」：向下、且竖直位移压过水平位移。
     *
     * @param dy 相对按下点的竖直位移，向下为正
     */
    private fun isBookmarkSwipeCandidate(dy: Float, absX: Float, absY: Float): Boolean =
        bookmarkSwipeEnabled && !isScroll && !isTextSelected &&
            dy > 0 && absY > absX * VERTICAL_DOMINANCE_RATIO

    /**
     * 手势结束（抬手/取消）：页面弹回原位、收起提示条。翻页委托不受影响。
     */
    private fun resetBookmarkSwipe() {
        isBookmarkSwipe = false
        bookmarkSwipeReleased = false
        hideBookmarkSwipeHint()
        curPage.animate().cancel()
        curPage.animate()
            .translationY(0f)
            .setDuration(BOOKMARK_SWIPE_SPRING_BACK_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * 手势已交还 [pageDelegate]（中途转为横向翻页）：立即把页面拉回原位，
     * 不起动画，避免与委托的翻页动画互相干扰。
     */
    private fun endBookmarkSwipeDrag() {
        hideBookmarkSwipeHint()
        curPage.animate().cancel()
        curPage.translationY = 0f
    }

    /**
     * 拖动跟随：页面随手指下移（封顶），达到最小距离后顶部亮起提示条。
     *
     * @param dy 相对按下点的竖直位移，向下为正
     */
    private fun updateBookmarkSwipeDrag(dy: Float) {
        curPage.translationY = dy.coerceIn(0f, height.toFloat() * BOOKMARK_SWIPE_MAX_PULL_RATIO)
        if (dy >= bookmarkSwipeMinDistance) {
            showBookmarkSwipeHint()
        } else {
            hideBookmarkSwipeHint()
        }
    }

    private fun showBookmarkSwipeHint() {
        if (bookmarkSwipeHint.visibility != View.VISIBLE) {
            // 提示条是 ReadView 的直接子 View，edge-to-edge 下会被状态栏盖住；
            // 每次显示前按当前系统栏顶部 inset 垫高，状态栏隐藏（inset 为 0）时自然回落顶部。
            bookmarkSwipeHint.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bookmarkSwipeHintTopInset() + BOOKMARK_SWIPE_HINT_TOP_MARGIN_DP.dpToPx()
            }
            bookmarkSwipeHint.visibility = View.VISIBLE
        }
    }

    private fun bookmarkSwipeHintTopInset(): Int =
        ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.top
            ?: context.statusBarHeight

    private fun hideBookmarkSwipeHint() {
        if (bookmarkSwipeHint.visibility != View.GONE) {
            bookmarkSwipeHint.visibility = View.GONE
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // The Canvas-rendered page is exposed as one stable text node.
        info.className = TextView::class.java.name
        info.text = accessibilityPageText
        info.isScrollable = true
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                context.getString(R.string.prev_page)
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                context.getString(R.string.next_page)
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                context.getString(R.string.menu)
            )
        )
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            event.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
                true
            }

            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
                true
            }

            AccessibilityNodeInfo.ACTION_CLICK -> {
                performClick()
                pageDelegate?.dismissSnackBar()
                eventListener.onEvent(ReaderEvent.ShowActionMenu)
                true
            }

            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        if (isTextSelected || clearSearchResult) {
            curPage.cancelSelect(clearSearchResult)
            isTextSelected = false
        }
    }

    /**
     * 更新状态栏
     */
    fun upStatusBar() {
        curPage.upStatusBar()
        prevPage.upStatusBar()
        nextPage.upStatusBar()
    }

    /**
     * 保存开始位置
     */
    fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y

        if (invalidate) {
            invalidate()
        }
    }

    /**
     * 保存当前位置
     */
    fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidate) {
            invalidate()
        }
        pageDelegate?.onScroll()
    }

    /**
     * 长按选择
     */
    private fun onLongPress() {
        kotlin.runCatching {
            curPage.longPress(startX, startY) { textPos: TextPos ->
                isTextSelected = true
                pressOnTextSelected = true
                initialTextPos.upData(textPos)
                val startPos = textPos.copy()
                val endPos = textPos.copy()
                val page = curPage.relativePage(textPos.relativePagePos)
                val stringBuilder = StringBuilder()
                var cIndex = textPos.columnIndex
                var lineStart = textPos.lineIndex
                var lineEnd = textPos.lineIndex
                for (index in textPos.lineIndex - 1 downTo 0) {
                    val textLine = page.getLine(index)
                    if (textLine.isParagraphEnd) {
                        break
                    } else {
                        stringBuilder.insert(0, textLine.text)
                        lineStart -= 1
                        cIndex += textLine.charSize
                    }
                }
                for (index in textPos.lineIndex until page.lineSize) {
                    val textLine = page.getLine(index)
                    stringBuilder.append(textLine.text)
                    lineEnd += 1
                    if (textLine.isParagraphEnd) {
                        break
                    }
                }
                var start: Int
                var end: Int
                boundary.setText(stringBuilder.toString())
                start = boundary.first()
                end = boundary.next()
                while (end != BreakIterator.DONE) {
                    if (cIndex in start until end) {
                        break
                    }
                    start = end
                    end = boundary.next()
                }
                kotlin.run {
                    var ci = 0
                    for (index in lineStart..lineEnd) {
                        val textLine = page.getLine(index)
                        for (j in textLine.columns.indices) {
                            if (ci == start) {
                                startPos.lineIndex = index
                                startPos.columnIndex = j
                            } else if (ci == end - 1) {
                                endPos.lineIndex = index
                                endPos.columnIndex = j
                                return@run
                            }
                            val column = textLine.getColumn(j)
                            if (column is TextBaseColumn) {
                                ci += column.charData.length
                            } else {
                                ci++
                            }
                        }
                    }
                }
                curPage.selectStartMoveIndex(startPos)
                curPage.selectEndMoveIndex(endPos)
            }
        }
    }

    /**
     * 单击
     */
    private fun onSingleTapUp() {
        when {
            isTextSelected -> Unit
            mcRect.contains(startX, startY) -> if (!isAbortAnim) {
                click(ReadConfig.clickActionMC)
            }

            bcRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionBC)
            }

            blRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionBL)
            }

            brRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionBR)
            }

            mlRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionML)
            }

            mrRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionMR)
            }

            tlRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionTL)
            }

            tcRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionTC)
            }

            trRect.contains(startX, startY) -> {
                click(ReadConfig.clickActionTR)
            }
        }
    }

    /**
     * 点击
     */
    private fun click(action: Int) {
        when (action) {
            0 -> {
                pageDelegate?.dismissSnackBar()
                eventListener.onEvent(ReaderEvent.ShowActionMenu)
            }

            1 -> pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
            2 -> pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
            3 -> eventListener.onEvent(ReaderEvent.NextChapter)
            4 -> eventListener.onEvent(ReaderEvent.PrevChapter)
            5 -> eventListener.onEvent(ReaderEvent.ReadAloudPrevParagraph)
            6 -> eventListener.onEvent(ReaderEvent.ReadAloudNextParagraph)
            7 -> eventListener.onEvent(ReaderEvent.AddBookmark)
            8 -> eventListener.onEvent(ReaderEvent.OpenContentEdit)
            9 -> eventListener.onEvent(ReaderEvent.ChangeReplaceRuleState)
            10 -> eventListener.onEvent(ReaderEvent.OpenChapterList)
            11 -> eventListener.onEvent(ReaderEvent.OpenSearch)
            12 -> eventListener.onEvent(ReaderEvent.SyncProgress)
            13 -> eventListener.onEvent(ReaderEvent.ToggleReadAloudPause)
        }
    }

    /**
     * 选择文本
     */
    private fun selectText(x: Float, y: Float) {
        curPage.selectText(x, y) { textPos ->
            val compare = initialTextPos.compare(textPos)
            when {
                compare > 0 -> {
                    curPage.selectStartMoveIndex(textPos)
                    curPage.selectEndMoveIndex(
                        initialTextPos.relativePagePos,
                        initialTextPos.lineIndex,
                        initialTextPos.columnIndex - 1
                    )
                }

                else -> {
                    curPage.selectStartMoveIndex(initialTextPos)
                    curPage.selectEndMoveIndex(textPos)
                }
            }
        }
    }

    /**
     * 销毁事件
     */
    fun onDestroy() {
        pageDelegate?.onDestroy()
        curPage.cancelSelect()
        invalidateTextPage()
    }

    /**
     * 翻页动画完成后事件
     * @param direction 翻页方向
     */
    fun fillPage(direction: PageDirection): Boolean {
        return when (direction) {
            PageDirection.PREV -> {
                pageFactory.moveToPrev(true)
            }

            PageDirection.NEXT -> {
                pageFactory.moveToNext(true)
            }

            else -> false
        }
    }

    /**
     * 更新翻页动画
     */
    fun upPageAnim(upRecorder: Boolean = false) {
        isScroll = pageSource.pageAnim == 3
        ChapterProvider.upLayout()
        when (pageSource.pageAnim) {
            PageAnim.coverPageAnim -> if (pageDelegate !is CoverPageDelegate) {
                pageDelegate = CoverPageDelegate(this)
            }

            PageAnim.slidePageAnim -> if (pageDelegate !is SlidePageDelegate) {
                pageDelegate = SlidePageDelegate(this)
            }

            PageAnim.simulationPageAnim -> if (pageDelegate !is SimulationPageDelegate) {
                pageDelegate = SimulationPageDelegate(this)
            }

            PageAnim.scrollPageAnim -> if (pageDelegate !is ScrollPageDelegate) {
                pageDelegate = ScrollPageDelegate(this)
            }

            PageAnim.fadePageAnim -> if (pageDelegate !is FadePageDelegate) {
                pageDelegate = FadePageDelegate(this)
            }

            else -> if (pageDelegate !is NoAnimPageDelegate) {
                pageDelegate = NoAnimPageDelegate(this)
            }
        }
        (pageDelegate as? ScrollPageDelegate)?.noAnim = ReadConfig.noAnimScrollPage
        if (upRecorder) {
            (pageDelegate as? HorizontalPageDelegate)?.upRecorder()
            autoPager.upRecorder()
        }
        pageDelegate?.setViewSize(width, height)
        if (isScroll) {
            curPage.setAutoPager(autoPager)
        } else {
            curPage.setAutoPager(null)
        }
        curPage.setIsScroll(isScroll)
    }

    /**
     * 更新阅读内容
     * @param relativePosition 相对位置 -1 上一页 0 当前页 1 下一页
     * @param resetPageOffset 滚动阅读是是否重置位置
     */
    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        post {
            val text = pageFactory.curPage.text
            updateAccessibilityPageText(text)
            if (BuildConfig.DEBUG) {
                // Device-independent readiness/page-change signal for the debug
                // scenario runner (uiautomator does not expose ReadView on all
                // devices). See tools/android/runner.py.
                Log.i("LegadoDebug", "READER_PAGE " + text.replace("\n", " "))
                if (text == context.getString(R.string.data_loading)) {
                    // Locale-independent signal for the placeholder frame; the
                    // scenario runner counts these. See tools/android/runner.py.
                    Log.i("LegadoDebug", "READER_PAGE_PLACEHOLDER_FRAME")
                }
            }
        }
        if (isScroll && !isAutoPage) {
            if (relativePosition == 0) {
                curPage.setContent(pageFactory.curPage, resetPageOffset)
            } else {
                curPage.invalidateContentView()
            }
        } else {
            when (relativePosition) {
                -1 -> prevPage.setContent(pageFactory.prevPage)
                1 -> nextPage.setContent(pageFactory.nextPage)
                else -> {
                    curPage.setContent(pageFactory.curPage, resetPageOffset)
                    nextPage.setContent(pageFactory.nextPage)
                    prevPage.setContent(pageFactory.prevPage)
                }
            }
        }
        callBack.screenOffTimerStart()
    }

    private fun updateAccessibilityPageText(text: String) {
        if (accessibilityPageText == text) return
        accessibilityPageText = text
        if (isAttachedToWindow) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    private fun upProgress() {
        curPage.setProgress(pageFactory.curPage)
    }

    /**
     * 更新滑动距离
     */
    fun upPageSlopSquare() {
        val pageTouchSlop = ReadConfig.pageTouchSlop
        this.pageSlopSquare = if (pageTouchSlop == 0) slopSquare else pageTouchSlop
        pageSlopSquare2 = this.pageSlopSquare * this.pageSlopSquare
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        ChapterProvider.upStyle()
        TipStyleProvider.upTipStyle()
        curPage.upStyle()
        prevPage.upStyle()
        nextPage.upStyle()
    }

    /**
     * 原子应用日夜主题颜色：先废弃旧正文录制缓存，再在同一主线程任务内更新
     * 背景、正文画笔和页眉页脚，避免新背景与旧文字位图出现在同一帧。
     */
    fun applyThemeColors() {
        ChapterProvider.upThemeColors()
        TipStyleProvider.upTipStyle()
        invalidateTextPage()
        ReadSessionState.updateBackground(width, height)
        curPage.apply {
            upThemeColors()
            upBg()
        }
        prevPage.apply {
            upThemeColors()
            upBg()
        }
        nextPage.apply {
            upThemeColors()
            upBg()
        }
        pageDelegate?.postInvalidate()
        invalidate()
    }

    /**
     * 更新背景
     */
    fun upBg() {
        ReadSessionState.updateBackground(width, height)
        curPage.upBg()
        prevPage.upBg()
        nextPage.upBg()
        // 与页面同底：下拉书签手势把 curPage 移开后，露出的区域仍是页面背景而非主题色。
        background = LayerDrawable(
            arrayOf(
                ReadSessionState.backgroundMeanColor.toDrawable(),
                ReadSessionState.background
            )
        )
    }

    /**
     * 更新背景透明度
     */
    fun upBgAlpha() {
        curPage.upBgAlpha()
        prevPage.upBgAlpha()
        nextPage.upBgAlpha()
    }

    /**
     * 更新时间信息
     */
    fun upTime() {
        curPage.upTime()
        prevPage.upTime()
        nextPage.upTime()
    }

    /**
     * 更新右上角书签角标
     */
    fun upBookmarkBadge() {
        curPage.upBookmarkBadge()
        prevPage.upBookmarkBadge()
        nextPage.upBookmarkBadge()
    }

    /**
     * 更新电量信息
     */
    fun upBattery(battery: Int) {
        curPage.upBattery(battery)
        prevPage.upBattery(battery)
        nextPage.upBattery(battery)
    }

    /**
     * 当前页内 行/列 对应的章节内位置（渲染面查询，翻页推进由外层负责）
     */
    fun posByLineColumn(line: Int, column: Int): Int {
        return curPage.textPage.getPosByLineColumn(line, column)
    }

    /**
     * @return 选择的文本
     */
    fun getSelectText(): String {
        return curPage.selectedText
    }

    fun getSelectTextPos(): Int {
        val selectStartPos = curPage.selectStartPos
        return curPage.textPage.getPosByLineColumn(
            selectStartPos.lineIndex,
            selectStartPos.columnIndex
        )
    }

    fun getCurVisiblePage(): TextPage {
        return curPage.getCurVisiblePage()
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        return curPage.getReadAloudPos()
    }

    fun invalidateTextPage() {
        if (!ReadConfig.optimizeRender) {
            return
        }
        pageFactory.run {
            prevPage.invalidateAll()
            curPage.invalidateAll()
            nextPage.invalidateAll()
            nextPlusPage.invalidateAll()
        }
    }

    /**
     * 供 [io.legado.app.ui.book.read.page.delegate.PageDelegate] 在翻到尽头时出站——
     * 翻页委托不持有 eventListener，经这里转发（Track D·D1）。
     */
    internal fun requestAutoPageStop() {
        eventListener.onEvent(ReaderEvent.AutoPageStop)
    }

    fun onScrollAnimStart() {
        autoPager.pause()
    }

    fun onScrollAnimStop() {
        autoPager.resume()
    }

    fun onPageChange() {
        autoPager.reset()
        submitRenderTask()
    }

    fun submitRenderTask() {
        if (!ReadConfig.optimizeRender) {
            return
        }
        curPage.submitRenderTask()
    }

    fun isLongScreenShot(): Boolean {
        return curPage.isLongScreenShot()
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upProgressThrottle.invoke()
    }

    override val pageIndex: Int get() = pageSource.durPageIndex

    override val currentChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) pageSource.textChapter(0) else null
        }

    override val nextChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) pageSource.textChapter(1) else null
        }

    override val prevChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) pageSource.textChapter(-1) else null
        }

    override fun hasNextChapter(): Boolean {
        return pageSource.durChapterIndex < pageSource.simulatedChapterSize - 1
    }

    override fun hasPrevChapter(): Boolean {
        return pageSource.durChapterIndex > 0
    }

    /**
     * 与宿主的**瞬时 UI 副作用**协作面（Track D·D1）。
     *
     * 业务意图一律走 [ReaderEvent]；这里只留不属于业务状态、由 View 直接驱动宿主的那几项，
     * 外加首帧放行门闩 `isInitFinish`（入站状态查询，随 D2 数据面一起处理）。
     */
    interface CallBack {
        val isInitFinish: Boolean
        fun screenOffTimerStart()
        fun showTextActionMenu()
        fun upSystemUiVisibility()
    }

    private companion object {
        /** 下滑切换书签所需的最小竖直位移。取得比翻页 slop 大，避免与轻微斜划抢手势。 */
        const val BOOKMARK_SWIPE_MIN_DISTANCE_DP = 80f

        /** 竖直位移需超过水平位移的这个倍数，才判定为下滑而非斜向翻页。 */
        const val VERTICAL_DOMINANCE_RATIO = 1.5f

        /** 顶部提示条相对页面顶部的距离。 */
        const val BOOKMARK_SWIPE_HINT_TOP_MARGIN_DP = 12

        /** 顶部提示条圆角半径。 */
        const val BOOKMARK_SWIPE_HINT_CORNER_RADIUS_DP = 24f

        /** 松手/取消后页面弹回原位的动画时长。 */
        const val BOOKMARK_SWIPE_SPRING_BACK_MS = 180L

        /** 下拉时页面最多跟随手指移动的比例，防止整页拖出屏幕。 */
        const val BOOKMARK_SWIPE_MAX_PULL_RATIO = 0.6f
    }
}
