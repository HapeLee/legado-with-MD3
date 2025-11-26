package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.screenshot

class FadePageDelegate(readView: ReadView) : PageDelegate(readView) {

    private var curRecorder = CanvasRecorderFactory.create()
    private var prevRecorder = CanvasRecorderFactory.create()
    private var nextRecorder = CanvasRecorderFactory.create()
    private val slopSquare get() = readView.pageSlopSquare2

    private var fadeProgress = 0f
    private val flipThreshold = 0.1f

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        fadeProgress = 0f
        setBitmap()
    }

    private fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevPage.screenshot(prevRecorder)
                curPage.screenshot(curRecorder)
            }
            PageDirection.NEXT -> {
                nextPage.screenshot(nextRecorder)
                curPage.screenshot(curRecorder)
            }
            else -> {
                curPage.screenshot(curRecorder)
            }
        }
    }

    private fun upRecorder() {
        curRecorder.recycle()
        prevRecorder.recycle()
        nextRecorder.recycle()
        curRecorder = CanvasRecorderFactory.create()
        prevRecorder = CanvasRecorderFactory.create()
        nextRecorder = CanvasRecorderFactory.create()
    }

    override fun onTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                readView.setStartPoint(event.x, event.y, false)
            }
            MotionEvent.ACTION_MOVE -> onScroll(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isMoved || mDirection == PageDirection.NONE) return
                // 超过阈值自动翻页，否则回弹
                val shouldFlip = fadeProgress >= flipThreshold
                if (shouldFlip) 1f else 0f
                isCancel = !shouldFlip
                onAnimStart(readView.defaultAnimationSpeed)
            }
        }
    }

    private fun onScroll(event: MotionEvent) {
        val focusX = event.x
        val deltaX = focusX - startX
        if (!isMoved) {
            val distance = deltaX * deltaX
            isMoved = distance > slopSquare
            if (isMoved) {
                animDirectionByDelta(deltaX)
                readView.setStartPoint(event.x, event.y, false)
            }
        }

        if (isMoved && mDirection != PageDirection.NONE) {
            fadeProgress = (kotlin.math.abs(deltaX) / viewWidth).coerceIn(0f, 1f)
            isRunning = true
            readView.setTouchPoint(focusX, startY)
            readView.invalidate()
        }
    }

    private fun animDirectionByDelta(deltaX: Float) {
        mDirection = if (deltaX > 0) {
            if (!hasPrev()) PageDirection.NONE else PageDirection.PREV
        } else {
            if (!hasNext()) PageDirection.NONE else PageDirection.NEXT
        }
        setBitmap()
    }

    override fun onDraw(canvas: Canvas) {
        curRecorder.draw(canvas)
        if (mDirection == PageDirection.NONE) {
            // 直接绘制 curPage，不使用 curRecorder
            curPage.draw(canvas)
            return
        }

        val alpha = (fadeProgress * 255).toInt().coerceIn(0, 255)
        val paint = Paint().apply { this.alpha = alpha }

        val save = canvas.saveLayer(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat(), paint)
        when (mDirection) {
            PageDirection.PREV -> prevRecorder.draw(canvas)
            PageDirection.NEXT -> nextRecorder.draw(canvas)
            else -> {}
        }
        canvas.restoreToCount(save)
    }

    override fun onAnimStart(animationSpeed: Int) {
        if (mDirection == PageDirection.NONE) return
        // 使用 scroller 做松手后的自动动画
        val start = (fadeProgress * 1000).toInt()
        val end = if (isCancel) 0 else 1000
        scroller.startScroll(start, 0, end - start, 0, animationSpeed)
        isStarted = true
        isRunning = true
        readView.invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            fadeProgress = scroller.currX / 1000f
            readView.invalidate()
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    override fun onAnimStop() {
        if (!isCancel) {
            // 更新当前页显示
            readView.fillPage(mDirection)
            // 延迟更新 curRecorder，确保 curPage 已经刷新
            readView.post {
                try {
                    curPage.screenshot(curRecorder)
                    readView.invalidate()
                } catch (_: Exception) {
                    // 捕获可能的异常，避免闪退
                }
            }
        }

        // 重置状态
        fadeProgress = 0f
        isRunning = false
        isMoved = false

        mDirection = PageDirection.NONE
        readView.setStartPoint(0f, 0f, false)
        readView.setTouchPoint(0f, 0f)
    }


    override fun abortAnim() {
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            scroller.abortAnimation()
            if (!isCancel) {
                readView.fillPage(mDirection)
                curPage.screenshot(curRecorder) // 同步 curRecorder
                readView.invalidate()
            }
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        if (!hasNext()) return
        setDirection(PageDirection.NEXT)
        fadeProgress = 0f
        isCancel = false
        onAnimStart(animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        if (!hasPrev()) return
        setDirection(PageDirection.PREV)
        fadeProgress = 0f
        isCancel = false
        onAnimStart(animationSpeed)
    }

    override fun onDown() {
        super.onDown()
        fadeProgress = 0f
        mDirection = PageDirection.NONE
        isMoved = false
        isCancel = false
        readView.setStartPoint(0f, 0f, false)
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        upRecorder()
    }

    override fun onDestroy() {
        super.onDestroy()
        curRecorder.recycle()
        prevRecorder.recycle()
        nextRecorder.recycle()
    }
}
